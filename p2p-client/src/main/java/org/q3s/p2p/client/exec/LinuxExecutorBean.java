package org.q3s.p2p.client.exec;

public class LinuxExecutorBean implements Executor {

	public void open(String fullname) throws InterruptedException {
		try {
			new ProcessBuilder("xdg-open", fullname).start();
		} catch (Exception e) {
			System.err.println("Error opening file: " + e.getMessage());
		}
	}

}
