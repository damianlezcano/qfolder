package org.q3s.p2p.client.ws;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.q3s.p2p.core.codec.CoreEnvelope;

class WsClientTest {

	@Test
	@DisplayName("WsClient can be instantiated with null logger")
	void canBeInstantiatedWithNullLogger() {
		WsClient client = new WsClient(URI.create("ws://localhost:1"), null,
				event -> {}, error -> {}, () -> {});
		assertNotNull(client);
	}

	@Test
	@DisplayName("WsClient can be instantiated with callbacksOnEdt=false")
	void canBeInstantiatedWithNonEdtCallbacks() {
		WsClient client = new WsClient(URI.create("ws://localhost:1"), null,
				event -> {}, error -> {}, () -> {}, false);
		assertNotNull(client);
	}

	@Test
	@DisplayName("onError with null logger does not throw NPE")
	void onErrorWithNullLoggerDoesNotThrowNpe() {
		AtomicBoolean errorCalled = new AtomicBoolean(false);
		WsClient client = new WsClient(URI.create("ws://localhost:1"), null,
				event -> {}, error -> errorCalled.set(true), () -> {});
		try {
			client.onError(new Exception("test"));
		} catch (NullPointerException npe) {
			assertFalse(true, "onError should not throw NPE when log is null");
		}
	}

	@Test
	@DisplayName("onClose triggers close callback only once")
	void onCloseTriggersCallbackOnce() {
		AtomicInteger closeCount = new AtomicInteger(0);
		WsClient client = new WsClient(URI.create("ws://localhost:1"), null,
				event -> {}, error -> {}, closeCount::incrementAndGet);
		client.onClose(1001, "test", true);
		client.onClose(1001, "test", true);
		client.onClose(1001, "test", true);
		assertEquals(1, closeCount.get());
	}

	@Test
	@DisplayName("sendEnvelope when not open triggers close callback")
	void sendEnvelopeWhenNotOpenTriggersCloseCallback() {
		AtomicBoolean closeCalled = new AtomicBoolean(false);
		WsClient client = new WsClient(URI.create("ws://localhost:1"), null,
				event -> {}, error -> {}, () -> closeCalled.set(true));
		CoreEnvelope envelope = CoreEnvelope.of("test.event", "user-1", "payload-data");
		client.sendEnvelope(envelope);
		assertTrue(closeCalled.get());
	}

	@Test
	@DisplayName("sendEnvelope with null envelope when not open triggers close callback")
	void sendNullEnvelopeWhenNotOpenTriggersCloseCallback() {
		AtomicBoolean closeCalled = new AtomicBoolean(false);
		WsClient client = new WsClient(URI.create("ws://localhost:1"), null,
				event -> {}, error -> {}, () -> closeCalled.set(true));
		client.sendEnvelope(null);
		assertTrue(closeCalled.get());
	}

	@Test
	@DisplayName("onMessage with oversized message closes connection (does not invoke onEvent)")
	void onMessageWithOversizedMessageDoesNotInvokeOnEvent() {
		AtomicBoolean eventCalled = new AtomicBoolean(false);
		WsClient client = new WsClient(URI.create("ws://localhost:1"), null,
				event -> eventCalled.set(true),
				error -> {},
				() -> {});
		StringBuilder huge = new StringBuilder(11_000_000);
		for (int i = 0; i < 11_000_000; i++) huge.append('x');
		client.onMessage(huge.toString());
		assertFalse(eventCalled.get());
	}

	@Test
	@DisplayName("onMessage with small invalid message does not invoke onEvent")
	void onMessageWithInvalidMessageDoesNotInvokeOnEvent() {
		AtomicBoolean eventCalled = new AtomicBoolean(false);
		WsClient client = new WsClient(URI.create("ws://localhost:1"), null,
				event -> eventCalled.set(true),
				error -> {},
				() -> {});
		client.onMessage("not-a-valid-envelope");
		assertFalse(eventCalled.get());
	}

	@Test
	@DisplayName("onMessage with valid envelope invokes onEvent")
	void onMessageWithValidEnvelopeInvokesOnEvent() throws Exception {
		AtomicBoolean eventCalled = new AtomicBoolean(false);
		WsClient client = new WsClient(URI.create("ws://localhost:1"), null,
				event -> eventCalled.set(true),
				error -> {},
				() -> {},
				false);
		CoreEnvelope envelope = CoreEnvelope.of("test.event", "user-1", "payload-data");
		client.onMessage(envelope.toJsonBase64());
		Thread.sleep(100);
		assertTrue(eventCalled.get());
	}
}
