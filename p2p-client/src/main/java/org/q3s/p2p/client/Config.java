package org.q3s.p2p.client;

import java.io.File;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Config {

	public static String TEMP_PATH = "temp";

	public static final int WS_SERVER_PORT = AppConfig.getInt("qfolder.ws.port", 18765);

	public static final String USER_NAME = AppConfig.get("qfolder.user.name", System.getenv("USER"));

	public static final String SHARED_DIR = resolveHome(AppConfig.get("qfolder.shared.dir",
			"~/qfolder/temporal"));

	public static final String HISTORY_DIR = resolveHome(AppConfig.get("qfolder.history.dir",
			"~/qfolder/history"));

	private static String resolveHome(String path) {
		if (path == null) return null;
		if (path.startsWith("~/") || path.equals("~")) {
			return System.getProperty("user.home") + path.substring(1);
		}
		return new File(path).getAbsolutePath();
	}

	public static boolean isTunnelMockEnabled() {
		return AppConfig.getBoolean("qfolder.tunnel.mock", false);
	}

	public static String getTunnelMockHost() {
		return AppConfig.get("qfolder.tunnel.mock.host", "localhost");
	}

	public static String getCloudflaredPath() {
		String configured = AppConfig.get("qfolder.cloudflared.path", null);
		if (configured != null) return configured;

		String packaged = getPackagedCloudflaredPath();
		if (packaged != null) return packaged;

		String os = System.getProperty("os.name").toLowerCase();
		String installDir;
		if (os.contains("windows")) {
			installDir = System.getProperty("user.home") + "/AppData/Local/qfolder/bin";
			return installDir + "/cloudflared.exe";
		}
		installDir = System.getProperty("user.home") + "/.local/qfolder/bin";
		return installDir + "/cloudflared";
	}

	private static String getPackagedCloudflaredPath() {
		String name = System.getProperty("os.name").toLowerCase().contains("windows")
				? "cloudflared.exe" : "cloudflared";
		try {
			URI codeUri = Config.class.getProtectionDomain().getCodeSource().getLocation().toURI();
			Path appDir = Paths.get(codeUri).toAbsolutePath().getParent();
			Path[] candidates = new Path[] {
					appDir.resolve("bin").resolve(name),
					appDir.resolve(name),
					appDir.resolve("content").resolve("bin").resolve(name),
					appDir.getParent() != null ? appDir.getParent().resolve("bin").resolve(name) : null,
					appDir.getParent() != null ? appDir.getParent().resolve(name) : null,
					appDir.getParent() != null ? appDir.getParent().resolve("content").resolve("bin").resolve(name) : null,
					Paths.get(System.getProperty("user.dir", ".")).resolve("bin").resolve(name),
					Paths.get(System.getProperty("user.dir", ".")).resolve(name)
			};
			for (Path candidate : candidates) {
				if (candidate != null && Files.exists(candidate)) {
					File file = candidate.toFile();
					file.setExecutable(true);
					return file.getAbsolutePath();
				}
			}
		} catch (Exception e) {
			return null;
		}
		return null;
	}
}
