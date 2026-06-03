package org.q3s.p2p.adapters.network;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.q3s.p2p.core.app.CoreApplicationService;
import org.q3s.p2p.core.mesh.MeshPolicy;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.state.WorkspaceState;
import org.q3s.p2p.model.User;

public class P2PMeshService {
	private final P2PNetworkAdapter p2p;
	private final CoreApplicationService core;
	private final Supplier<String> workspaceId;
	private final Supplier<String> localPeerUrl;
	private final Consumer<WorkspaceState> onStateChanged;
	private final Consumer<Event> onLocalStatusEvent;
	private final Consumer<String> debug;
	private final MeshPolicy policy;
	private final java.util.Map<String, String> peerCatalog = new java.util.concurrent.ConcurrentHashMap<>();
	private final Set<String> connectingPeerIds = ConcurrentHashMap.newKeySet();
	private volatile String lastPublishedPeerUrl = null;
	private volatile Set<String> lastPublishedConnections = Set.of();
	private volatile boolean shuttingDown = false;

	private record PeerCandidate(String peerId, String peerUrl, int degree) {}

	public P2PMeshService(P2PNetworkAdapter p2p, CoreApplicationService core,
			Supplier<String> workspaceId, Supplier<String> localPeerUrl, Consumer<WorkspaceState> onStateChanged,
			Consumer<Event> onLocalStatusEvent, Consumer<String> debug) {
		this(p2p, core, workspaceId, localPeerUrl, onStateChanged, onLocalStatusEvent, debug, new MeshPolicy(2, 4));
	}

	public P2PMeshService(P2PNetworkAdapter p2p, CoreApplicationService core,
			Supplier<String> workspaceId, Supplier<String> localPeerUrl, Consumer<WorkspaceState> onStateChanged,
			Consumer<Event> onLocalStatusEvent, Consumer<String> debug, MeshPolicy policy) {
		this.p2p = p2p;
		this.core = core;
		this.workspaceId = workspaceId;
		this.localPeerUrl = localPeerUrl == null ? () -> "" : localPeerUrl;
		this.onStateChanged = onStateChanged;
		this.onLocalStatusEvent = onLocalStatusEvent == null ? ignored -> {} : onLocalStatusEvent;
		this.debug = debug == null ? ignored -> {} : debug;
		this.policy = policy == null ? new MeshPolicy(2, 4) : policy;
		p2p.onCoreEventStored(e -> { if (!shuttingDown) notifyEventStored(e); });
		p2p.onEphemeralCoreEvent(e -> { if (!shuttingDown) applyEphemeralState(e); });
		p2p.onCoreSyncApplied(events -> { if (!shuttingDown) applyState(core.currentState()); });
		p2p.onPeerConnectionsChanged(peers -> {
			if (shuttingDown) return;
			connectingPeerIds.removeIf(peers::contains);
			scheduleAutoReconnect();
			WorkspaceState state = core.currentState();
			publishPeerStatusIfChanged();
			rebalanceConnections(state);
			onStateChanged.accept(state);
		});
	}

	private final java.util.concurrent.ScheduledExecutorService reconnectScheduler =
			java.util.concurrent.Executors.newSingleThreadScheduledExecutor(r -> {
				Thread t = new Thread(r, "p2p-mesh-reconnect");
				t.setDaemon(true);
				return t;
			});
	private final java.util.Map<String, java.util.concurrent.ScheduledFuture<?>> reconnectFutures =
			new java.util.concurrent.ConcurrentHashMap<>();

	/**
	 * Programa un reintento de reconexion para peers del catalogo que no esten conectados
	 * ni en proceso de conectar. Reemplaza cualquier intento previo pendiente para el
	 * mismo peer. Se cancela en shutdown o si el peer vuelve a estar conectado.
	 */
	private void scheduleAutoReconnect() {
		if (shuttingDown) return;
		for (var entry : peerCatalog.entrySet()) {
			String peerId = entry.getKey();
			if (peerId == null || peerId.isBlank()) continue;
			if (p2p.hasPeer(peerId) || connectingPeerIds.contains(peerId)) {
				java.util.concurrent.ScheduledFuture<?> existing = reconnectFutures.remove(peerId);
				if (existing != null) existing.cancel(false);
				continue;
			}
			if (reconnectFutures.containsKey(peerId)) continue;
			java.util.concurrent.ScheduledFuture<?> future = reconnectScheduler.schedule(() -> {
				reconnectFutures.remove(peerId);
				if (shuttingDown) return;
				if (p2p.hasPeer(peerId) || connectingPeerIds.contains(peerId)) return;
				String peerUrl = peerCatalog.get(peerId);
				if (peerUrl == null || peerUrl.isBlank()) return;
				String wsId = workspaceId.get();
				if (wsId == null || wsId.isBlank()) return;
				debug.accept("P2P mesh: auto-reconnect a " + peerId);
				User peer = User.build(peerId);
				peer.setPeerUrl(peerUrl);
				peerAppeared(peer);
			}, 5, java.util.concurrent.TimeUnit.SECONDS);
			reconnectFutures.put(peerId, future);
		}
	}

