package org.q3s.p2p.client.exec;

public class ExecutorFactoryBean {

	public static Executor create() {
		String os = getOperatingSystem();
		if(os.startsWith("Linux")) {
			return new LinuxExecutorBean();			
		}else if(os.startsWith("Mac")){
			return new MacExecutorBean();
		}
		return null;
	}

	public static String getOperatingSystem() {
	    String os = System.getProperty("os.name");
	    return os;
	}
	
}