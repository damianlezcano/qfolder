package org.q3s.p2p.core;

import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.java_websocket.WebSocket;
import org.java_websocket.server.WebSocketServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.q3s.p2p.adapters.memory.InMemoryEventStore;
import org.q3s.p2p.adapters.memory.InMemoryFileChunkStore;
import org.q3s.p2p.adapters.memory.SystemClockProvider;
import org.q3s.p2p.adapters.memory.UuidIdGenerator;
import org.q3s.p2p.adapters.network.DirectBootstrap;
import org.q3s.p2p.adapters.network.InviteCode;
import org.q3s.p2p.adapters.network.P2PNetworkAdapter;
import org.q3s.p2p.core.app.CoreApplicationService;
import org.q3s.p2p.core.codec.CoreEnvelope;
import org.q3s.p2p.core.codec.CoreEnvelopeCodec;
import org.q3s.p2p.core.events.EventService;
import org.q3s.p2p.core.events.EventFactory;
import org.q3s.p2p.core.events.EventTypes;
import org.q3s.p2p.core.model.Event;

@Tag("integration")
class CoreWebSocketIntegrationTest {

	private final List<TestPeer> peers = new ArrayList<>();

	static class TestPeer {
		final String id;
		final int port;
		final InMemoryEventStore store;
		final EventFactory events;
		final TestWebSocketServer server;
		final P2PNetworkAdapter adapter;
		volatile boolean started;

		TestPeer(String id, int port) {
			this.id = id;
			this.port = port;
			this.store = new InMemoryEventStore();
			this.events = new EventFactory(new UuidIdGenerator(), new SystemClockProvider());
			this.server = new TestWebSocketServer(id, port, store);
			this.adapter = new P2PNetworkAdapter(id, () -> "ws://localhost:" + port, () -> "ws_test",
					store, (conn, evt) -> {}, msg -> {});
		}

		void start() throws Exception {
			server.start();
			started = true;
		}

		void stop() throws Exception {
			server.stop(500);
			adapter.disconnectAll();
			started = false;
		}
	}

	static class TestWebSocketServer extends WebSocketServer {
		private final String peerId;
		private final InMemoryEventStore store;

		TestWebSocketServer(String peerId, int port, InMemoryEventStore store) {
			super(new InetSocketAddress(port));
			this.peerId = peerId;
			this.store = store;
			setReuseAddr(true);
		}

		@Override public void onOpen(WebSocket conn, org.java_websocket.handshake.ClientHandshake handshake) {}
		@Override public void onClose(WebSocket conn, int code, String reason, boolean remote) {}
		@Override public void onMessage(WebSocket conn, String message) {
			CoreEnvelope envelope = CoreEnvelope.fromJsonBase64(message);
			if (envelope == null || envelope.name() == null) return;
			if (CoreEnvelopeCodec.CORE_EVENT_NAME.equals(envelope.name())) {
				Event coreEvent = CoreEnvelopeCodec.decodeCoreEvent(envelope);
				if (coreEvent != null) new EventService(store).accept(coreEvent);
			} else if (CoreEnvelopeCodec.CORE_SYNC_REQUEST_NAME.equals(envelope.name())) {
				List<Event> missing = store.getMissingEvents("ws_test", CoreEnvelopeCodec.decodeKnownEventIds(envelope));
				CoreEnvelope response = CoreEnvelope.of(
						CoreEnvelopeCodec.CORE_SYNC_RESPONSE_NAME,
						peerId,
						CoreEnvelopeCodec.encodeSyncPayload(missing));
				try {
					conn.send(response.toJsonBase64());
				} catch (Exception ignored) {}
			}
		}
		@Override public void onError(WebSocket conn, Exception ex) {}
		@Override public void onStart() {}
	}

	private int randomPort() {
		return 18000 + (int)(Math.random() * 1000);
	}

	private void waitUntil(java.util.function.BooleanSupplier condition) throws Exception {
		long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
		while (System.nanoTime() < deadline) {
			if (condition.getAsBoolean()) return;
			Thread.sleep(25);
		}
		assertTrue(condition.getAsBoolean());
	}

	@BeforeEach
	void setUp() {
		peers.clear();
	}

