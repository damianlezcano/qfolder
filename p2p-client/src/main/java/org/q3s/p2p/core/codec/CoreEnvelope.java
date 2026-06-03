package org.q3s.p2p.core.codec;

import java.io.Serializable;
import java.util.Base64;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;

public record CoreEnvelope(String name, String userId, String response, long sequence) implements Serializable {

	private static final Jsonb JSONB = JsonbBuilder.create();

	public static CoreEnvelope of(String name, String userId, String response) {
		return new CoreEnvelope(name, userId, response, 0L);
	}

	public static CoreEnvelope of(String name, String userId, String response, long sequence) {
		return new CoreEnvelope(name, userId, response, sequence);
	}

	public String toJsonBase64() {
		String json = JSONB.toJson(this);
		return Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	public static CoreEnvelope fromJsonBase64(String dataBase64) {
		if (dataBase64 == null) return null;
		String payload = dataBase64;
		if (payload.startsWith("data:")) {
			int comma = payload.indexOf(',');
			if (comma >= 0) payload = payload.substring(comma + 1);
		}
		try {
			String json = new String(Base64.getDecoder().decode(payload), java.nio.charset.StandardCharsets.UTF_8);
			return JSONB.fromJson(json, CoreEnvelope.class);
		} catch (Exception e) {
			return null;
		}
	}
}
