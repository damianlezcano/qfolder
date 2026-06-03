package org.q3s.p2p.adapters.network;

import java.security.MessageDigest;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.java_websocket.WebSocket;
import org.q3s.p2p.core.app.CoreApplicationService;
import org.q3s.p2p.core.files.DistributedChunkPlanner;
import org.q3s.p2p.core.model.FileMetadata;
import org.q3s.p2p.model.Event;
import org.q3s.p2p.model.QFile;
import org.q3s.p2p.model.User;
import org.q3s.p2p.model.util.EventUtils;

public class CoreChunkTransferCoordinator {
	private final CoreApplicationService core;
	private final Supplier<User> localUser;
	private static final int MAX_RETRIES = 5;

	private final Consumer<Event> outbound;
	private final ProgressSink progress;
	private final CompletionSink completed;
	private final BiConsumer<String, String> debug;
	private final BiConsumer<QFile, String> fallback;
	private final ScheduledExecutorService retries = Executors.newSingleThreadScheduledExecutor(r -> {
		Thread thread = new Thread(r, "core-chunk-retries");
		thread.setDaemon(true);
		return thread;
	});

	private final Map<String, FileMetadata> files = new LinkedHashMap<>();
	private final Map<String, QFile> requests = new LinkedHashMap<>();
	private final Map<String, Map<String, Set<String>>> availability = new LinkedHashMap<>();
	private final Map<String, Map<String, byte[]>> receivedChunks = new LinkedHashMap<>();
	private final Map<String, Set<String>> requestedChunks = new LinkedHashMap<>();
	private final Map<String, Integer> retryCounts = new LinkedHashMap<>();
	private final Map<String, ScheduledFuture<?>> retryFutures = new LinkedHashMap<>();
	private final Set<String> seenAvailabilityResponses = ConcurrentHashMap.newKeySet();
	private final Map<String, TransferStatus> terminalTransfers = new ConcurrentHashMap<>();
	private final Object lock = new Object();

	public CoreChunkTransferCoordinator(CoreApplicationService core, Supplier<User> localUser, Consumer<Event> outbound,
			ProgressSink progress, CompletionSink completed, BiConsumer<QFile, String> fallback,
			BiConsumer<String, String> debug) {
		this.core = core;
		this.localUser = localUser;
		this.outbound = outbound;
		this.progress = progress;
		this.completed = completed;
		this.fallback = fallback;
		this.debug = debug == null ? (message, ignored) -> {} : debug;
	}

	public boolean request(QFile file) {
		try {
			FileMetadata metadata = findCoreFile(file);
			if (metadata == null || metadata.chunks() == null || metadata.chunks().isEmpty()) return false;
			String transferId = file.getTransferId();
			debug.accept("[CHUNK] request '" + metadata.name() + "'", "transferId=" + transferId + " chunks=" + (metadata.chunks() != null ? metadata.chunks().size() : 0));
			synchronized (lock) {
				if (terminalTransfers.containsKey(transferId)) return false;
				files.put(transferId, metadata);
				requests.put(transferId, file);
				availability.put(transferId, new LinkedHashMap<>());
				receivedChunks.put(transferId, new LinkedHashMap<>());
				requestedChunks.put(transferId, new HashSet<>());
				retryCounts.put(transferId, 0);
				for (String chunk : core.availableChunks(metadata.chunks())) {
					core.readChunk(chunk).ifPresent(bytes -> receivedChunks.get(transferId).put(chunk, bytes));
				}
				if (receivedChunks.get(transferId).keySet().containsAll(metadata.chunks())) {
					finish(transferId, metadata);
					return true;
				}
			}
			outbound.accept(new Event(CoreChunkTransferProtocol.AVAILABILITY_REQUEST, localUser.get(),
					CoreChunkTransferProtocol.availabilityRequest(transferId, metadata.fileId(), metadata.chunks())));
			progress.update(transferId, "Buscando chunks de " + metadata.name(), receivedChunks.get(transferId) != null ? receivedChunks.get(transferId).size() : 0, metadata.chunks().size());
			scheduleRetry(transferId);
			return true;
		} catch (Exception e) {
			debug.accept("No se pudo iniciar descarga distribuida", e.getMessage());
			return false;
		}
	}

