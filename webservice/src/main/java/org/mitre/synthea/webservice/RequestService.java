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
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.json.JSONException;
import org.json.JSONObject;
import org.mitre.synthea.helpers.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
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
		
	// Subset of VA Synthea configuration properties that web service allows user to customize
	public final Set<String> configPropertiesWhiteList = new HashSet<String>();

	// Messaging template used for requests from WebSocket clients
	@Autowired
	private SimpMessagingTemplate messagingTemplate;
	
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
	
	public Path getZipOutputPath() {
		return zipOutputPath;
	}
	
	public Set<String> getConfigPropertiesWhiteList() {
		return configPropertiesWhiteList;
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
     * Generates File object for a target ZIP file based on UUID.
     * Returns null if the specified UUID is malformed.
     */
    public File getZipFile(String uuid) {
    	if (uuid != null && uuid.matches(UUID_REGEX_PATTERN)) {
    		return new File(zipOutputPath.toString() + File.separator + uuid + ".zip");
    	} else {
    		return null;
    	}
    }
    
    /**
     * Send message on channel associated with the specified UUID
     */
    public void sendMessage(String uuid, String jsonContent) {
    	messagingTemplate.convertAndSend("/json/" + uuid, jsonContent);
    }
}
