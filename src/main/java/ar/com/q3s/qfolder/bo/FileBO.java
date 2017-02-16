package ar.com.q3s.qfolder.bo;

import java.io.File;
import java.io.InputStream;
import java.util.Set;

import ar.com.q3s.qfolder.model.QFile;

public interface FileBO {

	int size();
	Set<QFile> getAll() throws Exception;
	File get(String name) throws Exception;
	void write(String fileName, InputStream inputStream) throws Exception;
	void depuration();
	QFile open(String host, String filename) throws Exception;
	
}
