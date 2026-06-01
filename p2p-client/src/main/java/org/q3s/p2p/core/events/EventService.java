package org.q3s.p2p.core.events;

import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.ports.EventStore;

public class EventService {
	private final EventStore store;
	private final boolean validateRemote;

	public EventService(EventStore store) {
		this(store, false);
	}

	public EventService(EventStore store, boolean validateRemote) {
		this.store = store;
		this.validateRemote = validateRemote;
	}

	public boolean accept(Event event) {
		if (event == null || event.isEphemeral() || store.hasEvent(event.eventId())) return false;
		if (validateRemote && !new EventValidator(store).isAcceptable(event)) return false;
		store.append(event);
		return true;
	}
}
