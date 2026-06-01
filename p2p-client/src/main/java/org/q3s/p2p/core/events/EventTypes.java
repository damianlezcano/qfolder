package org.q3s.p2p.core.events;

import java.util.Set;

public final class EventTypes {
	public static final String WORKSPACE_CREATED = "workspace.created";
	public static final String MEMBER_JOIN_REQUESTED = "member.join.requested";
	public static final String MEMBER_JOIN_APPROVAL = "member.join.approval";
	public static final String MEMBER_REVOKED = "member.revoked";
	public static final String MEMBER_ROLE_CHANGED = "member.role.changed";
	public static final String CHAT_MESSAGE_CREATED = "chat.message.created";
	public static final String NOTE_CREATED = "note.created";
	public static final String NOTE_UPDATED = "note.updated";
	public static final String NOTE_DELETED = "note.deleted";
	public static final String NOTE_INSERT = "note.insert";
	public static final String NOTE_DELETE_OP = "note.deleteOp";
	public static final String NOTE_STYLE_APPLIED = "note.styleApplied";
	public static final String WHITEBOARD_STROKE_ADDED = "whiteboard.stroke.added";
	public static final String WHITEBOARD_OBJECT_ADDED = "whiteboard.object.added";
	public static final String WHITEBOARD_OBJECT_MOVED = "whiteboard.object.moved";
	public static final String WHITEBOARD_OBJECT_DELETED = "whiteboard.object.deleted";
	public static final String WHITEBOARD_CLEARED = "whiteboard.cleared";
	public static final String FILE_SHARED = "file.shared";
	public static final String PEER_STATUS_UPDATED = "peer.status.updated";
	public static final String USER_TYPING = "user.typing";
	public static final String CURSOR_MOVED = "cursor.moved";
	public static final String DRAWING_PREVIEW = "drawing.preview";

	private static final Set<String> EPHEMERAL = Set.of(USER_TYPING, CURSOR_MOVED, DRAWING_PREVIEW, "mouse.moved");

	private EventTypes() {}
	public static boolean isPersistent(String type) { return !EPHEMERAL.contains(type); }
}
