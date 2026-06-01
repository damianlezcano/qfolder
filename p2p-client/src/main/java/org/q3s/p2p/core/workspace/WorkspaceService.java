package org.q3s.p2p.core.workspace;

import java.util.Map;

import org.q3s.p2p.core.events.EventFactory;
import org.q3s.p2p.core.events.EventTypes;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.model.Member;
import org.q3s.p2p.ports.AuthProvider;
import org.q3s.p2p.ports.EventStore;
import org.q3s.p2p.ports.IdGenerator;

public class WorkspaceService {
	private final EventStore store;
	private final AuthProvider auth;
	private final IdGenerator ids;
	private final EventFactory events;

	public WorkspaceService(EventStore store, AuthProvider auth, IdGenerator ids, EventFactory events) {
		this.store = store;
		this.auth = auth;
		this.ids = ids;
		this.events = events;
	}

	public CreatedWorkspace createWorkspace(String name, String creatorDisplayName, int requiredApprovals) {
		if (name == null || name.isBlank()) throw new IllegalArgumentException("Workspace name is required");
		String workspaceId = ids.newId("ws");
		Member creator = auth.createMemberIdentity(creatorDisplayName);
		Event event = events.create(workspaceId, EventTypes.WORKSPACE_CREATED, creator.memberId(), Map.of(
				"name", name,
				"required_approvals", requiredApprovals,
				"auth_mode", auth.authMode(),
				"creator_member_id", creator.memberId(),
				"creator_display_name", creator.displayName(),
				"creator_device_id", creator.deviceId(),
				"creator_membership_token", creator.membershipToken(),
				"creator_public_key", creator.publicKey()), null);
		Event stamped = auth.stampEvent(event, creator);
		store.append(stamped);
		return new CreatedWorkspace(workspaceId, creator, stamped);
	}

	public record CreatedWorkspace(String workspaceId, Member creator, Event createdEvent) {}
}
