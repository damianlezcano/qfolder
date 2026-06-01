package org.q3s.p2p.core.auth;

import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.model.Member;
import org.q3s.p2p.core.state.WorkspaceState;
import org.q3s.p2p.ports.AuthProvider;
import org.q3s.p2p.ports.IdGenerator;

public class TokenAuthProvider implements AuthProvider {
	private final IdGenerator ids;

	public TokenAuthProvider(IdGenerator ids) {
		this.ids = ids;
	}

	@Override
	public Member createMemberIdentity(String displayName) {
		return new Member(ids.newId("member"), displayName, ids.newId("device"), ids.newId("mbrtok"), false);
	}

	@Override
	public boolean validateJoinRequest(Event event, WorkspaceState state) {
		return "member.join.requested".equals(event.type()) && event.payload().containsKey("candidate_member_id");
	}

	@Override
	public boolean validateMemberReconnect(String workspaceId, String memberId, String membershipToken, WorkspaceState state) {
		Member member = state.authorizedMembers().get(memberId);
		return member != null && !member.revoked() && member.membershipToken() != null && member.membershipToken().equals(membershipToken);
	}

	@Override
	public Event stampEvent(Event event) {
		return event;
	}

	@Override
	public boolean validateEventAuthor(Event event, WorkspaceState state) {
		return event.authorMemberId() == null || state.isAuthorized(event.authorMemberId()) || "workspace.created".equals(event.type())
				|| "member.join.requested".equals(event.type());
	}
}
