package org.q3s.p2p.client.util;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;
import java.util.Properties;

import org.q3s.p2p.client.Config;

public final class UserPreferences {

	private static final String FILE_NAME = "preferences.properties";
	private static final String SYSTEMDATA_DIR = "systemdata";

	public static final String PREF_LANGUAGE = "qfolder.language";
	public static final String PREF_LOOK_AND_FEEL = "qfolder.lookAndFeel";

	private static Path preferencesFile;
	private static Properties props;

	private UserPreferences() {}

	public static void init(Path qfolderRoot) {
		Path systemdata = qfolderRoot.resolve(SYSTEMDATA_DIR);
		try {
			Files.createDirectories(systemdata);
		} catch (IOException e) {
		}
		preferencesFile = systemdata.resolve(FILE_NAME);
		reload();
	}

	public static void reload() {
		props = new Properties();
		if (preferencesFile != null && Files.exists(preferencesFile)) {
			try (InputStream is = Files.newInputStream(preferencesFile)) {
				props.load(is);
			} catch (Exception e) {
			}
		}
	}

	private static void lazyInit() {
		if (preferencesFile == null) {
			preferencesFile = Paths.get(Config.SHARED_DIR, SYSTEMDATA_DIR, FILE_NAME);
			reload();
		}
	}

	public static String get(String key, String defaultValue) {
		String sys = System.getProperty(key);
		if (sys != null) return sys;
		lazyInit();
		if (props != null) {
			return props.getProperty(key, defaultValue);
		}
		return defaultValue;
	}

	public static void set(String key, String value) {
		lazyInit();
		if (props == null) return;
		if (value == null) {
			props.remove(key);
		} else {
			props.setProperty(key, value);
		}
		save();
	}

	private static void save() {
		if (preferencesFile == null) return;
		try {
			Files.createDirectories(preferencesFile.getParent());
		} catch (IOException e) {
		}
		try (OutputStream os = Files.newOutputStream(preferencesFile)) {
			props.store(os, "qfolder user preferences");
		} catch (Exception e) {
		}
	}

	public static Locale getLanguage() {
		String code = get(PREF_LANGUAGE, "");
		if (code.isEmpty()) return Locale.getDefault();
		return new Locale(code);
	}

	public static void setLanguage(Locale locale) {
		if (locale != null) {
			String langCode = locale.getLanguage();
			System.out.println("[UserPreferences] setLanguage called with: " + langCode + ", preferencesFile=" + preferencesFile);
			set(PREF_LANGUAGE, langCode);
		}
	}

	public static String getLookAndFeel() {
		return get(PREF_LOOK_AND_FEEL, "flat-light");
	}

	public static void setLookAndFeel(String lafId) {
		set(PREF_LOOK_AND_FEEL, lafId);
	}
}
