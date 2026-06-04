package org.q3s.p2p.client;

import java.awt.Desktop;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JOptionPane;

import org.q3s.p2p.client.util.I18n;
import org.q3s.p2p.client.util.Logger;

public class UpdateChecker {

	private static final String VERSION = loadVersion();
	private static final String GITHUB_API = "https://api.github.com/repos/damianlezcano/qfolder/releases/latest";
	private static final String RELEASES_URL = "https://github.com/damianlezcano/qfolder/releases/latest";

	private static String loadVersion() {
		java.util.Properties props = new java.util.Properties();
		try (InputStream is = UpdateChecker.class.getResourceAsStream("/META-INF/maven/org.q3s/p2p-client/pom.properties")) {
			if (is != null) {
				props.load(is);
				return props.getProperty("version", "0.0.0");
			}
		} catch (Exception e) {
			java.util.logging.Logger.getLogger("qfolder.UpdateChecker").fine("version resource not available: " + e.getMessage());
		}
		return "0.0.0";
	}

	public static String getVersion() {
		return VERSION;
	}

	public static void checkForUpdates(java.awt.Component parent, Logger log) {
		new Thread(() -> {
			try {
				HttpClient client = HttpClient.newBuilder()
						.connectTimeout(Duration.ofSeconds(10))
						.build();
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(GITHUB_API))
						.timeout(Duration.ofSeconds(15))
						.header("Accept", "application/json")
						.build();
				HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
				if (response.statusCode() != 200) return;

				String body = response.body();
				String latestTag;
				List<String> urls = new ArrayList<>();
				try (javax.json.JsonReader reader = javax.json.Json.createReader(new java.io.StringReader(body))) {
					javax.json.JsonObject release = reader.readObject();
					latestTag = release.getString("tag_name", "").replace("v", "").trim();
					if (latestTag.isEmpty() || !isNewer(VERSION, latestTag)) return;
					javax.json.JsonArray assets = release.getJsonArray("assets");
					if (assets != null) {
						for (javax.json.JsonValue asset : assets) {
							javax.json.JsonObject obj = asset.asJsonObject();
							String dlUrl = obj.getString("browser_download_url", null);
							if (dlUrl != null) urls.add(dlUrl);
						}
					}
				}
				boolean hasJar = urls.stream().anyMatch(u -> u.endsWith("qfolder.jar"));
				boolean hasPackage = urls.stream().anyMatch(u -> u.contains("-x64."));

				if (!hasJar && !hasPackage) return;

				javax.swing.SwingUtilities.invokeLater(() -> {
					if (hasPackage) {
						showMajorUpdateDialog(parent, log, latestTag);
					} else {
						String jarUrl = urls.stream().filter(u -> u.endsWith("qfolder.jar")).findFirst().orElse(null);
						if (jarUrl != null) showUpdateDialog(parent, log, latestTag, jarUrl);
					}
				});
			} catch (Exception e) {
				log.debug("Update check skipped: " + e.getMessage());
			}
		}, "update-check").start();
	}

	private static boolean isNewer(String current, String latest) {
		try {
			int[] cur = parseVersion(current);
			int[] lat = parseVersion(latest);
			for (int i = 0; i < Math.min(cur.length, lat.length); i++) {
				if (lat[i] > cur[i]) return true;
				if (lat[i] < cur[i]) return false;
			}
			return lat.length > cur.length;
		} catch (Exception e) {
			return !current.equals(latest);
		}
	}

	private static int[] parseVersion(String v) {
		String[] parts = v.split("\\.");
		int[] nums = new int[parts.length];
		for (int i = 0; i < parts.length; i++) {
			nums[i] = Integer.parseInt(parts[i].replaceAll("[^0-9]", ""));
		}
		return nums;
	}

	private static void showMajorUpdateDialog(java.awt.Component parent, Logger log, String newVersion) {
		int opt = JOptionPane.showConfirmDialog(parent,
				I18n.get("update.major.message", newVersion, VERSION),
				I18n.get("update.major.title", "qfolder Major Update"),
				JOptionPane.YES_NO_OPTION);
		if (opt == JOptionPane.YES_OPTION) {
			try {
				Desktop.getDesktop().browse(URI.create(RELEASES_URL));
			} catch (Exception e) {
				log.err("Could not open browser: " + e.getMessage());
			}
		}
	}

	private static void showUpdateDialog(java.awt.Component parent, Logger log, String newVersion, String downloadUrl) {
		int opt = JOptionPane.showConfirmDialog(parent,
				I18n.get("update.available.message", newVersion, VERSION),
				I18n.get("update.available.title", "qfolder Update"),
				JOptionPane.YES_NO_OPTION);
		if (opt == JOptionPane.YES_OPTION) {
			downloadAndInstall(parent, log, downloadUrl, newVersion);
		}
	}

	private static void downloadAndInstall(java.awt.Component parent, Logger log, String url, String version) {
		new Thread(() -> {
			try {
				log.info(I18n.get("update.downloading", version));
				HttpClient client = HttpClient.newBuilder()
						.followRedirects(HttpClient.Redirect.NORMAL)
						.connectTimeout(Duration.ofSeconds(10))
						.build();
				HttpRequest request = HttpRequest.newBuilder()
						.uri(URI.create(url))
						.timeout(Duration.ofSeconds(120))
						.build();
				HttpResponse<InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());

				String jarPath = UpdateChecker.class.getProtectionDomain().getCodeSource().getLocation().toURI().getPath();
				Path currentJar = Paths.get(jarPath);
				Path newJar = currentJar.resolveSibling("qfolder-v" + version + ".jar");
				try (InputStream in = response.body();
					 OutputStream out = Files.newOutputStream(newJar)) {
					byte[] buf = new byte[8192];
					int n;
					while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
				}
				Path backup = currentJar.resolveSibling("qfolder-old.jar");
				Files.move(currentJar, backup, StandardCopyOption.REPLACE_EXISTING);
				Files.move(newJar, currentJar, StandardCopyOption.REPLACE_EXISTING);
				Files.deleteIfExists(backup);
				log.info(I18n.get("update.success", version));
				javax.swing.SwingUtilities.invokeLater(() ->
						JOptionPane.showMessageDialog(parent, I18n.get("update.success", version)));
			} catch (Exception e) {
				log.err(I18n.get("update.failed", e.getMessage()));
			}
		}, "update-download").start();
	}
}
