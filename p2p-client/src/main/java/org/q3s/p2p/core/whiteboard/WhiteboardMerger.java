package org.q3s.p2p.core.whiteboard;

public class WhiteboardMerger {

	public String merge(String existing, String update) {
		if (update == null) return "";
		if (!isImageWithoutData(update)) return update;
		String data = imageData(existing);
		return data == null || data.isBlank() ? update : update + "|" + data;
	}

	private boolean isImageWithoutData(String operation) {
		if (operation == null || !operation.startsWith("I|")) return false;
		return operation.indexOf('|', 2) < 0;
	}

	private String imageData(String operation) {
		if (operation == null || !operation.startsWith("I|")) return null;
		int separator = operation.indexOf('|', 2);
		if (separator < 0 || separator + 1 >= operation.length()) return null;
		return operation.substring(separator + 1);
	}
}
