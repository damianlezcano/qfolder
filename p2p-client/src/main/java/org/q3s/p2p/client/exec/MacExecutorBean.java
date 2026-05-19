package org.q3s.p2p.client.exec;

public class MacExecutorBean implements Executor {

	
	public void open(String fullname) throws InterruptedException {
		try {
			new ProcessBuilder("open", "-n", fullname).start();
		} catch (Exception e) {
			System.err.println("Error opening file: " + e.getMessage());
		}
	}
	
}
