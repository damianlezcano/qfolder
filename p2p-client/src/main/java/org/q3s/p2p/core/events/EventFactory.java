package org.q3s.p2p.core.events;

import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;

import org.q3s.p2p.core.model.AuthInfo;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.ports.ClockProvider;
import org.q3s.p2p.ports.IdGenerator;

public class EventFactory {
	private final IdGenerator ids;
	private final ClockProvider clock;
	private final UnaryOperator<Event> stamper;

	public EventFactory(IdGenerator ids, ClockProvider clock) {
		this(ids, clock, UnaryOperator.identity());
	}

	public EventFactory(IdGenerator ids, ClockProvider clock, UnaryOperator<Event> stamper) {
		this.ids = ids;
		this.clock = clock;
		this.stamper = stamper == null ? UnaryOperator.identity() : stamper;
	}

	public Event create(String workspaceId, String type, String authorMemberId, Map<String, Object> payload, List<String> parents) {
		Event event = new Event(ids.newId("evt"), workspaceId, type, authorMemberId, clock.now(),
				parents == null ? List.of() : List.copyOf(parents), payload == null ? Map.of() : Map.copyOf(payload),
				new AuthInfo("token"), null, EventTypes.isPersistent(type));
		return stamper.apply(event);
	}
}
