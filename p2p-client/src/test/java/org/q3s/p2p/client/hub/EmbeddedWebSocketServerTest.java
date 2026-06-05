package org.q3s.p2p.client.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.q3s.p2p.client.ws.WsClient;
import org.q3s.p2p.core.codec.CoreEnvelope;

class EmbeddedWebSocketServerTest {

	private EmbeddedWebSocketServer server;
	private final AtomicInteger portCounter = new AtomicInteger(0);
	private int currentPort;

	@BeforeEach
	void setUp() throws Exception {
		currentPort = 18000 + portCounter.incrementAndGet();
		server = new EmbeddedWebSocketServer(currentPort, null,
				(conn, env) -> {}, userId -> {});
		server.start();
		Thread.sleep(200);
	}

	@AfterEach
	void tearDown() throws Exception {
		if (server != null) {
			server.shutdown();
		}
	}

	@Test
	@DisplayName("EmbeddedWebSocketServer can be instantiated")
	void canBeInstantiated() {
		assertNotNull(server);
	}

	@Test
	@DisplayName("EmbeddedWebSocketServer with 1-arg constructor works")
	void canBeInstantiatedWithSimpleConstructor() {
		EmbeddedWebSocketServer simple = new EmbeddedWebSocketServer(18099, null);
		assertNotNull(simple);
	}

	@Test
	@DisplayName("EmbeddedWebSocketServer with 2-arg constructor works")
	void canBeInstantiatedWith2Args() {
		EmbeddedWebSocketServer simple = new EmbeddedWebSocketServer(18098, null, (conn, env) -> {});
		assertNotNull(simple);
	}

	@Test
	@DisplayName("EmbeddedWebSocketServer accepts and closes a connection without wkId")
	void connectionWithoutWkIdIsClosed() throws Exception {
		CountDownLatch closeLatch = new CountDownLatch(1);
		WsClient client = new WsClient(URI.create("ws://localhost:" + currentPort + "/ws"),
				null, event -> {}, error -> {}, closeLatch::countDown, false);
		client.setConnectionLostTimeout(5);
		client.connectBlocking(3, TimeUnit.SECONDS);
		assertTrue(closeLatch.await(3, TimeUnit.SECONDS), "Connection should close when wkId is missing");
	}

	@Test
	@DisplayName("EmbeddedWebSocketServer accepts a connection with wkId and userId")
	void connectionWithWkIdIsAccepted() throws Exception {
		CountDownLatch errorLatch = new CountDownLatch(1);
		WsClient client = new WsClient(URI.create("ws://localhost:" + currentPort + "/ws?wkId=ws-test&userId=user-1&direct=true"),
				null, event -> {}, error -> errorLatch.countDown(), () -> {}, false);
		client.setConnectionLostTimeout(5);
		client.connectBlocking(3, TimeUnit.SECONDS);
		Thread.sleep(200);
		assertTrue(client.isOpen(), "Connection should remain open with valid wkId/userId");
		client.close();
	}

	@Test
	@DisplayName("EmbeddedWebSocketServer invokes directMessageHandler on incoming message")
	void directMessageHandlerIsInvoked() throws Exception {
		CountDownLatch messageLatch = new CountDownLatch(1);
		AtomicInteger messageCount = new AtomicInteger(0);
		EmbeddedWebSocketServer customServer = new EmbeddedWebSocketServer(currentPort + 100, null,
				(conn, env) -> {
					messageCount.incrementAndGet();
					messageLatch.countDown();
				}, userId -> {});
		customServer.start();
		try {
			Thread.sleep(200);
			WsClient client = new WsClient(URI.create("ws://localhost:" + (currentPort + 100) + "/ws?wkId=ws-test&userId=user-1&direct=true"),
					null, event -> {}, error -> {}, () -> {}, false);
			client.setConnectionLostTimeout(5);
			client.connectBlocking(3, TimeUnit.SECONDS);
			Thread.sleep(200);
			CoreEnvelope envelope = CoreEnvelope.of("test.event", "user-1", "payload");
			client.sendEnvelope(envelope);
			assertTrue(messageLatch.await(3, TimeUnit.SECONDS), "Direct message handler should be invoked");
			assertEquals(1, messageCount.get());
			client.close();
		} finally {
			customServer.shutdown();
		}
	}

	@Test
	@DisplayName("EmbeddedWebSocketServer handles oversized message without crashing")
	void oversizedMessageHandledGracefully() throws Exception {
		CountDownLatch closeLatch = new CountDownLatch(1);
		WsClient client = new WsClient(URI.create("ws://localhost:" + currentPort + "/ws?wkId=ws-test&userId=user-1&direct=true"),
				null, event -> {}, error -> {}, closeLatch::countDown, false);
		client.setConnectionLostTimeout(5);
		client.connectBlocking(3, TimeUnit.SECONDS);
		Thread.sleep(200);
		StringBuilder huge = new StringBuilder(11_000_000);
		for (int i = 0; i < 11_000_000; i++) huge.append('x');
		client.send(huge.toString());
		assertTrue(closeLatch.await(5, TimeUnit.SECONDS), "Server should close connection on oversized message");
	}
}
