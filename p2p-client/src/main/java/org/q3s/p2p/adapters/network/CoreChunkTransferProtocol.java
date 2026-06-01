package org.q3s.p2p.adapters.network;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

public final class CoreChunkTransferProtocol {
	public static final String AVAILABILITY_REQUEST = "Core chunk availability request";
	public static final String AVAILABILITY_RESPONSE = "Core chunk availability response";
	public static final String CHUNK_REQUEST = "Core chunk request";
	public static final String CHUNK_RESPONSE = "Core chunk response";
	public static final String PAYLOAD_VERSION = "QCHUNK1";

	private CoreChunkTransferProtocol() {}

	public static String availabilityRequest(String transferId, String fileId, List<String> chunks) {
		return envelope(transferId + "\n" + fileId + "\n" + String.join(",", chunks == null ? List.of() : chunks));
	}

	public static AvailabilityRequest parseAvailabilityRequest(String payload) {
		String[] parts = split(body(payload), 3);
		return new AvailabilityRequest(parts[0], parts[1], csv(parts[2]));
	}

	public static String availabilityResponse(String transferId, String peerId, List<String> chunks) {
		return envelope(transferId + "\n" + peerId + "\n" + String.join(",", chunks == null ? List.of() : chunks));
	}

	public static AvailabilityResponse parseAvailabilityResponse(String payload) {
		String[] parts = split(body(payload), 3);
		return new AvailabilityResponse(parts[0], parts[1], csv(parts[2]));
	}

	public static String chunkRequest(String transferId, String fileId, String chunkHash) {
		return envelope(transferId + "\n" + fileId + "\n" + chunkHash);
	}

	public static ChunkRequest parseChunkRequest(String payload) {
		String[] parts = split(body(payload), 3);
		return new ChunkRequest(parts[0], parts[1], parts[2]);
	}

	public static String chunkResponse(String transferId, String fileId, String chunkHash, byte[] bytes) {
		return envelope(transferId + "\n" + fileId + "\n" + chunkHash + "\n" + Base64.getEncoder().encodeToString(bytes == null ? new byte[0] : bytes));
	}

	public static ChunkResponse parseChunkResponse(String payload) {
		String[] parts = split(body(payload), 4);
		return new ChunkResponse(parts[0], parts[1], parts[2], Base64.getDecoder().decode(parts[3].getBytes(StandardCharsets.UTF_8)));
	}

	private static String envelope(String body) {
		return PAYLOAD_VERSION + "\n" + body;
	}

	private static String body(String payload) {
		String[] parts = (payload == null ? "" : payload).split("\n", 2);
		if (parts.length != 2 || !PAYLOAD_VERSION.equals(parts[0])) {
			throw new IllegalArgumentException("Version de payload chunk no soportada: " + (parts.length > 0 ? parts[0] : ""));
		}
		return parts[1];
	}

	private static String[] split(String payload, int parts) {
		String[] split = (payload == null ? "" : payload).split("\n", parts);
		String[] out = new String[parts];
		for (int i = 0; i < parts; i++) out[i] = i < split.length ? split[i] : "";
		return out;
	}

	private static List<String> csv(String value) {
		List<String> out = new ArrayList<>();
		if (value == null || value.isBlank()) return out;
		for (String item : value.split(",")) {
			if (!item.isBlank()) out.add(item);
		}
		return out;
	}

	public record AvailabilityRequest(String transferId, String fileId, List<String> chunks) {}
	public record AvailabilityResponse(String transferId, String peerId, List<String> chunks) {}
	public record ChunkRequest(String transferId, String fileId, String chunkHash) {}
	public record ChunkResponse(String transferId, String fileId, String chunkHash, byte[] bytes) {}
}
