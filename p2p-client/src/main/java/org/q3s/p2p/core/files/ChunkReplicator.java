package org.q3s.p2p.core.files;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import org.q3s.p2p.core.events.EventTypes;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.model.FileMetadata;
import org.q3s.p2p.core.state.WorkspaceState;
import org.q3s.p2p.core.state.WorkspaceStateBuilder;
import org.q3s.p2p.ports.EventStore;
import org.q3s.p2p.ports.FileChunkStore;

/**
 * Replicador opcional de chunks. Cuando se activa, monitorea los eventos
 * file.shared entrantes y notifica a un Consumer<FileMetadata> para que el caller
 * pueda descargar los chunks via CoreChunkTransferCoordinator y mantenerlos
 * localmente. Asi, un peer puede tener disponibilidad offline de archivos que
 * fueron anunciados en el workspace.
 *
 * Modo opt-in: usar enable()/disable() o el flag autoCache en el constructor.
 */
public class ChunkReplicator {

	private final EventStore store;
	private final FileChunkStore chunks;
	private volatile boolean autoCache = false;
	private final List<Consumer<FileMetadata>> listeners = new CopyOnWriteArrayList<>();

	public ChunkReplicator(EventStore store, FileChunkStore chunks) {
		this.store = store;
		this.chunks = chunks;
	}

	public void enable() { this.autoCache = true; }
	public void disable() { this.autoCache = false; }
	public boolean isEnabled() { return autoCache; }

	public void onFileAvailable(Consumer<FileMetadata> listener) {
		if (listener != null) listeners.add(listener);
	}

	/**
	 * Procesa eventos nuevos para detectar archivos que vale la pena cachear.
	 * Idempotente: solo notifica por archivos que tienen chunks faltantes.
	 * Retorna la cantidad de FileMetadata nuevos detectados.
	 */
	public int processEvents(String workspaceId, Iterable<Event> events) {
		if (!autoCache) return 0;
		int newFiles = 0;
		for (Event event : events) {
			if (!EventTypes.FILE_SHARED.equals(event.type())) continue;
			FileMetadata metadata = metadataFor(workspaceId, event);
			if (metadata == null) continue;
			if (metadata.chunks().isEmpty()) continue;
			if (chunks.listChunks(metadata.fileId()).containsAll(metadata.chunks())) continue;
			notify(metadata);
			newFiles++;
		}
		return newFiles;
	}

	/**
	 * Procesa el estado actual del workspace. Conveniente para el startup: si
	 * autoCache esta activo, dispara listeners por todos los archivos compartidos
	 * que aun no tenemos localmente.
	 */
	public int processCurrentState(String workspaceId) {
		if (!autoCache) return 0;
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents(workspaceId));
		int newFiles = 0;
		for (FileMetadata metadata : state.files().values()) {
			if (metadata.chunks().isEmpty()) continue;
			if (chunks.listChunks(metadata.fileId()).containsAll(metadata.chunks())) continue;
			notify(metadata);
			newFiles++;
		}
		return newFiles;
	}

	/**
	 * Devuelve los chunks que faltan localmente para reconstruir un archivo.
	 * Util cuando un peer ya anuncio el archivo y queremos saber que descargar.
	 */
	public List<String> missingChunks(FileMetadata metadata) {
		if (metadata == null || metadata.chunks() == null) return List.of();
		Set<String> have = Set.copyOf(chunks.listChunks(metadata.fileId()));
		return metadata.chunks().stream().filter(c -> !have.contains(c)).toList();
	}

	private FileMetadata metadataFor(String workspaceId, Event event) {
		if (!workspaceId.equals(event.workspaceId())) return null;
		java.util.Map<String, Object> p = event.payload();
		if (p == null) return null;
		@SuppressWarnings("unchecked")
		List<String> chunkList = (List<String>) p.getOrDefault("chunks", List.of());
		return new FileMetadata(String.valueOf(p.get("file_id")), String.valueOf(p.get("name")),
				((Number) p.getOrDefault("size", 0)).longValue(),
				String.valueOf(p.getOrDefault("hash", "")),
				chunkList, String.valueOf(p.getOrDefault("shared_by", "")),
				Boolean.parseBoolean(String.valueOf(p.getOrDefault("chat_attachment", "false"))));
	}

	private void notify(FileMetadata metadata) {
		for (Consumer<FileMetadata> listener : listeners) {
			try { listener.accept(metadata); } catch (Exception ignored) { ignored.getMessage(); }
		}
	}
}
