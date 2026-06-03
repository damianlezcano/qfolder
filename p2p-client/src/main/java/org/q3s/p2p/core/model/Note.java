package org.q3s.p2p.core.model;

import java.io.Serializable;
import java.time.Instant;
import java.util.Collections;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Estado de una nota: lineas CRDT inmutables indexadas por lineId, mas un
 * snapshot opcional en formato legacy QNOTES2. text() reconstruye el contenido
 * visible concatenando las lineas vivas en orden estable (createdAt + lineId).
 */
public record Note(
		String noteId,
		Map<String, NoteLine> lines,
		String legacyText) implements Serializable {

	public Note {
		lines = lines == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(lines));
		legacyText = legacyText == null ? "" : legacyText;
	}

	public Note(String noteId, String text) {
		this(noteId, Map.of(), text == null ? "" : text);
	}

	public String text() {
		if (lines == null || lines.isEmpty()) return legacyText == null ? "" : legacyText;
		StringBuilder sb = new StringBuilder();
		boolean first = true;
		for (NoteLine line : visibleLines()) {
			if (!first) sb.append('\n');
			sb.append(line.text() == null ? "" : line.text());
			first = false;
		}
		return sb.toString();
	}

	public List<NoteLine> visibleLines() {
		List<NoteLine> ordered = new ArrayList<>();
		if (lines == null || lines.isEmpty()) return ordered;
		Map<String, List<NoteLine>> children = new LinkedHashMap<>();
		for (NoteLine line : lines.values()) {
			String parent = normalizeParent(line.afterLineId());
			children.computeIfAbsent(parent, ignored -> new ArrayList<>()).add(line);
		}
		Comparator<NoteLine> comparator = Comparator
				.comparing((NoteLine line) -> line.createdAt() == null ? Instant.EPOCH : line.createdAt())
				.thenComparing(line -> line.lineId() == null ? "" : line.lineId());
		for (List<NoteLine> siblings : children.values()) siblings.sort(comparator);
		appendVisibleDepthFirst("", children, ordered, new java.util.HashSet<>());
		List<NoteLine> orphans = new ArrayList<>();
		for (NoteLine line : lines.values()) {
			if (!ordered.contains(line) && !line.deleted()) orphans.add(line);
		}
		orphans.sort(comparator);
		ordered.addAll(orphans);
		return ordered;
	}

	private static void appendVisibleDepthFirst(String parentId, Map<String, List<NoteLine>> children,
			List<NoteLine> ordered, java.util.Set<String> visited) {
		for (NoteLine line : children.getOrDefault(parentId, List.of())) {
			String lineId = line.lineId() == null ? "" : line.lineId();
			if (!visited.add(lineId)) continue;
			if (!line.deleted()) ordered.add(line);
			appendVisibleDepthFirst(lineId, children, ordered, visited);
		}
	}

	private static String normalizeParent(String value) {
		return value == null || value.isBlank() || "null".equals(value) ? "" : value;
	}

	public int lineCount() {
		int n = 0;
		for (NoteLine l : lines.values()) if (!l.deleted()) n++;
		return n;
	}

	public Note withLine(NoteLine line) {
		Map<String, NoteLine> updated = new LinkedHashMap<>(lines);
		updated.put(line.lineId(), line);
		return new Note(noteId, updated, legacyText);
	}

	public Note withLines(Map<String, NoteLine> newLines) {
		return new Note(noteId, newLines, legacyText);
	}

	public Note withLegacyText(String text) {
		Map<String, NoteLine> replaced = new LinkedHashMap<>(lines);
		return new Note(noteId, replaced, text == null ? "" : text);
	}

}
