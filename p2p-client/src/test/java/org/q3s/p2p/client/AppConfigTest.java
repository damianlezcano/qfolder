package org.q3s.p2p.client;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AppConfigTest {

	@Test
	void getReturnsDefaultWhenPropertyNotSet() {
		String result = AppConfig.get("nonexistent.key.test", "myDefault");
		assertEquals("myDefault", result);
	}

	@Test
	void getIntReturnsDefaultForInvalidValue() {
		int result = AppConfig.getInt("nonexistent.int.key.test", 42);
		assertEquals(42, result);
	}

	@Test
	void getBooleanReturnsDefaultForMissingKey() {
		boolean result = AppConfig.getBoolean("nonexistent.bool.key.test", true);
		assertTrue(result);
	}

	@Test
	void getReturnsSystemPropertyOverDefault() {
		String key = "qfolder.test.sysprop." + System.nanoTime();
		System.setProperty(key, "fromSystemProp");
		try {
			String result = AppConfig.get(key, "defaultValue");
			assertEquals("fromSystemProp", result);
		} finally {
			System.clearProperty(key);
		}
	}

	@Test
	void getIntParsesValidSystemProperty() {
		String key = "qfolder.test.int." + System.nanoTime();
		System.setProperty(key, "99");
		try {
			int result = AppConfig.getInt(key, 0);
			assertEquals(99, result);
		} finally {
			System.clearProperty(key);
		}
	}

	@Test
	void getBooleanFindsSystemPropertyTrue() {
		String key = "qfolder.test.bool." + System.nanoTime();
		System.setProperty(key, "true");
		try {
			boolean result = AppConfig.getBoolean(key, false);
			assertTrue(result);
		} finally {
			System.clearProperty(key);
		}
	}
}
