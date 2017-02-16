package ar.com.q3s.qfolder.exception;

public class WebServiceException extends Exception {

	private static final long serialVersionUID = 3344053745680523059L;

	public WebServiceException(String message){
		super(message);
	}
	
	public WebServiceException(String message, Throwable cause){
		super(message,cause);
	}
}