	public void peerAppeared(User peer) {
		if (peer == null || shuttingDown) return;
		String wsId = workspaceId.get();
		if (wsId == null || wsId.isBlank()) return;
		String peerId = peer.getId();
		String peerUrl = peer.getPeerUrl();
		if (peerId == null || peerId.isBlank()) return;
		if (peerUrl == null || peerUrl.isBlank()) return;
		peerCatalog.put(peerId, peerUrl);

		if (p2p.hasPeer(peerId)) return;

		if (!connectingPeerIds.add(peerId)) return;

		if (connectedPeers().size() + connectingPeerIds.size() > policy.maxConnectionsPerPeer()) {
			connectingPeerIds.remove(peerId);
			debug.accept("P2P mesh: no conecto a " + peerId + " porque se alcanzo maxConnections");
			return;
		}

		debug.accept("P2P mesh: conectando a " + peerId + " via " + peerUrl);
		new Thread(() -> {
			try {
				if (shuttingDown) {
					connectingPeerIds.remove(peerId);
					return;
				}
				p2p.connectTo(peerId, peerUrl, wsId, () -> {
					connectingPeerIds.remove(peerId);
					if (shuttingDown) return;
					if (p2p.hasPeer(peerId)) debug.accept("P2P mesh: conectado a " + peerId);
					publishPeerStatusIfChanged();
				});
			} catch (Exception e) {
				connectingPeerIds.remove(peerId);
				debug.accept("P2P mesh: error conectando a " + peerId + ": " + e.getMessage());
			}
		}, "p2p-mesh-connect").start();
	}

	public void peerDisappeared(String peerId) {
		disconnectPeer(peerId, true, true);
	}

	private void disconnectPeer(String peerId, boolean rebalance, boolean removeFromCatalog) {
		if (peerId == null || peerId.isBlank()) return;

		connectingPeerIds.remove(peerId);
		if (removeFromCatalog) peerCatalog.remove(peerId);

		debug.accept("P2P mesh: desconectando de " + peerId);
		p2p.disconnectFrom(peerId);

		if (rebalance && !shuttingDown) {
			publishPeerStatusIfChanged();
			WorkspaceState state = core.currentState();
			rebalanceConnections(state);
			onStateChanged.accept(state);
		}
	}

	public void publish(Event event) {
		if (!shuttingDown && event != null) p2p.broadcast(event);
	}

	public void broadcastSyncRequest() {
		if (shuttingDown) return;
		try {
			Set<String> ids = core.eventIds();
			debug.accept("P2P mesh: broadcast sync request with " + ids.size() + " ids");
			p2p.broadcastSyncRequest();
		} catch (Exception e) {
			debug.accept("P2P mesh: no se pudo pedir sync: " + e.getMessage());
		}
	}

	public void disconnectAll() {
		shuttingDown = true;
		connectingPeerIds.clear();
		peerCatalog.clear();
		lastPublishedConnections = Set.of();
		for (var future : reconnectFutures.values()) future.cancel(false);
		reconnectFutures.clear();
		reconnectScheduler.shutdownNow();
		debug.accept("P2P mesh: desconectando todos los peers");
		p2p.disconnectAll();
	}

	public Set<String> connectedPeers() { return p2p.connectedPeers(); }

	public java.util.Map<String, String> catalog() { return new java.util.LinkedHashMap<>(peerCatalog); }

	public void publishPeerStatusIfChanged() {
		publishPeerStatus(false);
	}

	public Event forcePublishPeerStatus() {
		return publishPeerStatus(true);
	}

	private Event publishPeerStatus(boolean force) {
		if (shuttingDown) return null;
		try {
			String url = localPeerUrl.get();
			Set<String> connections = connectedPeers();
			if (!force && java.util.Objects.equals(url, lastPublishedPeerUrl) && connections.equals(lastPublishedConnections)) return null;
			lastPublishedPeerUrl = url;
			lastPublishedConnections = new LinkedHashSet<>(connections);
			Event event = core.updatePeerStatus(url, connections);
			debug.accept((force ? "P2P mesh: force publish status" : "P2P mesh: publish status")
					+ " url=" + url + " peers=" + connections.size());
			onLocalStatusEvent.accept(event);
			return event;
		} catch (Exception e) {
			debug.accept("P2P mesh: no se pudo publicar estado: " + e.getMessage());
			return null;
		}
	}

	public void applyState(WorkspaceState state) {
		if (shuttingDown || state == null) return;
		applyPeerDiscoveryState(state);
		onStateChanged.accept(state);
	}

