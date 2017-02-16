package ar.com.q3s.qfolder.resources;

import java.util.HashSet;
import java.util.Set;

import javax.ws.rs.core.Application;

import org.jboss.resteasy.plugins.interceptors.CorsFilter;

public class RegisterApplication extends Application
{
	private Set<Object> singletons;
	
	@Override
	public Set<Class<?>> getClasses() {
		Set<Class<?>> clazzes = new HashSet<Class<?>>();
		clazzes.add(DefaultResourceBean.class);
		return clazzes;
	}

	@Override
	public Set<Object> getSingletons() {
		if (singletons == null) {
			
			CorsFilter corsFilter = new CorsFilter();
			corsFilter.getAllowedOrigins().add("*");

			singletons = new HashSet<Object>();
			singletons.add(corsFilter);
		}
		return singletons;
	}
}