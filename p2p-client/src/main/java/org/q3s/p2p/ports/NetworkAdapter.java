package org.q3s.p2p.ports;

import java.util.Set;

import org.q3s.p2p.core.model.Event;

public interface NetworkAdapter {
	void send(String peerId, Event event);
	void broadcast(Event event);
	Set<String> peers();
}
