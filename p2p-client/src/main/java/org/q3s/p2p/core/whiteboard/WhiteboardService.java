package org.q3s.p2p.core.whiteboard;

import java.util.List;
import java.util.Map;

import org.q3s.p2p.core.events.EventFactory;
import org.q3s.p2p.core.events.EventTypes;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.ports.EventStore;
import org.q3s.p2p.ports.IdGenerator;

public class WhiteboardService {
	private final EventStore store;
	private final EventFactory events;
	private final IdGenerator ids;

	public WhiteboardService(EventStore store, EventFactory events, IdGenerator ids) {
		this.store = store;
		this.events = events;
		this.ids = ids;
	}

	public Event preview(String workspaceId, String authorMemberId) {
		return events.create(workspaceId, EventTypes.DRAWING_PREVIEW, authorMemberId, Map.of(), null);
	}

	public Event finishStroke(String workspaceId, String authorMemberId, List<int[]> points) {
		return finishStroke(workspaceId, authorMemberId, points, "#000000", 2);
	}

	public Event finishStroke(String workspaceId, String authorMemberId, List<int[]> points, String color, int width) {
		Event event = events.create(workspaceId, EventTypes.WHITEBOARD_STROKE_ADDED, authorMemberId,
				Map.of("stroke_id", ids.newId("stroke"), "points", points,
						"color", color == null || color.isBlank() ? "#000000" : color,
						"width", Math.max(1, width)), null);
		store.append(event);
		return event;
	}

	public Event objectAdded(String workspaceId, String authorMemberId, String objectId, String operation) {
		Event event = events.create(workspaceId, EventTypes.WHITEBOARD_OBJECT_ADDED, authorMemberId,
				Map.of("object_id", objectId, "operation", operation == null ? "" : operation), null);
		store.append(event);
		return event;
	}

	public Event objectMoved(String workspaceId, String authorMemberId, String objectId, String operation) {
		Event event = events.create(workspaceId, EventTypes.WHITEBOARD_OBJECT_MOVED, authorMemberId,
				Map.of("object_id", objectId, "operation", operation == null ? "" : operation), null);
		store.append(event);
		return event;
	}

	public Event objectDeleted(String workspaceId, String authorMemberId, String objectId) {
		Event event = events.create(workspaceId, EventTypes.WHITEBOARD_OBJECT_DELETED, authorMemberId,
				Map.of("object_id", objectId == null ? "" : objectId), null);
		store.append(event);
		return event;
	}

	public Event cleared(String workspaceId, String authorMemberId) {
		Event event = events.create(workspaceId, EventTypes.WHITEBOARD_CLEARED, authorMemberId, Map.of(), null);
		store.append(event);
		return event;
	}
}
