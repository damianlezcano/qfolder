package org.q3s.p2p.core.members;

import java.util.Map;

import org.q3s.p2p.core.events.EventFactory;
import org.q3s.p2p.core.events.EventTypes;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.model.Member;
import org.q3s.p2p.core.state.WorkspaceState;
import org.q3s.p2p.core.state.WorkspaceStateBuilder;
import org.q3s.p2p.ports.AuthProvider;
import org.q3s.p2p.ports.EventStore;

public class MembershipService {
	private final EventStore store;
	private final AuthProvider auth;
	private final EventFactory events;

	public MembershipService(EventStore store, AuthProvider auth, EventFactory events) {
		this.store = store;
		this.auth = auth;
		this.events = events;
	}

	public Member createCandidate(String displayName) {
		return auth.createMemberIdentity(displayName);
	}

	public Event requestJoin(String workspaceId, Member candidate) {
		Event event = events.create(workspaceId, EventTypes.MEMBER_JOIN_REQUESTED, candidate.memberId(), Map.of(
				"candidate_member_id", candidate.memberId(),
				"candidate_display_name", candidate.displayName(),
				"candidate_device_id", candidate.deviceId(),
				"membership_token", candidate.membershipToken(),
				"public_key", candidate.publicKey()), null);
		store.append(event);
		return event;
	}

	public Event approve(String workspaceId, String approverMemberId, String candidateMemberId) {
		Event event = events.create(workspaceId, EventTypes.MEMBER_JOIN_APPROVAL, approverMemberId, Map.of(
				"candidate_member_id", candidateMemberId,
				"approved_by", approverMemberId), null);
		store.append(event);
		return event;
	}

	public Event revoke(String workspaceId, String authorMemberId, String memberId) {
		Event event = events.create(workspaceId, EventTypes.MEMBER_REVOKED, authorMemberId, Map.of("member_id", memberId), null);
		store.append(event);
		return event;
	}

	public Event addAuthorizedMember(String workspaceId, String authorMemberId, Member member) {
		Event event = events.create(workspaceId, EventTypes.MEMBER_ROLE_CHANGED, authorMemberId, Map.of(
				"member_id", member.memberId(),
				"display_name", member.displayName(),
				"device_id", member.deviceId(),
				"membership_token", member.membershipToken(),
				"public_key", member.publicKey(),
				"role", "member"), null);
		store.append(event);
		return event;
	}

	public boolean reconnect(String workspaceId, String memberId, String token) {
		WorkspaceState state = WorkspaceStateBuilder.fromEvents(store.listEvents(workspaceId));
		return auth.validateMemberReconnect(workspaceId, memberId, token, state);
	}
}
