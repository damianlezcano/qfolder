package org.q3s.p2p.adapters.filesystem;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class QfolderLayoutTest {

	@Test
	void userdataRootEsBaseFolderMasUserdata(@TempDir Path tmp) {
		QfolderLayout l = new QfolderLayout(tmp);
		assertEquals(tmp.resolve("userdata"), l.userdataRoot());
	}

	@Test
	void systemdataRootEsBaseFolderMasSystemdata(@TempDir Path tmp) {
		QfolderLayout l = new QfolderLayout(tmp);
		assertEquals(tmp.resolve("systemdata"), l.systemdataRoot());
	}

	@Test
	void systemWorkspaceRootContieneWorkspacesYIdSafe(@TempDir Path tmp) {
		QfolderLayout l = new QfolderLayout(tmp);
		Path p = l.systemWorkspaceRoot("ws-123");
		assertEquals(tmp.resolve("systemdata/workspaces/ws-123"), p);
	}

	@Test
	void identityFileEstaEnSystemdata(@TempDir Path tmp) {
		QfolderLayout l = new QfolderLayout(tmp);
		assertEquals(tmp.resolve("systemdata/identity.properties"), l.identityFile());
	}

	@Test
	void datePrefixFormatoYYYYMMDD() {
		Instant t = LocalDateTime.of(2024, 3, 15, 10, 30).atZone(ZoneId.systemDefault()).toInstant();
		assertEquals("2024/03/15", QfolderLayout.datePrefix(t));
	}

	@Test
	void folderNameFormatoHHmmSlug() {
		Instant t = LocalDateTime.of(2024, 3, 15, 9, 5).atZone(ZoneId.systemDefault()).toInstant();
		assertEquals("0905-mi-workspace", QfolderLayout.folderName(t, "id-1", "Mi Workspace"));
	}

	@Test
	void safeIdLimpiaCaracteresRaros() {
		assertEquals("abc-123", QfolderLayout.safeId("abc/123"));
		assertEquals("workspace", QfolderLayout.safeId(""));
		assertEquals("workspace", QfolderLayout.safeId(null));
		assertEquals("workspace", QfolderLayout.safeId("###"));
	}

	@Test
	void slugNormalizaYAplana() {
		assertEquals("mi-workspace", QfolderLayout.slug("Mi Workspace"));
		assertEquals("cafe", QfolderLayout.slug("Café"));
		assertEquals("workspace", QfolderLayout.slug(""));
		assertEquals("workspace", QfolderLayout.slug(null));
	}

	@Test
	void slugTruncaALongitud60() {
		String longName = "a".repeat(100);
		String result = QfolderLayout.slug(longName);
		assertEquals(60, result.length());
	}

	@Test
	void createStructureCreaTodosLosSubdirs(@TempDir Path tmp) {
		QfolderLayout l = new QfolderLayout(tmp);
		Path ws = tmp.resolve("ws");
		assertTrue(l.createStructure(ws));
		for (String sub : QfolderLayout.USERDATA_SUBDIRS) {
			assertTrue(Files.isDirectory(ws.resolve(sub)), "subdir " + sub + " debe existir");
		}
	}

	@Test
	void workspaceJsonIncluyeCamposEsperados() {
		Instant t1 = Instant.parse("2024-01-01T00:00:00Z");
		Instant t2 = Instant.parse("2024-01-02T00:00:00Z");
		Map<String, Object> json = QfolderLayout.workspaceJson("ws-1", "My WS", t1, t2, "/local/path", 2);
		assertEquals("ws-1", json.get("workspace_id"));
		assertEquals("My WS", json.get("name"));
		assertEquals("my-ws", json.get("workspace_slug"));
		assertEquals("2024-01-01T00:00:00Z", json.get("created_at"));
		assertEquals("2024-01-02T00:00:00Z", json.get("joined_locally_at"));
		assertEquals("/local/path", json.get("local_path"));
		assertEquals("ed25519", json.get("auth_mode"));
		assertNotNull(json.get("join_policy"));
	}

	@Test
	void findExistingWorkspaceFolderEncuentraPorId(@TempDir Path tmp) throws Exception {
		QfolderLayout l = new QfolderLayout(tmp);
		Path dayDir = l.userdataRoot().resolve("2024/03/15");
		Files.createDirectories(dayDir);
		Path wsFolder = dayDir.resolve("0905-test");
		Files.createDirectories(wsFolder);
		Files.writeString(wsFolder.resolve("workspace.json"),
				"{\"workspace_id\":\"ws-found\",\"name\":\"Test\"}");
		assertTrue(l.findExistingWorkspaceFolder("ws-found").isPresent());
		assertFalse(l.findExistingWorkspaceFolder("ws-missing").isPresent());
	}
}
