package org.mitre.synthea.webservice;

import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.file.Files;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;

@RestController
public class Controller {

	private static final Logger LOGGER = LoggerFactory.getLogger(Controller.class);

	private Map<UUID, Thread> uuidGenerateThreadMap = new ConcurrentHashMap<UUID, Thread>();
	private Map<UUID, Thread> uuidResultThreadMap = new ConcurrentHashMap<UUID, Thread>();

	private File outputDir;
	
	public Controller() {
		
		// Initialize ZIP export directory relative to VA Synthea's base directory configuration parameter
		
		String baseDir = Config.get("exporter.baseDirectory");
		if (baseDir != null && !baseDir.endsWith(File.separator)) {
			baseDir += File.separator;
		} else {
			baseDir = "";
		}
		
		outputDir = new File(baseDir + "zip");
		if (!outputDir.exists()) {
			outputDir.mkdirs();
		}
		
		LOGGER.info("Output directory: " + outputDir.toString());
	}
	
    /**
     * POST endpoint that submits request to generate results based on specified VA Synthea configuration paramaeters (JSON).
     * If the request was successfully submitted, returns status code 200 and a UUID that can be used to refer to request in other endpoints.
     * Returns status code 400 if there was a problem processing the configuration parameters.
     */
    @PostMapping(value = "/generate", consumes = APPLICATION_JSON_VALUE)
	public ResponseEntity<String> generateResults(HttpServletRequest request, HttpEntity<String> httpEntity) {
    	try {
		    Generator.GeneratorOptions options = new Generator.GeneratorOptions();

		    String requestBody = httpEntity.getBody();
		    if (requestBody != null) {
	    		JSONObject configuration = new JSONObject(httpEntity.getBody());
				
				LOGGER.info("Requested generator configuration: " + configuration.toString());
				
				// Configure the generator with specified configuration
			    JSONArray names = configuration.names();
				for (int idx=0; idx<names.length(); ++idx) {
					String name = names.getString(idx);
					
					switch (name) {
					case "seed":
						options.seed = configuration.getLong(name);
						break;
					case "population":
						options.population = configuration.getInt(name);
						break;
					case "gender":
						options.gender = configuration.getString(name);
						break;
					case "minAge":
						options.minAge = configuration.getInt(name);
						break;
					case "maxAge":
						options.maxAge = configuration.getInt(name);
						break;
					case "state":
						options.state = configuration.getString(name);
						break;
					case "city":
						options.city = configuration.getString(name);
						break;
					default:
						if (Config.get(name) != null) {
							LOGGER.info("Updating existing configuration parameter: " + name);
							Config.set(name, configuration.getString(name));
						} else {
							LOGGER.info("Adding missing configuration parameter: " + name);
							Config.set(name, configuration.getString(name));
						}
						
						break;
					}
				}
		    }
			
			// Mark this request as coming from a web client
			Config.set("exporter.webclient", "true");
			
			// Start generating
			Generator generator = new Generator(options);
			Thread generateThread = new Thread() {
			    public void run() {
			    	generator.run();
			    }
			};
			generateThread.start();
			
			// Capture the results in a zip file
			final UUID uuid = UUID.randomUUID();
			uuidGenerateThreadMap.put(uuid, generateThread);
			Thread resultThread = new Thread() {
			    public void run() {
			    	StringBuilder builder = new StringBuilder("[");
					int population = generator.options.population;
			    	int idx = 0;
			    	String person;
			    	while (idx < population) {
			    		try {
			    			person = generator.getNextPerson();
			    			if (idx > 0) {
			    				builder.append(",");
			    			}
			    			
			    			builder.append("\n").append(person);
				    		++idx;
			    		} catch(InterruptedException iex) {
				        	LOGGER.error("Interrupted while waiting for results");
				        	uuidGenerateThreadMap.remove(uuid);
				        	uuidResultThreadMap.remove(uuid);
				        	return;
				        }
		            }
			    	
			    	builder.append("\n]");
		    		LOGGER.info("Generation done");
		    		
			    	uuidGenerateThreadMap.remove(uuid);
		    		
		    		File zipFile = new File(outputDir.toString() + File.separator + uuid.toString() + ".zip");
		    		try {
			    		ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile));
			    		ZipEntry e = new ZipEntry(uuid.toString() + ".json");
			    		out.putNextEntry(e);
	
			    		byte[] data = builder.toString().getBytes();
			    		out.write(data, 0, data.length);
			    		out.closeEntry();
			    		out.close();
			    		
			    		LOGGER.info("Results written to " + zipFile.toPath());
		    		} catch(Exception ex) {
		    			LOGGER.error("Exception while creating zip file for request " + uuid.toString(), ex);
		    		}
			    }
			};
			resultThread.start();
			uuidResultThreadMap.put(uuid, resultThread);
			
