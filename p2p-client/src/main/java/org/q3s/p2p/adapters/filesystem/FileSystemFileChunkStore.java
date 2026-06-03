package org.q3s.p2p.adapters.filesystem;

import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import org.q3s.p2p.ports.FileChunkStore;

public class FileSystemFileChunkStore implements FileChunkStore {
	private final Path root;

	public FileSystemFileChunkStore(Path root) {
		this.root = root;
	}

	@Override
	public synchronized void putChunk(String fileId, String hash, byte[] bytes) {
		try {
			Files.createDirectories(fileDir(fileId));
			Files.write(fileDir(fileId).resolve(hash + ".chunk"), bytes);
			Path manifest = fileDir(fileId).resolve("chunks.order");
			List<String> known = Files.exists(manifest) ? Files.readAllLines(manifest) : List.of();
			if (!known.contains(hash)) {
				java.util.ArrayList<String> next = new java.util.ArrayList<>(known);
				next.add(hash);
				Files.write(manifest, next);
			}
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo guardar chunk", e);
		}
	}

	@Override
	public synchronized Optional<byte[]> getChunk(String hash) {
		try {
			Path file = resolveChunkPath(hash);
			return file != null ? Optional.of(Files.readAllBytes(file)) : Optional.empty();
		} catch (Exception e) {
			throw new IllegalStateException("No se pudo leer chunk", e);
		}
	}

	@Override
	public synchronized boolean hasChunk(String hash) {
		try {
			return resolveChunkPath(hash) != null;
		} catch (Exception e) {
			return false;
		}
	}

	private Path resolveChunkPath(String hash) throws java.io.IOException {
		if (!Files.isDirectory(root)) return null;
		try (var dirs = Files.list(root)) {
			for (Path dir : (Iterable<Path>) dirs.toList()) {
				Path candidate = dir.resolve(hash + ".chunk");
				if (Files.isRegularFile(candidate)) return candidate;
			}
		}
		return null;
	}

	@Override
	public synchronized List<String> listChunks(String fileId) {
		Path dir = fileDir(fileId);
		if (!Files.isDirectory(dir)) return List.of();
		try (var files = Files.list(dir)) {
			Path manifest = dir.resolve("chunks.order");
			if (Files.exists(manifest)) return Files.readAllLines(manifest).stream()
					.filter(hash -> Files.exists(dir.resolve(hash + ".chunk"))).toList();
			return files.filter(path -> path.getFileName().toString().endsWith(".chunk"))
					.sorted().map(path -> path.getFileName().toString().replace(".chunk", "")).toList();
		} catch (Exception e) {
			throw new IllegalStateException("No se pudieron listar chunks", e);
		}
	}

	@Override
	public synchronized byte[] reconstructFile(String fileId) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (String hash : listChunks(fileId)) getChunk(hash).ifPresent(out::writeBytes);
		return out.toByteArray();
	}

	private Path fileDir(String fileId) {
		return root.resolve(fileId);
	}
}
