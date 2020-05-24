package org.q3s.p2p.client.exec;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.util.Date;

public class LinuxExecutorBean implements Executor {

	private final static String COMMAND_DEFAULT_APPLICATION = "cat /usr/share/applications/%s | grep Exec";
	private final static String COMMAND_MIME_APPLICATION = "xdg-mime query default %s";
	private final static String COMMAND_MIME_TYPE = "xdg-mime query filetype %s";
	private final static String COMMAND_DEFAULT_MIME_APPLICATION = "xdg-mime query default text/plain";
	
	public void open(String fullname) throws InterruptedException{
		String mimeType = getMimeType(fullname);

		String mimeApplication = getMimeApplication(mimeType);
		
		String defaultApplication = getDefaultApplication(mimeApplication);
		
		System.out.println("## Archivo abierto! " + new Date());
		
		openFileAndLock(defaultApplication,fullname);

		System.out.println("## Archivo cerrado! " + new Date());		
	}
	
	private void openFileAndLock(String defaultApplication, String fullname) throws InterruptedException {
		String commandOpenFile = String.format("%s %s", defaultApplication, fullname);
		
		executeCommand(commandOpenFile);
		
		String filename = fullname.substring(fullname.lastIndexOf(File.separator)+1);
		String commandPs = String.format("ps -aux", filename);
				
		boolean exist = false;
		do {
			
			String result = executeCommand(commandPs);
			exist = contains(result,defaultApplication,filename);
			Thread.sleep(200);
			
		} while (exist);
		
	}

	private boolean contains(String result, String defaultApplication, String filename) {
		for(String line : result.split("\n")){
			if(line.indexOf(defaultApplication) != -1 && line.indexOf(filename) != -1){
				return true;
			}
		}
		return false;
	}

	private String getDefaultApplication(String defaultApplication) {
		final String field = "\nExec=";
		String command = String.format(COMMAND_DEFAULT_APPLICATION, defaultApplication);
		String shellResult = executeCommand(command);
		
		int start = shellResult.indexOf(field) + field.length();
		int end = shellResult.indexOf(" ", start);
				
		return shellResult.substring(start,end);
	}

	private String getMimeApplication(String mimeType) {
		String command = String.format(COMMAND_MIME_APPLICATION, mimeType);
		String result = executeCommand(command);
		if(result.isEmpty()){
			command = COMMAND_DEFAULT_MIME_APPLICATION;
			result = executeCommand(command);
		}else{
			int fis = result.lastIndexOf(File.separator);
			if(fis != -1){
				result = result.substring(fis+1);
			}
		}
		return result;
	}

	private String getMimeType(String filename){
		String command = String.format(COMMAND_MIME_TYPE, filename);
		return executeCommand(command);
	}

	private String executeCommand(String command) {
		StringBuffer output = new StringBuffer();
		Process p;
		try {
			p = Runtime.getRuntime().exec(command);
			p.waitFor();
			BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line = "";
			while ((line = reader.readLine())!= null) {
				output.append(line + "\n");
			}

		} catch (Exception e) {
			e.printStackTrace();
		}

		return output.toString();

	}

}