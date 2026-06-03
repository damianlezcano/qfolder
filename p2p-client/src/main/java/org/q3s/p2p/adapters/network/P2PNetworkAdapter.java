package org.q3s.p2p.adapters.network;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.java_websocket.WebSocket;
import org.q3s.p2p.client.ws.WsClient;
import org.q3s.p2p.core.codec.CoreEnvelope;
import org.q3s.p2p.core.codec.CoreEnvelopeCodec;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.sync.SyncEngine;
import org.q3s.p2p.ports.EventStore;
import org.q3s.p2p.ports.NetworkAdapter;

public class P2PNetworkAdapter implements NetworkAdapter {
	private final String localPeerId;
	private final Supplier<String> localWebSocketUri;
	private final Supplier<String> workspaceId;
	private final EventStore store;
	private final SyncEngine sync;
	private final BiConsumer<WebSocket, CoreEnvelope> onInboundDirect;
	private final Consumer<String> debug;
	private final Map<String, PeerLink> peers = new ConcurrentHashMap<>();
	private volatile boolean shuttingDown = false;
	private Consumer<Event> onCoreEventStored;
	private Consumer<java.util.List<Event>> onCoreSyncApplied;
	private Consumer<Set<String>> onPeerConnectionsChanged;

	public P2PNetworkAdapter(String localPeerId, Supplier<String> localWebSocketUri, Supplier<String> workspaceId,
			EventStore store, BiConsumer<WebSocket, CoreEnvelope> onInboundDirect, Consumer<String> debug) {
		this(localPeerId, localWebSocketUri, workspaceId, store, onInboundDirect, debug, false);
	}

	public P2PNetworkAdapter(String localPeerId, Supplier<String> localWebSocketUri, Supplier<String> workspaceId,
			EventStore store, BiConsumer<WebSocket, CoreEnvelope> onInboundDirect, Consumer<String> debug,
			boolean validateRemoteEvents) {
		this.localPeerId = localPeerId;
		this.localWebSocketUri = localWebSocketUri;
		this.workspaceId = workspaceId;
		this.store = store;
		this.sync = new SyncEngine(store, this, validateRemoteEvents);
		this.onInboundDirect = onInboundDirect;
		this.debug = debug == null ? ignored -> {} : debug;
	}

	public void onCoreEventStored(Consumer<Event> callback) { this.onCoreEventStored = callback; }
	public void onCoreSyncApplied(Consumer<java.util.List<Event>> callback) { this.onCoreSyncApplied = callback; }
	public void onPeerConnectionsChanged(Consumer<Set<String>> callback) { this.onPeerConnectionsChanged = callback; }

	public SyncEngine sync() { return sync; }

	public boolean hasPeer(String peerId) {
		PeerLink link = peers.get(peerId);
		return link != null && link.active();
	}

	public Set<String> connectedPeers() {
		Set<String> active = new LinkedHashSet<>();
		for (Map.Entry<String, PeerLink> entry : peers.entrySet()) {
			if (entry.getValue().active()) active.add(entry.getKey());
		}
		return active;
	}

	public void connectTo(String peerId, String peerUrl, String workspaceId, Runnable onReady) {
		if (shuttingDown || peerId == null || peerUrl == null) {
			if (onReady != null) onReady.run();
			return;
		}
		try {
			String uri = webSocketUriForEndpoint(peerUrl) + "/ws?wkId="
					+ java.net.URLEncoder.encode(workspaceId != null ? workspaceId : "", "UTF-8")
					+ "&userId=" + java.net.URLEncoder.encode(localPeerId, "UTF-8")
					+ "&direct=true";
			PeerLink link = new PeerLink(peerId, uri);
			PeerLink existing = peers.putIfAbsent(peerId, link);
			if (existing != null) {
				if (existing.active()) {
					if (onReady != null) onReady.run();
					return;
				}
				peers.remove(peerId, existing);
				try { existing.close(); } catch (Exception ignored) { debug.accept("close: " + ignored.getMessage()); }
				if (peers.putIfAbsent(peerId, link) != null) {
					if (onReady != null) onReady.run();
					return;
				}
			}
			link.connect(onReady);
		} catch (Exception e) {
			debug.accept("No se pudo conectar P2P a " + peerId + ": " + e.getMessage());
			peers.remove(peerId);
		}
	}

