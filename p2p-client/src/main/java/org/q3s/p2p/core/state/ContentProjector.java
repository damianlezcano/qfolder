package org.q3s.p2p.core.state;

import java.util.List;
import java.util.Map;

import org.q3s.p2p.core.events.EventTypes;
import org.q3s.p2p.core.model.ChatMessage;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.model.FileMetadata;
import org.q3s.p2p.core.model.Note;
import org.q3s.p2p.core.model.WhiteboardStroke;
import org.q3s.p2p.core.whiteboard.WhiteboardMerger;

/**
 * Proyecta eventos de contenido: chat, notas (CRDT o snapshot), archivos y pizarra
 * (strokes, objetos, clear). Mantiene el estado de WorkspaceState relativo al
 * contenido compartido.
 */
public class ContentProjector {

	private final WhiteboardMerger merger = new WhiteboardMerger();

	public void apply(WorkspaceState state, Event event) {
		if (state == null || event == null) return;
		Map<String, Object> p = event.payload();
		if (p == null) return;
		switch (event.type()) {
			case EventTypes.CHAT_MESSAGE_CREATED -> applyChat(state, event, p);
			case EventTypes.NOTE_CREATED, EventTypes.NOTE_UPDATED -> applyNoteReplace(state, p);
			case EventTypes.NOTE_DELETED -> applyNoteDeleted(state, p);
			case EventTypes.NOTE_INSERT, EventTypes.NOTE_DELETE_OP, EventTypes.NOTE_STYLE_APPLIED -> applyNoteOp(state, p, event);
			case EventTypes.WHITEBOARD_STROKE_ADDED -> applyStroke(state, event, p);
			case EventTypes.WHITEBOARD_OBJECT_ADDED -> applyObjectAdded(state, p);
			case EventTypes.WHITEBOARD_OBJECT_MOVED -> applyObjectMoved(state, p);
			case EventTypes.WHITEBOARD_OBJECT_DELETED -> applyObjectDeleted(state, p);
			case EventTypes.WHITEBOARD_CLEARED -> applyCleared(state, p);
			case EventTypes.FILE_SHARED -> applyFileEvent(state, p);
			default -> { /* not content */ }
		}
	}

	private void applyChat(WorkspaceState state, Event event, Map<String, Object> p) {
		state.chatMessages().put(String.valueOf(p.get("message_id")),
				new ChatMessage(String.valueOf(p.get("message_id")), event.authorMemberId(), String.valueOf(p.get("text"))));
	}

	private void applyNoteReplace(WorkspaceState state, Map<String, Object> p) {
		state.notes().put(String.valueOf(p.get("note_id")),
				new Note(String.valueOf(p.get("note_id")), String.valueOf(p.get("text"))));
	}

	private void applyNoteDeleted(WorkspaceState state, Map<String, Object> p) {
		state.notes().remove(String.valueOf(p.get("note_id")));
	}

	private void applyNoteOp(WorkspaceState state, Map<String, Object> p, Event event) {
		String noteId = String.valueOf(p.get("note_id"));
		Note existing = state.notes().getOrDefault(noteId, new Note(noteId, ""));
		String text = applyCrdtOp(existing.text(), event.type(), p);
		state.notes().put(noteId, new Note(noteId, text));
	}

	private String applyCrdtOp(String current, String type, Map<String, Object> p) {
		return switch (type) {
			case EventTypes.NOTE_INSERT -> {
				int pos = ((Number) p.getOrDefault("position", 0)).intValue();
				String text = String.valueOf(p.getOrDefault("text", ""));
				pos = Math.max(0, Math.min(pos, current.length()));
				yield current.substring(0, pos) + text + current.substring(pos);
			}
			case EventTypes.NOTE_DELETE_OP -> {
				int pos = ((Number) p.getOrDefault("position", 0)).intValue();
				int delLen = ((Number) p.getOrDefault("length", 0)).intValue();
				pos = Math.max(0, Math.min(pos, current.length()));
				int delPos = Math.min(pos + delLen, current.length());
				yield current.substring(0, pos) + current.substring(delPos);
			}
			case EventTypes.NOTE_STYLE_APPLIED -> current;
			default -> current;
		};
	}

	@SuppressWarnings("unchecked")
	private void applyStroke(WorkspaceState state, Event event, Map<String, Object> p) {
		state.strokes().put(String.valueOf(p.get("stroke_id")),
				new WhiteboardStroke(String.valueOf(p.get("stroke_id")), event.authorMemberId(),
						(List<int[]>) p.getOrDefault("points", List.of()),
						String.valueOf(p.getOrDefault("color", "#000000")),
						((Number) p.getOrDefault("width", 2)).intValue()));
	}

	private void applyObjectAdded(WorkspaceState state, Map<String, Object> p) {
		state.whiteboardObjects().put(String.valueOf(p.get("object_id")), String.valueOf(p.getOrDefault("operation", "")));
	}

	private void applyObjectMoved(WorkspaceState state, Map<String, Object> p) {
		String objectId = String.valueOf(p.get("object_id"));
		String operation = String.valueOf(p.getOrDefault("operation", ""));
		String existing = state.whiteboardObjects().get(objectId);
		state.whiteboardObjects().put(objectId, merger.merge(existing, operation));
	}

	private void applyObjectDeleted(WorkspaceState state, Map<String, Object> p) {
		state.whiteboardObjects().remove(String.valueOf(p.get("object_id")));
	}

	private void applyCleared(WorkspaceState state, Map<String, Object> p) {
		if (Boolean.parseBoolean(String.valueOf(p.getOrDefault("all", "true")))) {
			state.whiteboardObjects().clear();
			state.strokes().clear();
		} else {
			String author = String.valueOf(p.getOrDefault("author", ""));
			state.whiteboardObjects().entrySet().removeIf(e -> e.getValue() != null && e.getValue().contains(author));
		}
	}

	private void applyFileEvent(WorkspaceState state, Map<String, Object> p) {
		String fileId = String.valueOf(p.get("file_id"));
		if (fileId == null || fileId.isBlank() || "null".equals(fileId)) return;
		state.files().put(fileId, new FileMetadata(fileId,
				String.valueOf(p.get("name")),
				((Number) p.getOrDefault("size", 0)).longValue(),
				String.valueOf(p.getOrDefault("hash", "")),
				(List<String>) p.getOrDefault("chunks", List.of()),
				String.valueOf(p.getOrDefault("shared_by", "")),
				Boolean.parseBoolean(String.valueOf(p.getOrDefault("chat_attachment", "false")))));
	}
}