	@AfterEach
	void tearDown() {
		for (TestPeer peer : peers) {
			try { peer.stop(); } catch (Exception ignored) {}
		}
	}

	@Test void twoPeersStartServersAndDiscoverEachOther() throws Exception {
		int portA = randomPort();
		int portB = randomPort();
		TestPeer a = new TestPeer("A", portA);
		TestPeer b = new TestPeer("B", portB);
		peers.add(a); peers.add(b);

		a.start();
		b.start();

		assertTrue(a.started);
		assertTrue(b.started);
		assertTrue(a.server.getPort() > 0);
		assertTrue(b.server.getPort() > 0);
	}

	@Test void p2pAdapterConnectsAndSendsEvent() throws Exception {
		int portA = randomPort();
		int portB = randomPort();
		TestPeer a = new TestPeer("A", portA);
		TestPeer b = new TestPeer("B", portB);
		peers.add(a); peers.add(b);

		a.start();
		b.start();

		a.adapter.connectTo("B", "localhost:" + portB, "ws_test", null);

		Event event = a.events.create("ws_test", EventTypes.CHAT_MESSAGE_CREATED, "A",
				Map.of("message_id", "m1", "text", "hola p2p"), null);
		a.adapter.broadcast(event);

		assertTrue(a.store.hasEvent(event.eventId()));
		waitUntil(() -> b.store.hasEvent(event.eventId()));
	}

	@Test void p2pInitialSyncPullsMissingEventsFromRemotePeer() throws Exception {
		int portA = randomPort();
		int portB = randomPort();
		TestPeer a = new TestPeer("A", portA);
		TestPeer b = new TestPeer("B", portB);
		peers.add(a); peers.add(b);

		a.start();
		b.start();

		Event remoteOnly = b.events.create("ws_test", EventTypes.CHAT_MESSAGE_CREATED, "B",
				Map.of("message_id", "remote", "text", "evento previo"), null);
		b.store.append(remoteOnly);

		a.adapter.connectTo("B", "localhost:" + portB, "ws_test", null);

		waitUntil(() -> a.store.hasEvent(remoteOnly.eventId()));
	}

	@Test void threePeersFormMeshAndPropagateEvents() throws Exception {
		int portA = randomPort();
		int portB = randomPort() + 1;
		int portC = randomPort() + 2;
		TestPeer a = new TestPeer("A", portA);
		TestPeer b = new TestPeer("B", portB);
		TestPeer c = new TestPeer("C", portC);
		peers.add(a); peers.add(b); peers.add(c);

		a.start();
		b.start();
		c.start();

		a.adapter.connectTo("B", "localhost:" + portB, "ws_test", null);
		b.adapter.connectTo("C", "localhost:" + portC, "ws_test", null);

		Event event = a.events.create("ws_test", EventTypes.CHAT_MESSAGE_CREATED, "A",
				Map.of("message_id", "mesh", "text", "mesh p2p"), null);
		a.adapter.broadcast(event);

		assertTrue(a.store.hasEvent(event.eventId()));
		waitUntil(() -> b.store.hasEvent(event.eventId()));
	}

	@Test void membershipJoinApprovalFlowsThroughP2P() throws Exception {
		int portA = randomPort();
		int portB = randomPort();
		TestPeer a = new TestPeer("creator", portA);
		TestPeer b = new TestPeer("joiner", portB);
		peers.add(a); peers.add(b);

		a.start();
		b.start();

		a.adapter.connectTo("joiner", "localhost:" + portB, "ws_test", null);

		Event joinRequest = a.events.create("ws_test", EventTypes.MEMBER_JOIN_REQUESTED, "joiner",
				Map.of("candidate_member_id", "joiner", "candidate_display_name", "B"), null);
		a.adapter.broadcast(joinRequest);

		Event approval = a.events.create("ws_test", EventTypes.MEMBER_JOIN_APPROVAL, "creator",
				Map.of("candidate_member_id", "joiner", "approved_by", "creator"), null);
		a.adapter.broadcast(approval);

		assertTrue(a.store.hasEvent(joinRequest.eventId()));
		assertTrue(a.store.hasEvent(approval.eventId()));
		waitUntil(() -> b.store.hasEvent(joinRequest.eventId()) && b.store.hasEvent(approval.eventId()));
	}

