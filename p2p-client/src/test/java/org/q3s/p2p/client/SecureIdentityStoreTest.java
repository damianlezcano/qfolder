package org.q3s.p2p.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.nio.file.Files;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SecureIdentityStoreTest {

	@Test
	void generaParDeClavesEd25519(@TempDir File tmp) throws Exception {
		SecureIdentityStore store = new SecureIdentityStore(new File(tmp, "id.bin"), "alice");
		store.loadOrCreate();
		String pub = store.getPublicKeyBase64();
		String priv = store.getPrivateKeyBase64();
		assertNotNull(pub);
		assertFalse(pub.isBlank());
		assertNotNull(priv);
		assertFalse(priv.isBlank());
	}

	@Test
	void persisteYRecuperaMismasClaves(@TempDir File tmp) throws Exception {
		File f = new File(tmp, "id.bin");
		SecureIdentityStore a = new SecureIdentityStore(f, "bob");
		a.loadOrCreate();
		String pubA = a.getPublicKeyBase64();
		String privA = a.getPrivateKeyBase64();

		SecureIdentityStore b = new SecureIdentityStore(f, "bob");
		b.loadOrCreate();
		assertEquals(pubA, b.getPublicKeyBase64());
		assertEquals(privA, b.getPrivateKeyBase64());
	}

	@Test
	void clavePrivadaNoApareceEnDiscoEnPlaintext(@TempDir File tmp) throws Exception {
		File f = new File(tmp, "id.bin");
		SecureIdentityStore a = new SecureIdentityStore(f, "carol");
		a.loadOrCreate();
		String priv = a.getPrivateKeyBase64();
		byte[] bytes = Files.readAllBytes(f.toPath());
		String onDisk = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
		assertFalse(onDisk.contains(priv), "private key (Base64) must not appear in plaintext on disk");
		assertTrue(onDisk.contains("QFOL-ID1:"), "header marker should be present");
	}

	@Test
	void dosMiembrosGeneranClavesDistintas(@TempDir File tmp) throws Exception {
		SecureIdentityStore a = new SecureIdentityStore(new File(tmp, "a.bin"), "dave");
		SecureIdentityStore b = new SecureIdentityStore(new File(tmp, "b.bin"), "dave");
		a.loadOrCreate();
		b.loadOrCreate();
		assertNotEquals(a.getPublicKeyBase64(), b.getPublicKeyBase64());
		assertNotEquals(a.getPrivateKeyBase64(), b.getPrivateKeyBase64());
	}

	@Test
	void archivoCorruptoGeneraNuevoPar(@TempDir File tmp) throws Exception {
		File f = new File(tmp, "id.bin");
		Files.writeString(f.toPath(), "garbage data");
		SecureIdentityStore s = new SecureIdentityStore(f, "eve");
		s.loadOrCreate();
		assertFalse(s.getPublicKeyBase64().isBlank());
		assertFalse(s.getPrivateKeyBase64().isBlank());
	}

	@Test
	void clavePrivadaNoEsTextoPlanoProperties(@TempDir File tmp) throws Exception {
		File f = new File(tmp, "id.bin");
		SecureIdentityStore s = new SecureIdentityStore(f, "frank");
		s.loadOrCreate();
		byte[] bytes = Files.readAllBytes(f.toPath());
		String onDisk = new String(bytes, java.nio.charset.StandardCharsets.ISO_8859_1);
		assertFalse(onDisk.contains("member.privateKey"));
		assertFalse(onDisk.contains("privateKey="));
	}

	@Test
	void clavesBase64SonValidas(@TempDir File tmp) throws Exception {
		SecureIdentityStore s = new SecureIdentityStore(new File(tmp, "id.bin"), "grace");
		s.loadOrCreate();
		byte[] pubBytes = Base64.getDecoder().decode(s.getPublicKeyBase64());
		assertNotNull(pubBytes);
		assertTrue(pubBytes.length > 0);
	}
}
