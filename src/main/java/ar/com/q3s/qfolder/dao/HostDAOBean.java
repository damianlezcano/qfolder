package ar.com.q3s.qfolder.dao;

import java.util.ArrayList;
import java.util.List;

import ar.com.q3s.qfolder.model.QHost;

public class HostDAOBean implements HostDAO {

	private List<String> list = new ArrayList<String>();
	
	@Override
	public void add(String host) throws Exception {
		if(!list.contains(host)){
			list.add(host);
		}
	}

	@Override
	public List<String> getAll() throws Exception {
		return list;
	}

//	@Override
//	public QHost findByName(String name) {
//		for (QHost item : list) {
//			if(name.equals(item.getName())){
//				return item;
//			}
//		}
//		return null;
//	}
	
//	private List<String> list = new ArrayList<String>();
//
//	public HostDAOBean() throws Exception {
//		add("");
//		String property = PropertyUtils.getProperty("app.hosts");
//		if(property != null){
//			list.addAll(Arrays.asList(property.split(",")));
//		}
//	}
//	
//	@Override
//	public void add(String host) throws Exception {
//		if(!list.contains(host)){
//			list.add(host);
//		}
//	}
//
//	@Override
//	public List<String> getAll() throws Exception {
//		return list;
//	}

}