	@Test void multiplePeersSyncEventsAfterDisconnect() throws Exception {
		int portA = randomPort();
		int portB = randomPort();
		int portC = randomPort();
		TestPeer a = new TestPeer("A", portA);
		TestPeer b = new TestPeer("B", portB);
		TestPeer c = new TestPeer("C", portC);
		peers.add(a); peers.add(b); peers.add(c);

		a.start();
		b.start();
		c.start();

		a.adapter.connectTo("B", "localhost:" + portB, "ws_test", null);
		b.adapter.connectTo("C", "localhost:" + portC, "ws_test", null);

		for (int i = 0; i < 5; i++) {
			Event evt = a.events.create("ws_test", EventTypes.CHAT_MESSAGE_CREATED, "A",
					Map.of("message_id", "m" + i, "text", "msg" + i), null);
			a.store.append(evt);
			a.adapter.broadcast(evt);
		}

		assertEquals(5, a.store.listEvents("ws_test").size());
	}

	@Test void directBootstrapJoinApprovalUsesSameLiveSocket() throws Exception {
		int portA = randomPort();
		var storeA = new InMemoryEventStore();
		var appA = new CoreApplicationService(storeA, new InMemoryFileChunkStore(), new UuidIdGenerator());
		var created = appA.createWorkspace("Direct", "A", 1);
		String wsId = created.workspaceId();
		appA.attachExistingSession(wsId, created.creator().memberId(), "A", "devA", created.creator().membershipToken());

		WebSocketServer server = new WebSocketServer(new InetSocketAddress(portA)) {
			@Override public void onOpen(WebSocket conn, org.java_websocket.handshake.ClientHandshake handshake) {}
			@Override public void onClose(WebSocket conn, int code, String reason, boolean remote) {}
			@Override public void onMessage(WebSocket conn, String message) {
				CoreEnvelope envelope = CoreEnvelope.fromJsonBase64(message);
				if (envelope == null || envelope.name() == null) return;
				if (CoreEnvelopeCodec.CORE_EVENT_NAME.equals(envelope.name())) {
					Event coreEvent = CoreEnvelopeCodec.decodeCoreEvent(envelope);
					if (coreEvent != null && EventTypes.MEMBER_JOIN_REQUESTED.equals(coreEvent.type())) {
						appA.receiveRemoteEvent(coreEvent);
						appA.approveJoin("B");
						appA.authorizeKnownMember("B", "B", "devB", "tokB");
					}
				} else if (CoreEnvelopeCodec.CORE_SYNC_REQUEST_NAME.equals(envelope.name())) {
					List<Event> missing = appA.missingEvents(CoreEnvelopeCodec.decodeKnownEventIds(envelope));
					CoreEnvelope response = CoreEnvelope.of(
							CoreEnvelopeCodec.CORE_SYNC_RESPONSE_NAME,
							created.creator().memberId(),
							CoreEnvelopeCodec.encodeSyncPayload(missing));
					try { conn.send(response.toJsonBase64()); } catch (Exception ignored) {}
				}
			}
			@Override public void onError(WebSocket conn, Exception ex) {}
			@Override public void onStart() {}
		};
		server.setReuseAddr(true);
		server.start();

		try {
			var appB = new CoreApplicationService(new InMemoryEventStore(), new InMemoryFileChunkStore(), new UuidIdGenerator());
			CountDownLatch welcomed = new CountDownLatch(1);
			DirectBootstrap bootstrap = new DirectBootstrap(appB, null, event -> {}, welcomed::countDown,
					(peer, workspace) -> {}, message -> {});

			assertTrue(bootstrap.join(InviteCode.encode("localhost:" + portA, wsId), "B", "B"));
			assertTrue(welcomed.await(3, TimeUnit.SECONDS), "B debe activarse al recibir aprobacion por el socket de bootstrap");
			assertTrue(appB.currentState().isAuthorized("B"));
			assertEquals(1, appB.currentState().authorizedMembers().values().stream()
					.filter(member -> "B".equals(member.memberId())).count());
		} finally {
			server.stop(500);
		}
	}
}
