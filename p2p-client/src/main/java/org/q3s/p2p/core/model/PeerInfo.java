package org.q3s.p2p.core.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;

public record PeerInfo(
		String peerId,
		String memberId,
		boolean connected,
		List<String> knownNeighbors,
		int degree,
		Instant lastSeen,
		long latencyMillis,
		int maxConnections) implements Serializable {
}
