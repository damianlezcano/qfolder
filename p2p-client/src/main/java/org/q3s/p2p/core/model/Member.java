package org.q3s.p2p.core.model;

import java.io.Serializable;

public record Member(String memberId, String displayName, String deviceId, String membershipToken, boolean revoked, String publicKey) implements Serializable {
	public Member(String memberId, String displayName, String deviceId, String membershipToken, boolean revoked) {
		this(memberId, displayName, deviceId, membershipToken, revoked, "");
	}
}
