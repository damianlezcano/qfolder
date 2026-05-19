package org.q3s.p2p.client;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Properties;

public class AppConfig {

	private static final String FILE_NAME = "qfolder.properties";
	private static Properties props;

	static {
		load();
	}

	public static void load() {
		props = new Properties();

		for (Path propFile : getPropertyFileCandidates()) {
			if (propFile != null && Files.exists(propFile)) {
				try (InputStream is = new FileInputStream(propFile.toFile())) {
					props.load(is);
					return;
				} catch (Exception e) {
					System.err.println("Error loading " + FILE_NAME + ": " + e.getMessage());
				}
			}
		}
	}

	private static Path[] getPropertyFileCandidates() {
		String jarDir = getJarDirectory();
		Path appDir = Paths.get(jarDir);
		return new Path[] {
				appDir.resolve(FILE_NAME),
				appDir.resolve("content").resolve(FILE_NAME),
				appDir.getParent() != null ? appDir.getParent().resolve(FILE_NAME) : null,
				appDir.getParent() != null ? appDir.getParent().resolve("content").resolve(FILE_NAME) : null,
				Paths.get(System.getProperty("user.dir", ".")).resolve(FILE_NAME)
		};
	}

	public static String get(String key, String defaultValue) {
		String systemValue = System.getProperty(key);
		if (systemValue != null) {
			return systemValue;
		}

		String envValue = System.getenv(toEnvKey(key));
		if (envValue != null) {
			return envValue;
		}

		return props.getProperty(key, defaultValue);
	}

	public static int getInt(String key, int defaultValue) {
		String v = get(key, null);
		if (v != null) {
			try {
				return Integer.parseInt(v);
			} catch (NumberFormatException e) {
			}
		}
		return defaultValue;
	}

	public static boolean getBoolean(String key, boolean defaultValue) {
		String v = get(key, null);
		if (v == null) {
			return defaultValue;
		}
		return "true".equalsIgnoreCase(v) || "1".equals(v) || "yes".equalsIgnoreCase(v);
	}

	private static String toEnvKey(String key) {
		return key.toUpperCase().replace('.', '_').replace('-', '_');
	}

	private static String getJarDirectory() {
		try {
			String path = AppConfig.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
			Path p = Paths.get(path);
			if (Files.isRegularFile(p)) {
				return p.getParent().toString();
			}
			return path;
		} catch (Exception e) {
			return ".";
		}
	}
}
