package org.q3s.p2p.client.util;

import java.util.LinkedHashMap;
import java.util.Map;

import javax.swing.UIManager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.formdev.flatlaf.FlatIntelliJLaf;
import com.formdev.flatlaf.FlatDarculaLaf;
import com.formdev.flatlaf.FlatLightLaf;

public final class LookAndFeelManager {

	public static final String DEFAULT = "flat-light";

	private static final Map<String, String> OPTIONS = new LinkedHashMap<>();
	static {
		OPTIONS.put("flat-light", "Flat Light");
		OPTIONS.put("flat-dark", "Flat Dark");
		OPTIONS.put("flat-intellij", "Flat IntelliJ");
		OPTIONS.put("flat-darcula", "Flat Darcula");
		OPTIONS.put("metal", "Metal");
		OPTIONS.put("nimbus", "Nimbus");
		OPTIONS.put("system", "System");
	}

	private LookAndFeelManager() {}

	public static Map<String, String> options() {
		return new LinkedHashMap<>(OPTIONS);
	}

	public static String optionDisplayName(String id) {
		return OPTIONS.getOrDefault(id, id);
	}

	public static void apply(String id) {
		if (id == null) id = DEFAULT;
		try {
			switch (id) {
				case "flat-dark" -> UIManager.setLookAndFeel(new FlatDarkLaf());
				case "flat-intellij" -> UIManager.setLookAndFeel(new FlatIntelliJLaf());
				case "flat-darcula" -> UIManager.setLookAndFeel(new FlatDarculaLaf());
				case "metal" -> UIManager.setLookAndFeel("javax.swing.plaf.metal.MetalLookAndFeel");
				case "nimbus" -> UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
				case "system" -> UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
				default -> UIManager.setLookAndFeel(new FlatLightLaf());
			}
		} catch (Exception e) {
			try {
				UIManager.setLookAndFeel(new FlatLightLaf());
			} catch (Exception ex) {
			}
		}
	}
}
