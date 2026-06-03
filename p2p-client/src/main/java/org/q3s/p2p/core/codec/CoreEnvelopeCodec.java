package org.q3s.p2p.core.codec;

import java.io.Serializable;
import java.util.List;

import org.q3s.p2p.core.events.CoreEventCodec;
import org.q3s.p2p.core.model.Event;

public final class CoreEnvelopeCodec {
	public static final String CORE_EVENT_NAME = "core.event";
	public static final String CORE_SYNC_REQUEST_NAME = "core.sync.request";
	public static final String CORE_SYNC_RESPONSE_NAME = "core.sync.response";
	public static final String CHUNK_PROTOCOL_EVENT_NAME = "core.chunk.protocol";
	public static final String CORE_PAYLOAD_VERSION = CoreEventCodec.EVENT_VERSION;
	public static final String CORE_SYNC_PAYLOAD_VERSION = CoreEventCodec.SYNC_VERSION;

	private CoreEnvelopeCodec() {}

	public static String encodeCoreEvent(Event event) {
		return CoreEventCodec.encodeEventPayload(event);
	}

	public static String encodeKnownEventIds(java.util.Set<String> knownEventIds) {
		return CoreEventCodec.encodeKnownIds(knownEventIds);
	}

	public static String encodeSyncPayload(List<Event> events) {
		return CoreEventCodec.encodeSyncEvents(events);
	}

	public static String encodeChunkProtocolPayload(String chunkPayload) {
		return chunkPayload;
	}

	public static Event decodeCoreEvent(CoreEnvelope envelope) {
		if (envelope == null
				|| !CORE_EVENT_NAME.equals(envelope.name())
				|| envelope.response() == null) {
			return null;
		}
		return CoreEventCodec.decodeEventPayload(envelope.response());
	}

	public static java.util.Set<String> decodeKnownEventIds(CoreEnvelope envelope) {
		if (envelope == null
				|| !CORE_SYNC_REQUEST_NAME.equals(envelope.name())
				|| envelope.response() == null) {
			return java.util.Set.of();
		}
		return CoreEventCodec.decodeKnownIds(envelope.response());
	}

	public static List<Event> decodeSyncEvents(CoreEnvelope envelope) {
		if (envelope == null
				|| !CORE_SYNC_RESPONSE_NAME.equals(envelope.name())
				|| envelope.response() == null) {
			return List.of();
		}
		return CoreEventCodec.decodeSyncEvents(envelope.response());
	}

	public static String decodeChunkProtocolPayload(CoreEnvelope envelope) {
		if (envelope == null) return null;
		return envelope.response();
	}
}