	public boolean cancel(String transferId) {
		if (transferId == null || transferId.isBlank()) return false;
		synchronized (lock) {
			if (!files.containsKey(transferId) && !requests.containsKey(transferId)) return false;
			terminalTransfers.put(transferId, TransferStatus.FAILED);
			cleanup(transferId);
			debug.accept("[CHUNK] cancel", "transferId=" + transferId);
			return true;
		}
	}

	public void shutdown() {
		synchronized (lock) {
			for (String transferId : new java.util.ArrayList<>(files.keySet())) {
				terminalTransfers.put(transferId, TransferStatus.FAILED);
				cleanup(transferId);
			}
			for (ScheduledFuture<?> future : retryFutures.values()) {
				if (future != null && !future.isDone()) future.cancel(false);
			}
			retryFutures.clear();
		}
		retries.shutdownNow();
	}

	public boolean handle(Event event, WebSocket directConn) {
		if (event == null || event.getName() == null) return false;
		return switch (event.getName()) {
			case CoreChunkTransferProtocol.AVAILABILITY_REQUEST -> { respondAvailability(event); yield true; }
			case CoreChunkTransferProtocol.AVAILABILITY_RESPONSE -> { receiveAvailability(event); yield true; }
			case CoreChunkTransferProtocol.CHUNK_REQUEST -> { sendChunk(event, directConn); yield true; }
			case CoreChunkTransferProtocol.CHUNK_RESPONSE -> { receiveChunk(event); yield true; }
			default -> false;
		};
	}

	private void respondAvailability(Event event) {
		try {
			if (event.getUser() == null || event.getUser().equals(localUser.get())) return;
			var request = CoreChunkTransferProtocol.parseAvailabilityRequest(event.getResponse());
			FileMetadata meta = metadataByFileId(request.fileId());
			List<String> filtered;
			if (meta == null) {
				filtered = List.of();
			} else {
				List<String> available = new java.util.ArrayList<>(core.availableChunks(request.chunks()));
				available.retainAll(meta.chunks());
				filtered = available;
			}
			debug.accept("Disponibilidad de chunks para " + request.fileId(), filtered.size() + "/" + request.chunks().size() + " chunks disponibles");
			if (!filtered.isEmpty()) {
				debug.accept("[CHUNK] respondAvailability -> " + event.getUser().getId(), "transferId=" + request.transferId() + " chunks=" + filtered.size());
				outbound.accept(new Event("__to:" + event.getUser().getId() + ":" + CoreChunkTransferProtocol.AVAILABILITY_RESPONSE,
						localUser.get(), CoreChunkTransferProtocol.availabilityResponse(request.transferId(), localUser.get().getId(), filtered)));
			} else {
				debug.accept("No hay chunks disponibles para " + request.fileId(), "skip");
			}
		} catch (Exception e) {
			debug.accept("No se pudo responder disponibilidad de chunks", e.getMessage());
		}
	}

	private void receiveAvailability(Event event) {
		try {
			var response = CoreChunkTransferProtocol.parseAvailabilityResponse(event.getResponse());
			String dedupKey = response.transferId() + ":" + response.peerId();
			if (!seenAvailabilityResponses.add(dedupKey)) {
				debug.accept("[CHUNK] receiveAvailability skip duplicado", "transferId=" + response.transferId() + " peer=" + response.peerId());
				return;
			}
			synchronized (lock) {
				if (terminalTransfers.containsKey(response.transferId())) return;
				debug.accept("[CHUNK] receiveAvailability de " + response.peerId(), "transferId=" + response.transferId() + " chunks=" + response.chunks().size() + " files.containsKey=" + files.containsKey(response.transferId()));
				Map<String, Set<String>> byChunk = availability.computeIfAbsent(response.transferId(), ignored -> new LinkedHashMap<>());
				for (String chunk : response.chunks()) byChunk.computeIfAbsent(chunk, ignored -> new HashSet<>()).add(response.peerId());
			}
			plan(response.transferId());
		} catch (Exception e) {
			debug.accept("No se pudo procesar disponibilidad de chunks", e.getMessage());
		}
	}

