package ar.com.q3s.qfolder.exception;

public class DataAccessException extends Exception {

	private static final long serialVersionUID = 1L;
	
	public DataAccessException(String message){
		super(message);
	}
	
	public DataAccessException(String message, Throwable cause){
		super(message,cause);
	}
}
