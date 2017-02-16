package ar.com.q3s.qfolder.dao;

import java.io.File;
import java.io.InputStream;

public interface FileDAO {
	File[] getAll();
	File get(String name);
	int size();
	void write(String fileName, InputStream inputStream) throws Exception;
}