	private void plan(String transferId) {
		List<Event> pending = new java.util.ArrayList<>();
		synchronized (lock) {
			if (terminalTransfers.containsKey(transferId)) return;
			FileMetadata metadata = files.get(transferId);
			if (metadata == null) {
				debug.accept("[CHUNK] plan skip - metadata null", "transferId=" + transferId);
				return;
			}
			Map<String, List<String>> plan = new DistributedChunkPlanner().planDownloads(metadata.chunks(), availability.get(transferId));
			Set<String> requested = requestedChunks.computeIfAbsent(transferId, ignored -> new HashSet<>());
			Set<String> received = receivedChunks.getOrDefault(transferId, Map.of()).keySet();
			int planPeers = plan.size();
			int planChunks = plan.values().stream().mapToInt(List::size).sum();
			debug.accept("[CHUNK] plan " + metadata.name(), "transferId=" + transferId + " peers=" + planPeers + " chunks=" + planChunks + " alreadyReceived=" + received.size() + " total=" + metadata.chunks().size());
			progress.update(transferId, "Descargando chunks de " + metadata.name(), received.size(), metadata.chunks().size());
			for (Map.Entry<String, List<String>> entry : plan.entrySet()) {
				for (String chunk : entry.getValue()) {
					if (received.contains(chunk) || !requested.add(chunk)) continue;
					debug.accept("[CHUNK] request chunk " + chunk, "peer=" + entry.getKey() + " transferId=" + transferId);
					pending.add(new Event("__to:" + entry.getKey() + ":" + CoreChunkTransferProtocol.CHUNK_REQUEST, localUser.get(),
							CoreChunkTransferProtocol.chunkRequest(transferId, metadata.fileId(), chunk)));
				}
			}
		}
		for (Event evt : pending) outbound.accept(evt);
		scheduleRetry(transferId);
	}

	private void scheduleRetry(String transferId) {
		synchronized (lock) {
			ScheduledFuture<?> existing = retryFutures.remove(transferId);
			if (existing != null && !existing.isDone()) existing.cancel(false);
			if (terminalTransfers.containsKey(transferId)) return;
			ScheduledFuture<?> future = retries.schedule(() -> retryMissing(transferId), 4, TimeUnit.SECONDS);
			retryFutures.put(transferId, future);
		}
	}

	private void retryMissing(String transferId) {
		boolean shouldFallback = false;
		String fallbackReason = null;
		synchronized (lock) {
			retryFutures.remove(transferId);
			if (terminalTransfers.containsKey(transferId)) return;
			FileMetadata metadata = files.get(transferId);
			if (metadata == null) {
				debug.accept("[CHUNK] retryMissing skip - metadata null", "transferId=" + transferId);
				return;
			}
			Set<String> received = receivedChunks.getOrDefault(transferId, Map.of()).keySet();
			if (received.containsAll(metadata.chunks())) {
				debug.accept("[CHUNK] retryMissing skip - all received", "transferId=" + transferId);
				return;
			}
			int retriesSoFar = retryCounts.getOrDefault(transferId, 0);
			if (retriesSoFar >= MAX_RETRIES) {
				shouldFallback = true;
				fallbackReason = "No se recibieron todos los chunks de " + metadata.name();
				debug.accept("[CHUNK] fallback after " + MAX_RETRIES + " retries", "transferId=" + transferId);
			} else {
				retryCounts.put(transferId, retriesSoFar + 1);
				Set<String> requested = requestedChunks.computeIfAbsent(transferId, ignored -> new HashSet<>());
				for (String chunk : metadata.chunks()) if (!received.contains(chunk)) requested.remove(chunk);
				debug.accept("Reintentando chunks faltantes de " + metadata.name(), "intento " + (retriesSoFar + 1));
			}
		}
		if (shouldFallback) {
			fallback(transferId, fallbackReason);
		} else {
			plan(transferId);
		}
	}

