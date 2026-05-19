package org.q3s.p2p.client;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.q3s.p2p.client.util.Logger;

public class CloudflareInstaller {

	private static final String LINUX_URL = "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-linux-amd64";
	private static final String WINDOWS_URL = "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-windows-amd64.exe";
	private static final String MAC_URL = "https://github.com/cloudflare/cloudflared/releases/latest/download/cloudflared-darwin-amd64.tgz";

	private static boolean installed = false;

	public static void ensureInstalled(Logger log) {
		if (Config.isTunnelMockEnabled()) {
			log.info("Modo tunnel mock activo. No se instala cloudflared.");
			return;
		}

		if (installed)
			return;

		String path = Config.getCloudflaredPath();

		if (path != null && !path.equals("cloudflared") && Files.exists(Paths.get(path))) {
			installed = true;
			return;
		}

		String os = System.getProperty("os.name").toLowerCase();
		String downloadUrl;
		String targetName;

		if (os.contains("linux")) {
			downloadUrl = LINUX_URL;
			targetName = "cloudflared";
		} else if (os.contains("windows")) {
			downloadUrl = WINDOWS_URL;
			targetName = "cloudflared.exe";
		} else if (os.contains("mac")) {
			downloadUrl = MAC_URL;
			targetName = "cloudflared";
		} else {
			log.err("Sistema operativo no soportado para cloudflared: " + os);
			return;
		}

		File localDir = new File(getInstallDir());
		localDir.mkdirs();
		File target = new File(localDir, targetName);

		if (target.exists()) {
			target.setExecutable(true);
			installed = true;
			return;
		}

		log.info("Descargando cloudflared para " + os + "...");
		try {
			URLConnection conn = new URL(downloadUrl).openConnection();
			conn.setConnectTimeout(30000);
			conn.setReadTimeout(120000);

			File tempFile = File.createTempFile("cloudflared", ".tmp");
			try (InputStream is = conn.getInputStream();
					FileOutputStream fos = new FileOutputStream(tempFile)) {
				byte[] buffer = new byte[8192];
				int n;
				while ((n = is.read(buffer)) != -1) {
					fos.write(buffer, 0, n);
				}
			}

			if (os.contains("mac")) {
				extractTgz(tempFile, target);
			} else {
				Files.move(tempFile.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
			}

			target.setExecutable(true);
			installed = true;
			log.info("cloudflared instalado en " + target.getAbsolutePath());
			tempFile.delete();
		} catch (Exception e) {
			log.err("Error al descargar cloudflared: " + e.getMessage());
			log.err("Instalalo manualmente: https://developers.cloudflare.com/cloudflare-one/connections/connect-apps/install-and-setup/installation/");
		}
	}

	public static boolean isInstalled() {
		if (installed)
			return true;
		String path = Config.getCloudflaredPath();
		if (path != null && !path.equals("cloudflared") && Files.exists(Paths.get(path))) {
			installed = true;
			return true;
		}
		return false;
	}

	private static String getInstallDir() {
		String os = System.getProperty("os.name").toLowerCase();
		if (os.contains("windows")) {
			return System.getProperty("user.home") + "/AppData/Local/qfolder/bin";
		}
		return System.getProperty("user.home") + "/.local/qfolder/bin";
	}

	private static void extractTgz(File tgzFile, File target) throws Exception {
		File tmpDir = new File(tgzFile.getParent(), "cloudflared_extract");
		tmpDir.mkdirs();
		ProcessBuilder pb = new ProcessBuilder("tar", "xzf", tgzFile.getAbsolutePath(), "-C", tmpDir.getAbsolutePath());
		Process p = pb.start();
		p.waitFor();
		File extracted = new File(tmpDir, "cloudflared");
		if (extracted.exists()) {
			Files.move(extracted.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
		}
		Files.walk(tmpDir.toPath()).sorted(java.util.Comparator.reverseOrder()).forEach(p2 -> {
			try {
				Files.delete(p2);
			} catch (Exception e) {
			}
		});
	}
}
