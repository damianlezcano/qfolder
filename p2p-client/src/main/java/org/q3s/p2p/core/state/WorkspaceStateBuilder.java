package org.q3s.p2p.core.state;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.q3s.p2p.core.model.Event;

/**
 * Fachada de proyeccion de eventos a WorkspaceState. Internamente delega en
 * proyectores especializados (Membership, Content, Mesh) para mantener cada
 * responsabilidad aislada. Ordena los eventos por (createdAt, eventId) para
 * garantizar replay deterministico independiente del orden de insercion.
 */
public final class WorkspaceStateBuilder {
	private static final MembershipProjector MEMBERSHIP = new MembershipProjector();
	private static final ContentProjector CONTENT = new ContentProjector();
	private static final MeshProjector MESH = new MeshProjector();

	private WorkspaceStateBuilder() {}

	@SuppressWarnings("unchecked")
	public static WorkspaceState fromEvents(List<Event> events) {
		WorkspaceState state = new WorkspaceState();
		List<Event> ordered = new ArrayList<>(events);
		ordered.sort(Comparator.comparing(Event::createdAt).thenComparing(Event::eventId));
		for (Event event : ordered) {
			MEMBERSHIP.apply(state, event);
			CONTENT.apply(state, event);
			MESH.apply(state, event);
		}
		MESH.hydrate(state);
		return state;
	}

	public static WorkspaceState fromSnapshot(java.util.Map<String, Object> snapshot) {
		WorkspaceState state = new WorkspaceState();
		if (snapshot == null) return state;
		applyMembershipSnapshot(state, snapshot);
		applyContentSnapshot(state, snapshot);
		applyMeshSnapshot(state, snapshot);
		return state;
	}

	private static void applyMembershipSnapshot(WorkspaceState state, java.util.Map<String, Object> snap) {
		Object ws = snap.get("workspace");
		if (ws instanceof org.q3s.p2p.core.model.Workspace) state.workspace((org.q3s.p2p.core.model.Workspace) ws);
		Object auth = snap.get("authorizedMembers");
		if (auth instanceof java.util.Map<?, ?> map) for (var e : map.entrySet())
			if (e.getValue() instanceof org.q3s.p2p.core.model.Member m) state.authorizedMembers().put(m.memberId(), m);
		Object revoked = snap.get("revokedMembers");
		if (revoked instanceof java.util.Collection<?> c) state.revokedMembers().addAll((java.util.Collection<String>) c);
	}

	private static void applyContentSnapshot(WorkspaceState state, java.util.Map<String, Object> snap) {
		Object notes = snap.get("notes");
		if (notes instanceof java.util.Map<?, ?> map) for (var e : map.entrySet())
			if (e.getValue() instanceof org.q3s.p2p.core.model.Note n) state.notes().put(n.noteId(), n);
		Object chat = snap.get("chatMessages");
		if (chat instanceof java.util.Map<?, ?> map) for (var e : map.entrySet())
			if (e.getValue() instanceof org.q3s.p2p.core.model.ChatMessage m) state.chatMessages().put(m.messageId(), m);
		Object files = snap.get("files");
		if (files instanceof java.util.Map<?, ?> map) for (var e : map.entrySet())
			if (e.getValue() instanceof org.q3s.p2p.core.model.FileMetadata m) state.files().put(m.fileId(), m);
		Object strokes = snap.get("strokes");
		if (strokes instanceof java.util.Map<?, ?> map) for (var e : map.entrySet())
			if (e.getValue() instanceof org.q3s.p2p.core.model.WhiteboardStroke s) state.strokes().put(s.strokeId(), s);
		Object objects = snap.get("whiteboardObjects");
		if (objects instanceof java.util.Map<?, ?> map) for (var e : map.entrySet())
			state.whiteboardObjects().put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
	}

	private static void applyMeshSnapshot(WorkspaceState state, java.util.Map<String, Object> snap) {
		Object urls = snap.get("peerUrls");
		if (urls instanceof java.util.Map<?, ?> map) for (var e : map.entrySet())
			state.peerUrls().put(String.valueOf(e.getKey()), String.valueOf(e.getValue()));
		Object conns = snap.get("peerConnections");
		if (conns instanceof java.util.Map<?, ?> map) for (var e : map.entrySet()) {
			if (e.getValue() instanceof java.util.Collection<?> c) {
				state.peerConnections().put(String.valueOf(e.getKey()),
						new java.util.LinkedHashSet<>((java.util.Collection<String>) c));
			}
		}
	}
}
