package org.mitre.synthea.webservice;

import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.File;
import java.nio.file.Files;
import java.util.Queue;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

@RestController
public class Controller {

	private final static Logger LOGGER = LoggerFactory.getLogger(Controller.class);

	@Autowired
	private RequestService requestService;
	
	/*******************
     * RESTful interface
     ******************/
	
    /**
     * POST endpoint that submits request to generate results based on specified VA Synthea configuration paramaeters (JSON).
     * If the request was successfully submitted, returns status code 200 and a UUID that can be used to refer to request in other endpoints.
     * Returns status code 400 if there was a problem processing the configuration parameters.
     */
    @PostMapping(value = "/generate", consumes = APPLICATION_JSON_VALUE)
	public ResponseEntity<String> generateResults(HttpServletRequest httpServletRequest, HttpEntity<String> httpEntity) {
    	try {
    		
    		// Create and start request
    		Request request = requestService.createRequest(httpEntity.getBody());
    		request.start();
    		return new ResponseEntity<String>(request.getUuid(), HttpStatus.OK);
    	} catch(JSONException jex) {
    		return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    	}
	}
	
    /**
     * GET endpoint that retrieves results (as a ZIP file) associated with specified UUID (that was originally returned by the associated generate request).
     * Returns a ZIP file or a status code:
     * - 202 if results are still pending
     * - 400 if UUID path variable is missing or malformed
     * - 404 if request was not found (either the request is complete and results have been retrieved, or the request never existed)
     * - 500 if error was encountered while returning results
     */
    @GetMapping(value = "/zip/{uuid}")
	public HttpEntity<?> getResultsZip(@PathVariable String uuid) {
    	
    	if (uuid == null) {
    		return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    	}
    	
    	Request request = requestService.getRequest(uuid);
    	
    	// Check for ZIP file
		File zipFile = requestService.getZipFile(uuid);
		
		// A null zipFile indicates a malformed UUID
		if (zipFile == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		
		if (!zipFile.exists()) {
			
			LOGGER.info("Results file " + zipFile.toString() + " not found");
			
			// If the ZIP file does not exist, see if request is pending or not found.
			if (request != null) {
				
				// Report that request was found but results are not ready yet
				return new ResponseEntity<>(HttpStatus.ACCEPTED);
			} else {
				
				// Report that request was not found
				return new ResponseEntity<>(HttpStatus.NOT_FOUND);
			}
		}
		
		if (request != null && request.isFinished() && request.getResultQueue().size() == 0) {
			
			// Request is finished and all results are delivered
			requestService.removeRequest(uuid);
		}
		
		// Return the ZIP file and delete local copy
		try {
	    	byte[] zipContents = Files.readAllBytes(zipFile.toPath());
	    	// NOTE: Uncomment this to remove ZIPs after retrieval
	    	//zipFile.delete();
	    	
	        HttpHeaders header = new HttpHeaders();
	        header.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipFile.getName() + "\"");
	        header.set(HttpHeaders.CONTENT_TYPE, "application/zip");
	        header.setContentLength(zipContents.length);
	        
	        return new HttpEntity<byte[]>(zipContents, header);
		} catch(Exception ex) {
			LOGGER.error("Error while retrieving file " + zipFile, ex);
			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
		}
	}
    
    /**
     * GET endpoint that retrieves results (as a JSON array) associated with specified UUID (that was originally returned by the associated generate request).
     * Returns a JSON array with available results or a status code:
     * - 400 if UUID path variable is missing
     * - 404 if request was not found (either the request is complete and results have been retrieved, or the request never existed)
     */
    @GetMapping(value = "/json/{uuid}")
	public HttpEntity<?> getResultsJson(@PathVariable String uuid) {
    	
    	if (uuid == null) {
    		return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    	}
    	
    	Request request = requestService.getRequest(uuid);
    	if (request == null) {
    		return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    	}
    	
    	Queue<String> resultQueue = request.getResultQueue();
		synchronized(resultQueue) {
			if (resultQueue.size() == 0) {
				return new ResponseEntity<String>("[]", HttpStatus.OK);
			}
			
			LOGGER.info("Current size of result set for request " + uuid + ": " + resultQueue.size());
			
    		// Return available results as JSON array
			int idx = 0;
			StringBuilder builder = new StringBuilder("[");
			while(resultQueue.size() > 0) {
				String person = resultQueue.remove();
				if (idx > 0) {
					builder.append(",");
				}

				builder.append("\n").append(person);
				++idx;
			}
			
			builder.append("\n]");

			// Remove results that are being returned from result queue
			resultQueue.clear();
			
			if (request.isFinished() && resultQueue.size() == 0) {
				
				// Request is finished and all results are delivered
				requestService.removeRequest(uuid);
			}
			
			return new ResponseEntity<String>(builder.toString(), HttpStatus.OK);
		}		
	}
    
