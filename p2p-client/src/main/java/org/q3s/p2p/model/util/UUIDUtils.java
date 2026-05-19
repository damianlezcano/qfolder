package org.q3s.p2p.model.util;

import java.util.UUID;

public class UUIDUtils {

	public static String generate() {
		return UUID.randomUUID().toString();
	}
}
