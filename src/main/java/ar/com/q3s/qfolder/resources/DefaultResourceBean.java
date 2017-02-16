package ar.com.q3s.qfolder.resources;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;

import org.jboss.resteasy.plugins.providers.multipart.InputPart;
import org.jboss.resteasy.plugins.providers.multipart.MultipartFormDataInput;
import org.json.simple.JSONObject;

import ar.com.q3s.qfolder.bo.FileBO;
import ar.com.q3s.qfolder.bo.HostBO;
import ar.com.q3s.qfolder.model.QFile;
import ar.com.q3s.qfolder.util.PropertyUtils;
 
@Path("/api")
public class DefaultResourceBean {
 
	private static FileBO fileBO;
	private static HostBO hostBO;

	@GET
    @Path("/status")
    @Produces(MediaType.TEXT_PLAIN)
	@SuppressWarnings("unchecked")
    public Response status() {
    	JSONObject obj = new JSONObject();
    	try {
    		obj.put("username", PropertyUtils.getProperty("app.name"));
    		obj.put("hostname", InetAddress.getLocalHost().getHostName());
    		obj.put("os", PropertyUtils.getSystemName());
    		obj.put("size", fileBO.size());
		} catch (Exception e) {
			Response.serverError().entity(e.getMessage()).build();
		}
    	return Response.ok(obj.toJSONString()).build();
    }

	@GET
    @Path("/uuid")
    @Produces(MediaType.TEXT_PLAIN)
    public Response getCurrent() throws Exception {
    	return Response.ok(hostBO.uuid()).build();
    }
	
	@GET
    @Path("/file/all")
    @Produces(MediaType.APPLICATION_JSON)
    public Response all() {
    	try {
    		Set<QFile> files = fileBO.getAll();
    		return Response.ok(files).build();
		} catch (Exception e) {
			return Response.serverError().entity(e.getMessage()).build();
		}
    }	
	
	@GET
	@Path("/file/get/{filename}")
	@Produces(MediaType.APPLICATION_OCTET_STREAM)
	public Response get(@PathParam("filename") String filename) {
		FileInputStream inputStream = null;
		try {
			File downloadFile = fileBO.get(filename);
			inputStream = new FileInputStream(downloadFile);
			inputStream.close();
			return Response.ok(downloadFile).header("Content-Disposition", "attachment; filename=\""+filename+"\"").build();
		} catch (Exception e) {
			try {
				if (null != inputStream)
					inputStream.close();
			} catch (IOException e2) {
				return Response.serverError().entity(e2.getMessage()).build();
			}
			return Response.serverError().entity(e.getMessage()).build();
		}
	}
    
	@POST
	@Path("/file/put")
	@Consumes("multipart/form-data")
	public Response put(MultipartFormDataInput input) {
		Map<String, List<InputPart>> uploadForm = input.getFormDataMap();
		List<InputPart> inputParts = uploadForm.get("uploadedFile");
		for (InputPart inputPart : inputParts) {
			try {
				MultivaluedMap<String, String> header = inputPart.getHeaders();
				String fileName = getFileName(header);
				// convert the uploaded file to inputstream
				InputStream inputStream = inputPart.getBody(InputStream.class,null);
				fileBO.write(fileName, inputStream);
			} catch (Exception e) {
				return Response.serverError().entity(e.getMessage()).build();
			}
		}
		
		return Response.ok().build();
	}
	
	@GET
	@Path("/host/put")
	@Consumes(MediaType.APPLICATION_JSON)
	public Response getAllHosts(@QueryParam("name") String name) throws Exception {
		hostBO.add(name);
		return Response.ok().build();
//        ResteasyClient client = new ResteasyClientBuilder().build();
//        ResteasyWebTarget target = client.target(name + "/api/uuid");
//        
//		String remoteId = target.request().get(String.class);
//		String localId = hostBO.uuid();
//		
//		if(localId.equals(remoteId)){
//			return Response.serverError().build();
//		}else{
//			hostBO.add(name);
//			return Response.ok().build();			
//		}
        
	}
	
	@GET
	@Path("/host/all")
	@Produces(MediaType.APPLICATION_JSON)
	public Response getAllHosts() throws Exception {
		List<String> list = hostBO.getAll();
		return Response.ok(list).build();
	}
	
	//-----------------------------------------------------------------------------------

	private String getFileName(MultivaluedMap<String, String> header) {
		String[] contentDisposition = header.getFirst("Content-Disposition").split(";");
		for (String filename : contentDisposition) {
			if (filename.trim().startsWith("filename")) {
				String[] name = filename.split("=");
				String finalFileName = name[1].trim().replaceAll("\"", "");
				return finalFileName;
			}
		}
		return "unknown";
	}
 
	public FileBO getFileBO() {
		return fileBO;
	}

	public void setFileBO(FileBO fileBO) {
		DefaultResourceBean.fileBO = fileBO;
	}

	public HostBO getHostBO() {
		return hostBO;
	}

	public void setHostBO(HostBO hostBO) {
		DefaultResourceBean.hostBO = hostBO;
	}
	
}