	public void applyPeerDiscoveryState(WorkspaceState state) {
		if (shuttingDown || state == null) return;
		mergePeerState(state);
		debug.accept("P2P mesh: applying peer discovery, catalog=" + peerCatalog.size()
				+ " connected=" + connectedPeers().size());
		rebalanceConnections(state);
	}

	public void mergePeerState(WorkspaceState state) {
		if (shuttingDown) return;
		String localMemberId = core.currentMember().map(member -> member.memberId()).orElse("");
		for (Map.Entry<String, String> entry : state.peerUrls().entrySet()) {
			String peerId = entry.getKey();
			String peerUrl = entry.getValue();
			if (peerId == null || peerId.isBlank() || peerId.equals(localMemberId)) continue;
			if (peerUrl == null || peerUrl.isBlank()) continue;
			peerCatalog.putIfAbsent(peerId, peerUrl);
		}
	}

	public void mergePeerCatalog(java.util.Map<String, String> remoteCatalog, java.util.function.Consumer<String> onNewPeer) {
		if (shuttingDown) return;
		for (var entry : remoteCatalog.entrySet()) {
			if (!peerCatalog.containsKey(entry.getKey())) {
				peerCatalog.put(entry.getKey(), entry.getValue());
				if (onNewPeer != null) onNewPeer.accept(entry.getKey());
			}
		}
	}

	private void rebalanceConnections(WorkspaceState state) {
		if (shuttingDown || state == null) return;

		int activeOrPending = connectedPeers().size() + connectingPeerIds.size();

		if (activeOrPending < policy.targetConnectionsPerPeer()) {
			connectUntilTarget(state);
		}

		if (connectedPeers().size() > policy.maxConnectionsPerPeer()) {
			trimConnections(state);
		}
	}

	private void connectUntilTarget(WorkspaceState state) {
		String localMemberId = core.currentMember().map(m -> m.memberId()).orElse("");
		Set<String> active = connectedPeers();
		List<PeerCandidate> candidates = candidatesForConnection(state, localMemberId, active);
		if (candidates.isEmpty()) debug.accept("P2P mesh: no candidates for connection target");

		for (PeerCandidate candidate : candidates) {
			int activeOrPending = connectedPeers().size() + connectingPeerIds.size();
			if (activeOrPending >= policy.targetConnectionsPerPeer()) break;
			User peer = User.build(candidate.peerId());
			peer.setPeerUrl(candidate.peerUrl());
			peerAppeared(peer);
		}
	}

	private List<PeerCandidate> candidatesForConnection(WorkspaceState state, String localMemberId, Set<String> active) {
		List<PeerCandidate> candidates = new ArrayList<>();

		for (Map.Entry<String, String> entry : state.peerUrls().entrySet()) {
			String peerId = entry.getKey();
			String peerUrl = entry.getValue();

			if (peerId == null || peerId.isBlank()) continue;
			if (peerId.equals(localMemberId)) continue;
			if (peerUrl == null || peerUrl.isBlank()) continue;
			if (active.contains(peerId)) continue;
			if (connectingPeerIds.contains(peerId)) continue;
			if (p2p.hasPeer(peerId)) continue;

			int degree = state.peerConnections().getOrDefault(peerId, Set.of()).size();
			if (degree >= policy.maxConnectionsPerPeer()) continue;

			candidates.add(new PeerCandidate(peerId, peerUrl, degree));
		}

		candidates.sort(Comparator
				.comparingInt(PeerCandidate::degree)
				.thenComparing(PeerCandidate::peerId));

		return candidates;
	}

	private void trimConnections(WorkspaceState state) {
		Set<String> active = connectedPeers();
		if (active.size() <= policy.maxConnectionsPerPeer()) return;

		List<PeerCandidate> connected = new ArrayList<>();
		for (String peerId : active) {
			String peerUrl = peerCatalog.getOrDefault(peerId, state.peerUrls().getOrDefault(peerId, ""));
			int degree = state.peerConnections().getOrDefault(peerId, Set.of()).size();
			connected.add(new PeerCandidate(peerId, peerUrl, degree));
		}

		connected.sort(Comparator
				.comparingInt(PeerCandidate::degree).reversed()
				.thenComparing(PeerCandidate::peerId));

		for (PeerCandidate candidate : connected) {
			if (connectedPeers().size() <= policy.maxConnectionsPerPeer()) break;
			disconnectPeer(candidate.peerId(), false, false);
		}

		publishPeerStatusIfChanged();
		onStateChanged.accept(state);
	}

	private void notifyEventStored(Event event) {
		applyState(core.currentState());
	}

	/**
	 * Aplica un evento core efímero recibido por P2P (como peer.status.updated) al estado
	 * del workspace local sin persistirlo. Mantiene viva la URL/conexiones del peer remoto
	 * en la malla y refresca la UI.
	 */
	private void applyEphemeralState(Event event) {
		if (shuttingDown || event == null) return;
		if (event.isEphemeral()) core.receiveRemoteEvent(event);
		applyState(core.currentState());
	}
}
