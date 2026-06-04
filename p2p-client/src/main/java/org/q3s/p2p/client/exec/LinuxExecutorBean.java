package org.q3s.p2p.client.exec;

public class LinuxExecutorBean implements Executor {

	public void open(String fullname) throws InterruptedException {
		try {
			new ProcessBuilder("xdg-open", fullname).start();
		} catch (Exception e) {
			java.util.logging.Logger.getLogger("qfolder.exec").warning("Error opening file: " + e.getMessage());
		}
	}

}
