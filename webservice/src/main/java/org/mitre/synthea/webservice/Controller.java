package org.mitre.synthea.webservice;

import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
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
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;

@RestController
public class Controller {

	private static final Logger LOGGER = LoggerFactory.getLogger(Controller.class);

	// Maps UUID of request to generator thread
	private Map<UUID, Thread> uuidGenerateThreadMap = new ConcurrentHashMap<UUID, Thread>();
	
	// Maps UUID of request to thread that is gathering the results of the request
	private Map<UUID, Thread> uuidResultThreadMap = new ConcurrentHashMap<UUID, Thread>();

	// Maps UUID of request to collection of results (used to stream results)
	private Map<UUID, Queue<String>> uuidResultQueueMap = new ConcurrentHashMap<UUID, Queue<String>>();
	private final static int MAX_RESULTS_QUEUE_SIZE = 1000;
	
	// Path to ZIP output directory
	private Path zipOutputPath;
	
	// Max allowed age of ZIP files (used by scheduler task that deletes expired ZIP files)
	@Value("${zip.maxAgeSeconds:60}")
	private Integer maxZipAgeSeconds;
	
	// Subset of VA Synthea configuration properties that web service allows user to customize
	private Set<String> configPropertiesWhiteList = new HashSet<String>();
	
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
     * POST endpoint that submits request to generate results based on specified VA Synthea configuration paramaeters (JSON).
     * If the request was successfully submitted, returns status code 200 and a UUID that can be used to refer to request in other endpoints.
     * Returns status code 400 if there was a problem processing the configuration parameters.
     */
    @PostMapping(value = "/generate", consumes = APPLICATION_JSON_VALUE)
	public ResponseEntity<String> generateResults(HttpServletRequest request, HttpEntity<String> httpEntity) {
    	
    	Generator.GeneratorOptions options = new Generator.GeneratorOptions();
	    JSONObject configuration = null;
    	try {
		    String requestBody = httpEntity.getBody();
		    if (requestBody != null) {
		    	configuration = new JSONObject(httpEntity.getBody());
				
				LOGGER.info("Requested generator configuration: " + configuration.toString());
				
				// Configure the generator with specified configuration
			    JSONArray names = configuration.names();
				for (int idx=0; idx<names.length(); ++idx) {
					String name = names.getString(idx);
					
					// TODO: Check for valid config values
					
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
						if (configPropertiesWhiteList.contains(name)) {
							if (Config.get(name) != null) {
								LOGGER.info("Updating existing configuration parameter: " + name);
								Config.set(name, configuration.getString(name));
							} else {
								LOGGER.info("Adding missing configuration parameter: " + name);
								Config.set(name, configuration.getString(name));
							}
						} else {
							LOGGER.info("Unsupported configuration parameter: " + name);
						}
						break;
					}
				}
		    }
    	} catch(JSONException jex) {
			LOGGER.error("Error while processing JSON", jex);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
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
		
		// Create JSONObject configuration if it does not already exist
		if (configuration == null) {
			configuration = new JSONObject();
		}
				
		// Ensure that generation seed is in configuration that will be packaged with ZIP file
		final JSONObject configToExport = configuration;
		configToExport.put("seed", options.seed);
		
		// Prepare to make results available for streaming and capturing them in a ZIP file
		final UUID uuid = UUID.randomUUID();
		uuidGenerateThreadMap.put(uuid, generateThread);
		uuidResultQueueMap.put(uuid, new ConcurrentLinkedQueue<String>());

		// Create and start result thread
		Thread resultThread = new Thread() {
		    public void run() {
		    	StringBuilder builder = new StringBuilder("[");
				int population = generator.options.population;
		    	int idx = 0;
		    	String person;
		    	Queue<String> resultQueue = uuidResultQueueMap.get(uuid);
		    	while (idx < population) {
		    		try {
		    			person = generator.getNextPerson();
		    			
	    				synchronized(resultQueue) {
	    					
	    					// Remove oldest result if max queue size has been reached
	    					if (resultQueue.size() == MAX_RESULTS_QUEUE_SIZE) {
	    						resultQueue.poll();
	    					}
	    					
	    					resultQueue.add(person);
	    				}
		    			
			    		if (idx > 0) {
			    			builder.append(",");
			    		}
			    		builder.append("\n").append(person);
		    			
			    		++idx;
		    		} catch(InterruptedException iex) {
			        	LOGGER.error("Interrupted while waiting for results");
			        	uuidGenerateThreadMap.remove(uuid);
			        	uuidResultThreadMap.remove(uuid);
			        	uuidResultQueueMap.remove(uuid);
			        	return;
			        }
	            }
		    	
		    	builder.append("\n]");
	    		LOGGER.info("Generation done for request " + uuid);
	    		
		    	uuidGenerateThreadMap.remove(uuid);
		    			    	
		    	// Create ZIP file
	    		File zipFile = new File(zipOutputPath.toString() + File.separator + uuid.toString() + ".zip");
	    		try {
		    		ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile));
		    		
		    		// Add config file
		    		ZipEntry e = new ZipEntry(uuid.toString() + "-config.json");
		    		out.putNextEntry(e);
		    		byte[] data = configToExport.toString().getBytes();
		    		out.write(data, 0, data.length);
		    		out.closeEntry();
		    		
		    		// Add results file
		    		e = new ZipEntry(uuid.toString() + ".json");
		    		out.putNextEntry(e);
		    		data = builder.toString().getBytes();
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
	}
    
    /**
     * Utility method for generating File object for a target ZIP file based on UUID
     */
    private File getZipFile(UUID uuid) {
    	return new File(zipOutputPath.toString() + File.separator + uuid.toString() + ".zip");
    }
	
    /**
     * GET endpoint that retrieves results (as a ZIP file) associated with specified UUID (that was originally returned by the associated generate request).
     * Returns a ZIP file or a status code:
     * - 202 if results are still pending
     * - 400 if UUID path variable is missing
     * - 404 if request was not found (either the request is complete and results have been retrieved, or the request never existed)
     * - 500 if error was encountered while returning results
     */
    @GetMapping(value = "/zip/{uuidStr}")
	public HttpEntity<?> getResultsZip(@PathVariable String uuidStr) {
    	
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
	    	
	    	// Remove result thread entries from tracking collections
	    	uuidResultThreadMap.remove(uuid);
	    	uuidResultQueueMap.remove(uuid);
	    	
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
    @GetMapping(value = "/json/{uuidStr}")
	public HttpEntity<?> getResultsJson(@PathVariable String uuidStr) {
    	
    	if (uuidStr == null) {
    		return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    	}
    	
    	UUID uuid = UUID.fromString(uuidStr);

    	Queue<String> resultQueue = uuidResultQueueMap.get(uuid);
    	if (resultQueue == null) {
    		return new ResponseEntity<>(HttpStatus.NOT_FOUND);
    	}
    	
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

			if (uuidGenerateThreadMap.get(uuid) == null) {
				
		    	// Remove result thread entries from tracking collections
				uuidResultThreadMap.remove(uuid);
				uuidResultQueueMap.remove(uuid);
			} else {
				
				// Remove results that are being returned from result queue
				resultQueue.clear();
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
    	
    	// Remove result thread entries from tracking collections
    	uuidResultThreadMap.remove(uuid);
    	uuidResultQueueMap.remove(uuid);

    	// Try to interrupt the generator thread if it exists
    	Thread generateThread = uuidGenerateThreadMap.get(uuid);
    	if (generateThread != null && generateThread.isAlive()) {
    		generateThread.interrupt();
    		if (!generateThread.isInterrupted()) {
    			LOGGER.info("Could not interrupt generator thread for request " + uuidStr);
    			return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
    		}
    	}
    	
    	// Remove generation thread entry from tracking collection
		uuidGenerateThreadMap.remove(uuid);
		
		// Remove associated ZIP file if it exists
		File zipFile = getZipFile(uuid);
		if (zipFile.exists()) {
			zipFile.delete();
		}
		
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
}
