package org.mitre.synthea.webservice;

import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONObject;
import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.engine.Generator.GeneratorOptions;
import org.mitre.synthea.helpers.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import lombok.Getter;

@Getter
/**
 * Represents a request to generate synthetic patient data
 */
public class Request {
	
	private static final Logger LOGGER = LoggerFactory.getLogger(Request.class);

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
	
	public Request(JSONObject configuration) {
		super();
				
		// Create generator and associated thread. Set exporter.webclient to true in Config first.
		Config.set("exporter.webclient", "true");
		GeneratorOptions options = configureGeneratorOptions(configuration);
		generator = options != null ? new Generator(options) : new Generator();
		generateThread = new Thread() {
		    public void run() {
		    	generator.run();
		    }
		};
		
		// Create configuration object if needed
		if (configuration != null) {
			this.configuration = configuration;
		}
		
		// Save the generation seed that will be used
		this.configuration.put("seed", options.seed);
		
		// Update Synthea configuration
		// TODO: Re-enable processing of other Synthea config options once we have a solution to the static Config class issue.
	    //updateSyntheaConfig(configuration);
	}
	
	/**
	 * Configure generator options with a given JSON configuration
	 */
	private static GeneratorOptions configureGeneratorOptions(JSONObject configuration) {
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
	private static void updateSyntheaConfig(JSONObject configuration) {
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
				if (Controller.configPropertiesWhiteList.contains(name)) {
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
	
	public void setResultThread(Thread resultThread) {
		this.resultThread = resultThread;
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
	
	public void pause() {
		pauseFlag.set(true);
	}
	
	public void resume() {
		pauseFlag.set(false);
	}
	
	public void stop() {
		stopFlag.set(true);
	}
	
	public void finished() {
		finishedFlag.set(true);
	}
	
	public boolean isStarted() {
		return startedFlag.get();
	}
	
	public boolean isFinished() {
		return finishedFlag.get();
	}
}
