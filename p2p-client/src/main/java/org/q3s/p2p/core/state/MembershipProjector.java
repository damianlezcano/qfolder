package org.q3s.p2p.core.state;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import org.q3s.p2p.core.events.EventTypes;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.model.Member;
import org.q3s.p2p.core.model.Workspace;

/**
 * Proyecta eventos relacionados a membresía: creación de workspace, join requests,
 * approvals, revocaciones y cambios de rol. Mantiene el estado de WorkspaceState
 * relativo a miembros autorizados, pendientes, revocados y approvals.
 */
public class MembershipProjector {

	public void apply(WorkspaceState state, Event event) {
		if (state == null || event == null) return;
		Map<String, Object> p = event.payload();
		if (p == null) return;
		switch (event.type()) {
			case EventTypes.WORKSPACE_CREATED -> applyCreated(state, event, p);
			case EventTypes.MEMBER_JOIN_REQUESTED -> applyJoinRequested(state, p);
			case EventTypes.MEMBER_JOIN_APPROVAL -> applyJoinApproval(state, p);
			case EventTypes.MEMBER_REVOKED -> applyRevoked(state, p);
			case EventTypes.MEMBER_ROLE_CHANGED -> applyRoleChanged(state, event, p);
			default -> { /* not membership */ }
		}
	}

	private void applyCreated(WorkspaceState state, Event event, Map<String, Object> p) {
		int required = ((Number) p.getOrDefault("required_approvals", 1)).intValue();
		state.workspace(new Workspace(event.workspaceId(), String.valueOf(p.get("name")), required, String.valueOf(p.get("auth_mode"))));
		Member creator = new Member(String.valueOf(p.get("creator_member_id")), String.valueOf(p.get("creator_display_name")),
				String.valueOf(p.get("creator_device_id")), String.valueOf(p.get("creator_membership_token")), false,
				String.valueOf(p.getOrDefault("creator_public_key", "")));
		state.authorizedMembers().put(creator.memberId(), creator);
	}

	private void applyJoinRequested(WorkspaceState state, Map<String, Object> p) {
		String memberId = String.valueOf(p.get("candidate_member_id"));
		if (!state.authorizedMembers().containsKey(memberId) && !state.revokedMembers().contains(memberId)) {
			state.pendingMembers().put(memberId, new Member(memberId, String.valueOf(p.get("candidate_display_name")),
					String.valueOf(p.get("candidate_device_id")), String.valueOf(p.get("membership_token")), false,
					String.valueOf(p.getOrDefault("public_key", ""))));
		}
	}

	private void applyJoinApproval(WorkspaceState state, Map<String, Object> p) {
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

	private void applyRevoked(WorkspaceState state, Map<String, Object> p) {
		String memberId = String.valueOf(p.get("member_id"));
		state.revokedMembers().add(memberId);
		Member member = state.authorizedMembers().remove(memberId);
		if (member != null) state.authorizedMembers().put(memberId, new Member(member.memberId(), member.displayName(), member.deviceId(), member.membershipToken(), true, member.publicKey()));
	}

	private void applyRoleChanged(WorkspaceState state, Event event, Map<String, Object> p) {
		if (state.isAuthorized(event.authorMemberId())) {
			String memberId = String.valueOf(p.get("member_id"));
			state.pendingMembers().remove(memberId);
			state.authorizedMembers().put(memberId, new Member(memberId, String.valueOf(p.get("display_name")),
					String.valueOf(p.get("device_id")), String.valueOf(p.get("membership_token")), false,
					String.valueOf(p.getOrDefault("public_key", ""))));
		}
	}
}
