package org.q3s.p2p.client.exec;

public class WindowsExecutorBean implements Executor {

	@Override
	public void open(String fullname) throws InterruptedException {
		try {
			new ProcessBuilder("cmd", "/c", "start", "", fullname).start();
		} catch (Exception e) {
			try {
				java.awt.Desktop.getDesktop().open(new java.io.File(fullname));
			} catch (Exception ex) {
				throw new InterruptedException("No se pudo abrir: " + fullname);
			}
		}
	}

}
