package ar.com.q3s.qfolder.util;

import java.net.InetAddress;
import java.net.UnknownHostException;

public class NetworkUtils {

	static String port = PropertyUtils.getPort();
	
	public static String buildUri() throws UnknownHostException{
		InetAddress ip = InetAddress.getLocalHost();
		return String.format("http://%s:%s",ip.getHostAddress(),port);
	}

	public static Object getLocalHostName() throws UnknownHostException {
		return InetAddress.getLocalHost().getHostName();
	}
	
}