package ar.com.q3s.qfolder.dao;

import ar.com.q3s.qfolder.model.QLock;

public interface LockDAO {

	QLock get(String name) throws Exception;
	void put(String name, QLock lock) throws Exception;
	void remove(String fileName);
	boolean isLock(String name) throws Exception;
	void depuration();
	
}