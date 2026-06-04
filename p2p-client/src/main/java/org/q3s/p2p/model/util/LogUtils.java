package org.q3s.p2p.model.util;

public class LogUtils {

	public void info(String msg) {
		java.util.logging.Logger.getLogger("qfolder.server").info(msg);
	}
}
