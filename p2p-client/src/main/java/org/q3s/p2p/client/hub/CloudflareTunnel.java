package org.q3s.p2p.client.hub;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.function.Consumer;

import org.q3s.p2p.client.Config;
import org.q3s.p2p.client.util.Logger;

public class CloudflareTunnel {

	private final Logger log;
	private final String cloudflaredPath;
	private Process process;
	private String tunnelUrl;
	private boolean running;

	public CloudflareTunnel(Logger log) {
		this.log = log;
		this.cloudflaredPath = Config.getCloudflaredPath();
	}

	public void start(int localPort, Consumer<String> onUrlReady, Consumer<String> onError) {
		if (Config.isTunnelMockEnabled()) {
			tunnelUrl = Config.getTunnelMockHost() + ":" + localPort;
			running = true;
			log.info("[cloudflared-mock] Tunnel mock activo: " + tunnelUrl);
			onUrlReady.accept(tunnelUrl);
			return;
		}

		Thread t = new Thread(() -> {
			try {
				ProcessBuilder pb = new ProcessBuilder(cloudflaredPath, "tunnel", "--url",
						"http://localhost:" + localPort);
				pb.redirectErrorStream(true);
				process = pb.start();
				running = true;

				BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
				String line;
				while ((line = reader.readLine()) != null) {
					log.debug("[cloudflared] " + line);
					if (line.contains("trycloudflare.com")) {
						tunnelUrl = extractUrl(line);
						if (tunnelUrl != null) {
							onUrlReady.accept(tunnelUrl);
						}
					}
				}

				int exitCode = process.waitFor();
				running = false;
				log.debug("cloudflared exited with code: " + exitCode);
			} catch (Exception e) {
				running = false;
				log.err("Error starting cloudflared: " + e.getMessage());
				if (onError != null) {
					onError.accept(e.getMessage());
				}
			}
		}, "cloudflared-tunnel");
		t.setDaemon(true);
		t.start();
	}

	public void stop() {
		if (process != null && process.isAlive()) {
			process.destroyForcibly();
			running = false;
			log.info("Cloudflare Tunnel stopped");
		}
	}

	public String getTunnelUrl() {
		return tunnelUrl;
	}

	public boolean isRunning() {
		return running && process != null && process.isAlive();
	}

	private String extractUrl(String line) {
		try {
			int idx = line.indexOf("https://");
			if (idx >= 0) {
				String url = line.substring(idx);
				url = url.replaceAll("[|\\s]+$", "").trim();
				if (url.endsWith(".")) {
					url = url.substring(0, url.length() - 1);
				}
				return url;
			}
		} catch (Exception e) {
		}
		return null;
	}
}
