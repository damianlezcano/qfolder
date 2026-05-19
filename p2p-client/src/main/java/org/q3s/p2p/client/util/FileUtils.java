package org.q3s.p2p.client.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.q3s.p2p.model.QFile;

public class FileUtils {

	public static List<QFile> files(String path) {
		return files(path, "");
	}

	public static List<QFile> files(String basePath, String relativePath) {
		List<QFile> res = new ArrayList<QFile>();
		String fullPath = basePath;
		if (relativePath != null && !relativePath.isEmpty()) {
			fullPath = new File(basePath, relativePath).getAbsolutePath();
		}
		File folder = new File(fullPath);
		File[] flist = folder.listFiles();
		if (flist != null) {
			for (File file : flist) {
				if (file.isHidden()) continue;
				QFile f = new QFile();
				f.setName(file.getName());
				f.setDate(file.lastModified());
				f.setSize(file.length());
				f.setDirectory(file.isDirectory());
				String rel = relativePath == null || relativePath.isEmpty() ? file.getName() : relativePath + "/" + file.getName();
				f.setRelativePath(rel);
				if (file.isFile() || file.isDirectory()) {
					res.add(f);
				}
			}
		}
		return res;
	}

	public static void remove(Path path) throws IOException {
		if (Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
			try (DirectoryStream<Path> entries = Files.newDirectoryStream(path)) {
				for (Path entry : entries) {
					remove(entry);
				}
			}
		}
		Files.deleteIfExists(path);
	}

	public static void move(String from, String to) throws Exception {
		Files.move(Paths.get(from), Paths.get(to));
	}
}
