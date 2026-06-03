package org.q3s.p2p.core;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.q3s.p2p.adapters.memory.InMemoryEventStore;
import org.q3s.p2p.adapters.network.P2PNetworkAdapter;
import org.q3s.p2p.client.ws.WsClient;
import org.q3s.p2p.core.codec.CoreEnvelope;
import org.q3s.p2p.core.events.EventTypes;
import org.q3s.p2p.core.model.Event;

class CoreWsClientTest {

	@Test void wsClientWithNullLoggerDoesNotCrash() {
		var called = new AtomicBoolean(false);
		WsClient client = new WsClient(URI.create("ws://localhost:1"), null,
				event -> called.set(true),
				error -> called.set(true),
				() -> called.set(true));
		assertNotNull(client);
	}

	@Test void wsClientWithNullLoggerOnErrorDoesNotNPE() {
		var errorCalled = new AtomicBoolean(false);
		WsClient client = new WsClient(URI.create("ws://localhost:1"), null,
				event -> {},
				error -> errorCalled.set(true),
				() -> {});
		try {
			client.onError(new Exception("test error"));
		} catch (NullPointerException npe) {
			fail("WsClient.onError con log null no debe lanzar NPE");
		}
	}

	@Test void wsClientWithNullLoggerCloseDoesNotNPE() {
		var closeCalled = new AtomicBoolean(false);
		WsClient client = new WsClient(URI.create("ws://localhost:1"), null,
				event -> {},
				error -> {},
				() -> closeCalled.set(true));
		try {
			client.onClose(1001, "test", true);
		} catch (NullPointerException npe) {
			fail("WsClient.onClose con log null no debe lanzar NPE");
		}
	}

	@Test void wsClientWithNullLoggerSendEventDoesNotNPE() {
		var called = new AtomicBoolean(false);
		WsClient client = new WsClient(URI.create("ws://localhost:1"), null,
				event -> called.set(true),
				error -> {},
				() -> called.set(true));

		try {
			client.sendEnvelope(CoreEnvelope.of("test", "U1", ""));
		} catch (Exception e) {
			fail("sendEnvelope con log null no debe crashear: " + e.getMessage());
		}
	}

	@Test void p2pAdapterSendToDisconnectedPeerDoesNotCrash() {
		var store = new InMemoryEventStore();
		var adapter = new P2PNetworkAdapter("test-peer", () -> "ws://localhost:1",
				() -> "ws_test", store, (conn, evt) -> {}, msg -> {});

		var evtFactory = new org.q3s.p2p.core.events.EventFactory(
				new org.q3s.p2p.adapters.memory.UuidIdGenerator(),
				new org.q3s.p2p.adapters.memory.SystemClockProvider());

		Event event = evtFactory.create("ws_test", EventTypes.CHAT_MESSAGE_CREATED, "test-peer",
				Map.of("message_id", "m1", "text", "test"), null);

		try {
			adapter.send("disconnected-peer", event);
			adapter.broadcast(event);
		} catch (Exception e) {
			fail("send/broadcast a peer desconectado no debe crashear: " + e.getMessage());
		}
	}

	@Test void p2pAdapterDisconnectAllCleansUpProperly() {
		var store = new InMemoryEventStore();
		var adapter = new P2PNetworkAdapter("test-peer", () -> "ws://localhost:1",
				() -> "ws_test", store, (conn, evt) -> {}, msg -> {});
		adapter.disconnectAll();
		assertTrue(adapter.connectedPeers().isEmpty());
	}

	@Test void p2pAdapterHasPeerReturnsFalseForUnknown() {
		var store = new InMemoryEventStore();
		var adapter = new P2PNetworkAdapter("test-peer", () -> "ws://localhost:1",
				() -> "ws_test", store, (conn, evt) -> {}, msg -> {});
		assertFalse(adapter.hasPeer("unknown-peer"));
	}

	@Test void wsClientOnCloseInvokesCloseCallback() {
		var closeCalled = new AtomicBoolean(false);
		WsClient client = new WsClient(URI.create("ws://localhost:1"), null,
				event -> {},
				error -> {},
				() -> closeCalled.set(true), false);

		client.onClose(1000, "closed", true);

		assertTrue(closeCalled.get(), "onClose debe invocar close callback");
	}

	@Test void wsClientOnCloseCallbackIsIdempotent() {
		var count = new java.util.concurrent.atomic.AtomicInteger();
		WsClient client = new WsClient(URI.create("ws://localhost:1"), null,
				event -> {},
				error -> {},
				count::incrementAndGet, false);

		client.onClose(1000, "closed", true);
		client.onClose(1000, "closed again", true);

		assertEquals(1, count.get(), "close callback solo debe invocarse una vez");
	}
}
