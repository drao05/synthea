package org.mitre.synthea.webservice;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.engine.Generator.GeneratorOptions;
import org.mitre.synthea.helpers.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

/**
 * Represents a request to generate synthetic patient data
 */
@Component
@Scope("prototype")
public class Request {
	
	private final static Logger LOGGER = LoggerFactory.getLogger(Request.class);

	@Autowired
	private RequestService requestService;
	
	// Time in milliseconds to sleep between checks for a paused request
	private final static int PAUSE_SLEEP_MS = 1000;
		
	// Max size of result queues
	private final static int MAX_RESULTS_QUEUE_SIZE = 1000;
		
	// Request ID
	private String uuid = UUID.randomUUID().toString();
	
	// Generator
	private Generator generator;
			
	// Generation thread
	private Thread generateThread;
		
	// Configuration object
	private JSONObject configuration = new JSONObject();

	// Thread that gathers the results of the request
	private Thread resultThread;

	// Collection of results (used to serve up partial results as available)
	private Queue<String> resultQueue = new ConcurrentLinkedQueue<String>();
	
	// Stop request flag
	private AtomicBoolean stopFlag = new AtomicBoolean(false);
		
	// Pause request flag
	private AtomicBoolean pauseFlag = new AtomicBoolean(false);
	
	// Indicates if the request was ever started
	private AtomicBoolean startedFlag = new AtomicBoolean(false);
	
	// Indicates if the request is finished;
	private AtomicBoolean finishedFlag = new AtomicBoolean(false);
	
	public void configure(JSONObject configuration) {
		
		// Create generator and associated thread. Set exporter.webclient to true in Config first.
		Config.set("exporter.webclient", "true");
		GeneratorOptions options = configureGeneratorOptions(configuration);
		generator = options != null ? new Generator(options) : new Generator();
		generateThread = new Thread() {
		    public void run() {
		    	generator.run();
		    }
		};
		
		// Initialize the result thread
		initResultThread();
		
		// Create configuration object if needed
		if (configuration != null) {
			this.configuration = configuration;
		}
		
		// Save the generation seed that will be used
		this.configuration.put("seed", options.seed);
		
		// TODO: Re-enable processing of other Synthea config options once we have a solution to the static Config class issue.
	    //updateSyntheaConfig(configuration);
	}
	
	/**
	 * Configure generator options with a given JSON configuration
	 */
	private GeneratorOptions configureGeneratorOptions(JSONObject configuration) {
		if (configuration == null) {
			return null;
		}
				
		GeneratorOptions options = new GeneratorOptions();
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
				
				// Ignore other configuration values (e.g., Synthea config)
				break;
			}
		}
		
		return options;
	}
	
	/**
	 * Configure Synthea with a given JSON configuration
	 */
	@SuppressWarnings("unused")
	private void updateSyntheaConfig(JSONObject configuration) {
		Set<String> configPropertiesWhiteList = requestService.getConfigPropertiesWhiteList();
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
	
	/**
	 * Initializes the results thread
	 */
	private void initResultThread() {
		resultThread = new Thread() {
		    public void run() {
		    	StringBuilder builder = new StringBuilder("[");
		    	
		    	String person;
		    	int idx = 0;
				int population = generator.options.population;

		    	while (idx < population) {
		    		try {
		    			while(isPaused()) {
			    			Thread.sleep(PAUSE_SLEEP_MS);
			    		}
		    			
		    			while(isStopped()) {
		    				
		    				// Interrupt generation
		    				generateThread.interrupt();
		    				
		    				// Remove associated ZIP file if it exists
		    				File zipFile = requestService.getZipFile(uuid);
		    				if (zipFile != null && zipFile.exists()) {
		    					zipFile.delete();
		    				}
		    				
		    				// Remove the request
		    				requestService.removeRequest(uuid);
		    				
			    			return;
			    		}
		    			
		    			person = generator.getNextPerson();
		    			++idx;
		    			
		    			// Send person to WebSocket client
		    			requestService.sendMessage(uuid, person);
			    		
		    			// Make person available for immediately retrieval via RESTful interface
	    				synchronized(resultQueue) {
	    					
	    					// Remove oldest result if max queue size has been reached
	    					if (resultQueue.size() == MAX_RESULTS_QUEUE_SIZE) {
	    						resultQueue.poll();
	    					}
	    					
	    					resultQueue.add(person);
	    				}
		    			
	    				// Add person to string builder for eventual ZIP export
			    		if (idx != 1) {
			    			builder.append(",");
			    		}
			    		builder.append("\n").append(person);
		    			
		    		} catch(InterruptedException iex) {
			        	LOGGER.error("Result thread interrupted while waiting for results for request " + uuid);
			        	requestService.removeRequest(uuid);
			        	return;
			        } catch(Exception ex) {
			        	LOGGER.error("Error in result thread for request " + uuid, ex);
			        	requestService.removeRequest(uuid);
			        	return;
			        } 
	            }

		    	// Mark the request as finished
	    		finishedFlag.set(true);

	    		LOGGER.info("Generation done for request " + uuid);

	    		// Close out string builder for ZIP export
		    	builder.append("\n]");

		    	// Send completion notice to WebSocket client
		    	requestService.sendMessage(uuid, "{ \"status\": \"Completed\" }");
	    		
		    	// Create ZIP file for export
	    		File zipFile = requestService.getZipFile(uuid);
	    		
	    		// A null zipFile indicates a malformed UUID
	    		if (zipFile == null) {
	    			LOGGER.error("UUID is malformed: " + uuid);
	    		} else {
		    		try {
			    		ZipOutputStream out = new ZipOutputStream(new FileOutputStream(zipFile));
			    		
			    		// Add config file
			    		ZipEntry e = new ZipEntry(uuid + "-config.json");
			    		out.putNextEntry(e);
			    		byte[] data = configuration.toString().getBytes();
			    		out.write(data, 0, data.length);
			    		out.closeEntry();
			    		
			    		// Add results file
			    		e = new ZipEntry(uuid + ".json");
			    		out.putNextEntry(e);
			    		data = builder.toString().getBytes();
			    		out.write(data, 0, data.length);
			    		out.closeEntry();
			    		
			    		out.close();
			    		
			    		LOGGER.info("Results written to " + zipFile.toPath());
		    		} catch(Exception ex) {
		    			LOGGER.error("Exception while creating zip file for request " + uuid, ex);
		    		}
	    		}
		    }
		};		
	}
	
	public String getUuid() {
		return uuid;
	}
	
	public JSONObject getConfiguration() {
		return configuration;
	}
	
	public Queue<String> getResultQueue() {
		return resultQueue;
	}
	
	/**
	 * Start the request
	 */
	public void start() {
		generateThread.start();
		resultThread.start();
		startedFlag.set(true);
	}
	
	public boolean isPaused() {
		return pauseFlag.get();
	}
	
	public boolean isStopped() {
		return stopFlag.get();
	}
	
	public boolean isStarted() {
		return startedFlag.get();
	}
	
	public boolean isFinished() {
		return finishedFlag.get();
	}
	
	/**
	 * Pause the request
	 */
	public void pause() {
		pauseFlag.set(true);
	}
	
	/**
	 * Resume the request
	 */
	public void resume() {
		pauseFlag.set(false);
	}
	
	/**
	 * Stop the request permanently
	 */
	public void stop() {
		stopFlag.set(true);
	}
}
