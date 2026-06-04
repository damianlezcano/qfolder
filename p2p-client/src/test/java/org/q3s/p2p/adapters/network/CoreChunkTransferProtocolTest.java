package org.q3s.p2p.adapters.network;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

class CoreChunkTransferProtocolTest {

	@Test
	void availabilityRequestRoundTrip() {
		String payload = CoreChunkTransferProtocol.availabilityRequest("t-1", "f-1", List.of("h1", "h2", "h3"));
		assertTrue(payload.startsWith("QCHUNK1\n"));
		CoreChunkTransferProtocol.AvailabilityRequest req = CoreChunkTransferProtocol.parseAvailabilityRequest(payload);
		assertEquals("t-1", req.transferId());
		assertEquals("f-1", req.fileId());
		assertEquals(List.of("h1", "h2", "h3"), req.chunks());
	}

	@Test
	void availabilityResponseRoundTrip() {
		String payload = CoreChunkTransferProtocol.availabilityResponse("t-1", "peer-A", List.of("h1", "h2"));
		CoreChunkTransferProtocol.AvailabilityResponse res = CoreChunkTransferProtocol.parseAvailabilityResponse(payload);
		assertEquals("t-1", res.transferId());
		assertEquals("peer-A", res.peerId());
		assertEquals(List.of("h1", "h2"), res.chunks());
	}

	@Test
	void chunkRequestRoundTrip() {
		String payload = CoreChunkTransferProtocol.chunkRequest("t-1", "f-1", "h-abc");
		CoreChunkTransferProtocol.ChunkRequest req = CoreChunkTransferProtocol.parseChunkRequest(payload);
		assertEquals("t-1", req.transferId());
		assertEquals("f-1", req.fileId());
		assertEquals("h-abc", req.chunkHash());
	}

	@Test
	void chunkResponseRoundTripBytes() {
		byte[] data = new byte[]{1, 2, 3, 4, 5};
		String payload = CoreChunkTransferProtocol.chunkResponse("t-1", "f-1", "h-x", data);
		CoreChunkTransferProtocol.ChunkResponse res = CoreChunkTransferProtocol.parseChunkResponse(payload);
		assertEquals("t-1", res.transferId());
		assertEquals("f-1", res.fileId());
		assertEquals("h-x", res.chunkHash());
		assertArrayEquals(data, res.bytes());
	}

	@Test
	void chunkResponseConBytesVacios() {
		String payload = CoreChunkTransferProtocol.chunkResponse("t-1", "f-1", "h-empty", new byte[0]);
		CoreChunkTransferProtocol.ChunkResponse res = CoreChunkTransferProtocol.parseChunkResponse(payload);
		assertEquals(0, res.bytes().length);
	}

	@Test
	void chunkResponseConNullConvierteAVacio() {
		String payload = CoreChunkTransferProtocol.chunkResponse("t-1", "f-1", "h-null", null);
		CoreChunkTransferProtocol.ChunkResponse res = CoreChunkTransferProtocol.parseChunkResponse(payload);
		assertEquals(0, res.bytes().length);
	}

	@Test
	void availabilityRequestConChunksVacios() {
		String payload = CoreChunkTransferProtocol.availabilityRequest("t-1", "f-1", List.of());
		CoreChunkTransferProtocol.AvailabilityRequest req = CoreChunkTransferProtocol.parseAvailabilityRequest(payload);
		assertEquals(0, req.chunks().size());
	}

	@Test
	void availabilityRequestConChunksNullConvierteAVacio() {
		String payload = CoreChunkTransferProtocol.availabilityRequest("t-1", "f-1", null);
		CoreChunkTransferProtocol.AvailabilityRequest req = CoreChunkTransferProtocol.parseAvailabilityRequest(payload);
		assertEquals(0, req.chunks().size());
	}

	@Test
	void parseVersionInvalidaLanzaExcepcion() {
		assertThrows(IllegalArgumentException.class,
				() -> CoreChunkTransferProtocol.parseAvailabilityRequest("QCHUNK0\nfoo"));
	}

	@Test
	void parseSinVersionLanzaExcepcion() {
		assertThrows(IllegalArgumentException.class,
				() -> CoreChunkTransferProtocol.parseAvailabilityRequest("foo\nbar"));
	}
}