	private void sendChunk(Event event, WebSocket directConn) {
		try {
			var request = CoreChunkTransferProtocol.parseChunkRequest(event.getResponse());
			if (event.getUser() == null) return;
			FileMetadata meta = metadataByFileId(request.fileId());
			if (meta == null || !meta.chunks().contains(request.chunkHash())) {
				debug.accept("[CHUNK] sendChunk skip - chunk fuera de metadata", "fileId=" + request.fileId() + " chunk=" + request.chunkHash());
				return;
			}
			byte[] bytes = core.readChunk(request.chunkHash()).orElse(null);
			if (bytes == null) return;
			Event response = new Event(CoreChunkTransferProtocol.CHUNK_RESPONSE, localUser.get(),
					CoreChunkTransferProtocol.chunkResponse(request.transferId(), request.fileId(), request.chunkHash(), bytes));
			if (directConn != null) {
				directConn.send(EventUtils.toJsonBase64(response));
				debug.accept("[CHUNK] sendChunk direct", "chunk=" + request.chunkHash() + " bytes=" + bytes.length + " to=" + event.getUser().getId());
			} else {
				outbound.accept(new Event("__to:" + event.getUser().getId() + ":" + CoreChunkTransferProtocol.CHUNK_RESPONSE, localUser.get(), response.getResponse()));
				debug.accept("[CHUNK] sendChunk via outbound", "chunk=" + request.chunkHash() + " bytes=" + bytes.length + " to=" + event.getUser().getId());
			}
		} catch (Exception e) {
			debug.accept("No se pudo enviar chunk core", e.getMessage());
		}
	}

	private void receiveChunk(Event event) {
		try {
			var response = CoreChunkTransferProtocol.parseChunkResponse(event.getResponse());
			FileMetadata metadata;
			boolean allReceived = false;
			synchronized (lock) {
				metadata = files.get(response.transferId());
				if (metadata == null) {
					if (terminalTransfers.containsKey(response.transferId())) return;
					debug.accept("[CHUNK] receiveChunk skip - metadata null", "transferId=" + response.transferId() + " chunk=" + response.chunkHash());
					return;
				}
				if (!metadata.fileId().equals(response.fileId())) {
					debug.accept("[CHUNK] receiveChunk skip - fileId mismatch", "transferId=" + response.transferId());
					return;
				}
				if (!metadata.chunks().contains(response.chunkHash())) {
					debug.accept("[CHUNK] receiveChunk skip - chunk fuera de metadata", "chunk=" + response.chunkHash());
					return;
				}
				String actualHash = sha256(response.bytes());
				if (!response.chunkHash().equals(actualHash)) {
					debug.accept("[CHUNK] receiveChunk skip - hash invalido", "chunk=" + response.chunkHash() + " actual=" + actualHash);
					return;
				}
				Map<String, byte[]> received = receivedChunks.computeIfAbsent(response.transferId(), ignored -> new LinkedHashMap<>());
				if (received.containsKey(response.chunkHash())) return;
				core.storeChunk(metadata.fileId(), response.chunkHash(), response.bytes());
				received.put(response.chunkHash(), response.bytes());
				debug.accept("[CHUNK] receiveChunk " + metadata.name(), "chunk=" + response.chunkHash() + " progreso=" + received.size() + "/" + metadata.chunks().size());
				progress.update(response.transferId(), "Descargando chunks de " + metadata.name(), received.size(), metadata.chunks().size());
				if (received.keySet().containsAll(metadata.chunks())) {
					debug.accept("[CHUNK] all chunks received", "transferId=" + response.transferId());
					allReceived = true;
				}
			}
			if (allReceived) finish(response.transferId(), metadata);
		} catch (Exception e) {
			debug.accept("No se pudo recibir chunk core", e.getMessage());
		}
	}

