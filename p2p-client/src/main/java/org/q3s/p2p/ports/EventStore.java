package org.q3s.p2p.ports;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.q3s.p2p.core.model.Event;

public interface EventStore {
	void append(Event event);
	List<Event> listEvents(String workspaceId);
	boolean hasEvent(String eventId);
	Optional<Event> getEvent(String eventId);
	List<Event> getMissingEvents(String workspaceId, Set<String> knownEventIds);
	Set<String> listEventIds(String workspaceId);
	boolean containsType(String workspaceId, String eventType);
}
