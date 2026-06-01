package org.q3s.p2p.core.events;

import java.util.Map;

import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.auth.PublicKeyAuthProvider;
import org.q3s.p2p.core.model.Member;
import org.q3s.p2p.core.state.WorkspaceState;
import org.q3s.p2p.core.state.WorkspaceStateBuilder;
import org.q3s.p2p.ports.EventStore;

public class EventValidator {
	private final EventStore store;

	public EventValidator(EventStore store) {
		this.store = store;
	}

	public boolean isAcceptable(Event event) {
		if (event == null || event.eventId() == null || event.eventId().isBlank()
				|| event.workspaceId() == null || event.workspaceId().isBlank()
				|| event.type() == null || event.type().isBlank()
				|| event.authorMemberId() == null || event.authorMemberId().isBlank()) return false;
		if (event.payload() == null) return false;

		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents(event.workspaceId()));
		return switch (event.type()) {
			case EventTypes.WORKSPACE_CREATED -> !store.containsType(event.workspaceId(), EventTypes.WORKSPACE_CREATED)
					&& matchesPayload(event, "creator_member_id", event.authorMemberId())
					&& validSignatureIfPresent(event, String.valueOf(event.payload().getOrDefault("creator_public_key", "")));
			case EventTypes.MEMBER_JOIN_REQUESTED -> matchesPayload(event, "candidate_member_id", event.authorMemberId())
					&& !state.revokedMembers().contains(event.authorMemberId())
					&& validSignatureIfPresent(event, String.valueOf(event.payload().getOrDefault("public_key", "")));
			case EventTypes.MEMBER_JOIN_APPROVAL -> state.isAuthorized(event.authorMemberId())
					&& matchesPayload(event, "approved_by", event.authorMemberId())
					&& validAuthorizedSignature(event, state);
			case EventTypes.MEMBER_REVOKED, EventTypes.MEMBER_ROLE_CHANGED -> state.isAuthorized(event.authorMemberId())
					&& validAuthorizedSignature(event, state);
			default -> state.isAuthorized(event.authorMemberId()) && !state.revokedMembers().contains(event.authorMemberId())
					&& validAuthorizedSignature(event, state);
		};
	}

	private boolean matchesPayload(Event event, String key, String expected) {
		Map<String, Object> payload = event.payload();
		Object value = payload.get(key);
		return value != null && expected.equals(String.valueOf(value));
	}

	private boolean validAuthorizedSignature(Event event, WorkspaceState state) {
		Member member = state.authorizedMembers().get(event.authorMemberId());
		if (member == null) return false;
		return validSignatureIfPresent(event, member.publicKey());
	}

	private boolean validSignatureIfPresent(Event event, String publicKey) {
		if (publicKey == null || publicKey.isBlank()) return true;
		return PublicKeyAuthProvider.verifyEventSignature(event, publicKey);
	}
}
