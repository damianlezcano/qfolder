package org.q3s.p2p.adapters.network;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

public final class InviteCode {
	private InviteCode() {}

	public static String encode(String peerUrl, String workspaceId) {
		String safeUrl = peerUrl == null ? "" : peerUrl.trim();
		String safeWs = workspaceId == null ? "" : workspaceId.trim();
		return safeUrl + "?workspace=" + Base64.getUrlEncoder().withoutPadding().encodeToString(safeWs.getBytes(StandardCharsets.UTF_8));
	}

	public static DecodedInvite decode(String invite) {
		if (invite == null || invite.isBlank()) return null;
		try {
			String[] parts = invite.split("\\?workspace=", 2);
			if (parts.length < 2) {
				String peerUrl = invite.trim();
				return new DecodedInvite(peerUrl, peerUrl);
			}
			String peerUrl = parts[0];
			String wsId = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
			return new DecodedInvite(peerUrl, wsId);
		} catch (Exception e) {
			return null;
		}
	}

	public record DecodedInvite(String peerUrl, String workspaceId) {}
}
