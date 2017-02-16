package ar.com.q3s.qfolder.util;

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
	
}