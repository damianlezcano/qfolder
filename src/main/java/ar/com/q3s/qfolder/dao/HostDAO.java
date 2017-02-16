package ar.com.q3s.qfolder.dao;

import java.util.List;

import ar.com.q3s.qfolder.model.QHost;


public interface HostDAO {

	void add(String host) throws Exception;
	List<String> getAll() throws Exception;
//	QHost findByName(String string);
	
}