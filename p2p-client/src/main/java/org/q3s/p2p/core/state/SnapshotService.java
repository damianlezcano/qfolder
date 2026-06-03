package org.q3s.p2p.core.state;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import org.q3s.p2p.core.events.CoreEventCodec;
import org.q3s.p2p.core.model.Event;

public class SnapshotService {
	private final Path root;

	public SnapshotService(Path root) {
		this.root = root;
	}

	public Path save(String workspaceId, List<Event> events) {
		try {
			Files.createDirectories(snapshotDir(workspaceId));
			Path file = snapshotDir(workspaceId).resolve(System.currentTimeMillis() + ".snapshot");
			Files.writeString(file, CoreEventCodec.encodeSyncEvents(events));
			return file;
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo guardar snapshot", e);
		}
	}

	public Optional<WorkspaceState> loadLatest(String workspaceId) {
		Path dir = snapshotDir(workspaceId);
		if (!Files.isDirectory(dir)) return Optional.empty();
		try (var files = Files.list(dir)) {
			Optional<Path> latest = files.filter(path -> path.getFileName().toString().endsWith(".snapshot"))
					.max(Comparator.comparing(path -> path.getFileName().toString()));
			if (latest.isEmpty()) return Optional.empty();
			return Optional.of(WorkspaceStateBuilder.fromEvents(CoreEventCodec.decodeSyncEvents(Files.readString(latest.get()))));
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo cargar snapshot", e);
		}
	}

	/**
	 * Devuelve los eventos del snapshot junto con su timestamp de creación (Instant del
	 * último evento del snapshot, o epoch si está vacío). Útil para implementar
	 * snapshot + delta replay: cargar el snapshot y luego pedir al eventStore solo
	 * los eventos con createdAt posterior.
	 */
	public Optional<SnapshotLoadResult> loadLatestWithTimestamp(String workspaceId) {
		Path dir = snapshotDir(workspaceId);
		if (!Files.isDirectory(dir)) return Optional.empty();
		try (var files = Files.list(dir)) {
			Optional<Path> latest = files.filter(path -> path.getFileName().toString().endsWith(".snapshot"))
					.max(Comparator.comparing(path -> path.getFileName().toString()));
			if (latest.isEmpty()) return Optional.empty();
			List<Event> events = CoreEventCodec.decodeSyncEvents(Files.readString(latest.get()));
			Instant lastTimestamp = events.isEmpty() ? Instant.EPOCH
					: events.stream().map(Event::createdAt).max(Comparator.naturalOrder()).orElse(Instant.EPOCH);
			return Optional.of(new SnapshotLoadResult(events, lastTimestamp));
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo cargar snapshot", e);
		}
	}

	private Path snapshotDir(String workspaceId) {
		return root.resolve(workspaceId).resolve("snapshots");
	}

	public record SnapshotLoadResult(List<Event> snapshotEvents, Instant snapshotTimestamp) {}
}
