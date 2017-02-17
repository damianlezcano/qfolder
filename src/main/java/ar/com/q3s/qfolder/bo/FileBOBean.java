package ar.com.q3s.qfolder.bo;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.GenericEntity;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.jboss.resteasy.client.jaxrs.ResteasyClient;
import org.jboss.resteasy.client.jaxrs.ResteasyClientBuilder;
import org.jboss.resteasy.client.jaxrs.ResteasyWebTarget;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataOutput;
import org.jboss.resteasy.specimpl.MultivaluedMapImpl;

import ar.com.q3s.qfolder.dao.FileDAO;
import ar.com.q3s.qfolder.dao.LockDAO;
import ar.com.q3s.qfolder.exception.CommandNotFoundException;
import ar.com.q3s.qfolder.model.QFile;
import ar.com.q3s.qfolder.model.QLock;
import ar.com.q3s.qfolder.util.ExecutorUtils;
import ar.com.q3s.qfolder.util.PropertyUtils;

public class FileBOBean implements FileBO {

	private FileDAO dao;
	private LockDAO lockDAO;
	
	@Override
	public Set<QFile> getAll() throws Exception {
		Set<QFile> list = new TreeSet<QFile>();
		for (File file : dao.getAll()) {
			QFile qfile = new QFile();
			qfile.setName(file.getName());
			qfile.setSize(file.length());
			qfile.setLastModified(new Date(file.lastModified()));
			qfile.setLock(lockDAO.get(file.getName()));
			list.add(qfile);
		}
		return list;
	}

	@Override
	public File get(String name) throws Exception {
		if(lockDAO.isLock(name)){
			QLock l = lockDAO.get(name);
			throw new Exception("El archivo ya esta siendo usado por " + l.getUser() + " a las " + l.getDateAsString());
		}
		QLock lock = new QLock();
		lock.setUser(PropertyUtils.getName());
		lock.setDate(new Date());
		lockDAO.put(name, lock);
		return dao.get(name);
	}
	
	@Override
	public void write(String fileName, InputStream inputStream) throws Exception{
		dao.write(fileName, inputStream);
		lockDAO.remove(fileName);
		notifyDesktop(fileName);
	}

	@Override
	public QFile open(String host, String filename) throws Exception {
		try {
			String url = host + "/api/file/get/" + filename;
			File temp = new File(PropertyUtils.getTempPath(filename));
			// --------------------
			Client client =  new ResteasyClientBuilder()
		    .establishConnectionTimeout(2, TimeUnit.SECONDS)
		    .socketTimeout(2, TimeUnit.SECONDS)
		    .build();
			
			WebTarget target = client.target(url);
			Response response = target.request().get();
			
			if(response.getStatus() != Response.Status.OK.getStatusCode()){
				throw new Exception(response.readEntity(String.class));
			}
			
			File remoteFile = response.readEntity(File.class);
			response.close(); // You should close connections!
			File dest = new File(temp.toURI().toURL().getPath());
			copyFileUsingStream(remoteFile, dest);
			open(dest);
			put(host,filename, dest);
			
			QFile qfile = new QFile();
			qfile.setName(filename);
			qfile.setSize(dest.length());
			qfile.setLastModified(new Date(dest.lastModified()));
			return qfile;
		} catch (Exception e) {
			throw e;
		}
	}

	private void put(String host,String filename,File file) {
		ResteasyClient client = new ResteasyClientBuilder().build();
	    ResteasyWebTarget target = client.target(host + "/api/file/put");
	    MultivaluedMap<String,Object> mm=new MultivaluedMapImpl<String,Object>();
	    List<Object> contDis=new ArrayList<Object>();
	    contDis.add("form-data; name=\"uploadedFile\"; filename=\""+filename+"\"");
	    contDis.add(file);
	    mm.put("Content-Disposition",contDis);
	    MultipartFormDataOutput mdo = new MultipartFormDataOutput();
	    mdo.addFormData("uploadedFile", file, MediaType.APPLICATION_OCTET_STREAM_TYPE);
	    mdo.getFormData().get("uploadedFile").getHeaders().put("Content-Disposition",contDis);
	    GenericEntity<MultipartFormDataOutput> entity = new GenericEntity<MultipartFormDataOutput>(mdo) {};
	    target.request().header("Authorization", "Basic test123").post( Entity.entity(entity, MediaType.MULTIPART_FORM_DATA_TYPE));
	}
	
	private void open(File file) throws Exception{
		String command = PropertyUtils.getShellFileExec();
		if(command == null) throw new CommandNotFoundException("No existe el comando para este sistema operativo: " + PropertyUtils.getSystemName());
		//--------------------------------------------------------
        ExecutorUtils.exec(command.trim() + " " + file);
	}
	
	private void copyFileUsingStream(File source, File dest) throws IOException {
		InputStream is = null;
		OutputStream os = null;
		try {
			is = new FileInputStream(source);
			os = new FileOutputStream(dest);
			byte[] buffer = new byte[1024];
			int length;
			while ((length = is.read(buffer)) > 0) {
				os.write(buffer, 0, length);
			}
		} finally {
			is.close();
			os.close();
		}
	}
	
	private void notifyDesktop(String filename) {
		try {
			String comm1 = PropertyUtils.getShellNotifyExec();
			String comm2 = MessageFormat.format(comm1, "Actualizacion", filename);
			Runtime.getRuntime().exec(comm2);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public void depuration(){
		lockDAO.depuration();
	}
	
	@Override
	public int size() {
		return dao.size();
	}
	
	public FileDAO getDao() {
		return dao;
	}

	public void setDao(FileDAO dao) {
		this.dao = dao;
	}

	public LockDAO getLockDAO() {
		return lockDAO;
	}

	public void setLockDAO(LockDAO lockDAO) {
		this.lockDAO = lockDAO;
	}

}