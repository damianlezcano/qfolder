package org.q3s.p2p.core.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.List;
import java.util.Map;

public record Event(
		String eventId,
		String workspaceId,
		String type,
		String authorMemberId,
		Instant createdAt,
		List<String> parents,
		Map<String, Object> payload,
		AuthInfo auth,
		String signature,
		boolean persistent) implements Serializable {

	public boolean isEphemeral() {
		return !persistent;
	}
}
