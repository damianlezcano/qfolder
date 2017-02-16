package ar.com.q3s.qfolder.model;

public class QHost {

	private Long id;
	private String name;
	
	//---------------------------

	public QHost(){}
	
	public QHost(Long id, String name) {
		this.id = id;
		this.name = name;
	}
	
	public Long getId() {
		return id;
	}
	public void setId(Long id) {
		this.id = id;
	}
	public String getName() {
		return name;
	}
	public void setName(String name) {
		this.name = name;
	}
	
	@Override
	public boolean equals(Object obj) {
		if(obj instanceof QHost && obj != null){
			QHost host = (QHost) obj;
			return this.id == host.id;
		}else{
			return false;
		}
	}
	
	
	@Override
	public int hashCode() {
		return 1;
	}
}