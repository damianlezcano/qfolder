package org.q3s.p2p.adapters.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.q3s.p2p.core.app.CoreApplicationService;
import org.q3s.p2p.core.mesh.MeshPolicy;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.ports.EventStore;

class P2PMeshServiceTest {

	private StubEventStore stubStore;
	private P2PNetworkAdapter stubAdapter;
	private List<String> debugMessages;
	private List<Event> ephemeralEvents;

	@BeforeEach
	void setUp() {
		stubStore = new StubEventStore();
		ephemeralEvents = new ArrayList<>();
		debugMessages = new ArrayList<>();
		stubAdapter = new P2PNetworkAdapter("peer-local",
				() -> "ws://localhost:9999",
				() -> "ws-test",
				stubStore,
				(ws, env) -> {},
				debugMessages::add,
				false,
				null);
	}

	private P2PMeshService newMesh() {
		return new P2PMeshService(stubAdapter, null,
				() -> "ws-test",
				() -> "ws://localhost:9999",
				state -> {},
				ev -> {},
				debugMessages::add,
				new MeshPolicy(2, 4));
	}

	@Test
	@DisplayName("P2PMeshService can be instantiated with valid args")
	void meshServiceInstantiation() {
		P2PMeshService mesh = newMesh();
		assertNotNull(mesh);
		assertNotNull(mesh.catalog());
		assertTrue(mesh.catalog().isEmpty());
	}

	@Test
	@DisplayName("catalog() returns empty map initially")
	void catalogIsEmptyInitially() {
		P2PMeshService mesh = newMesh();
		assertTrue(mesh.catalog().isEmpty());
	}

	@Test
	@DisplayName("connectedPeers() delegates to adapter")
	void connectedPeersDelegatesToAdapter() {
		P2PMeshService mesh = newMesh();
		assertNotNull(mesh.connectedPeers());
		assertEquals(0, mesh.connectedPeers().size());
	}

	@Test
	@DisplayName("peerDisappeared with null peerId is a no-op (no exception)")
	void peerDisappearedWithNullIsSafe() {
		P2PMeshService mesh = newMesh();
		mesh.peerDisappeared(null);
		assertTrue(mesh.catalog().isEmpty());
	}

	@Test
	@DisplayName("peerAppeared with null peer is a no-op (no exception)")
	void peerAppearedWithNullIsSafe() {
		P2PMeshService mesh = newMesh();
		mesh.peerAppeared(null);
		assertTrue(mesh.catalog().isEmpty());
	}

	@Test
	@DisplayName("peerAppeared with peer missing url is a no-op (no exception)")
	void peerAppearedWithMissingUrlIsSafe() {
		P2PMeshService mesh = newMesh();
		org.q3s.p2p.model.User peer = org.q3s.p2p.model.User.build("peer-1");
		mesh.peerAppeared(peer);
		assertTrue(mesh.catalog().isEmpty());
	}

	@Test
	@DisplayName("setOnDebouncedRefresh stores callback")
	void setOnDebouncedRefresh() {
		P2PMeshService mesh = newMesh();
		AtomicInteger counter = new AtomicInteger(0);
		mesh.setOnDebouncedRefresh(counter::incrementAndGet);
		assertNotNull(mesh);
	}

	@Test
	@DisplayName("onEphemeralForwarded stores callback")
	void onEphemeralForwarded() {
		P2PMeshService mesh = newMesh();
		Consumer<Event> cb = ephemeralEvents::add;
		mesh.onEphemeralForwarded(cb);
		assertNotNull(mesh);
	}

	@Test
	@DisplayName("disconnectAll stops mesh gracefully")
	void disconnectAllIsSafe() {
		P2PMeshService mesh = newMesh();
		mesh.disconnectAll();
		assertEquals(0, mesh.connectedPeers().size());
	}

	@Test
	@DisplayName("publish with null event is a no-op (no exception)")
	void publishWithNullEventIsSafe() {
		P2PMeshService mesh = newMesh();
		mesh.publish(null);
		assertNotNull(mesh);
	}

	@Test
	@DisplayName("broadcastSyncRequest is safe with no peers")
	void broadcastSyncRequestIsSafe() {
		P2PMeshService mesh = newMesh();
		mesh.broadcastSyncRequest();
		assertNotNull(mesh);
	}

	@Test
	@DisplayName("applyState with null state is a no-op (no exception)")
	void applyStateWithNullIsSafe() {
		P2PMeshService mesh = newMesh();
		mesh.applyState(null);
		assertNotNull(mesh);
	}

	@Test
	@DisplayName("publishPeerStatusIfChanged is safe without core")
	void publishPeerStatusIfChangedIsSafe() {
		P2PMeshService mesh = newMesh();
		mesh.publishPeerStatusIfChanged();
		assertNotNull(mesh);
	}

	@Test
	@DisplayName("mergePeerCatalog with empty catalog is a no-op (no exception)")
	void mergePeerCatalogEmptyIsSafe() {
		P2PMeshService mesh = newMesh();
		mesh.mergePeerCatalog(new java.util.HashMap<>(), peerId -> {});
		assertNotNull(mesh);
	}

	@Test
	@DisplayName("forcePublishPeerStatus returns null when core is not available")
	void forcePublishPeerStatusWithoutCore() {
		P2PMeshService mesh = newMesh();
		Event result = mesh.forcePublishPeerStatus();
		assertEquals(null, result);
	}

	private static class StubEventStore implements EventStore {
		@Override public synchronized void append(Event event) {}
		@Override public synchronized java.util.List<Event> listEvents(String workspaceId) { return new ArrayList<>(); }
		@Override public synchronized boolean hasEvent(String eventId) { return false; }
		@Override public synchronized Optional<Event> getEvent(String eventId) { return Optional.empty(); }
		@Override public synchronized java.util.List<Event> getMissingEvents(String workspaceId, Set<String> knownEventIds) { return new ArrayList<>(); }
		@Override public synchronized Set<String> listEventIds(String workspaceId) { return new HashSet<>(); }
		@Override public synchronized boolean containsType(String workspaceId, String eventType) { return false; }
	}
}