	public void disconnectFrom(String peerId) {
		PeerLink link = peers.remove(peerId);
		if (link != null) link.close();
		notifyPeerConnectionsChanged();
	}

	public void disconnectAll() {
		shuttingDown = true;
		for (String peerId : new LinkedHashSet<>(peers.keySet())) disconnectFrom(peerId);
	}

	@Override
	public void send(String peerId, Event event) {
		if (shuttingDown || peerId == null || peerId.isBlank()) return;
		if (event != null && !store.hasEvent(event.eventId())) store.append(event);
		PeerLink link = peers.get(peerId);
		if (link != null && link.active()) {
			link.send(event);
		}
	}

	@Override
	public void broadcast(Event event) {
		if (shuttingDown) return;
		if (event != null && !store.hasEvent(event.eventId())) store.append(event);
		for (PeerLink link : peers.values()) {
			if (link.active()) link.send(event);
		}
	}

	public void broadcastSyncRequest() {
		if (shuttingDown) return;
		for (PeerLink link : peers.values()) {
			if (link.active()) link.sendSyncRequest();
		}
	}

	public void sendProtocolEvent(String peerId, CoreEnvelope envelope) {
		if (shuttingDown) return;
		PeerLink link = peers.get(peerId);
		if (link != null && link.active()) link.sendEnvelope(envelope);
	}

	public void broadcastProtocolEvent(CoreEnvelope envelope) {
		if (shuttingDown) return;
		for (PeerLink link : peers.values()) {
			if (link.active()) link.sendEnvelope(envelope);
		}
	}

	@Override
	public Set<String> peers() {
		return connectedPeers();
	}

	private void notifyPeerConnectionsChanged() {
		if (shuttingDown) return;
		if (onPeerConnectionsChanged != null) onPeerConnectionsChanged.accept(connectedPeers());
	}

	private String webSocketUriForEndpoint(String endpoint) {
		if (endpoint == null) return "";
		String value = endpoint.trim();
		if (value.startsWith("ws://") || value.startsWith("wss://")) return value;
		if (value.startsWith("https://")) return "wss://" + value.substring("https://".length());
		if (value.startsWith("http://")) return "ws://" + value.substring("http://".length());
		if (value.endsWith(".trycloudflare.com")) return "wss://" + value;
		return "ws://" + value;
	}

	private class PeerLink {
		private final String peerId;
		private final String uri;
		private WsClient client;
		private volatile boolean ready;

		PeerLink(String peerId, String uri) {
			this.peerId = peerId;
			this.uri = uri;
		}

		boolean active() { return ready && client != null && client.isOpen(); }

		void connect(Runnable onReady) {
			boolean connected = false;
			for (int attempt = 1; attempt <= 3; attempt++) {
				if (shuttingDown) break;
				if (client != null) {
					try { client.close(); } catch (Exception ignored) { debug.accept("close: " + ignored.getMessage()); }
					client = null;
				}
				try {
					client = new WsClient(new URI(uri), null, envelope -> {
						if (CoreEnvelopeCodec.CORE_EVENT_NAME.equals(envelope.name())) {
							Event coreEvent = CoreEnvelopeCodec.decodeCoreEvent(envelope);
							if (coreEvent != null && sync.receiveEvent(coreEvent)) {
								if (onCoreEventStored != null) onCoreEventStored.accept(coreEvent);
							}
						} else if (CoreEnvelopeCodec.CORE_SYNC_REQUEST_NAME.equals(envelope.name())) {
							handleIncomingSyncRequest(envelope);
						} else if (CoreEnvelopeCodec.CORE_SYNC_RESPONSE_NAME.equals(envelope.name())) {
							handleIncomingSyncResponse(envelope);
						} else if (onInboundDirect != null) {
							try {
								onInboundDirect.accept(null, envelope);
							} catch (Exception ignored) { /* listener */ }
						}
					}, error -> {
						if (!shuttingDown) debug.accept("P2P error con " + peerId + ": " + error);
						ready = false;
						peers.remove(peerId, this);
						notifyPeerConnectionsChanged();
					}, () -> {
						if (!shuttingDown) debug.accept("P2P desconectado de " + peerId);
						ready = false;
						peers.remove(peerId, this);
						notifyPeerConnectionsChanged();
					}, false);
					client.setConnectionLostTimeout(7);
					client.connectBlocking(6, java.util.concurrent.TimeUnit.SECONDS);
					if (shuttingDown) { client.close(); break; }
					ready = true;
					connected = true;
					debug.accept("P2P conectado a " + peerId);
					notifyPeerConnectionsChanged();
					sendSyncRequest();
					break;
				} catch (Exception e) {
					if (!shuttingDown) debug.accept("P2P intento " + attempt + " fallido a " + peerId + ": " + e.getMessage());
					if (attempt < 3) {
						try { Thread.sleep(500 * attempt); } catch (InterruptedException ignored) { break; }
					}
				}
			}
			if (!connected && !shuttingDown) {
				debug.accept("P2P imposible conectar a " + peerId + " tras 3 intentos");
				ready = false;
				peers.remove(peerId, this);
				notifyPeerConnectionsChanged();
			}
			if (onReady != null) onReady.run();
		}

