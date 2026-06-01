package org.q3s.p2p.core.chat;

import java.util.Map;

import org.q3s.p2p.core.events.EventFactory;
import org.q3s.p2p.core.events.EventTypes;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.ports.EventStore;
import org.q3s.p2p.ports.IdGenerator;

public class ChatService {
	private final EventStore store;
	private final EventFactory events;
	private final IdGenerator ids;

	public ChatService(EventStore store, EventFactory events, IdGenerator ids) {
		this.store = store;
		this.events = events;
		this.ids = ids;
	}

	public Event sendMessage(String workspaceId, String authorMemberId, String text) {
		Event event = events.create(workspaceId, EventTypes.CHAT_MESSAGE_CREATED, authorMemberId,
				Map.of("message_id", ids.newId("msg"), "text", text), null);
		store.append(event);
		return event;
	}
}
