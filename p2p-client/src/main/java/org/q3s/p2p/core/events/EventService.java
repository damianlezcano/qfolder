package org.q3s.p2p.core.events;

import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.ports.EventStore;

public class EventService {
	private final EventStore store;
	private final boolean validateRemote;
	private final EventValidator validator;

	public EventService(EventStore store) {
		this(store, false);
	}

	public EventService(EventStore store, boolean validateRemote) {
		this.store = store;
		this.validateRemote = validateRemote;
		this.validator = new EventValidator(store);
	}

	public boolean accept(Event event) {
		if (event == null || event.isEphemeral() || store.hasEvent(event.eventId())) return false;
		if (validateRemote && !validator.isAcceptable(event)) return false;
		store.append(event);
		validator.invalidate(event.workspaceId());
		return true;
	}
}
