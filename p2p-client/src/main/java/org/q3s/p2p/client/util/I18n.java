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
		activeLocale = Locale.forLanguageTag(locale.getLanguage());
		bundle = loadBundle(activeLocale);
	}

	public static Locale currentLocale() {
		return activeLocale != null ? activeLocale : Locale.getDefault();
	}

	private static ResourceBundle loadBundle(Locale locale) {
		Locale langOnly = Locale.forLanguageTag(locale.getLanguage());
		try {
			return ResourceBundle.getBundle("i18n/messages", langOnly);
		} catch (Exception e) {
			try {
				return ResourceBundle.getBundle("i18n/messages", Locale.ENGLISH);
			} catch (Exception ex) {
				return ResourceBundle.getBundle("i18n/messages");
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
