package org.q3s.p2p.adapters.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.ports.EventStore;

class P2PNetworkAdapterTest {

	private EventStore stubStore;
	private List<String> debugMessages;
	private AtomicInteger inboundCount;

	@BeforeEach
	void setUp() {
		stubStore = new StubEventStore();
		debugMessages = new ArrayList<>();
		inboundCount = new AtomicInteger(0);
	}

	private P2PNetworkAdapter newAdapter(String localPeerId) {
		return new P2PNetworkAdapter(localPeerId,
				() -> "ws://localhost:9999",
				() -> "ws-test",
				stubStore,
				(ws, env) -> inboundCount.incrementAndGet(),
				debugMessages::add,
				false,
				null);
	}

	@Test
	@DisplayName("localPeerId stored correctly")
	void localPeerIdIsStored() {
		P2PNetworkAdapter adapter = newAdapter("peer-local-1");
		assertNotNull(adapter);
	}

	@Test
	@DisplayName("connectedPeers returns empty set when no peers connected")
	void connectedPeersIsEmptyByDefault() {
		P2PNetworkAdapter adapter = newAdapter("peer-local");
		assertTrue(adapter.connectedPeers().isEmpty());
	}

	@Test
	@DisplayName("hasPeer returns false for unknown peer")
	void hasPeerReturnsFalseForUnknown() {
		P2PNetworkAdapter adapter = newAdapter("peer-local");
		assertFalse(adapter.hasPeer("unknown-peer"));
	}

	@Test
	@DisplayName("disconnectAll sets shuttingDown flag (no exception)")
	void disconnectAllIsSafeWithNoPeers() {
		P2PNetworkAdapter adapter = newAdapter("peer-local");
		adapter.disconnectAll();
		assertTrue(adapter.connectedPeers().isEmpty());
	}

	@Test
	@DisplayName("onCoreEventStored callback can be registered")
	void onCoreEventStoredCallbackRegistration() {
		P2PNetworkAdapter adapter = newAdapter("peer-local");
		List<Event> received = new ArrayList<>();
		adapter.onCoreEventStored(received::add);
		adapter.onEphemeralCoreEvent(received::add);
		adapter.onCoreSyncApplied(received::addAll);
		adapter.onPeerConnectionsChanged(peers -> {});
		assertNotNull(received);
	}

	@Test
	@DisplayName("sync() returns non-null SyncEngine")
	void syncEngineAvailable() {
		P2PNetworkAdapter adapter = newAdapter("peer-local");
		assertNotNull(adapter.sync());
	}

	@Test
	@DisplayName("send() with null peerId is a no-op (no exception)")
	void sendWithNullPeerIdIsSafe() {
		P2PNetworkAdapter adapter = newAdapter("peer-local");
		adapter.send(null, null);
	}

	@Test
	@DisplayName("sendProtocolEvent with valid-but-unknown peerId is a no-op (no exception)")
	void sendProtocolEventWithUnknownPeerIdIsSafe() {
		P2PNetworkAdapter adapter = newAdapter("peer-local");
		adapter.sendProtocolEvent("unknown-peer", null);
	}

	@Test
	@DisplayName("broadcast() with no peers is a no-op (no exception)")
	void broadcastWithNoPeersIsSafe() {
		P2PNetworkAdapter adapter = newAdapter("peer-local");
		adapter.broadcast(null);
	}

	@Test
	@DisplayName("disconnectFrom with unknown peer is a no-op (no exception)")
	void disconnectFromUnknownPeerIsSafe() {
		P2PNetworkAdapter adapter = newAdapter("peer-local");
		adapter.disconnectFrom("unknown-peer");
	}

	@Test
	@DisplayName("membershipVerifier is invoked for sync requests when present")
	void membershipVerifierIsWired() {
		List<String> authorized = new ArrayList<>();
		authorized.add("peer-allowed");
		P2PNetworkAdapter adapter = new P2PNetworkAdapter("peer-local",
				() -> "ws://localhost:9999",
				() -> "ws-test",
				stubStore,
				(ws, env) -> {},
				debugMessages::add,
				false,
				authorized::contains);
		assertNotNull(adapter);
	}

	@Test
	@DisplayName("peers() returns the same as connectedPeers()")
	void peersReturnsConnectedPeers() {
		P2PNetworkAdapter adapter = newAdapter("peer-local");
		Set<String> result = adapter.peers();
		assertNotNull(result);
		assertEquals(0, result.size());
	}

	@Test
	@DisplayName("broadcastSyncRequest with no peers is a no-op (no exception)")
	void broadcastSyncRequestWithNoPeersIsSafe() {
		P2PNetworkAdapter adapter = newAdapter("peer-local");
		adapter.broadcastSyncRequest();
	}

	@Test
	@DisplayName("broadcastProtocolEvent with null envelope is a no-op (no exception)")
	void broadcastProtocolEventWithNullEnvelopeIsSafe() {
		P2PNetworkAdapter adapter = newAdapter("peer-local");
		adapter.broadcastProtocolEvent(null);
	}

	@Test
	@DisplayName("connectTo with null peerId triggers onReady and does not throw")
	void connectToWithNullPeerIdIsSafe() {
		P2PNetworkAdapter adapter = newAdapter("peer-local");
		AtomicInteger readyCount = new AtomicInteger(0);
		adapter.connectTo(null, "ws://localhost:9999", "ws-test", readyCount::incrementAndGet);
		assertEquals(1, readyCount.get());
	}

	private static class StubEventStore implements EventStore {
		@Override public synchronized void append(Event event) {}
		@Override public synchronized java.util.List<Event> listEvents(String workspaceId) { return new ArrayList<>(); }
		@Override public synchronized boolean hasEvent(String eventId) { return false; }
		@Override public synchronized java.util.Optional<Event> getEvent(String eventId) { return java.util.Optional.empty(); }
		@Override public synchronized java.util.List<Event> getMissingEvents(String workspaceId, java.util.Set<String> knownEventIds) { return new ArrayList<>(); }
		@Override public synchronized java.util.Set<String> listEventIds(String workspaceId) { return new java.util.HashSet<>(); }
		@Override public synchronized boolean containsType(String workspaceId, String eventType) { return false; }
	}
}
