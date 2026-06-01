package org.q3s.p2p.adapters.memory;

import java.time.Instant;

import org.q3s.p2p.ports.ClockProvider;

public class SystemClockProvider implements ClockProvider {
	@Override public Instant now() { return Instant.now(); }
}
