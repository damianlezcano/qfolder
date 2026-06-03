package org.q3s.p2p.core.notes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.q3s.p2p.core.events.EventFactory;
import org.q3s.p2p.core.events.EventTypes;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.ports.EventStore;

public class NoteService {
	private final EventStore store;
	private final EventFactory events;
	private final Map<String, AtomicLong> memberCounters = new ConcurrentHashMap<>();

	public NoteService(EventStore store, EventFactory events) {
		this.store = store;
		this.events = events;
	}

	public Event updateNote(String workspaceId, String authorMemberId, String noteId, String text) {
		Event event = events.create(workspaceId, EventTypes.NOTE_UPDATED, authorMemberId,
				Map.of("note_id", noteId, "text", text == null ? "" : text), null);
		store.append(event);
		return event;
	}

	public Event insertLine(String workspaceId, String authorMemberId, String noteId, String afterLineId, String text) {
		long counter = nextCounter(authorMemberId);
		String lineId = buildLineId(authorMemberId, counter);
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("note_id", noteId);
		payload.put("line_id", lineId);
		payload.put("op_id", lineId);
		payload.put("after_line_id", afterLineId == null || afterLineId.isBlank() ? "" : afterLineId);
		payload.put("text", text == null ? "" : text);
		payload.put("created_at_ms", System.currentTimeMillis());
		Event event = events.create(workspaceId, EventTypes.NOTE_INSERT, authorMemberId, payload, null);
		store.append(event);
		return event;
	}

	public Event deleteLine(String workspaceId, String authorMemberId, String noteId, String lineId) {
		long counter = nextCounter(authorMemberId);
		String opId = "del:" + buildLineId(authorMemberId, counter);
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("note_id", noteId);
		payload.put("line_id", lineId);
		payload.put("op_id", opId);
		payload.put("created_at_ms", System.currentTimeMillis());
		Event event = events.create(workspaceId, EventTypes.NOTE_DELETE_OP, authorMemberId, payload, null);
		store.append(event);
		return event;
	}

	public Event applyStyle(String workspaceId, String authorMemberId, String noteId, int position, int length, String style) {
		Event event = events.create(workspaceId, EventTypes.NOTE_STYLE_APPLIED, authorMemberId,
				Map.of("note_id", noteId, "position", Math.max(0, position), "length", Math.max(0, length), "style", style == null ? "" : style), null);
		store.append(event);
		return event;
	}

	private long nextCounter(String memberId) {
		return memberCounters.computeIfAbsent(memberId == null ? "" : memberId, ignored -> new AtomicLong(0))
				.incrementAndGet();
	}

	private String buildLineId(String memberId, long counter) {
		String prefix = memberId == null || memberId.isBlank() ? "anon" : memberId;
		return prefix + ":" + Long.toString(counter, 36);
	}
}
