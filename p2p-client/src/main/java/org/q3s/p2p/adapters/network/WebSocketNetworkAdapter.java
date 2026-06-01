package org.q3s.p2p.adapters.network;

import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.q3s.p2p.core.events.CoreEventCodec;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.ports.NetworkAdapter;
import org.q3s.p2p.model.User;

public class WebSocketNetworkAdapter implements NetworkAdapter {
	public static final String CORE_EVENT_NAME = "Core event";
	public static final String CORE_SYNC_REQUEST_NAME = "Core sync request";
	public static final String CORE_SYNC_RESPONSE_NAME = "Core sync response";
	public static final String CORE_PAYLOAD_VERSION = CoreEventCodec.EVENT_VERSION;
	public static final String CORE_SYNC_PAYLOAD_VERSION = CoreEventCodec.SYNC_VERSION;

	private final Consumer<org.q3s.p2p.model.Event> outbound;
	private final Supplier<User> localUser;
	private final Supplier<Set<String>> peers;

	public WebSocketNetworkAdapter(Consumer<org.q3s.p2p.model.Event> outbound, Supplier<User> localUser,
			Supplier<Set<String>> peers) {
		this.outbound = outbound;
		this.localUser = localUser;
		this.peers = peers == null ? Set::of : peers;
	}

	@Override
	public void send(String peerId, Event event) {
		if (peerId == null || peerId.isBlank()) return;
		outbound.accept(toLegacyEvent("__to:" + peerId + ":" + CORE_EVENT_NAME, event));
	}

	@Override
	public void broadcast(Event event) {
		outbound.accept(toLegacyEvent(CORE_EVENT_NAME, event));
	}

	@Override
	public Set<String> peers() {
		return peers.get();
	}

	public static Event decode(org.q3s.p2p.model.Event event) {
		if (event == null || !CORE_EVENT_NAME.equals(event.getName()) || event.getResponse() == null) return null;
		return CoreEventCodec.decodeEventPayload(event.getResponse());
	}

	public org.q3s.p2p.model.Event syncRequest(Set<String> knownEventIds) {
		return new org.q3s.p2p.model.Event(CORE_SYNC_REQUEST_NAME, localUser.get(), CoreEventCodec.encodeKnownIds(knownEventIds));
	}

	public static String encodeKnownEventIds(Set<String> knownEventIds) {
		return CoreEventCodec.encodeKnownIds(knownEventIds);
	}

	public org.q3s.p2p.model.Event syncResponse(String peerId, List<Event> events) {
		return new org.q3s.p2p.model.Event("__to:" + peerId + ":" + CORE_SYNC_RESPONSE_NAME, localUser.get(), CoreEventCodec.encodeSyncEvents(events));
	}

	@SuppressWarnings("unchecked")
	public static Set<String> decodeKnownEventIds(org.q3s.p2p.model.Event event) {
		if (event == null || !CORE_SYNC_REQUEST_NAME.equals(event.getName()) || event.getResponse() == null) return Set.of();
		return CoreEventCodec.decodeKnownIds(event.getResponse());
	}

	@SuppressWarnings("unchecked")
	public static List<Event> decodeEvents(org.q3s.p2p.model.Event event) {
		if (event == null || !CORE_SYNC_RESPONSE_NAME.equals(event.getName()) || event.getResponse() == null) return List.of();
		return CoreEventCodec.decodeSyncEvents(event.getResponse());
	}

	private org.q3s.p2p.model.Event toLegacyEvent(String name, Event event) {
		return new org.q3s.p2p.model.Event(name, localUser.get(), CoreEventCodec.encodeEventPayload(event));
	}

	public static String encodeCoreEvent(Event event) {
		return CoreEventCodec.encodeEventPayload(event);
	}

	public static String encodeSyncPayload(java.util.List<Event> events) {
		return CoreEventCodec.encodeSyncEvents(events);
	}
}
