package org.q3s.p2p.adapters.filesystem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.q3s.p2p.core.events.CoreEventCodec;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.ports.EventStore;

public class FileSystemEventStore implements EventStore {
	private final Path root;
	private final boolean workspaceScoped;

	public FileSystemEventStore(Path root) {
		this(root, false);
	}

	public FileSystemEventStore(Path root, boolean workspaceScoped) {
		this.root = root;
		this.workspaceScoped = workspaceScoped;
	}

	@Override
	public synchronized void append(Event event) {
		if (event == null || event.isEphemeral() || hasEvent(event.eventId())) return;
		try {
			Path dir = eventDir(event.workspaceId());
			Files.createDirectories(dir);
			Files.writeString(dir.resolve(event.eventId() + ".evt"), CoreEventCodec.toJson(event));
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo persistir evento " + event.eventId(), e);
		}
	}

	@Override
	public synchronized List<Event> listEvents(String workspaceId) {
		Path dir = eventDir(workspaceId);
		if (!Files.isDirectory(dir)) return List.of();
		try (var stream = Files.list(dir)) {
			List<Path> files = stream.filter(path -> path.getFileName().toString().endsWith(".evt"))
					.sorted(Comparator.comparing(path -> path.getFileName().toString())).toList();
			List<Event> events = new ArrayList<>();
			for (Path file : files) events.add(readEvent(file));
			events.sort(Comparator.comparing(Event::createdAt).thenComparing(Event::eventId));
			return events;
		} catch (Exception e) {
			throw new IllegalStateException("No se pudieron leer eventos", e);
		}
	}

	@Override
	public synchronized boolean hasEvent(String eventId) {
		try {
			if (!Files.isDirectory(root)) return false;
			if (workspaceScoped) return Files.exists(root.resolve("events").resolve(eventId + ".evt"));
			try (var workspaces = Files.list(root)) {
				return workspaces.anyMatch(path -> Files.exists(path.resolve("events").resolve(eventId + ".evt")));
			}
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo consultar evento", e);
		}
	}

	@Override
	public synchronized Optional<Event> getEvent(String eventId) {
		try {
			if (!Files.isDirectory(root)) return Optional.empty();
			if (workspaceScoped) {
				Path file = root.resolve("events").resolve(eventId + ".evt");
				return Files.exists(file) ? Optional.of(readEvent(file)) : Optional.empty();
			}
			try (var workspaces = Files.list(root)) {
				for (Path workspace : workspaces.toList()) {
					Path file = workspace.resolve("events").resolve(eventId + ".evt");
					if (Files.exists(file)) return Optional.of(readEvent(file));
				}
			}
			return Optional.empty();
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo leer evento", e);
		}
	}

	@Override
	public synchronized List<Event> getMissingEvents(String workspaceId, Set<String> knownEventIds) {
		Set<String> known = knownEventIds == null ? Set.of() : knownEventIds;
		return listEvents(workspaceId).stream().filter(event -> !known.contains(event.eventId())).toList();
	}

	@Override
	public synchronized Set<String> listEventIds(String workspaceId) {
		Set<String> ids = new LinkedHashSet<>();
		for (Event event : listEvents(workspaceId)) ids.add(event.eventId());
		return ids;
	}

	@Override
	public synchronized boolean containsType(String workspaceId, String eventType) {
		return listEvents(workspaceId).stream().anyMatch(event -> event.type().equals(eventType));
	}

	private Path eventDir(String workspaceId) {
		if (workspaceScoped) return root.resolve("events");
		return root.resolve(workspaceId).resolve("events");
	}

	private Event readEvent(Path file) {
		try {
			return CoreEventCodec.eventFromJson(Files.readString(file));
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo leer " + file, e);
		}
	}
}
