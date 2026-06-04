package org.q3s.p2p.core.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PerformanceMetricsTest {

	@Test
	void incrementSumaDelta() {
		PerformanceMetrics.reset();
		PerformanceMetrics.increment("test.counter");
		PerformanceMetrics.increment("test.counter", 5);
		assertEquals(6, PerformanceMetrics.get("test.counter"));
	}

	@Test
	void recordSustituyeValor() {
		PerformanceMetrics.reset();
		PerformanceMetrics.record("test.value", 42);
		assertEquals(42, PerformanceMetrics.get("test.value"));
		PerformanceMetrics.record("test.value", 100);
		assertEquals(100, PerformanceMetrics.get("test.value"));
	}

	@Test
	void getInexistenteRetornaCero() {
		PerformanceMetrics.reset();
		assertEquals(0, PerformanceMetrics.get("missing"));
	}

	@Test
	void incrementNullNoFalla() {
		PerformanceMetrics.reset();
		PerformanceMetrics.increment(null);
		PerformanceMetrics.record(null, 5);
		assertEquals(0, PerformanceMetrics.get(null));
	}

	@Test
	void resetLimpiaTodosLosContadores() {
		PerformanceMetrics.increment("a", 10);
		PerformanceMetrics.increment("b", 20);
		PerformanceMetrics.reset();
		assertEquals(0, PerformanceMetrics.get("a"));
		assertEquals(0, PerformanceMetrics.get("b"));
	}

	@Test
	void snapshotAsTextIncluyeContadores() {
		PerformanceMetrics.reset();
		PerformanceMetrics.increment("k1", 3);
		String snap = PerformanceMetrics.snapshotAsText();
		assertTrue(snap.contains("k1=3"), "snapshot debe incluir k1=3, fue: " + snap);
		assertTrue(snap.startsWith("PerformanceMetrics{"));
		assertTrue(snap.endsWith("}"));
	}

	@Test
	void contadoresPredefinidosSonAccesibles() {
		assertEquals(0, PerformanceMetrics.get(PerformanceMetrics.Events.PUBLISHED));
		assertEquals(0, PerformanceMetrics.get(PerformanceMetrics.Chunks.TRANSFERRED));
		assertEquals(0, PerformanceMetrics.get(PerformanceMetrics.Peers.CONNECTED));
		assertEquals(0, PerformanceMetrics.get(PerformanceMetrics.Sync.LAST_DURATION_MS));
	}

	@Test
	void incrementConcurrenteEsAtomico() throws Exception {
		PerformanceMetrics.reset();
		int n = 1000;
		Thread[] threads = new Thread[10];
		for (int t = 0; t < threads.length; t++) {
			threads[t] = new Thread(() -> {
				for (int i = 0; i < n; i++) PerformanceMetrics.increment("concurrent");
			});
			threads[t].start();
		}
		for (Thread t : threads) t.join();
		assertEquals(n * 10, PerformanceMetrics.get("concurrent"));
	}
}
