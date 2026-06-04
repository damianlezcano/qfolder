package org.q3s.p2p.ports;

import java.util.Set;

import org.q3s.p2p.core.model.Event;

/**
 * Puerto de red para enviar eventos core entre peers. Las implementaciones
 * viven en adapters/network: SimulatedNetworkAdapter para tests con
 * packet loss y latencia programables, P2PNetworkAdapter para produccion
 * sobre conexiones WebSocket directas entre peers.
 */
public interface NetworkAdapter {
	void send(String peerId, Event event);
	void broadcast(Event event);
	Set<String> peers();
}
