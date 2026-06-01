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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;

import org.q3s.p2p.client.util.Logger;

public class UpdateChecker {

	private static final String VERSION = "1.0.7";
	private static final String GITHUB_API = "https://api.github.com/repos/damianlezcano/qfolder/releases/latest";
	private static final String RELEASES_URL = "https://github.com/damianlezcano/qfolder/releases/latest";
	private static final Pattern TAG_PATTERN = Pattern.compile("\"tag_name\"\\s*:\\s*\"([^\"]+)\"");
	private static final Pattern URL_PATTERN = Pattern.compile("\"browser_download_url\"\\s*:\\s*\"([^\"]+)\"");

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
				Matcher tagMatcher = TAG_PATTERN.matcher(body);
				if (!tagMatcher.find()) return;
				String latestTag = tagMatcher.group(1).replace("v", "").trim();
				if (!isNewer(VERSION, latestTag)) return;

				List<String> urls = collectAssetUrls(body);
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

	private static List<String> collectAssetUrls(String json) {
		List<String> urls = new ArrayList<>();
		Matcher m = URL_PATTERN.matcher(json);
		while (m.find()) urls.add(m.group(1));
		return urls;
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
				"Major update " + newVersion + " available (current: " + VERSION + ").\n"
				+ "This update includes platform changes and requires downloading the full package.\n"
				+ "Open the release page to download?",
				"qfolder Major Update", JOptionPane.YES_NO_OPTION);
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
				"New version " + newVersion + " available (current: " + VERSION + ").\nDownload and install?",
				"qfolder Update", JOptionPane.YES_NO_OPTION);
		if (opt == JOptionPane.YES_OPTION) {
			downloadAndInstall(parent, log, downloadUrl, newVersion);
		}
	}

	private static void downloadAndInstall(java.awt.Component parent, Logger log, String url, String version) {
		new Thread(() -> {
			try {
				log.info("Downloading qfolder " + version + "...");
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
				log.info("Updated to " + version + ". Restart to apply.");
				javax.swing.SwingUtilities.invokeLater(() ->
						JOptionPane.showMessageDialog(parent, "Updated to " + version + ". Restart the app to apply."));
			} catch (Exception e) {
				log.err("Update failed: " + e.getMessage());
			}
		}, "update-download").start();
	}
}
