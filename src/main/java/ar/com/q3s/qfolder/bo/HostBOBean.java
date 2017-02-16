package ar.com.q3s.qfolder.bo;

import java.util.Arrays;
import java.util.List;

import ar.com.q3s.qfolder.dao.HostDAO;
import ar.com.q3s.qfolder.util.PropertyUtils;

public class HostBOBean implements HostBO {

	private static final Long HOST_UUID = System.currentTimeMillis(); 
	private HostDAO dao;

	public void init() throws Exception {
		add("");
		String property = PropertyUtils.getProperty("app.hosts");
		if(property != null){
			for (String item : Arrays.asList(property.split(","))) {
				add(item);				
			}
		}
	}
	
	@Override
	public void add(String host) throws Exception {
		dao.add(host);
	}

	@Override
	public List<String> getAll() throws Exception {
		return dao.getAll();
	}
	
	@Override
	public String uuid() throws Exception {
		return HOST_UUID.toString();
	}
	
	//----------------------------------

	public HostDAO getDao() {
		return dao;
	}

	public void setDao(HostDAO dao) {
		this.dao = dao;
	}

}