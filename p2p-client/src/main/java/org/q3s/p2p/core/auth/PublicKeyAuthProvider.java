package org.q3s.p2p.core.auth;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyFactory;
import java.security.Signature;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.q3s.p2p.core.model.AuthInfo;
import org.q3s.p2p.core.model.Event;
import org.q3s.p2p.core.model.Member;
import org.q3s.p2p.core.state.WorkspaceState;
import org.q3s.p2p.ports.AuthProvider;
import org.q3s.p2p.ports.IdGenerator;

public class PublicKeyAuthProvider implements AuthProvider {
	private static final String ALGORITHM = "Ed25519";
	private final IdGenerator ids;
	private final Map<String, String> privateKeysByMember = new ConcurrentHashMap<>();

	public PublicKeyAuthProvider(IdGenerator ids) {
		this.ids = ids;
	}

	@Override
	public Member createMemberIdentity(String displayName) {
		try {
			KeyPair keyPair = KeyPairGenerator.getInstance(ALGORITHM).generateKeyPair();
			String memberId = ids.newId("mem");
			String publicKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
			String privateKeyBase64 = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
			registerPrivateKey(memberId, privateKeyBase64);
			return new Member(memberId, displayName, "device-ed25519", ids.newId("mbrtok"), false, publicKeyBase64);
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo crear identidad Ed25519", e);
		}
	}

	@Override
	public boolean validateJoinRequest(Event event, WorkspaceState state) {
		return event != null && event.payload() != null;
	}

	@Override
	public boolean validateMemberReconnect(String workspaceId, String memberId, String membershipToken, WorkspaceState state) {
		if (state == null || memberId == null) return false;
		return state.isAuthorized(memberId) && membershipToken != null && !membershipToken.isBlank();
	}

	@Override
	public Event stampEvent(Event event) {
		return stampEvent(event, null);
	}

	@Override
	public Event stampEvent(Event event, Member author) {
		if (event == null) return null;
		String privateKey = privateKeysByMember.get(event.authorMemberId());
		if (privateKey == null || privateKey.isBlank()) return event;
		return new Event(event.eventId(), event.workspaceId(), event.type(), event.authorMemberId(),
				event.createdAt(), event.parents(), event.payload(),
				new AuthInfo("ed25519"), signEvent(event, privateKey), event.persistent());
	}

	@Override
	public boolean validateEventAuthor(Event event, WorkspaceState state) {
		if (event == null || state == null) return false;
		Member member = state.authorizedMembers().get(event.authorMemberId());
		return member != null && !member.revoked() && verifyEventSignature(event, member.publicKey());
	}

	public void registerPrivateKey(String memberId, String privateKeyBase64) {
		if (memberId != null && !memberId.isBlank() && privateKeyBase64 != null && !privateKeyBase64.isBlank()) {
			privateKeysByMember.put(memberId, privateKeyBase64);
		}
	}

	@Override
	public String authMode() { return "ed25519"; }

	private String signEvent(Event event, String privateKeyBase64) {
		try {
			PrivateKey privateKey = KeyFactory.getInstance(ALGORITHM).generatePrivate(
					new PKCS8EncodedKeySpec(Base64.getDecoder().decode(privateKeyBase64)));
			Signature signature = Signature.getInstance(ALGORITHM);
			signature.initSign(privateKey);
			signature.update(canonicalBytes(event));
			return Base64.getEncoder().encodeToString(signature.sign());
		} catch (Exception e) {
			return "";
		}
	}

	public static boolean verifyEventSignature(Event event, String publicKeyBase64) {
		try {
			if (event == null || event.signature() == null || event.signature().isBlank()
					|| publicKeyBase64 == null || publicKeyBase64.isBlank()) return false;
			PublicKey publicKey = KeyFactory.getInstance(ALGORITHM).generatePublic(
					new X509EncodedKeySpec(Base64.getDecoder().decode(publicKeyBase64)));
			Signature signature = Signature.getInstance(ALGORITHM);
			signature.initVerify(publicKey);
			signature.update(canonicalBytes(event));
			return signature.verify(Base64.getDecoder().decode(event.signature()));
		} catch (Exception e) {
			return false;
		}
	}

	private static byte[] canonicalBytes(Event event) {
		return canonical(event).getBytes(java.nio.charset.StandardCharsets.UTF_8);
	}

	private static String canonical(Event event) {
		StringBuilder out = new StringBuilder();
		out.append(value(event.eventId())).append('\n')
				.append(value(event.workspaceId())).append('\n')
				.append(value(event.type())).append('\n')
				.append(value(event.authorMemberId())).append('\n')
				.append(event.createdAt() == null ? "" : event.createdAt().toString()).append('\n')
				.append(list(event.parents())).append('\n')
				.append(map(event.payload())).append('\n')
				.append(event.persistent());
		return out.toString();
	}

	private static String value(Object value) {
		return value == null ? "" : String.valueOf(value).replace("\\", "\\\\").replace("\n", "\\n");
	}

	private static String list(List<?> values) {
		if (values == null) return "[]";
		List<String> out = new ArrayList<>();
		for (Object item : values) out.add(value(item));
		return out.toString();
	}

	private static String map(Map<String, Object> values) {
		if (values == null) return "{}";
		Map<String, Object> sorted = new LinkedHashMap<>();
		values.keySet().stream().sorted().forEach(key -> sorted.put(key, values.get(key)));
		StringBuilder out = new StringBuilder("{");
		boolean first = true;
		for (Map.Entry<String, Object> entry : sorted.entrySet()) {
			if (!first) out.append(',');
			first = false;
			out.append(value(entry.getKey())).append('=').append(canonicalValue(entry.getValue()));
		}
		return out.append('}').toString();
	}

	private static String canonicalValue(Object value) {
		if (value instanceof Map<?, ?> map) {
			Map<String, Object> stringMap = new LinkedHashMap<>();
			for (Map.Entry<?, ?> entry : map.entrySet()) stringMap.put(String.valueOf(entry.getKey()), entry.getValue());
			return map(stringMap);
		}
		if (value instanceof List<?> list) return list(list);
		if (value instanceof Number number) return canonicalNumber(number);
		if (value instanceof Instant instant) return instant.toString();
		return value(value);
	}

	private static String canonicalNumber(Number number) {
		if (number instanceof Float || number instanceof Double) {
			double value = number.doubleValue();
			if (Math.rint(value) == value) return Long.toString((long) value);
			return Double.toString(value);
		}
		return String.valueOf(number);
	}
}
