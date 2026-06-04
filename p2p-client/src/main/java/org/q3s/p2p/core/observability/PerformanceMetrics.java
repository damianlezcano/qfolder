package org.q3s.p2p.core.observability;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Metricas en memoria de la aplicacion: contadores de eventos publicados,
 * chunks transferidos, tiempo de sync, conexiones P2P, etc. Permite exponer
 * estado de salud via logging (o JMX/Micrometer en el futuro) sin necesidad
 * de un sistema externo. Thread-safe.
 */
public final class PerformanceMetrics {

	private static final ConcurrentHashMap<String, AtomicLong> COUNTERS = new ConcurrentHashMap<>();

	private PerformanceMetrics() {}

	public static void increment(String name) {
		increment(name, 1);
	}

	public static void increment(String name, long delta) {
		if (name == null) return;
		COUNTERS.computeIfAbsent(name, k -> new AtomicLong()).addAndGet(delta);
	}

	public static void record(String name, long value) {
		if (name == null) return;
		COUNTERS.computeIfAbsent(name, k -> new AtomicLong()).set(value);
	}

	public static long get(String name) {
		if (name == null) return 0;
		AtomicLong v = COUNTERS.get(name);
		return v == null ? 0 : v.get();
	}

	public static void reset() {
		COUNTERS.clear();
	}

	public static String snapshotAsText() {
		StringBuilder sb = new StringBuilder("PerformanceMetrics{");
		boolean first = true;
		for (var e : COUNTERS.entrySet()) {
			if (!first) sb.append(", ");
			sb.append(e.getKey()).append('=').append(e.getValue().get());
			first = false;
		}
		return sb.append('}').toString();
	}

	public static final class Events {
		public static final String PUBLISHED = "events.published";
		public static final String RECEIVED = "events.received";
		public static final String APPLIED = "events.applied";
		public static final String DROPPED = "events.dropped";
		public static final String EPHEMERAL = "events.ephemeral";
		public static final String SYNC_REQUESTS = "events.sync.requests";
		public static final String SYNC_RESPONSES = "events.sync.responses";
		private Events() {}
	}

	public static final class Chunks {
		public static final String TRANSFERRED = "chunks.transferred";
		public static final String FAILED = "chunks.failed";
		public static final String AVAILABLE_REQUESTS = "chunks.availability.requests";
		public static final String AVAILABLE_RESPONSES = "chunks.availability.responses";
		private Chunks() {}
	}

	public static final class Peers {
		public static final String CONNECTED = "peers.connected";
		public static final String DISCONNECTED = "peers.disconnected";
		public static final String RECONNECTS = "peers.reconnects";
		private Peers() {}
	}

	public static final class Sync {
		public static final String LAST_DURATION_MS = "sync.last.duration.ms";
		public static final String TOTAL_DURATION_MS = "sync.total.duration.ms";
		public static final String EVENTS_DELIVERED = "sync.events.delivered";
		private Sync() {}
	}
}
