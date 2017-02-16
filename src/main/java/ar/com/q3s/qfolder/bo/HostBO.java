package ar.com.q3s.qfolder.bo;

import java.util.List;

public interface HostBO {

	void add(String host) throws Exception;
	List<String> getAll() throws Exception;
	String uuid() throws Exception;
	
}