
function loadPage(){
	$.get("/host/all", function(data) {
	    var header = [];
	    for (var key in data) {
	    	var localHost = $("#ip").text();
	    	var remoteHost = data[key];
	    	//procesar host
	    	var status = sync(processHost,[remoteHost])
	    	if(status != ""){
		    	//registrar los archivos
		    	sync(registrarHost,[remoteHost,localHost])
		    	//asignar texto a encabezado
		    	header.push(status.username);
	    	}
	    }
    	$("a.link-panel-header").text("Todo (" + header.length + ") -> " + header)
	});	
}

function processHost(host){
	var status = sync(getStatusHost,[host])
	if(status != ""){
		sync(processFiles,[host,status]);
	}
	return status;
}

function processFiles(host,status){
	$.get(host + "/api/file/all", function(data) {
	    for (var key in data) {
	    	var realhost = host == "" ? $("#ip").text() : host;
	    	var filename = data[key].name;
	    	var extension = filename.split(".")[1];
	    	var size = data[key].size;
	    	//---------------------------------
	    	var item = sync(createItem,[realhost,filename,extension,size,status.username,status.hostname,status.os]);
	        $("#elements").append(item);
	    }
	});
}

function getStatusHost(host){
	var str = "";
	$.get(host + "/api/status", function(data) {
		str = data
	});
	return "" == str ? "" : JSON.parse(str);
}

function registrarHost(source,destination){
	if(source != ""){
		$.get(source + "/api/host/all", function(data) {
			var eq = false;
		    for (var key in data) {
		    	var remoteHost = data[key];
		    	if(remoteHost != ""){
			    	//-------------------------
			    	var remoteUuid = sync(getUuid,[remoteHost]);
			    	var localUuid = $("#uuid").text();
			    	//-------------------------

			    	if(localUuid == remoteUuid){
			    		eq = true;
			    		break;
			    	}
		    	}
		    }

		    if(!eq){
				$.get( source + "/api/host/put", { name: destination } );
		    }
		});	
	}
}

function getUuid(hostname){
	var str = "";
	$.get(hostname + "/api/uuid", function(data) {
		str = data
	});
	return str;
}

function openfile(target){
	var hostname = $(target).attr("host");
	var filename = $(target).text();
	//-------------------------------------
	$.post("/open", {host: hostname, filename: filename}, function(file) {
		// $.ajaxSetup({async:false});
		// $("#"+filekey)[0].outerHTML=createFileLI(filekey,hostip,file.name,file.sizeAsString,file.lastModifiedAsString);
		// $.ajaxSetup({async:true});
	})
	.fail(function(data) {
		alert(data.statusText);
		//window.location.href=host+"/api/get/"+filename;
	});	
}

function createItem(host,filename,extension,size,username,hostname,os){
	var str = "";
	$.get("/view/components/item.ht", function(contents) {
		str = contents
		.replace(/\${host}/g,host)
		.replace(/\${filename}/g,filename)
		.replace(/\${extension}/g,extension)
		.replace(/\${size}/g,size)
		.replace(/\${username}/g,username)
		.replace(/\${hostname}/g,hostname)
		.replace(/\${os}/g,os)
	});
	return str;
}

function sync(fuct, params){
	var result;
	$.ajaxSetup({async:false});
	result = fuct.apply(this,params);
	$.ajaxSetup({async:true});
	return result;
}