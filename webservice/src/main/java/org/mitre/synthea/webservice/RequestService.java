package org.mitre.synthea.webservice;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.mitre.synthea.helpers.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.context.WebApplicationContext;

@Service
public class RequestService {

	private final static Logger LOGGER = LoggerFactory.getLogger(RequestService.class);

	// Maps UUID to a request object
	private final Map<String, Request> uuidRequestMap = new ConcurrentHashMap<String, Request>();
	
	// UUID regex pattern
	private final static String UUID_REGEX_PATTERN = "[0-9a-fA-F]{8}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{4}\\-[0-9a-fA-F]{12}";
	
	// Path to ZIP output directory
	private Path zipOutputPath;
	
	// Path to CSV output directory
	private Path csvOutputPath;
		
	// Subset of VA Synthea configuration properties that web service allows user to customize
	public static final Set<String> configPropertiesWhiteList = new HashSet<String>();

	// Messaging handler used for requests from WebSocket clients
	@Autowired
	private SocketHandler socketHandler;
	
	@Autowired
	private WebApplicationContext context;
	
	public RequestService() {
		
		// Initialize ZIP export directory relative to VA Synthea's base directory configuration parameter
		String baseDir = Config.get("exporter.baseDirectory");
		if (baseDir != null && !baseDir.endsWith(File.separator)) {
			baseDir += File.separator;
		} else {
			baseDir = "";
		}
		
		File zipOutputDir = new File(baseDir + "zip");
    	zipOutputPath = Paths.get(zipOutputDir.toURI());
		if (!zipOutputDir.exists()) {
			zipOutputDir.mkdirs();
		}
		LOGGER.info("ZIP output directory: " + zipOutputPath.toString());
		
		File csvOutputDir = new File(baseDir + "csv");
    	csvOutputPath = Paths.get(csvOutputDir.toURI());
		if (!csvOutputDir.exists()) {
			csvOutputDir.mkdirs();
		}
		LOGGER.info("CSV output directory: " + csvOutputPath.toString());
		
		// Initialize config properties white list
		InputStream whiteListStream = RequestService.class.getClassLoader().getResourceAsStream("static/va-synthea-properties-whitelist.txt");
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
	
	public Path getZipOutputPath() {
		return zipOutputPath;
	}
	
	/**
	 * Creates and initializes a new generation request based on the specified configuration
	 */
	public Request createRequest(String configurationStr) throws JSONException {
		
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
		Request request = context.getBean(Request.class);
		request.configure(configuration);
		uuidRequestMap.put(request.getUuid(), request);
		return request;
	}
	
	/**
	 * Get request by UUID
	 */
	public Request getRequest(String uuid) {
		return uuidRequestMap.get(uuid);
	}
	
	/**
	 * Delete a request by UUID
	 */
	public void removeRequest(String uuid) {
		uuidRequestMap.remove(uuid);
	}
	
	/**
     * Generates File object for a target ZIP file based on UUID and type (currently "fhir" or "csv").
     * Returns null if the specified UUID is malformed.
     */
    public File getZipFileObject(String uuid, String type) {
    	return getFileObject(uuid, type, "zip");
    }
    
    /**
     * Generates File object for a temporary ZIP file based on UUID and type (currently "fhir" or "csv").
     * Returns null if the specified UUID is malformed.
     */
    public File getTempZipFileObject(String uuid, String type) {
    	return getFileObject(uuid, type, "tmp");
    }
    
    /**
     * Generates File object for a target JSON file based on UUID.
     * Returns null if the specified UUID is malformed.
     */
    public File getJsonFileObject(String uuid) {
    	return getFileObject(uuid, "fhir", "json");
    }
    
    /**
     * Generates CSV File object for a target file based on UUID and filenam.
     * Returns null if the specified UUID is malformed.
     */
    public File getCsvFileObject(String uuid, String filename) {
    	if (uuid != null && uuid.matches(UUID_REGEX_PATTERN)) {
    		return new File(csvOutputPath.toString() + File.separator + uuid + File.separator + filename);
    	} else {
    		return null;
    	}
    }
        
    /**
     * Generates File object for a target file based on UUID, type (currently "fhir" or "csv"), and file extension.
     * Returns null if the specified UUID is malformed.
     */
    private File getFileObject(String uuid, String type, String extension) {
    	if (uuid != null && uuid.matches(UUID_REGEX_PATTERN)) {
    		
    		if (extension.equals("zip")) {
    			// Return a file in the ZIP directory
    			return new File(zipOutputPath.toString() + File.separator + uuid + "-" + type + "." + extension);
    		}
    		
    		switch(type) {
	    		case "fhir":
	    			return new File(zipOutputPath.toString() + File.separator + uuid + "-" + type + "." + extension);
	    		case "csv":
	    			return new File(csvOutputPath.toString() + File.separator + uuid + "-" + type + "." + extension);
	    		default:
	    			return null;
    		}
    	} else {
    		return null;
    	}
    }
    
    /**
     * Get the JSON array string with the current results for the request with the specified UUID.
     * Returns null if the request is not found.
     */
    public String getCurrentResults(String uuid) {
    	
    	Request request = getRequest(uuid);
    	if (request == null) {
    		return null;
    	}
    	
	    Queue<String> resultQueue = request.getResultQueue();
		synchronized(resultQueue) {
			if (resultQueue.size() == 0) {
				return "[]";
			}
						
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
				removeRequest(uuid);
			}
			
			return builder.toString();
		}
    }
    
    /**
     * Send message to Web Socket client for request with the specified UUID
     */
    public void sendMessage(String uuid, String message) {
    	socketHandler.sendMessage(uuid,  message);
    }
    
    /**
	 * Configure Synthea with a given JSON configuration
	 */
	public static void updateSyntheaConfig(JSONObject configuration) {
	    JSONArray names = configuration.names();
		for (int idx=0; idx<names.length(); ++idx) {
			String name = names.getString(idx);			
			switch (name) {
			case "seed":
			case "population":
			case "gender":
			case "minAge":
			case "maxAge":
			case "state":
			case "city":
			case "generateCSV":
				// Ignore generator configuration values
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
}
