package ar.com.q3s.qfolder.util;

import java.io.IOException;

import org.apache.commons.exec.CommandLine;
import org.apache.commons.exec.DefaultExecutor;
import org.apache.commons.exec.ExecuteException;

public class ExecutorUtils {

	public static int exec(String command) throws ExecuteException, IOException{
		CommandLine cmdLine = CommandLine.parse(command);
		DefaultExecutor executor = new DefaultExecutor();
		return executor.execute(cmdLine);
	}
}