    /**
     * DELETE endpoint that attempts to terminate a pending request associated with specified UUID (that was originally returned by the associated generate request).
     * Returns a status code:
     * - 200 if request was found and associated threads were interrupted, though the request processing may not terminate immediately
     * - 400 if UUID path variable is missing
     * - 404 request was not found
     */
    @DeleteMapping(value = "/terminate/{uuid}")
	public HttpEntity<?> deleteRequest(@PathVariable String uuid) {
    	
    	if (uuid == null) {
    		return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    	}
    	
    	Request request = requestService.getRequest(uuid);
    	if (request == null) {
    		return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    	}
    	
    	request.stop();
		
    	return new ResponseEntity<>(HttpStatus.OK);
	}
    
    
    /*********************
     * WebSocket interface
     ********************/
    
    /**
     * WebSocket endpoint for configuring a generation request based on the specified JSON configuration string.
     * Response includes the UUID and the specified configuration (including generation seed).
     */
    @MessageMapping("/configure")
    @SendToUser("/reply/configure")
    public String webSocketConfig(String configurationStr) {
    	try {
    		Request request = requestService.createRequest(configurationStr);
    		return "{ \"status\": \"Configured\", \"uuid\": \"" + request.getUuid() +"\", \"configuration\": " + request.getConfiguration().toString() + " }";
    	} catch(JSONException jex) {
    		return "{ \"error\": \"Could not process specified configuration\" }";
    	}
    }
    
    /**
     * WebSocket endpoint for starting/resuming the generation request associated with the specified UUID.
     */
    @MessageMapping("/start")
    @SendToUser("/reply/start")
    public String webSocketStart(String uuid) {
    	
    	if (uuid == null) {
    		return "{ \"error\": \"UUID required\" }";
    	}
    	
    	Request request = requestService.getRequest(uuid);
    	if (request == null) {
    		return "{ \"error\": \"Request not found (may be finished)\" }";
    	}
    	
    	if (request.isFinished()) {
    		return "{ \"error\": \"Request has finished\" }";
    	}
    	
    	if (!request.isStarted()) {
    		
    		// Request has not started yet
    		request.start();
    		return "{ \"status\": \"Started\" }";
    	}
    	
    	if (request.isPaused()) {
    		request.resume();
    		return "{ \"status\": \"Resumed\" }";
    	} else {
    		return "{ \"status\": \"Already running\" }";
    	}
    }
    
    /**
     * WebSocket endpoint for pausing the generation request associated with the specified UUID.
     */
    @MessageMapping("/pause")
    @SendToUser("/reply/pause")
    public String webSocketPause(String uuid) {
    	
    	if (uuid == null) {
    		return "{ \"error\": \"UUID required\" }";
    	}
    	
    	Request request = requestService.getRequest(uuid);
    	if (request == null) {
    		return "{ \"error\": \"Request not found (may be finished)\" }";
    	}
    	
    	if (!request.isStarted()) {
    		return "{ \"error\": \"Request has not started yet\" }";
    	}
    	
    	if (request.isFinished()) {
    		return "{ \"error\": \"Request has finished\" }";
    	}
    	
    	if (request.isPaused()) {
    		return "{ \"status\": \"Already paused\" }";
    	} else {
    		request.pause();
    		return "{ \"status\": \"Paused\" }";
    	}
	}
    
    /**
     * WebSocket endpoint for ending the generation request associated with the specified UUID.
     */
    @MessageMapping("/stop")
    @SendToUser("/reply/stop")
    public String webSocketStop(String uuid) {
    	
    	if (uuid == null) {
    		return "{ \"error\": \"UUID required\" }";
    	}
    	
    	Request request = requestService.getRequest(uuid);
    	if (request == null) {
    		return "{ \"error\": \"Request not found (may be finished)\" }";
    	}
    	
    	if (!request.isStarted()) {
    		return "{ \"error\": \"Request has not started yet\" }";
    	}
    	
    	if (request.isFinished()) {
    		return "{ \"error\": \"Request has finished\" }";
    	}
    	
    	if (request.isStopped()) {
    		return "{ \"status\": \"Already stopped\" }";
    	} else {
    		request.stop();
    		return "{ \"status\": \"Stopped\" }";
    	}
	}
}
