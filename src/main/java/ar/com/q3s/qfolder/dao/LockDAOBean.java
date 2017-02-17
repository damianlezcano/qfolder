package ar.com.q3s.qfolder.dao;

import java.io.File;
import java.io.FileFilter;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Date;
import java.util.Properties;

import ar.com.q3s.qfolder.model.QLock;
import ar.com.q3s.qfolder.util.PropertyUtils;

public class LockDAOBean implements LockDAO {

	private final String path = PropertyUtils.getDataPath();
	
	@Override
	public QLock get(String name) throws Exception {
		try {
			if(isLock(name)){
				Properties prop = new Properties();
				String key = path + File.separator + "."+name+".lock";
				InputStream inputStream = new FileInputStream(key);
				prop.load(inputStream);
				QLock lock = new QLock();
				lock.setUser(prop.getProperty("user"));
				lock.setDate(new Date(Long.valueOf(prop.getProperty("date"))));
				return lock;
			}
			return null;
		} catch (Exception e) {
			throw e;
		}
	}

	@Override
	public boolean isLock(String name) throws Exception {
		String key = path + File.separator + "."+name+".lock";
		return new File(key).exists();
	}
	
	@Override
	public void put(String name, QLock lock) throws Exception {
		try {
			OutputStream out = null;
			String key = path + File.separator + "."+name+".lock";
			Properties prop = new Properties();
			File file = new File(key);
			if(!file.exists()){
				out = new FileOutputStream(file);
			}
			InputStream inputStream = new FileInputStream(key);
			prop.load(inputStream);
			prop.setProperty("user", lock.getUser());
			prop.setProperty("date", String.valueOf(lock.getDate().getTime()));
			prop.save(out,null);
		} catch (Exception e) {
			throw e;
		}
		
	}

	@Override
	public void remove(String name) {
		String key = path + File.separator + "."+name+".lock";
		new File(key).delete();
	}

	public void depuration(){
		new File(path).listFiles(new FileFilter() {
			@Override
			public boolean accept(File file) {
				if((file.getName().startsWith(".") && file.getName().endsWith(".lock"))){
					return file.delete();					
				}
				return false;
			}
		});
		
	}
	
}