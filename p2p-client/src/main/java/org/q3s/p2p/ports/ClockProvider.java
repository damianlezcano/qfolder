package org.q3s.p2p.ports;

import java.time.Instant;

public interface ClockProvider {
	Instant now();
}
