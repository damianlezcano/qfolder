package org.q3s.p2p.core.state;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.q3s.p2p.core.events.EventTypes;
import org.q3s.p2p.core.model.ChatMessage;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.model.FileMetadata;
import org.q3s.p2p.core.model.Member;
import org.q3s.p2p.core.model.Note;
import org.q3s.p2p.core.model.WhiteboardStroke;
import org.q3s.p2p.core.model.Workspace;

public final class WorkspaceStateBuilder {
	private WorkspaceStateBuilder() {}

	private static String mergeWhiteboardImageData(String existing, String update) {
		if (update == null) return "";
		if (!isWhiteboardImageWithoutData(update)) return update;
		String data = whiteboardImageData(existing);
		return data == null || data.isBlank() ? update : update + "|" + data;
	}

	private static boolean isWhiteboardImageWithoutData(String operation) {
		if (operation == null || !operation.startsWith("I|")) return false;
		return operation.indexOf('|', 2) < 0;
	}

	private static String whiteboardImageData(String operation) {
		if (operation == null || !operation.startsWith("I|")) return null;
		int separator = operation.indexOf('|', 2);
		if (separator < 0 || separator + 1 >= operation.length()) return null;
		return operation.substring(separator + 1);
	}

	private static String applyCrdtOp(String current, String type, Map<String, Object> p) {
		int pos = ((Number) p.getOrDefault("position", 0)).intValue();
		int len = ((Number) p.getOrDefault("length", 0)).intValue();
		return switch (type) {
			case EventTypes.NOTE_INSERT -> {
				String ins = String.valueOf(p.getOrDefault("text", ""));
				int insertPos = Math.min(pos, current.length());
				yield current.substring(0, insertPos) + ins + current.substring(insertPos);
			}
			case EventTypes.NOTE_DELETE_OP -> {
				int delPos = Math.min(pos, current.length());
				int delLen = Math.min(len, current.length() - delPos);
				yield current.substring(0, delPos) + current.substring(delPos + delLen);
			}
			case EventTypes.NOTE_STYLE_APPLIED -> current;
			default -> current;
		};
	}

