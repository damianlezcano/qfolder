package ar.com.q3s.qfolder.web;

import org.eclipse.jetty.util.component.LifeCycle;

public class JettyStartupListener implements org.eclipse.jetty.util.component.LifeCycle.Listener {

	private Command delegate = new BrowserCommandBean();
	
	@Override
	public void lifeCycleStarting(LifeCycle event) {}

	@Override
	public void lifeCycleStarted(LifeCycle event) {
        delegate.invoke();
	}

	@Override
	public void lifeCycleFailure(LifeCycle event, Throwable cause) {}

	@Override
	public void lifeCycleStopping(LifeCycle event) {}

	@Override
	public void lifeCycleStopped(LifeCycle event) {}

}
