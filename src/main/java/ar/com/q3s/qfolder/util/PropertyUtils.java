package ar.com.q3s.qfolder.util;

import java.io.File;
import java.io.InputStream;
import java.util.Properties;

/**
 * La idea de esta clase es que nos permita acceder a las properties sin tener que declararlo en los mapeos
 * @author damian
 *
 */
public class PropertyUtils {

	private static Properties prop = new Properties();
	
	static {
		try {
			InputStream inputStream = PropertyUtils.class.getClassLoader().getResourceAsStream("setting.properties");
			prop.load(inputStream);
		} catch (Exception e) {
		}
	}
	
	private PropertyUtils(){}
	
	public static String getProperty(String key){
		String value = System.getProperty(key);
		if(value == null){
			value = prop.getProperty(key + "." +  getSystemName());
			if(value == null){
				value = prop.getProperty(key);
			}			
		}
		return value;
	}
	
	public static String getSystemName(){
		return System.getProperty("os.name").replaceAll(" ", "");
	}
	
	public static String getDesktop(){
		return System.getenv("XDG_CURRENT_DESKTOP");
	}

	public static String getHosts() {
		return getProperty("app.hosts");
	}

	public static String getName() {
		return getProperty("app.name");
	}

	public static String getShellFileExec() {
		return getProperty("app.shell.file.exec");
	}

	public static String getShellNotifyExec() {
		return getProperty("app.shell.notify.exec." + PropertyUtils.getSystemName() + "." + PropertyUtils.getDesktop());
	}

	public static String getPort() {
		return getProperty("app.rest.port");
	}

	public static String getBindAddress() {
		return getProperty("app.rest.bind.address");
	}

	public static String getThreadPoolNumber() {
		return getProperty("app.rest.thread.pool");
	}

	public static String getDataPath() {
		return getProperty("app.data.path");
	}

	public static String getShellBrowserName() {
		return getProperty("app.shell.browser");
	}

	public static String getShellBrowserExec() {
		return getProperty("app.shell.browser.exec");
	}

	public static String getTempPath() {
		return System.getProperty("java.io.tmpdir");
	}

	public static String getTempPath(String filename) {
		return getTempPath() + File.separator + filename;
	}
	
}