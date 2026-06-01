package org.q3s.p2p.adapters.network;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.Random;
import java.util.Set;

import org.q3s.p2p.adapters.memory.InMemoryEventStore;
import org.q3s.p2p.core.mesh.MeshPolicy;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.model.PeerInfo;
import org.q3s.p2p.core.sync.SyncEngine;
import org.q3s.p2p.ports.EventStore;
import org.q3s.p2p.ports.NetworkAdapter;

public class SimulatedNetworkAdapter {
	private final Map<String, SimulatedNode> nodes = new LinkedHashMap<>();
	private final Random rng = new Random(42);

	public SimulatedNode createNode(String id) {
		SimulatedNode node = new SimulatedNode(id);
		nodes.put(id, node);
		return node;
	}

	public Optional<SimulatedNode> node(String id) {
		return Optional.ofNullable(nodes.get(id));
	}

	public void connect(SimulatedNode a, SimulatedNode b) {
		a.neighbors.add(b.id);
		b.neighbors.add(a.id);
	}

	public void disconnect(SimulatedNode a, SimulatedNode b) {
		a.neighbors.remove(b.id);
		b.neighbors.remove(a.id);
	}

	public void disconnectAll(SimulatedNode node) {
		for (String neighborId : new ArrayList<>(node.neighbors)) {
			SimulatedNode neighbor = nodes.get(neighborId);
			if (neighbor != null) disconnect(node, neighbor);
		}
	}

	public int degree(SimulatedNode node) {
		return node.neighbors.size();
	}

	public int totalEvents(String workspaceId) {
		return nodes.values().stream().mapToInt(n -> n.store().listEvents(workspaceId).size()).max().orElse(0);
	}

	public Optional<PeerInfo> selectPeerFor(SimulatedNode node, MeshPolicy policy) {
		return policy.selectBestPeer(peerInfosExcept(node.id));
	}

	public void connectUsingPolicy(SimulatedNode node, MeshPolicy policy) {
		while (node.degree() < policy.targetConnectionsPerPeer()) {
			Optional<PeerInfo> selected = selectPeerFor(node, policy);
			if (selected.isEmpty()) return;
			SimulatedNode peer = nodes.get(selected.get().peerId());
			if (peer == null || node.neighbors.contains(peer.id) || peer.degree() >= policy.maxConnectionsPerPeer()) return;
			connect(node, peer);
		}
	}

	public String topologyReport() {
		StringBuilder report = new StringBuilder();
		for (SimulatedNode node : nodes.values()) {
			report.append(node.id()).append(" -> ").append(node.neighbors).append(" (degree=").append(node.degree()).append(")\n");
		}
		return report.toString();
	}

	public List<String> edges() {
		List<String> edges = new ArrayList<>();
		for (SimulatedNode node : nodes.values()) {
			for (String neighbor : node.neighbors) {
				if (node.id.compareTo(neighbor) < 0) edges.add(node.id + " -- " + neighbor);
			}
		}
		return edges;
	}

	public Collection<PeerInfo> peerInfosExcept(String excludedId) {
		List<PeerInfo> peers = new ArrayList<>();
		for (SimulatedNode node : nodes.values()) {
			if (node.id.equals(excludedId)) continue;
			peers.add(new PeerInfo(node.id, node.id, true, List.copyOf(node.neighbors), node.neighbors.size(), Instant.now(), 1, 4));
		}
		return peers;
	}

	public void runGossipRounds(int rounds) {
		for (int i = 0; i < rounds; i++) {
			for (SimulatedNode node : new ArrayList<>(nodes.values())) node.deliverRound(0.0, 0, rng);
		}
	}

	public int runGossipRoundsWithLoss(int rounds, double packetLoss) {
		int delivered = 0;
		for (int i = 0; i < rounds; i++) {
			for (SimulatedNode node : new ArrayList<>(nodes.values()))
				delivered += node.deliverRound(packetLoss, 0, rng);
		}
		return delivered;
	}

	public int runGossipRoundsWithLatencyAndLoss(int rounds, double packetLoss, int maxDelayRounds) {
		int delivered = 0;
		for (int i = 0; i < rounds; i++) {
			for (SimulatedNode node : new ArrayList<>(nodes.values()))
				delivered += node.deliverRound(packetLoss, maxDelayRounds, rng);
		}
		return delivered;
	}

	public class SimulatedNode implements NetworkAdapter {
		private final String id;
		private final EventStore store = new InMemoryEventStore();
		private final Set<String> neighbors = new LinkedHashSet<>();
		private final Queue<Event> inbox = new ArrayDeque<>();
		private final Map<Event, Integer> delayedInbox = new LinkedHashMap<>();
		private final SyncEngine sync = new SyncEngine(store, this);

		private SimulatedNode(String id) { this.id = id; }
		public String id() { return id; }
		public EventStore store() { return store; }
		public SyncEngine sync() { return sync; }
		public int degree() { return neighbors.size(); }

		@Override
		public void send(String peerId, Event event) {
			SimulatedNode peer = nodes.get(peerId);
			if (peer != null) peer.inbox.add(event);
		}

		@Override
		public void broadcast(Event event) {
			for (String peerId : neighbors) send(peerId, event);
		}

		@Override
		public Set<String> peers() {
			return Set.copyOf(neighbors);
		}

		private int deliverRound(double packetLoss, int maxDelayRounds, Random rng) {
			int delivered = 0;
			List<Event> toRemove = new ArrayList<>();
			for (Map.Entry<Event, Integer> entry : delayedInbox.entrySet()) {
				int remaining = entry.getValue() - 1;
				if (remaining <= 0) {
					toRemove.add(entry.getKey());
				} else {
					delayedInbox.put(entry.getKey(), remaining);
				}
			}
			for (Event e : toRemove) {
				delayedInbox.remove(e);
				if (rng.nextDouble() >= packetLoss) {
					sync.receiveEvent(e);
					delivered++;
				}
			}

			int size = inbox.size();
			for (int i = 0; i < size; i++) {
				Event event = inbox.poll();
				if (event == null) continue;
				if (rng.nextDouble() < packetLoss) continue;
				if (maxDelayRounds > 0) {
					int delay = 1 + rng.nextInt(maxDelayRounds);
					delayedInbox.put(event, delay);
				} else {
					sync.receiveEvent(event);
					delivered++;
				}
			}
			return delivered;
		}
	}
}
