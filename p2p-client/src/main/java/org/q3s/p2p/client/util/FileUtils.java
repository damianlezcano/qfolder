package org.q3s.p2p.client.util;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

import javax.xml.bind.DatatypeConverter;

import org.q3s.p2p.client.Config;
import org.q3s.p2p.model.QFile;

public class FileUtils {
	
    public static List<QFile> files(String path) {
        List<QFile> res = new ArrayList<QFile>();
        File folder = new File(path);
        File[] files = folder.listFiles();
        for (File file : files) {
            if(file.isFile() && !file.isHidden()){
                QFile f = new QFile();
                f.setName(file.getName());
                f.setDate(file.lastModified());
                f.setSize(file.length());
                res.add(f);
            }
        }
        return res;
    }
    
    public static int anyPending(String path) {
        File folder = new File(path);
        File[] files = folder.listFiles();
        for (File file : files) {
            if(file.isFile() && !file.isHidden()){
                if(file.getName().endsWith(Config.SUFFIX_PENDING)) {
                	return Integer.valueOf(file.getName().split("\\.")[0]);
                }
            }
        }
        return -1;
    }
    
    //------------------------------------------------------------
    
	public static String encoder(String filePath) {
		String base64File = "";
		File file = new File(filePath);
		try (FileInputStream imageInFile = new FileInputStream(file)) {
			// Reading a file from file system
			byte fileData[] = new byte[(int) file.length()];
			imageInFile.read(fileData);
			base64File = Base64.getEncoder().encodeToString(fileData);
		} catch (FileNotFoundException e) {
			System.out.println("File not found" + e);
		} catch (IOException ioe) {
			System.out.println("Exception while reading the file " + ioe);
		}
		return base64File;
	}

	public static Path saveInTempDirectory(String filename, String content) throws IOException {
		File file = File.createTempFile("qfolder", null);
		System.out.println(file.getAbsolutePath());
		return Files.write(Paths.get(file.getAbsolutePath()), content.getBytes());
	}
	
	//---------------------------------
	
	public static byte[] decode(String filename) {
		String str = read(filename);
		return Base64.getDecoder().decode(str);
	}

	public static String read(String filename) {
	    String content = "";
	    try
	    {
	        content = new String ( Files.readAllBytes( Paths.get(filename) ) );
	    } 
	    catch (IOException e) 
	    {
	        e.printStackTrace();
	    }
	    return content;
	}
	
	public static void save(String filename, byte[] content) throws IOException {
		File file = new File(filename);
		if(file.exists()) {
			Files.write(Paths.get(filename), content, StandardOpenOption.APPEND);
		}else{
			Files.write(Paths.get(filename), content);			
		}
	}
	
	public static void remove(String filename) throws IOException {
		new File(filename).delete();
	}
	
	public static void move(String from, String to) throws Exception {
        Path temp = Files.move(Paths.get(from),Paths.get(to)); 
        if(temp != null) { 
            System.out.println("File renamed and moved successfully"); 
        } else { 
            System.out.println("Failed to move the file"); 
        } 
	}
	
	public static String md5(String file) {
		try {
		    MessageDigest md = MessageDigest.getInstance("MD5");
		    md.update(Files.readAllBytes(Paths.get(file)));
		    byte[] digest = md.digest();
		    return DatatypeConverter.printHexBinary(digest).toUpperCase();			
		} catch (Exception e) {
			return "";
		}
	}

	
	//-----------------------------------
	
	
	/**
	 * Split a file into multiples files.
	 *
	 * @param fileName   Name of file to be split.
	 * @param mBperSplit maximum number of MB per file.
	 * @throws IOException
	 */
	public static int splitFile(final String fileName, final int mBperSplit, String destination) throws IOException {

	    if (mBperSplit <= 0) {
	        throw new IllegalArgumentException("mBperSplit must be more than zero");
	    }

	    List<Path> partFiles = new ArrayList<>();
	    final long sourceSize = Files.size(Paths.get(fileName));
	    final long bytesPerSplit = 1024L * mBperSplit;
	    final long numSplits = sourceSize / bytesPerSplit;
	    final long remainingBytes = sourceSize % bytesPerSplit;
	    int position = 0;

	    try (RandomAccessFile sourceFile = new RandomAccessFile(fileName, "r");
	         FileChannel sourceChannel = sourceFile.getChannel()) {

	        for (; position < numSplits; position++) {
	            //write multipart files.
	            writePartToFile(destination, bytesPerSplit, position, bytesPerSplit, sourceChannel, partFiles);
	        }

	        if (remainingBytes > 0) {
	            writePartToFile(destination, remainingBytes, position, bytesPerSplit, sourceChannel, partFiles);
	        }
	    }
	    return partFiles.size();
	}

	private static void writePartToFile(String destination, long byteSize, long position, long bytesPerSplit, FileChannel sourceChannel, List<Path> partFiles) throws IOException {
	    Path fileName = Paths.get(destination + File.separator + position + Config.SUFFIX_PART);
	    try (RandomAccessFile toFile = new RandomAccessFile(fileName.toFile(), "rw");
	         FileChannel toChannel = toFile.getChannel()) {
	        sourceChannel.position(position * bytesPerSplit);
	        toChannel.transferFrom(sourceChannel, 0, byteSize);
	    }
	    partFiles.add(fileName);
	}
	
}
