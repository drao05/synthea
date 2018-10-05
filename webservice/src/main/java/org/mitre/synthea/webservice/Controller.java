package org.mitre.synthea.webservice;

import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.mitre.synthea.helpers.Config;

@RestController
public class Controller {

	private final static Logger LOGGER = LoggerFactory.getLogger(Controller.class);

	// Maps UUID to a request object
	private final Map<String, Request> uuidRequestMap = new ConcurrentHashMap<String, Request>();
	
	@Autowired
	// Messaging template used for requests from WebSocket clients
	private SimpMessagingTemplate messagingTemplate;         

	// Path to ZIP output directory
	private Path zipOutputPath;
	
	// Max allowed age of ZIP files (used by scheduler task that deletes expired ZIP files)
	@Value("${zip.maxAgeSeconds:60}")
	private Integer maxZipAgeSeconds;
	
	// Subset of VA Synthea configuration properties that web service allows user to customize
	public final static Set<String> configPropertiesWhiteList = new HashSet<String>();

	public Controller() {
		
		// Initialize ZIP export directory relative to VA Synthea's base directory configuration parameter
		String baseDir = Config.get("exporter.baseDirectory");
		if (baseDir != null && !baseDir.endsWith(File.separator)) {
			baseDir += File.separator;
		} else {
			baseDir = "";
		}
		
		File outputDir = new File(baseDir + "zip");
    	zipOutputPath = Paths.get(outputDir.toURI());
		if (!outputDir.exists()) {
			outputDir.mkdirs();
		}

		LOGGER.info("ZIP output directory: " + zipOutputPath.toString());

    	// Initialize config properties white list
		InputStream whiteListStream = this.getClass().getClassLoader().getResourceAsStream("static/va-synthea-properties-whitelist.txt");
		try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(whiteListStream));
            String line;
            while ((line = reader.readLine()) != null) {
            	configPropertiesWhiteList.add(line);
        		LOGGER.info("White listed config parameter: " + line);
            }
		} catch(IOException ioex) {
			LOGGER.error("Error while initlizing config properties white list", ioex);
        } finally {
        	try {
        		whiteListStream.close();
        	} catch(IOException ioex) {
    			LOGGER.error("Error while closing config properties input stream", ioex);
            }
        }
	}
	
	/**
	 * Creates and initializes a new generation request based on the specified configuration
	 */
	private Request createRequest(String configurationStr) throws JSONException {
		
		LOGGER.info("Requested generator configuration: " + configurationStr);
		
		// Create configuration object
		JSONObject configuration = null;
		if (configurationStr != null) {
			try {
				configuration = new JSONObject(configurationStr);
			} catch(JSONException jex) {
				LOGGER.error("Error while creating JSON object from string", jex);
				return null;
			}
		}
	    	
		
		// Create the request
		Request request = new Request(this, configuration);
		uuidRequestMap.put(request.getUuid(), request);
		return request;
	}
	
	/**
	 * Stop tracking the specified UUID
	 */
	public void removeRequestFromMap(String uuid) {
		uuidRequestMap.remove(uuid);
	}
	
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
    		Request request = createRequest(httpEntity.getBody());
    		request.start();
    		return new ResponseEntity<String>(request.getUuid(), HttpStatus.OK);
    	} catch(JSONException jex) {
    		return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    	}
	}
    
    /**
     * Generates File object for a target ZIP file based on UUID
     */
    public File getZipFile(String uuid) {
    	return new File(zipOutputPath.toString() + File.separator + uuid + ".zip");
    }
	
    /**
     * GET endpoint that retrieves results (as a ZIP file) associated with specified UUID (that was originally returned by the associated generate request).
     * Returns a ZIP file or a status code:
     * - 202 if results are still pending
     * - 400 if UUID path variable is missing
     * - 404 if request was not found (either the request is complete and results have been retrieved, or the request never existed)
     * - 500 if error was encountered while returning results
     */
    @GetMapping(value = "/zip/{uuid}")
	public HttpEntity<?> getResultsZip(@PathVariable String uuid) {
    	
    	if (uuid == null) {
    		return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    	}
    	
    	Request request = uuidRequestMap.get(uuid);
    	
    	// Check for ZIP file
		File zipFile = getZipFile(uuid);
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
			uuidRequestMap.remove(uuid);
		}
		
		// Return the ZIP file and delete local copy
		try {
	    	byte[] zipContents = Files.readAllBytes(zipFile.toPath());
	    	zipFile.delete();
	    	
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
    	
    	Request request = uuidRequestMap.get(uuid);
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
				uuidRequestMap.remove(uuid);
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
    	
    	Request request = uuidRequestMap.get(uuid);
    	if (request == null) {
    		return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    	}
    	
    	request.stop();
		
    	return new ResponseEntity<>(HttpStatus.OK);
	}
    
    /**
     * Delete file if max age has been exceeded
     */
    private void checkForExpiredFile(Path path, int maxAgeSeconds) {
    	try {
	    	BasicFileAttributes attributes = Files.readAttributes(path, BasicFileAttributes.class);
	        Date now = new Date();
	        if (now.getTime() - attributes.creationTime().toMillis() >= maxAgeSeconds * 1000) {
	        	Files.delete(path);
	        	LOGGER.info("Deleted expired file: " + path.getFileName());
	        }
    	} catch(IOException ioex) {
    		LOGGER.error("Error while checking for expired file", ioex);
    	}
    }
    
    /**
     * Scheduled task for deleting expired ZIP files
     */
    @Scheduled(fixedRateString = "${zip.expiration.testIntervalSeconds:60}000")
    public void deleteExpiredZipFiles() {
    	try (DirectoryStream<Path> paths = Files.newDirectoryStream(zipOutputPath, "*.zip")) {
    	    paths.forEach(p->checkForExpiredFile(p, maxZipAgeSeconds));
    	} catch(IOException ioex) {
    		LOGGER.error("Error while deleting expired ZIP files", ioex);
    	}
    }
    
    
    /*********************
     * WebSocket interface
     ********************/
    
    /**
     * Send message on channel associated with the specified UUID
     */
    public void sendMessage(String uuid, String jsonContent) {
    	messagingTemplate.convertAndSend("/json/" + uuid, jsonContent);
    }
    
    /**
     * WebSocket endpoint for configuring a generation request based on the specified JSON configuration string.
     * Response includes the UUID and the specified configuration (including generation seed).
     */
    @MessageMapping("/configure")
    @SendToUser("/reply/configure")
    public String webSocketConfig(String configurationStr) {
    	try {
    		Request request = createRequest(configurationStr);
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
    	
    	Request request = uuidRequestMap.get(uuid);
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
    	
    	Request request = uuidRequestMap.get(uuid);
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
    	
    	Request request = uuidRequestMap.get(uuid);
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
