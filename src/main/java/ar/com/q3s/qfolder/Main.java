package ar.com.q3s.qfolder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.nio.SelectChannelConnector;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.util.thread.ThreadPool;
import org.eclipse.jetty.webapp.WebAppContext;

import ar.com.q3s.qfolder.util.PropertyUtils;
import ar.com.q3s.qfolder.web.JettyStartupListener;

public class Main {

	private static final String CLASS_ONLY_AVAILABLE_IN_IDE = "com.sjl.IDE";

	private static final String WEB_XML = "META-INF/webapp/WEB-INF/web.xml";
	private static final String PROJECT_RELATIVE_PATH_TO_WEBAPP = "src/main/webapp";
	
	private static final String HELP_FILE = "help.txt";

	private Server server;
	
	private Integer port;
	private String bindInterface;
	private Integer threadMax;
	
	public Main() 
	{
		this.port = Integer.valueOf(PropertyUtils.getPort());
		this.bindInterface = PropertyUtils.getBindAddress();
		this.threadMax = Integer.valueOf(PropertyUtils.getThreadPoolNumber());
	}

	public void start() throws Exception {
		server = new Server();
		server.setThreadPool(createThreadPool());
		server.addConnector(createConnector());
		server.addLifeCycleListener(new JettyStartupListener());
		server.setHandler(createHandlers());
		server.setStopAtShutdown(true);
		server.start();
		server.join();
	}

	public void stop() throws Exception {
		server.stop();
	}

	//----------------------------------------------------------
	
	private ThreadPool createThreadPool() {
		QueuedThreadPool threadPool = new QueuedThreadPool();
		threadPool.setMinThreads(10);
		threadPool.setMaxThreads(threadMax);
		return threadPool;
	}

	private SelectChannelConnector createConnector() {
		SelectChannelConnector connector = new SelectChannelConnector();
		connector.setPort(port);
		connector.setHost(bindInterface);
		return connector;
	}

	private HandlerCollection createHandlers() {
		WebAppContext ctx = new WebAppContext();
		ctx.setContextPath("/");
		if (isRunningInShadedJar()) {
			ctx.setWar(getShadedWarUrl());
		} else {
			ctx.setWar(PROJECT_RELATIVE_PATH_TO_WEBAPP);
		}
		List<Handler> handlers = new ArrayList<Handler>();
		handlers.add(ctx);
		HandlerList contexts = new HandlerList();
		contexts.setHandlers(handlers.toArray(new Handler[0]));
		RequestLogHandler _log = new RequestLogHandler();
		HandlerCollection result = new HandlerCollection();
		result.setHandlers(new Handler[] { contexts, _log });
		return result;
	}

	private URL getResource(String aResource) {
		return Thread.currentThread().getContextClassLoader().getResource(aResource);
	}

	private String getShadedWarUrl() {
		String urlStr = getResource(WEB_XML).toString();
		return urlStr.substring(0, urlStr.length() - 15);
	}

	private boolean isRunningInShadedJar() {
		try {
			Class.forName(CLASS_ONLY_AVAILABLE_IN_IDE);
			return false;
		} catch (ClassNotFoundException anExc) {
			return true;
		}
	}

	//--------------------------------------------------------------
	
	public static void main(String[] args) throws Exception 
	{
		if(find(args, "start")){
			Main main = new Main();
			main.start();
		}else if(find(args, "help")){
			printHelpFile();
		}else{
			printGetStarted();
		}
	}

	private static void printGetStarted() 
	{
		System.out.println("\nEjemplo de uso ->");
		System.out.println("\n   java -Dapp.name=Juan -Dapp.rest.port=9999 -jar qfolder.jar start");
		System.out.println("\nMas informacion ->");
		System.out.println("\n   java -jar qfolder.jar help\n\n");
	}

	private static boolean find(String[] args, String ... options) 
	{
		for (String arg : args) {
			for(String opt : options){
				if(arg.equals(opt)){
					return true;
				}
			}
		}
		return false;
	}
	
	public static void printHelpFile() throws IOException
	{
		InputStream in = Main.class.getClassLoader().getResourceAsStream(HELP_FILE);
	    BufferedReader reader = new BufferedReader(new InputStreamReader(in));
	    StringBuilder out = new StringBuilder();
	    String newLine = System.getProperty("line.separator");
	    String line;
	    while ((line = reader.readLine()) != null) {
	        out.append(line);
	        out.append(newLine);
	    }
	    System.out.println(out.toString());
	}

}