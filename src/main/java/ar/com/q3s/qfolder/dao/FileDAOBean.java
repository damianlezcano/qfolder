package ar.com.q3s.qfolder.dao;

import java.io.File;
import java.io.FileFilter;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

import org.apache.commons.io.IOUtils;

import ar.com.q3s.qfolder.util.PropertyUtils;

public class FileDAOBean implements FileDAO {

	@Override
	public File[] getAll() {
		return new File(buildPath()).listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				if(!(file.getName().startsWith(".") && file.getName().endsWith(".lock"))){
					return file.isFile();
				}
				return false;
			}
		});
	}

	@Override
	public File get(String name) {
		return new File(buildPath(name));
	}

	public void write(String fileName, InputStream inputStream) throws Exception{
		byte [] bytes = IOUtils.toByteArray(inputStream);
		writeFile(bytes,buildPath(fileName));
	}

	@Override
	public int size() {
		return getAll().length;
	}
	
	private void writeFile(byte[] content, String filename) throws IOException {
		 
		File file = new File(filename);
 
		if (!file.exists()) {
			file.createNewFile();
		}
 
		FileOutputStream fop = new FileOutputStream(file);
 
		fop.write(content);
		fop.flush();
		fop.close();
 
	}

	private String buildPath() {
		String dataPath = PropertyUtils.getDataPath();
		String spath = FileDAOBean.class.getProtectionDomain().getCodeSource().getLocation().getPath();
		if(spath.indexOf(".jar") > 0){
			dataPath = String.format("%s%s%s",(spath.substring(0,spath.lastIndexOf(File.separator))),File.separator,dataPath);
		}else{
			dataPath = "/home/damian/dev/workspace/qfolder" + File.separator + dataPath;
		}
		return dataPath;
	}
	
	private String buildPath(String name) {
		return String.format("%s%s%s", buildPath(),File.separator,name);
	}
	
}