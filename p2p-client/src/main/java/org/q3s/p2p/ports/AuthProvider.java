package org.q3s.p2p.ports;

import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.model.Member;
import org.q3s.p2p.core.state.WorkspaceState;

public interface AuthProvider {
	Member createMemberIdentity(String displayName);
	boolean validateJoinRequest(Event event, WorkspaceState state);
	boolean validateMemberReconnect(String workspaceId, String memberId, String membershipToken, WorkspaceState state);
	Event stampEvent(Event event);
	default Event stampEvent(Event event, Member author) { return stampEvent(event); }
	boolean validateEventAuthor(Event event, WorkspaceState state);
	default String authMode() { return "token"; }
}
