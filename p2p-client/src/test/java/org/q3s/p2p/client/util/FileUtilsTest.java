package org.q3s.p2p.client.util;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class FileUtilsTest {

	@Test void filesListaContenidoDeDirectorio(@TempDir Path tmp) throws Exception {
		Files.writeString(tmp.resolve("a.txt"), "hello");
		Files.writeString(tmp.resolve("b.txt"), "world");
		Files.createDirectory(tmp.resolve("sub"));

		List<org.q3s.p2p.model.QFile> result = FileUtils.files(tmp.toString());
		assertEquals(3, result.size());
		boolean hasA = result.stream().anyMatch(f -> "a.txt".equals(f.getName()) && f.getSize() == 5);
		boolean hasB = result.stream().anyMatch(f -> "b.txt".equals(f.getName()) && f.getSize() == 5);
		boolean hasSub = result.stream().anyMatch(f -> "sub".equals(f.getName()) && f.isDirectory());
		assertTrue(hasA && hasB && hasSub);
	}

	@Test void removeBorraRecursivamente(@TempDir Path tmp) throws Exception {
		Path dir = tmp.resolve("nested");
		Files.createDirectory(dir);
		Files.writeString(dir.resolve("inner.txt"), "data");

		FileUtils.remove(dir);

		assertFalse(Files.exists(dir));
	}

	@Test void moveReubicaArchivo(@TempDir Path tmp) throws Exception {
		Path src = tmp.resolve("src.txt");
		Path dst = tmp.resolve("dst.txt");
		Files.writeString(src, "payload");

		FileUtils.move(src.toString(), dst.toString());

		assertFalse(Files.exists(src));
		assertTrue(Files.exists(dst));
		assertEquals("payload", Files.readString(dst));
	}
}