		void send(Event event) {
			if (!active()) return;
			try {
				client.sendEnvelope(CoreEnvelope.of(
						CoreEnvelopeCodec.CORE_EVENT_NAME,
						localPeerId,
						CoreEnvelopeCodec.encodeCoreEvent(event)));
			} catch (Exception e) {
				debug.accept("P2P error enviando a " + peerId + ": " + e.getMessage());
				ready = false;
				peers.remove(peerId, this);
				notifyPeerConnectionsChanged();
			}
		}

		void sendEnvelope(CoreEnvelope envelope) {
			if (!active() || envelope == null) return;
			try {
				client.sendEnvelope(envelope);
			} catch (Exception e) {
				debug.accept("P2P error enviando protocolo a " + peerId + ": " + e.getMessage());
				ready = false;
				peers.remove(peerId, this);
				notifyPeerConnectionsChanged();
			}
		}

		void sendSyncRequest() {
			if (!active()) return;
			try {
				String wsId = workspaceId.get();
				if (wsId == null || wsId.isBlank()) return;
				CoreEnvelope request = CoreEnvelope.of(
						CoreEnvelopeCodec.CORE_SYNC_REQUEST_NAME,
						localPeerId,
						CoreEnvelopeCodec.encodeKnownEventIds(store.listEventIds(wsId)));
				client.sendEnvelope(request);
			} catch (Exception e) {
				debug.accept("P2P error solicitando sync a " + peerId + ": " + e.getMessage());
				ready = false;
				peers.remove(peerId, this);
				notifyPeerConnectionsChanged();
			}
		}

		void close() {
			ready = false;
			notifyPeerConnectionsChanged();
			if (client != null) {
				try { client.close(); } catch (Exception ignored) { /* cleanup */ }
			}
		}
	}

	private void handleIncomingSyncRequest(CoreEnvelope envelope) {
		try {
			String wsId = workspaceId.get();
			if (wsId == null || wsId.isBlank()) return;
			Set<String> knownIds = CoreEnvelopeCodec.decodeKnownEventIds(envelope);
			java.util.List<Event> missing = store.getMissingEvents(wsId, knownIds);
			if (!missing.isEmpty()) {
				PeerLink link = clientForPeer(envelope.userId());
				if (link != null && link.active()) {
					try {
						CoreEnvelope response = CoreEnvelope.of(
								CoreEnvelopeCodec.CORE_SYNC_RESPONSE_NAME,
								localPeerId,
								CoreEnvelopeCodec.encodeSyncPayload(missing));
						link.client.sendEnvelope(response);
					} catch (Exception e) {
						debug.accept("P2P error respondiendo sync: " + e.getMessage());
					}
				}
			}
		} catch (Exception e) {
			debug.accept("P2P error sync request: " + e.getMessage());
		}
	}

	private void handleIncomingSyncResponse(CoreEnvelope envelope) {
		try {
			java.util.List<Event> received = CoreEnvelopeCodec.decodeSyncEvents(envelope);
			sync.applyReceivedEvents(received);
			if (onCoreSyncApplied != null && !received.isEmpty()) onCoreSyncApplied.accept(received);
		} catch (Exception e) {
			debug.accept("P2P error sync response: " + e.getMessage());
		}
	}

	private PeerLink clientForPeer(String peerId) {
		return peerId != null ? peers.get(peerId) : null;
	}
}
