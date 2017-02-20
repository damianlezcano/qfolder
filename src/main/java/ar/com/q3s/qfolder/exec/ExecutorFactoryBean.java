package ar.com.q3s.qfolder.exec;

public class ExecutorFactoryBean {

	public static Executor create(){
		return new LinuxExecutorBean();
	}
	
}