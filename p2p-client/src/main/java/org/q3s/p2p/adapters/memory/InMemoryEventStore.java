package org.q3s.p2p.adapters.memory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.ports.EventStore;

public class InMemoryEventStore implements EventStore {
	private final Map<String, Event> byId = new LinkedHashMap<>();
	private final Map<String, List<String>> byWorkspace = new LinkedHashMap<>();

	@Override
	public synchronized void append(Event event) {
		if (event == null || event.isEphemeral() || byId.containsKey(event.eventId())) return;
		byId.put(event.eventId(), event);
		byWorkspace.computeIfAbsent(event.workspaceId(), ignored -> new ArrayList<>()).add(event.eventId());
	}

	@Override
	public synchronized List<Event> listEvents(String workspaceId) {
		return byWorkspace.getOrDefault(workspaceId, List.of()).stream()
				.map(byId::get)
				.sorted(java.util.Comparator.comparing(Event::createdAt).thenComparing(Event::eventId))
				.toList();
	}

	@Override
	public synchronized boolean hasEvent(String eventId) {
		return byId.containsKey(eventId);
	}

	@Override
	public synchronized Optional<Event> getEvent(String eventId) {
		return Optional.ofNullable(byId.get(eventId));
	}

	@Override
	public synchronized List<Event> getMissingEvents(String workspaceId, Set<String> knownEventIds) {
		Set<String> known = knownEventIds == null ? Set.of() : knownEventIds;
		return listEvents(workspaceId).stream().filter(event -> !known.contains(event.eventId())).toList();
	}

	@Override
	public synchronized Set<String> listEventIds(String workspaceId) {
		return new LinkedHashSet<>(byWorkspace.getOrDefault(workspaceId, List.of()));
	}

	@Override
	public synchronized boolean containsType(String workspaceId, String eventType) {
		return listEvents(workspaceId).stream().anyMatch(event -> event.type().equals(eventType));
	}
}
