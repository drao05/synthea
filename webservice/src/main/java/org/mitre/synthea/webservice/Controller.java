package org.mitre.synthea.webservice;

import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.File;
import java.nio.file.Files;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
     * POST endpoint that submits request to generate results based on specified VA Synthea configuration parameters (JSON).
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
     * GET endpoint that retrieves FHIR or CSV results (as a ZIP file) associated with specified UUID (that was originally returned by the associated generate request).
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
		File zipFile = requestService.getZipFileObject(uuid, "fhir");
		
		// A null zipFile indicates a malformed UUID
		if (zipFile == null) {
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
		
		if (!zipFile.exists()) {
			
			// FHIR results not found. Check for CSV results.
			zipFile = requestService.getZipFileObject(uuid, "csv");
			
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
		}
		
		if (request != null && request.isFinished() && request.getResultQueue().size() == 0) {
			
			// Request is finished and all results are delivered
			requestService.removeRequest(uuid);
		}
		
		// Return the ZIP file
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
     * GET endpoint that retrieves results (as a JSON array) associated with specified UUID (that was originally returned by the associated generate request). Note this is not used when for requests that were configured to generate CSV results.
     * Returns a JSON array with available results or a status code:
     * - 400 if UUID path variable is missing
     * - 404 if request was not found (either the request is complete and results have been retrieved, or the request never existed)
     */
    @GetMapping(value = "/json/{uuid}")
	public HttpEntity<?> getResultsJson(@PathVariable String uuid) {
    	
    	if (uuid == null) {
    		return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    	}
    	
    	String results = requestService.getCurrentResults(uuid);
    	
    	if (results == null) {
    		return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    	} else {
    		return new ResponseEntity<String>(results, HttpStatus.OK);
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
}
