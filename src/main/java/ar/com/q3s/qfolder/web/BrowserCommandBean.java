package ar.com.q3s.qfolder.web;

import java.text.MessageFormat;

import ar.com.q3s.qfolder.util.NetworkUtils;
import ar.com.q3s.qfolder.util.PropertyUtils;

public class BrowserCommandBean implements Command {

	@Override
	public void invoke() {
		try {
			String uri = NetworkUtils.buildUri();
			String browserName = PropertyUtils.getProperty("app.shell.browser");
			String browserExec = PropertyUtils.getProperty("app.shell.browser.exec");
			if(browserName != null && browserExec != null){
				System.err.println("### ERR: No se puede definir las dos propiedades a la vez (app.shell.browser | app.shell.browser.exec)");
				return;
			}
			if(browserName != null){
				String command = PropertyUtils.getProperty(String.format("app.shell.browser.exec.%s.%s",browserName,PropertyUtils.getSystemName()));
				if(command == null){
					System.err.println("### ERR: No esta definido el comando "+ browserName);
					return;
				}
				command = MessageFormat.format(command, uri);
				Runtime.getRuntime().exec(command);
			}else if(browserExec != null){
				Runtime.getRuntime().exec(MessageFormat.format(browserExec, uri));
			}
		} catch (Exception e) {
			// TODO: handle exception
		}
	}

}
