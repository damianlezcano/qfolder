package org.q3s.p2p.client.exec;

public class ExecutorFactoryBean {

	public static Executor create() {
		String os = getOperatingSystem();
		if(os.startsWith("Linux")) {
			return new LinuxExecutorBean();
		}else if(os.startsWith("Mac")){
			return new MacExecutorBean();
		} else if (os.startsWith("Windows")) {
			return new WindowsExecutorBean();
		}
		throw new IllegalStateException("Unsupported OS: " + os);
	}

	public static String getOperatingSystem() {
	    String os = System.getProperty("os.name");
	    return os;
	}

}