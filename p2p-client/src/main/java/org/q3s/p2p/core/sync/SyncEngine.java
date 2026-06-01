package org.q3s.p2p.core.sync;

import java.util.List;

import org.q3s.p2p.core.events.EventService;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.ports.EventStore;
import org.q3s.p2p.ports.NetworkAdapter;

public class SyncEngine {
	private final EventStore store;
	private final EventService events;
	private final NetworkAdapter network;

	public SyncEngine(EventStore store, NetworkAdapter network) {
		this(store, network, false);
	}

	public SyncEngine(EventStore store, NetworkAdapter network, boolean validateRemote) {
		this.store = store;
		this.events = new EventService(store, validateRemote);
		this.network = network;
	}

	public boolean receiveEvent(Event event) {
		boolean accepted = events.accept(event);
		if (accepted && network != null) network.broadcast(event);
		return accepted;
	}

	public void broadcastEvent(Event event) {
		if (events.accept(event) && network != null) network.broadcast(event);
	}

	public void applyReceivedEvents(List<Event> received) {
		for (Event event : received) receiveEvent(event);
	}

	public List<Event> missingFor(String workspaceId, java.util.Set<String> knownIds) {
		return store.getMissingEvents(workspaceId, knownIds);
	}
}