			return new ResponseEntity<String>(uuid.toString(), HttpStatus.OK);
		} catch(JSONException jex) {
			LOGGER.error("Error while processing JSON", jex);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
	}
    
    /**
     * Utility method for generating File object for a target ZIP file based on UUID
     */
    private File getZipFile(UUID uuid) {
    	return new File(outputDir + File.separator + uuid.toString() + ".zip");
    }
	
    /**
     * GET endpoint that retrieves ZIP file with results associated with specified UUID (that was originally returned by the associated generate request).
     * Returns either the ZIP file or a status code:
     * - 202 if results are still pending
     * - 400 if UUID path variable is missing
     * - 404 if request was not found
     * - 500 if error was encountered while returning results
     */
    @GetMapping(value = "/results/{uuidStr}")
	public HttpEntity<?> getResults(@PathVariable String uuidStr) {
    	
    	if (uuidStr == null) {
    		return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    	}
    	
    	UUID uuid = UUID.fromString(uuidStr);

    	// Check for ZIP file
		File zipFile = getZipFile(uuid);
		if (!zipFile.exists()) {
			
			LOGGER.info("Results file " + zipFile.toString() + " not found");
			
			// If the ZIP file does not exist, see if request is pending or not found.
			if (uuidResultThreadMap.containsKey(uuid)) {
				
				// Report that request was found but results are not ready yet
				return new ResponseEntity<>(HttpStatus.ACCEPTED);
			} else {
				
				// Report that request was not found
				return new ResponseEntity<>(HttpStatus.NOT_FOUND);
			}
		}
		
		// Return the ZIP file
		try {
	    	byte[] zipContents = Files.readAllBytes(zipFile.toPath());
	    	zipFile.delete();
	    	uuidResultThreadMap.remove(uuid);
	    	
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
     * DELETE endpoint that attempts to terminate a pending request associated with specified UUID (that was originally returned by the associated generate request).
     * Returns a status code:
     * - 200 if request was found and associated threads were interrupted, though the request processing may not terminate immediately
     * - 400 if UUID path variable is missing
     * - 404 request was not found
     * - 500 if error was encountered while trying to terminate request
     */
    @DeleteMapping(value = "/terminate/{uuidStr}")
	public HttpEntity<?> deleteRequest(@PathVariable String uuidStr) {
    	
    	if (uuidStr == null) {
    		return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    	}
    	
    	UUID uuid = UUID.fromString(uuidStr);
    	
    	// Try to interrupt the result thread
    	Thread resultThread = uuidResultThreadMap.get(uuid);
    	if (resultThread == null) {
    		return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    	}
    	
    	if (resultThread.isAlive()) {
	    	resultThread.interrupt();
	    	if (!resultThread.isInterrupted()) {
	    		LOGGER.info("Could not interrupt result thread for request " + uuidStr);
	    		return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
	    	}
    	}
    	
    	uuidResultThreadMap.remove(uuid);
    	
    	// Try to interrupt the generator thread if it exists
    	Thread generateThread = uuidGenerateThreadMap.get(uuid);
    	if (generateThread != null && generateThread.isAlive()) {
    		generateThread.interrupt();
    		if (!generateThread.isInterrupted()) {
    			LOGGER.info("Could not interrupt generator thread for request " + uuidStr);
    			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    		}
    	}
    	
		uuidGenerateThreadMap.remove(uuid);
		
		// Remove associated ZIP file if it exists
		File zipFile = getZipFile(uuid);
		if (zipFile.exists()) {
			zipFile.delete();
		}
		
    	return new ResponseEntity<>(HttpStatus.OK);
	}
}