	@SuppressWarnings("unchecked")
	public static WorkspaceState fromEvents(List<Event> events) {
		WorkspaceState state = new WorkspaceState();
		for (Event event : events) {
			Map<String, Object> p = event.payload();
			switch (event.type()) {
				case EventTypes.WORKSPACE_CREATED -> {
					int required = ((Number) p.getOrDefault("required_approvals", 1)).intValue();
					state.workspace(new Workspace(event.workspaceId(), String.valueOf(p.get("name")), required, String.valueOf(p.get("auth_mode"))));
					Member creator = new Member(String.valueOf(p.get("creator_member_id")), String.valueOf(p.get("creator_display_name")),
							String.valueOf(p.get("creator_device_id")), String.valueOf(p.get("creator_membership_token")), false,
							String.valueOf(p.getOrDefault("creator_public_key", "")));
					state.authorizedMembers().put(creator.memberId(), creator);
				}
				case EventTypes.MEMBER_JOIN_REQUESTED -> {
					String memberId = String.valueOf(p.get("candidate_member_id"));
					if (!state.authorizedMembers().containsKey(memberId) && !state.revokedMembers().contains(memberId)) {
						state.pendingMembers().put(memberId, new Member(memberId, String.valueOf(p.get("candidate_display_name")),
								String.valueOf(p.get("candidate_device_id")), String.valueOf(p.get("membership_token")), false,
								String.valueOf(p.getOrDefault("public_key", ""))));
					}
				}
				case EventTypes.MEMBER_JOIN_APPROVAL -> {
					String candidateId = String.valueOf(p.get("candidate_member_id"));
					String approver = String.valueOf(p.get("approved_by"));
					if (state.isAuthorized(approver) && state.pendingMembers().containsKey(candidateId)) {
						state.approvals().computeIfAbsent(candidateId, ignored -> new LinkedHashSet<>()).add(approver);
						int configuredRequired = state.workspace() != null ? state.workspace().requiredApprovals() : 1;
						int availableApprovers = Math.max(1, state.authorizedMembers().size());
						int required = Math.max(1, Math.min(configuredRequired, availableApprovers));
						if (state.approvals().get(candidateId).size() >= required) {
							Member member = state.pendingMembers().remove(candidateId);
							if (member != null) {
								state.authorizedMembers().put(candidateId, member);
							}
						}
					}
				}
				case EventTypes.MEMBER_REVOKED -> {
					String memberId = String.valueOf(p.get("member_id"));
					state.revokedMembers().add(memberId);
					Member member = state.authorizedMembers().remove(memberId);
					if (member != null) state.authorizedMembers().put(memberId, new Member(member.memberId(), member.displayName(), member.deviceId(), member.membershipToken(), true, member.publicKey()));
				}
				case EventTypes.MEMBER_ROLE_CHANGED -> {
					if (state.isAuthorized(event.authorMemberId())) {
						String memberId = String.valueOf(p.get("member_id"));
						state.pendingMembers().remove(memberId);
						state.authorizedMembers().put(memberId, new Member(memberId, String.valueOf(p.get("display_name")),
								String.valueOf(p.get("device_id")), String.valueOf(p.get("membership_token")), false,
								String.valueOf(p.getOrDefault("public_key", ""))));
					}
				}
				case EventTypes.CHAT_MESSAGE_CREATED -> state.chatMessages().put(String.valueOf(p.get("message_id")),
						new ChatMessage(String.valueOf(p.get("message_id")), event.authorMemberId(), String.valueOf(p.get("text"))));
				case EventTypes.NOTE_CREATED, EventTypes.NOTE_UPDATED -> state.notes().put(String.valueOf(p.get("note_id")),
						new Note(String.valueOf(p.get("note_id")), String.valueOf(p.get("text"))));
				case EventTypes.NOTE_DELETED -> state.notes().remove(String.valueOf(p.get("note_id")));
				case EventTypes.NOTE_INSERT, EventTypes.NOTE_DELETE_OP, EventTypes.NOTE_STYLE_APPLIED -> {
					String noteId = String.valueOf(p.get("note_id"));
					Note existing = state.notes().getOrDefault(noteId, new Note(noteId, ""));
					String text = applyCrdtOp(existing.text(), event.type(), p);
					state.notes().put(noteId, new Note(noteId, text));
				}
				case EventTypes.WHITEBOARD_STROKE_ADDED -> state.strokes().put(String.valueOf(p.get("stroke_id")),
						new WhiteboardStroke(String.valueOf(p.get("stroke_id")), event.authorMemberId(),
								(List<int[]>) p.getOrDefault("points", List.of()),
								String.valueOf(p.getOrDefault("color", "#000000")),
								((Number) p.getOrDefault("width", 2)).intValue()));
			case EventTypes.WHITEBOARD_OBJECT_ADDED -> {
				String objectId = String.valueOf(p.get("object_id"));
				String operation = String.valueOf(p.getOrDefault("operation", ""));
				state.whiteboardObjects().put(objectId, operation);
			}
			case EventTypes.WHITEBOARD_OBJECT_MOVED -> {
				String objectId = String.valueOf(p.get("object_id"));
				String operation = String.valueOf(p.getOrDefault("operation", ""));
				String existing = state.whiteboardObjects().get(objectId);
				state.whiteboardObjects().put(objectId, mergeWhiteboardImageData(existing, operation));
			}
				case EventTypes.WHITEBOARD_OBJECT_DELETED -> state.whiteboardObjects().remove(String.valueOf(p.get("object_id")));
				case EventTypes.WHITEBOARD_CLEARED -> {
					state.whiteboardObjects().clear();
					state.strokes().clear();
				}
				case EventTypes.FILE_SHARED -> state.files().put(String.valueOf(p.get("file_id")),
						new FileMetadata(String.valueOf(p.get("file_id")), String.valueOf(p.get("name")), ((Number) p.get("size")).longValue(),
								String.valueOf(p.get("hash")), (List<String>) p.get("chunks"), String.valueOf(p.get("shared_by")),
								Boolean.parseBoolean(String.valueOf(p.getOrDefault("chat_attachment", false)))));
				case EventTypes.PEER_STATUS_UPDATED -> {
					String memberId = String.valueOf(p.getOrDefault("member_id", event.authorMemberId()));
					String peerUrl = String.valueOf(p.getOrDefault("peer_url", ""));
					if (!peerUrl.isBlank()) state.peerUrls().put(memberId, peerUrl);
					Object peers = p.get("connected_peers");
					LinkedHashSet<String> connected = new LinkedHashSet<>();
					if (peers instanceof List<?> list) for (Object peer : list) if (peer != null) connected.add(String.valueOf(peer));
					state.peerConnections().put(memberId, connected);
				}
				default -> { }
			}
		}
		return state;
	}
}
