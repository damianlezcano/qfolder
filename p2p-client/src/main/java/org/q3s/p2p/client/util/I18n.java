package org.q3s.p2p.client.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

public class I18n {

	private static ResourceBundle bundle;
	private static Locale activeLocale;

	static {
		setLocale(Locale.getDefault());
	}

	public static void setLocale(Locale locale) {
		activeLocale = new Locale(locale.getLanguage());
		bundle = loadBundle(activeLocale);
	}

	public static Locale currentLocale() {
		return activeLocale != null ? activeLocale : Locale.getDefault();
	}

	private static ResourceBundle loadBundle(Locale locale) {
		Locale langOnly = new Locale(locale.getLanguage());
		try {
			ResourceBundle b = ResourceBundle.getBundle("i18n/messages", langOnly);
			System.out.println("[I18n] loadBundle locale=" + locale + " -> bundle=" + b.getLocale() + ", spanish=" + b.getString("config.languageSpanish") + ", english=" + b.getString("config.languageEnglish"));
			return b;
		} catch (Exception e) {
			try {
				ResourceBundle b = ResourceBundle.getBundle("i18n/messages", Locale.ENGLISH);
				System.out.println("[I18n] loadBundle fallback ENGLISH, bundle=" + b.getLocale());
				return b;
			} catch (Exception ex) {
				ResourceBundle b = ResourceBundle.getBundle("i18n/messages");
				System.out.println("[I18n] loadBundle fallback default, bundle=" + b.getLocale());
				return b;
			}
		}
	}

	public static String get(String key) {
		try {
			return bundle.getString(key);
		} catch (Exception e) {
			return "??" + key + "??";
		}
	}

	public static String get(String key, Object... args) {
		try {
			return MessageFormat.format(bundle.getString(key), args);
		} catch (Exception e) {
			return "??" + key + "??";
		}
	}
}
