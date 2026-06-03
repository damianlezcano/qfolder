package org.q3s.p2p.ports;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.q3s.p2p.core.model.Event;

public interface EventStore {
	void append(Event event);
	List<Event> listEvents(String workspaceId);
	default List<Event> listEventsAfter(String workspaceId, Instant after) {
		if (after == null) return listEvents(workspaceId);
		Instant threshold = after.minusMillis(1);
		return listEvents(workspaceId).stream()
				.filter(e -> e.createdAt() != null && !e.createdAt().isBefore(threshold))
				.toList();
	}
	boolean hasEvent(String eventId);
	Optional<Event> getEvent(String eventId);
	List<Event> getMissingEvents(String workspaceId, Set<String> knownEventIds);
	Set<String> listEventIds(String workspaceId);
	boolean containsType(String workspaceId, String eventType);
}
