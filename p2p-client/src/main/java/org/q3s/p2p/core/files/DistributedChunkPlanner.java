package org.q3s.p2p.core.files;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DistributedChunkPlanner {
	public Map<String, List<String>> planDownloads(List<String> chunks, Map<String, Set<String>> chunkOwners) {
		Map<String, List<String>> plan = new LinkedHashMap<>();
		if (chunks == null || chunkOwners == null) return plan;
		for (String chunk : chunks) {
			String owner = chunkOwners.getOrDefault(chunk, Set.of()).stream()
					.min(Comparator.comparingInt(peer -> plan.getOrDefault(peer, List.of()).size()))
					.orElse(null);
			if (owner != null) plan.computeIfAbsent(owner, ignored -> new ArrayList<>()).add(chunk);
		}
		return plan;
	}
}
