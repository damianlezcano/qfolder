package org.q3s.p2p.client.util;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Locale;
import java.util.ResourceBundle;

import org.junit.jupiter.api.Test;

class I18nTest {

	@Test void cargaBundleEspanol() {
		ResourceBundle b = ResourceBundle.getBundle("i18n/messages", new Locale("es"));
		assertEquals("qfolder", b.getString("app.name"));
	}

	@Test void cargaBundleIngles() {
		ResourceBundle b = ResourceBundle.getBundle("i18n/messages", new Locale("en"));
		assertEquals("qfolder", b.getString("app.name"));
	}

	@Test void keyInexistenteRetornaFallbackConMarcadores() {
		String result = I18n.get("esta_key_no_existe_zzz");
		assertTrue(result.contains("esta_key_no_existe_zzz"));
	}

	@Test void cambioDeLocaleActualizaTextos() {
		I18n.setLocale(new Locale("es"));
		String es = I18n.get("config.languageSpanish");
		I18n.setLocale(new Locale("en"));
		String en = I18n.get("config.languageSpanish");
		assertNotEquals(es, en);
		I18n.setLocale(Locale.getDefault());
	}

	@Test void paridadDeKeysEntreBundlesEsyEn() {
		ResourceBundle es = ResourceBundle.getBundle("i18n/messages", new Locale("es"));
		ResourceBundle en = ResourceBundle.getBundle("i18n/messages", new Locale("en"));
		java.util.Set<String> esKeys = es.keySet();
		java.util.Set<String> enKeys = en.keySet();
		java.util.Set<String> missingInEn = new java.util.HashSet<>(esKeys);
		missingInEn.removeAll(enKeys);
		java.util.Set<String> missingInEs = new java.util.HashSet<>(enKeys);
		missingInEs.removeAll(esKeys);
		assertTrue(missingInEn.isEmpty(), "Keys en es sin contraparte en en: " + missingInEn);
		assertTrue(missingInEs.isEmpty(), "Keys en en sin contraparte en es: " + missingInEs);
	}

	@Test void getConArgsFormateaMensaje() {
		I18n.setLocale(new Locale("en"));
		String formatted = I18n.get("chat.userDisconnected", "Alice");
		assertTrue(formatted.contains("Alice"));
		I18n.setLocale(Locale.getDefault());
	}
}
