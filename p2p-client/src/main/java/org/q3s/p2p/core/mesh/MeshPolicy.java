package org.q3s.p2p.core.mesh;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

import org.q3s.p2p.core.model.PeerInfo;

public class MeshPolicy {
	private final int targetConnectionsPerPeer;
	private final int maxConnectionsPerPeer;

	public MeshPolicy(int targetConnectionsPerPeer, int maxConnectionsPerPeer) {
		this.targetConnectionsPerPeer = targetConnectionsPerPeer;
		this.maxConnectionsPerPeer = maxConnectionsPerPeer;
	}

	public Optional<PeerInfo> selectBestPeer(Collection<PeerInfo> knownPeers) {
		return knownPeers.stream()
				.filter(PeerInfo::connected)
				.filter(peer -> peer.degree() < Math.min(peer.maxConnections(), maxConnectionsPerPeer))
				.sorted(Comparator.comparingInt(PeerInfo::degree)
						.thenComparingLong(PeerInfo::latencyMillis)
						.thenComparing(PeerInfo::peerId))
				.findFirst();
	}

	public int targetConnectionsPerPeer() { return targetConnectionsPerPeer; }
	public int maxConnectionsPerPeer() { return maxConnectionsPerPeer; }
}
