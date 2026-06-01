package org.q3s.p2p.core.model;

import java.io.Serializable;
import java.util.List;

public record FileMetadata(String fileId, String name, long size, String hash, List<String> chunks, String sharedBy,
		boolean chatAttachment) implements Serializable {
	public FileMetadata(String fileId, String name, long size, String hash, List<String> chunks, String sharedBy) {
		this(fileId, name, size, hash, chunks, sharedBy, false);
	}
}
