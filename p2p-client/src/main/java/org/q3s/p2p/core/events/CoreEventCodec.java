package org.q3s.p2p.core.events;

import java.io.StringReader;
import java.io.StringWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.json.JsonReader;
import javax.json.JsonString;
import javax.json.JsonValue;
import javax.json.JsonWriter;

import org.q3s.p2p.core.model.AuthInfo;
import org.q3s.p2p.core.model.Event;

public final class CoreEventCodec {
	public static final String EVENT_VERSION = "QCOREJSON1";
	public static final String SYNC_VERSION = "QCORESYNCJSON1";

	private CoreEventCodec() {}

	public static String encodeEventPayload(Event event) {
		return envelope(EVENT_VERSION, toJson(event));
	}

	public static Event decodeEventPayload(String payload) {
		return eventFromJson(body(payload, EVENT_VERSION));
	}

	public static String encodeSyncEvents(List<Event> events) {
		JsonArrayBuilder array = Json.createArrayBuilder();
		for (Event event : events == null ? List.<Event>of() : events) array.add(eventObject(event));
		return envelope(SYNC_VERSION, jsonToString(array.build()));
	}

	public static List<Event> decodeSyncEvents(String payload) {
		try (JsonReader reader = Json.createReader(new StringReader(body(payload, SYNC_VERSION)))) {
			List<Event> events = new ArrayList<>();
			for (JsonValue value : reader.readArray()) events.add(eventFromObject(value.asJsonObject()));
			return events;
		}
	}

	public static String encodeKnownIds(Set<String> ids) {
		JsonArrayBuilder array = Json.createArrayBuilder();
		for (String id : ids == null ? Set.<String>of() : ids) array.add(id);
		return envelope(SYNC_VERSION, jsonToString(array.build()));
	}

	public static Set<String> decodeKnownIds(String payload) {
		try (JsonReader reader = Json.createReader(new StringReader(body(payload, SYNC_VERSION)))) {
			Set<String> ids = new java.util.LinkedHashSet<>();
			for (JsonValue value : reader.readArray()) ids.add(((JsonString) value).getString());
			return ids;
		}
	}

	public static String toJson(Event event) {
		return jsonToString(eventObject(event));
	}

	public static Event eventFromJson(String json) {
		try (JsonReader reader = Json.createReader(new StringReader(json))) {
			return eventFromObject(reader.readObject());
		}
	}

	private static JsonObject eventObject(Event event) {
		JsonObjectBuilder auth = Json.createObjectBuilder();
		if (event.auth() != null && event.auth().mode() != null) auth.add("mode", event.auth().mode());
		JsonObjectBuilder root = Json.createObjectBuilder()
				.add("eventId", event.eventId())
				.add("workspaceId", event.workspaceId())
				.add("type", event.type())
				.add("authorMemberId", event.authorMemberId())
				.add("createdAt", event.createdAt().toString())
				.add("persistent", event.persistent())
				.add("parents", stringArray(event.parents()))
				.add("payload", mapObject(event.payload()))
				.add("auth", auth);
		if (event.signature() != null) root.add("signature", event.signature());
		else root.addNull("signature");
		return root.build();
	}

	private static Event eventFromObject(JsonObject object) {
		return new Event(
				object.getString("eventId"),
				object.getString("workspaceId"),
				object.getString("type"),
				object.getString("authorMemberId"),
				Instant.parse(object.getString("createdAt")),
				stringList(object.getJsonArray("parents")),
				mapFromObject(object.getJsonObject("payload")),
				new AuthInfo(object.getJsonObject("auth") != null ? object.getJsonObject("auth").getString("mode", "token") : "token"),
				object.isNull("signature") ? null : object.getString("signature", null),
				object.getBoolean("persistent", true));
	}

	private static JsonArrayBuilder stringArray(List<String> values) {
		JsonArrayBuilder array = Json.createArrayBuilder();
		for (String value : values == null ? List.<String>of() : values) array.add(value);
		return array;
	}

	private static JsonObjectBuilder mapObject(Map<String, Object> map) {
		JsonObjectBuilder object = Json.createObjectBuilder();
		for (Map.Entry<String, Object> entry : (map == null ? Map.<String, Object>of() : map).entrySet()) {
			addValue(object, entry.getKey(), entry.getValue());
		}
		return object;
	}

	private static void addValue(JsonObjectBuilder object, String key, Object value) {
		if (value == null) object.addNull(key);
		else if (value instanceof Integer v) object.add(key, v);
		else if (value instanceof Long v) object.add(key, v);
		else if (value instanceof Double v) object.add(key, v);
		else if (value instanceof Float v) object.add(key, v.doubleValue());
		else if (value instanceof Boolean v) object.add(key, v);
		else if (value instanceof List<?> v) object.add(key, listArray(v));
		else if (value instanceof int[] v) object.add(key, intArray(v));
		else object.add(key, String.valueOf(value));
	}

	private static JsonArrayBuilder listArray(List<?> values) {
		JsonArrayBuilder array = Json.createArrayBuilder();
		for (Object value : values) {
			if (value == null) array.addNull();
			else if (value instanceof Integer v) array.add(v);
			else if (value instanceof Long v) array.add(v);
			else if (value instanceof Double v) array.add(v);
			else if (value instanceof Float v) array.add(v.doubleValue());
			else if (value instanceof Boolean v) array.add(v);
			else if (value instanceof List<?> v) array.add(listArray(v));
			else if (value instanceof int[] v) array.add(intArray(v));
			else array.add(String.valueOf(value));
		}
		return array;
	}

	private static JsonArrayBuilder intArray(int[] values) {
		JsonArrayBuilder array = Json.createArrayBuilder();
		for (int value : values) array.add(value);
		return array;
	}

	private static List<String> stringList(javax.json.JsonArray array) {
		List<String> values = new ArrayList<>();
		if (array != null) for (JsonValue value : array) values.add(((JsonString) value).getString());
		return values;
	}

	private static Map<String, Object> mapFromObject(JsonObject object) {
		Map<String, Object> map = new LinkedHashMap<>();
		if (object == null) return map;
		for (String key : object.keySet()) map.put(key, javaValue(object.get(key)));
		return map;
	}

	private static Object javaValue(JsonValue value) {
		return switch (value.getValueType()) {
			case STRING -> ((JsonString) value).getString();
			case NUMBER -> {
				var number = (javax.json.JsonNumber) value;
				yield number.isIntegral() ? number.longValue() : number.doubleValue();
			}
			case TRUE -> true;
			case FALSE -> false;
			case ARRAY -> {
				List<Object> list = new ArrayList<>();
				for (JsonValue item : value.asJsonArray()) list.add(javaValue(item));
				yield list;
			}
			case OBJECT -> mapFromObject(value.asJsonObject());
			case NULL -> null;
		};
	}

	private static String envelope(String version, String json) {
		return version + "\n" + Base64.getEncoder().encodeToString(json.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}

	private static String body(String payload, String expectedVersion) {
		String[] parts = (payload == null ? "" : payload).split("\n", 2);
		if (parts.length != 2 || !expectedVersion.equals(parts[0])) {
			throw new IllegalArgumentException("Version de payload core no soportada: " + (parts.length > 0 ? parts[0] : ""));
		}
		return new String(Base64.getDecoder().decode(parts[1]), java.nio.charset.StandardCharsets.UTF_8);
	}

	private static String jsonToString(JsonValue value) {
		StringWriter writer = new StringWriter();
		try (JsonWriter jsonWriter = Json.createWriter(writer)) {
			jsonWriter.write(value);
		}
		return writer.toString();
	}
}
