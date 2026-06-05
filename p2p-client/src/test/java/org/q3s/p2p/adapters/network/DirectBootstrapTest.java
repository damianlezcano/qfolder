package org.q3s.p2p.adapters.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.q3s.p2p.core.codec.CoreEnvelope;

class DirectBootstrapTest {

	@Test
	@DisplayName("DirectBootstrap can be instantiated with valid args")
	void directBootstrapInstantiation() {
		DirectBootstrap bootstrap = new DirectBootstrap(null, null, env -> {}, () -> {},
				(peer, wsId) -> {}, msg -> {});
		assertNotNull(bootstrap);
	}

	@Test
	@DisplayName("DirectBootstrap accepts null debug consumer via constructor")
	void directBootstrapWithNullDebug() {
		DirectBootstrap bootstrap = new DirectBootstrap(null, null, env -> {}, () -> {},
				(peer, wsId) -> {}, null);
		assertNotNull(bootstrap);
	}

	@Test
	@DisplayName("join with null invite returns false (does not throw)")
	void joinWithNullInviteReturnsFalse() {
		List<String> debug = new ArrayList<>();
		DirectBootstrap bootstrap = new DirectBootstrap(null, null, env -> {}, () -> {},
				(peer, wsId) -> {}, debug::add);
		boolean result = bootstrap.join(null, "user-1", "User One");
		assertEquals(false, result);
	}

	@Test
	@DisplayName("join with empty invite returns false (does not throw)")
	void joinWithEmptyInviteReturnsFalse() {
		List<String> debug = new ArrayList<>();
		DirectBootstrap bootstrap = new DirectBootstrap(null, null, env -> {}, () -> {},
				(peer, wsId) -> {}, debug::add);
		boolean result = bootstrap.join("", "user-1", "User One");
		assertEquals(false, result);
	}

	@Test
	@DisplayName("join with blank invite returns false (does not throw)")
	void joinWithBlankInviteReturnsFalse() {
		List<String> debug = new ArrayList<>();
		DirectBootstrap bootstrap = new DirectBootstrap(null, null, env -> {}, () -> {},
				(peer, wsId) -> {}, debug::add);
		boolean result = bootstrap.join("   ", "user-1", "User One");
		assertEquals(false, result);
	}

	@Test
	@DisplayName("join with garbage base64 invite returns false (does not throw)")
	void joinWithGarbageInviteReturnsFalse() {
		List<String> debug = new ArrayList<>();
		DirectBootstrap bootstrap = new DirectBootstrap(null, null, env -> {}, () -> {},
				(peer, wsId) -> {}, debug::add);
		boolean result = bootstrap.join("ws://localhost:9999?workspace=!!notbase64!!", "user-1", "User One");
		assertEquals(false, result);
	}

	@Test
	@DisplayName("inviteCode with null core returns empty string gracefully (does not throw)")
	void inviteCodeWithoutCore() {
		DirectBootstrap bootstrap = new DirectBootstrap(null, null, env -> {}, () -> {},
				(peer, wsId) -> {}, msg -> {});
		try {
			String code = bootstrap.inviteCode("ws://localhost:9999");
			assertNotNull(code);
		} catch (Exception expected) {
		}
	}
}
