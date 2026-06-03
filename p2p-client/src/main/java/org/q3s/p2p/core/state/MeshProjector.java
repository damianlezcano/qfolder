package org.q3s.p2p.core.state;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.q3s.p2p.core.events.EventTypes;
import org.q3s.p2p.core.model.Event;

/**
 * Proyecta eventos de mesh/membresía distribuida: peer status updates (URL pública
 * y conexiones declaradas). Mantiene el estado de WorkspaceState relativo a la
 * topología mesh, más una cache en memoria de los últimos status de cada miembro
 * (PEER_STATUS_UPDATED es efímero y no se persiste en event store).
 */
public class MeshProjector {

	private static final Map<String, String> LIVE_PEER_URLS = new ConcurrentHashMap<>();
	private static final Map<String, java.util.Set<String>> LIVE_PEER_CONNECTIONS = new ConcurrentHashMap<>();

	public void apply(WorkspaceState state, Event event) {
		if (state == null || event == null) return;
		Map<String, Object> p = event.payload();
		if (p == null) return;
		if (EventTypes.PEER_STATUS_UPDATED.equals(event.type())) {
			String memberId = String.valueOf(p.getOrDefault("member_id", event.authorMemberId()));
			String peerUrl = String.valueOf(p.getOrDefault("peer_url", ""));
			if (memberId.isBlank() || "null".equals(memberId)) return;
			if (peerUrl != null && !peerUrl.isBlank() && !"null".equals(peerUrl)) {
				LIVE_PEER_URLS.put(memberId, peerUrl);
				state.peerUrls().put(memberId, peerUrl);
			}
			Object raw = p.get("connected_peers");
			if (raw instanceof List<?> list) {
				java.util.Set<String> conns = new java.util.LinkedHashSet<>((List<String>) list);
				LIVE_PEER_CONNECTIONS.put(memberId, conns);
				state.peerConnections().put(memberId, conns);
			}
		}
	}

	/**
	 * Carga el cache en memoria sobre el state. Llamar tras construir un WorkspaceState
	 * para repoblar el estado mesh a partir de PEER_STATUS efímeros recibidos en runtime
	 * (no persistidos en event store).
	 */
	public void hydrate(WorkspaceState state) {
		if (state == null) return;
		state.peerUrls().putAll(LIVE_PEER_URLS);
		LIVE_PEER_CONNECTIONS.forEach((k, v) -> state.peerConnections().put(k, new java.util.LinkedHashSet<>(v)));
	}

	public static void clearLiveCache() {
		LIVE_PEER_URLS.clear();
		LIVE_PEER_CONNECTIONS.clear();
	}
}
