package org.q3s.p2p.adapters.filesystem;

import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

public class QfolderLayout {
	public static final String USERDATA_DIR = "userdata";
	public static final String SYSTEMDATA_DIR = "systemdata";
	public static final String WORKSPACES_DIR = "workspaces";

	public static final String[] USERDATA_SUBDIRS = {
		"files", "chat", "notes", "whiteboard", "logs", "members"
	};

	private final Path baseFolder;

	public QfolderLayout(Path baseFolder) {
		this.baseFolder = baseFolder;
	}

	public Path baseFolder() { return baseFolder; }

	public Path userdataRoot() {
		return baseFolder.resolve(USERDATA_DIR);
	}

	public Path systemdataRoot() {
		return baseFolder.resolve(SYSTEMDATA_DIR);
	}

	public Path systemWorkspaceRoot(String workspaceId) {
		return systemdataRoot().resolve(WORKSPACES_DIR).resolve(safeId(workspaceId));
	}

	public Path identityFile() {
		return systemdataRoot().resolve("identity.properties");
	}

	public Path workspaceFolder(String workspaceId, Instant createdAt, String name) {
		return userdataRoot().resolve(datePrefix(createdAt)).resolve(folderName(createdAt, workspaceId, name));
	}

	public Path resolveExisting(Path workspaceFolder) {
		if (Files.isDirectory(workspaceFolder)) return workspaceFolder;
		return null;
	}

	public Optional<Path> findExistingWorkspaceFolder(String workspaceId) {
		Path userdata = userdataRoot();
		if (!Files.isDirectory(userdata)) return Optional.empty();
		try (Stream<Path> days = Files.walk(userdata, 4)) {
			return days.filter(Files::isDirectory)
					.filter(p -> p.getFileName().toString().contains(workspaceId))
					.findFirst();
		} catch (Exception e) {
			return Optional.empty();
		}
	}

	public boolean createStructure(Path workspaceFolder) {
		try {
			for (String subdir : USERDATA_SUBDIRS) Files.createDirectories(workspaceFolder.resolve(subdir));
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	public static String datePrefix(Instant createdAt) {
		LocalDateTime dt = createdAt.atZone(ZoneId.systemDefault()).toLocalDateTime();
		return String.format("%04d/%02d/%02d", dt.getYear(), dt.getMonthValue(), dt.getDayOfMonth());
	}

	public static String folderName(Instant createdAt, String workspaceId, String name) {
		LocalDateTime dt = createdAt.atZone(ZoneId.systemDefault()).toLocalDateTime();
		String timePart = String.format("%02d%02d", dt.getHour(), dt.getMinute());
		return timePart + "-" + safeId(workspaceId) + "-" + slug(name);
	}

	public static String safeId(String value) {
		if (value == null || value.isBlank()) return "workspace";
		String safe = value.replaceAll("[^a-zA-Z0-9._-]+", "-").replaceAll("^-|-$", "");
		return safe.isEmpty() ? "workspace" : safe;
	}

	public static String slug(String name) {
		if (name == null || name.isBlank()) return "workspace";
		String normalized = Normalizer.normalize(name, Normalizer.Form.NFD)
				.replaceAll("[^\\p{ASCII}]", "")
				.toLowerCase()
				.replaceAll("[^a-z0-9]+", "-")
				.replaceAll("^-|-$", "");
		if (normalized.isEmpty()) return "workspace";
		if (normalized.length() > 60) normalized = normalized.substring(0, 60);
		return normalized;
	}

	public static Map<String, Object> workspaceJson(String workspaceId, String name, Instant createdAt,
			Instant joinedAt, String localPath, int requiredApprovals) {
		Map<String, Object> joinPolicy = new LinkedHashMap<>();
		joinPolicy.put("type", "light_consensus");
		joinPolicy.put("required_approvals", requiredApprovals);

		Map<String, Object> json = new LinkedHashMap<>();
		json.put("workspace_id", workspaceId);
		json.put("name", name);
		json.put("workspace_slug", slug(name));
		json.put("created_at", createdAt.toString());
		json.put("joined_locally_at", joinedAt.toString());
		json.put("local_path", localPath);
		json.put("auth_mode", "ed25519");
		json.put("join_policy", joinPolicy);
		return json;
	}
}
