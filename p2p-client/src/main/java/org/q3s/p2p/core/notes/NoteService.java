package org.q3s.p2p.core.notes;

import java.util.Map;

import org.q3s.p2p.core.events.EventFactory;
import org.q3s.p2p.core.events.EventTypes;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.ports.EventStore;

public class NoteService {
	private final EventStore store;
	private final EventFactory events;

	public NoteService(EventStore store, EventFactory events) {
		this.store = store;
		this.events = events;
	}

	public Event updateNote(String workspaceId, String authorMemberId, String noteId, String text) {
		Event event = events.create(workspaceId, EventTypes.NOTE_UPDATED, authorMemberId, Map.of("note_id", noteId, "text", text), null);
		store.append(event);
		return event;
	}

	public Event insertText(String workspaceId, String authorMemberId, String noteId, int position, String text) {
		Event event = events.create(workspaceId, EventTypes.NOTE_INSERT, authorMemberId,
				Map.of("note_id", noteId, "position", position, "text", text == null ? "" : text), null);
		store.append(event);
		return event;
	}

	public Event deleteText(String workspaceId, String authorMemberId, String noteId, int position, int length) {
		Event event = events.create(workspaceId, EventTypes.NOTE_DELETE_OP, authorMemberId,
				Map.of("note_id", noteId, "position", Math.max(0, position), "length", Math.max(0, length)), null);
		store.append(event);
		return event;
	}

	public Event applyStyle(String workspaceId, String authorMemberId, String noteId, int position, int length, String style) {
		Event event = events.create(workspaceId, EventTypes.NOTE_STYLE_APPLIED, authorMemberId,
				Map.of("note_id", noteId, "position", Math.max(0, position), "length", Math.max(0, length), "style", style == null ? "" : style), null);
		store.append(event);
		return event;
	}
}
