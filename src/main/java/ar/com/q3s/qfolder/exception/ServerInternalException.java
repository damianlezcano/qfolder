package ar.com.q3s.qfolder.exception;

public class ServerInternalException extends Exception {

	private static final long serialVersionUID = 1L;
	
	public ServerInternalException(String message){
		super(message);
	}
	
	public ServerInternalException(String message, Throwable cause){
		super(message,cause);
	}
}
