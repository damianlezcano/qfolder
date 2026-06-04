package org.q3s.p2p.adapters.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class InviteCodeTest {

	@Test
	void encodeYDecodePreservaDatos() {
		String invite = InviteCode.encode("ws://localhost:12345", "ws-abc-123");
		assertTrue(invite.startsWith("ws://localhost:12345?workspace="));
		InviteCode.DecodedInvite decoded = InviteCode.decode(invite);
		assertNotNull(decoded);
		assertEquals("ws://localhost:12345", decoded.peerUrl());
		assertEquals("ws-abc-123", decoded.workspaceId());
	}

	@Test
	void encodeConPeerUrlVacio() {
		String invite = InviteCode.encode("", "ws-1");
		assertTrue(invite.contains("?workspace="));
		InviteCode.DecodedInvite d = InviteCode.decode(invite);
		assertNotNull(d);
		assertEquals("", d.peerUrl());
		assertEquals("ws-1", d.workspaceId());
	}

	@Test
	void decodeNullRetornaNull() {
		assertNull(InviteCode.decode(null));
	}

	@Test
	void decodeVacioRetornaNull() {
		assertNull(InviteCode.decode(""));
		assertNull(InviteCode.decode("   "));
	}

	@Test
	void decodeSinWorkspaceRetornaPeerUrlComoWorkspaceId() {
		InviteCode.DecodedInvite d = InviteCode.decode("ws://peer:9000");
		assertNotNull(d);
		assertEquals("ws://peer:9000", d.peerUrl());
		assertEquals("ws://peer:9000", d.workspaceId());
	}

	@Test
	void encodeBase64UrlSinPadding() {
		String invite = InviteCode.encode("ws://x", "ws-1");
		String suffix = invite.split("\\?workspace=")[1];
		assertTrue(!suffix.contains("="), "no debe llevar padding Base64 URL");
	}

	@Test
	void encodeYDecodeUnicode() {
		String invite = InviteCode.encode("ws://host", "ws-café-ñoño-日本語");
		InviteCode.DecodedInvite d = InviteCode.decode(invite);
		assertNotNull(d);
		assertEquals("ws-café-ñoño-日本語", d.workspaceId());
	}
}
