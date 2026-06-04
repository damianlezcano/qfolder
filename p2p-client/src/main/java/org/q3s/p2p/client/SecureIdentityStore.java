package org.q3s.p2p.client;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.SecureRandom;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Arrays;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;

/**
 * Almacena la identidad local (par de claves Ed25519) encriptada con AES-256-GCM
 * y passphrase derivada de PBE (PBKDF2-HMAC-SHA256, 100k iteraciones).
 * Reemplaza al almacenamiento en plaintext en identity.properties que era
 * legible por cualquier proceso con acceso al systemdata dir.
 *
 * Estrategia de passphrase: SHA-256(user.name + ":qfolder.identity.v1") + sal
 * aleatoria por archivo + PBKDF2 100k iteraciones. Asi la passphrase depende
 * del usuario del SO y un salt aleatorio impide ataques de rainbow tables
 * precomputadas. El salt + IV + ciphertext se almacenan en un unico archivo
 * binario junto a las claves publicas (que pueden ir en claro para
 * compartirse con peers).
 *
 * Para distribucion publica amplia se recomienda migrar a almacenamiento
 * seguro de plataforma (Keychain, libsecret, Windows Credential Manager).
 */
public class SecureIdentityStore {

	private static final String SALT_PREFIX = "QFOL-ID1:";
	private static final byte VERSION = 1;
	private static final int PBKDF2_ITERATIONS = 100_000;
	private static final int KEY_LENGTH_BITS = 256;
	private static final int GCM_TAG_BITS = 128;
	private static final int IV_LENGTH = 12;
	private static final int SALT_LENGTH = 16;
	private static final SecureRandom RNG = new SecureRandom();

	private final File identityFile;
	private final String memberId;
	private PublicKey publicKey;
	private PrivateKey privateKey;

	public SecureIdentityStore(File identityFile, String memberId) {
		this.identityFile = identityFile;
		this.memberId = memberId == null ? "anonymous" : memberId;
	}

	public void loadOrCreate() throws Exception {
		if (identityFile.exists() && identityFile.length() > 0) {
			if (tryLoad()) return;
		}
		generateAndStore();
	}

	private boolean tryLoad() throws Exception {
		byte[] data = Files.readAllBytes(identityFile.toPath());
		if (data.length < SALT_PREFIX.length() + 1 + SALT_LENGTH + IV_LENGTH + 16) return false;
		byte[] prefix = Arrays.copyOfRange(data, 0, SALT_PREFIX.length());
		if (!new String(prefix, StandardCharsets.US_ASCII).equals(SALT_PREFIX)) return false;
		byte versionByte = data[SALT_PREFIX.length()];
		if (versionByte != VERSION) return false;
		int cursor = SALT_PREFIX.length() + 1;
		byte[] salt = Arrays.copyOfRange(data, cursor, cursor + SALT_LENGTH);
		cursor += SALT_LENGTH;
		byte[] iv = Arrays.copyOfRange(data, cursor, cursor + IV_LENGTH);
		cursor += IV_LENGTH;
		byte[] encrypted = Arrays.copyOfRange(data, cursor, data.length);

		SecretKey aesKey = deriveKey(salt);
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.DECRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
		byte[] plaintext;
		try {
			plaintext = cipher.doFinal(encrypted);
		} catch (Exception e) {
			return false;
		}
		String[] parts = new String(plaintext, StandardCharsets.UTF_8).split("\n", 2);
		if (parts.length != 2) return false;
		String publicKeyB64 = parts[0];
		String privateKeyB64 = parts[1];
		try {
			byte[] pubBytes = Base64.getDecoder().decode(publicKeyB64);
			byte[] privBytes = Base64.getDecoder().decode(privateKeyB64);
			KeyFactory kf;
			try {
				kf = KeyFactory.getInstance("Ed25519");
			} catch (Exception e) {
				return false;
			}
			this.publicKey = kf.generatePublic(new X509EncodedKeySpec(pubBytes));
			this.privateKey = kf.generatePrivate(new PKCS8EncodedKeySpec(privBytes));
			return true;
		} catch (Exception e) {
			return false;
		}
	}

	private void generateAndStore() throws Exception {
		KeyPair pair;
		try {
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("Ed25519");
			pair = kpg.generateKeyPair();
		} catch (Exception edFail) {
			KeyPairGenerator kpg = KeyPairGenerator.getInstance("RSA");
			kpg.initialize(2048);
			pair = kpg.generateKeyPair();
		}
		this.publicKey = pair.getPublic();
		this.privateKey = pair.getPrivate();

		String publicKeyB64 = Base64.getEncoder().encodeToString(publicKey.getEncoded());
		String privateKeyB64 = Base64.getEncoder().encodeToString(privateKey.getEncoded());
		String plaintext = publicKeyB64 + "\n" + privateKeyB64;

		SecureRandom rng = RNG;
		byte[] salt = new byte[SALT_LENGTH];
		rng.nextBytes(salt);
		byte[] iv = new byte[IV_LENGTH];
		rng.nextBytes(iv);

		SecretKey aesKey = deriveKey(salt);
		Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
		cipher.init(Cipher.ENCRYPT_MODE, aesKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
		byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

		File parent = identityFile.getParentFile();
		if (parent != null && !parent.exists()) parent.mkdirs();
		try (var out = Files.newOutputStream(identityFile.toPath())) {
			out.write(SALT_PREFIX.getBytes(StandardCharsets.US_ASCII));
			out.write(VERSION);
			out.write(salt);
			out.write(iv);
			out.write(ciphertext);
		}
	}

	private SecretKey deriveKey(byte[] salt) throws Exception {
		String user = System.getProperty("user.name", "default");
		String staticSalt = "qfolder.identity.v1";
		char[] passphrase = (user + ":" + staticSalt).toCharArray();
		PBEKeySpec spec = new PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_LENGTH_BITS);
		SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
		byte[] keyBytes = skf.generateSecret(spec).getEncoded();
		Arrays.fill(passphrase, '\0');
		return new javax.crypto.spec.SecretKeySpec(keyBytes, "AES");
	}

	public String getPublicKeyBase64() {
		return publicKey == null ? "" : Base64.getEncoder().encodeToString(publicKey.getEncoded());
	}

	public String getPrivateKeyBase64() {
		return privateKey == null ? "" : Base64.getEncoder().encodeToString(privateKey.getEncoded());
	}
}
