package org.q3s.p2p.client.util;

import java.text.MessageFormat;
import java.util.Locale;
import java.util.ResourceBundle;

public class I18n {

	private static ResourceBundle bundle;

	static {
		Locale locale = Locale.getDefault();
		Locale langOnly = new Locale(locale.getLanguage());
		try {
			bundle = ResourceBundle.getBundle("i18n/messages", langOnly);
		} catch (Exception e) {
			try {
				bundle = ResourceBundle.getBundle("i18n/messages", Locale.ENGLISH);
			} catch (Exception ex) {
				bundle = ResourceBundle.getBundle("i18n/messages");
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