	private void finish(String transferId, FileMetadata metadata) {
		QFile original = null;
		byte[] bytes = null;
		boolean hashOk = false;
		String failReason = null;
		synchronized (lock) {
			if (terminalTransfers.putIfAbsent(transferId, TransferStatus.COMPLETING) != null) {
				debug.accept("[CHUNK] finish skip - ya terminal", "transferId=" + transferId);
				return;
			}
			debug.accept("[CHUNK] finish start", "name=" + metadata.name() + " transferId=" + transferId);
			original = requests.get(transferId);
			try {
				bytes = reconstructInMetadataOrder(metadata);
			} catch (Exception e) {
				terminalTransfers.put(transferId, TransferStatus.FAILED);
				debug.accept("[CHUNK] finish reconstruct error", e.getMessage());
				cleanup(transferId);
				failReason = "Error al reconstruir " + metadata.name();
				hashOk = false;
			}
			if (bytes != null && !metadata.hash().equals(sha256(bytes))) {
				terminalTransfers.put(transferId, TransferStatus.FAILED);
				debug.accept("[CHUNK] finish hash mismatch", "name=" + metadata.name());
				cleanup(transferId);
				failReason = "Hash invalido en descarga distribuida de " + metadata.name();
				hashOk = false;
			}
			if (bytes != null && metadata.hash().equals(sha256(bytes))) {
				terminalTransfers.put(transferId, TransferStatus.COMPLETED);
				cleanup(transferId);
				hashOk = true;
			}
		}
		if (!hashOk && original != null && failReason != null) {
			fallback.accept(original, failReason);
		} else if (hashOk) {
			completed.complete(transferId, metadata, original, bytes);
		}
	}

	private byte[] reconstructInMetadataOrder(FileMetadata metadata) {
		java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
		for (String hash : metadata.chunks()) {
			byte[] bytes = core.readChunk(hash).orElseThrow(() ->
					new IllegalStateException("Falta chunk " + hash + " de " + metadata.name()));
			out.writeBytes(bytes);
		}
		return out.toByteArray();
	}

	private void fallback(String transferId, String reason) {
		QFile original;
		synchronized (lock) {
			TransferStatus prior = terminalTransfers.get(transferId);
			if (prior == TransferStatus.COMPLETED || prior == TransferStatus.FAILED) return;
			if (terminalTransfers.putIfAbsent(transferId, TransferStatus.FAILED) != null) return;
			original = requests.get(transferId);
			debug.accept("[CHUNK] fallback", "transferId=" + transferId + " reason=" + reason + " file=" + (original != null ? original.getName() : "null"));
			cleanup(transferId);
		}
		if (original != null) fallback.accept(original, reason);
	}

	private void cleanup(String transferId) {
		files.remove(transferId);
		requests.remove(transferId);
		availability.remove(transferId);
		receivedChunks.remove(transferId);
		requestedChunks.remove(transferId);
		retryCounts.remove(transferId);
		ScheduledFuture<?> future = retryFutures.remove(transferId);
		if (future != null && !future.isDone()) future.cancel(false);
		seenAvailabilityResponses.removeIf(key -> key.startsWith(transferId + ":"));
	}

	private FileMetadata findCoreFile(QFile file) {
		if (file == null) return null;
		String coreFileId = coreFileId(file);
		if (coreFileId != null) {
			for (FileMetadata metadata : core.currentState().files().values()) {
				if (coreFileId.equals(metadata.fileId())) return metadata;
			}
		}
		for (FileMetadata metadata : core.currentState().files().values()) {
			if (metadata.name().equals(file.getName()) && metadata.size() == file.getSize()) return metadata;
		}
		return null;
	}

	private String coreFileId(QFile file) {
		String md5 = file.getMd5();
		if (md5 == null || !md5.startsWith("core:")) return null;
		String id = md5.substring("core:".length()).trim();
		return id.isEmpty() ? null : id;
	}

	private String sha256(byte[] bytes) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
		} catch (Exception e) {
			throw new IllegalStateException(e);
		}
	}

	private enum TransferStatus { COMPLETING, COMPLETED, FAILED }

	private FileMetadata metadataByFileId(String fileId) {
		if (fileId == null || fileId.isBlank() || core == null) return null;
		return core.currentState().files().values().stream()
				.filter(m -> fileId.equals(m.fileId()))
				.findFirst().orElse(null);
	}

	public interface ProgressSink {
		void update(String transferId, String label, int current, int total);
	}

	public interface CompletionSink {
		void complete(String transferId, FileMetadata metadata, QFile request, byte[] bytes);
	}
}
