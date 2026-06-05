package org.q3s.p2p.client.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.DefaultListModel;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.q3s.p2p.client.util.Logger;

class CloudflareTunnelTest {

	private String prevMockProp;
	private String prevDelayProp;
	private Logger logger;

	@BeforeEach
	void setUp() {
		prevMockProp = System.getProperty("qfolder.tunnel.mock");
		prevDelayProp = System.getProperty("qfolder.tunnel.mock.delay");
		logger = new Logger(new DefaultListModel());
	}

	@AfterEach
	void tearDown() {
		if (prevMockProp == null) System.clearProperty("qfolder.tunnel.mock");
		else System.setProperty("qfolder.tunnel.mock", prevMockProp);
		if (prevDelayProp == null) System.clearProperty("qfolder.tunnel.mock.delay");
		else System.setProperty("qfolder.tunnel.mock.delay", prevDelayProp);
	}

	@Test
	@DisplayName("CloudflareTunnel can be instantiated")
	void canBeInstantiated() {
		CloudflareTunnel tunnel = new CloudflareTunnel(logger);
		assertNotNull(tunnel);
	}

	@Test
	@DisplayName("getTunnelUrl returns null when no tunnel started")
	void getTunnelUrlIsNullInitially() {
		CloudflareTunnel tunnel = new CloudflareTunnel(logger);
		assertNull(tunnel.getTunnelUrl());
	}

	@Test
	@DisplayName("isRunning returns false when no tunnel started")
	void isRunningIsFalseInitially() {
		CloudflareTunnel tunnel = new CloudflareTunnel(logger);
		assertFalse(tunnel.isRunning());
	}

	@Test
	@DisplayName("stop is safe to call when no tunnel started (no exception)")
	void stopIsSafeWhenNotRunning() {
		CloudflareTunnel tunnel = new CloudflareTunnel(logger);
		tunnel.stop();
		assertFalse(tunnel.isRunning());
	}

	@Test
	@DisplayName("Mock mode: start invokes onUrlReady with mock URL")
	void mockStartInvokesOnUrlReady() throws Exception {
		System.setProperty("qfolder.tunnel.mock", "true");
		System.setProperty("qfolder.tunnel.mock.delay", "50");
		CloudflareTunnel tunnel = new CloudflareTunnel(logger);
		CountDownLatch readyLatch = new CountDownLatch(1);
		AtomicReference<String> urlRef = new AtomicReference<>();
		tunnel.start(9999, url -> {
			urlRef.set(url);
			readyLatch.countDown();
		}, error -> {});
		assertTrue(readyLatch.await(5, TimeUnit.SECONDS), "onUrlReady should be invoked in mock mode");
		assertNotNull(urlRef.get());
		assertTrue(urlRef.get().contains("9999"), "Mock URL should contain the local port");
		tunnel.stop();
	}

	@Test
	@DisplayName("Mock mode: tunnel is running after start")
	void mockStartAndStopTransitions() throws Exception {
		System.setProperty("qfolder.tunnel.mock", "true");
		System.setProperty("qfolder.tunnel.mock.delay", "50");
		CloudflareTunnel tunnel = new CloudflareTunnel(logger);
		CountDownLatch readyLatch = new CountDownLatch(1);
		tunnel.start(9999, url -> readyLatch.countDown(), error -> {});
		assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
		assertTrue(tunnel.isRunning());
		tunnel.stop();
	}

	@Test
	@DisplayName("Mock mode: getTunnelUrl returns the URL after start")
	void mockGetTunnelUrlAfterStart() throws Exception {
		System.setProperty("qfolder.tunnel.mock", "true");
		System.setProperty("qfolder.tunnel.mock.delay", "50");
		CloudflareTunnel tunnel = new CloudflareTunnel(logger);
		CountDownLatch readyLatch = new CountDownLatch(1);
		tunnel.start(9999, url -> readyLatch.countDown(), error -> {});
		assertTrue(readyLatch.await(5, TimeUnit.SECONDS));
		String url = tunnel.getTunnelUrl();
		assertNotNull(url);
		assertTrue(url.contains("9999"));
	}
}
