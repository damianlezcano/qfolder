package ar.com.q3s.qfolder.web;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import ar.com.q3s.qfolder.bo.FileBO;
import ar.com.q3s.qfolder.bo.HostBO;
import ar.com.q3s.qfolder.model.QFile;
import ar.com.q3s.qfolder.model.QHost;
import ar.com.q3s.qfolder.util.NetworkUtils;

@Controller
public class IndexController {

	@Autowired
	private FileBO fileBO;
	
	@Autowired
	private HostBO hostBO;	

	@RequestMapping("/")
	public ModelAndView root() throws Exception{
		return new ModelAndView("unity");
	}
	
	@RequestMapping("/split")
	public ModelAndView index() throws Exception{
		return new ModelAndView("split");
	}
	
	@RequestMapping("/unity")
	public ModelAndView unity() throws Exception{
		Map<String,Object> map = new HashMap<String,Object>();
		map.put("ip", NetworkUtils.buildUri());
		map.put("uuid", hostBO.uuid());
		return new ModelAndView("unity",map);
	}

	@ResponseBody
	@RequestMapping(value="/open", method=RequestMethod.POST)
	public QFile open(
		@RequestParam(value = "host", required = true)String host,
		@RequestParam(value = "filename", required = true)String filename) throws Exception {
		return fileBO.open(host,filename);
	}
	
	@ResponseBody
	@RequestMapping(value="/host/all", method=RequestMethod.GET,produces = {MediaType.APPLICATION_JSON_VALUE})
	public List<String> getAll() throws Exception{
		return hostBO.getAll();
	}

	@ResponseBody
	@RequestMapping(value="/host/put", method=RequestMethod.POST)
	public void addHost(@RequestParam(value = "name", required = true) String name) throws Exception {
		hostBO.add(name);
	}
	
	//------------------------------------


	public FileBO getFileBO() {
		return fileBO;
	}

	public void setFileBO(FileBO fileBO) {
		this.fileBO = fileBO;
	}
	
	public HostBO getHostBO() {
		return hostBO;
	}

	public void setHostBO(HostBO hostBO) {
		this.hostBO = hostBO;
	}
}