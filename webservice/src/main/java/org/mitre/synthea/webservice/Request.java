package org.mitre.synthea.webservice;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Queue;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.json.JSONArray;
import org.json.JSONException;
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
		
	// Indicates if the request was ever started
	private AtomicBoolean startedFlag = new AtomicBoolean(false);
	
	// Indicates if the request is finished;
	private AtomicBoolean finishedFlag = new AtomicBoolean(false);
	
	private boolean generateCSV = false;
	
	public void configure(JSONObject configuration) {
		
		// Create generator and associated thread. Set exporter.webclient to true in Config first.
		Config.set("exporter.webclient", "true");
				
		try {
			if (configuration.getBoolean("generateCSV")) {
				generateCSV = true;
				Config.set("exporter.webclient.csv.uuid", uuid);
			}
		} catch(JSONException jex) {
		}
		
	    RequestService.updateSyntheaConfig(configuration);

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
			case "telemedAdoptionValues":
				options.telemedAdoptionValues = configuration.getString(name);
				break;
			case "yearsOfHistory":
				options.yearsOfHistory = configuration.getInt(name);
				break;
			default:
				
				// Ignore other configuration values (e.g., Synthea config)
				break;
			}
		}
		
		return options;
	}
	
	/**
	 * Update this request's configuration
	 */
	public void updateConfig(JSONObject configuration) {
		if (configuration == null) {
			return;
		}
				
	    JSONArray names = configuration.names();
		for (int idx=0; idx<names.length(); ++idx) {
			String name = names.getString(idx);
			
			switch (name) {
			case "telemedAdoptionValues":
				generator.updateTelehealthAdoption(configuration.getString(name));
				this.configuration.put(name, configuration.getString(name));
			default:
				
				// Ignore other configuration values for now
				break;
			}
		}
	}
	
	/**
	 * Initializes the results thread
	 */
	private void initResultThread() {
		if (!generateCSV) {
			
			// Create thread that will capture and process FHIR results
			resultThread = new Thread() {
			    public void run() {		    	
			    	String person;
			    	int idx = 0;
					int population = generator.options.population;
					
					FileWriter jsonWriter = null;
					File jsonFile = requestService.getJsonFileObject(uuid);
					
					try {
						
						// Prepare to capture JSON records in a file
					    jsonWriter = new FileWriter(jsonFile, true);
						jsonWriter.write("[");
						
				    	while (idx < population) {
				    		try {
				    			if(isStopped()) {
				    				
				    				// Interrupt generation
				    				generateThread.interrupt();
				    				
				    				// Close and remove associated JSON file
				    				jsonWriter.close();
				    				if (jsonFile.exists()) {
				    					jsonFile.delete();
				    				}
				    				
				    				// Remove associated ZIP file
				    				File zipFile = requestService.getZipFileObject(uuid, "fhir");
				    				if (zipFile.exists()) {
				    					zipFile.delete();
				    				}
				    				
				    				// Remove associated temporary ZIP file
				    				File tempZipFile = requestService.getTempZipFileObject(uuid, "fhir");
				    				if (tempZipFile.exists()) {
				    					tempZipFile.delete();
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
				    			
			    				// Add person to JSON file
					    		if (idx != 1) {
					    			jsonWriter.write(",");
					    		}
					    		jsonWriter.write("\n");
					    		jsonWriter.write(person);
				    			
					    		// Write out every 100 people to disk
					    		if (idx % 100 == 0) {
					    			jsonWriter.close();
					    			jsonWriter = new FileWriter(jsonFile, true);
					    		}
				    		} catch(InterruptedException iex) {
					        	LOGGER.error("Result thread interrupted while waiting for results for request " + uuid);
					        	jsonFile.delete();
				    			requestService.removeRequest(uuid);
					        	return;
					        } catch(Exception ex) {
					        	LOGGER.error("Error in result thread for request " + uuid, ex);
				    			jsonFile.delete();
				    			requestService.removeRequest(uuid);
					        	return;
					        } finally {
					        	resultQueue.clear();
					        }
			            }
				    	
			    		jsonWriter.write("\n]");
					} catch(IOException ioex) {
			        	LOGGER.error("Error writing JSON results file for request " + uuid, ioex);
					} finally {
						if (jsonWriter != null) {
							try {
								jsonWriter.close();
							} catch(IOException ioex) {
					        	LOGGER.error("Error closing JSON results file for request " + uuid, ioex);
							}
						}
					}
	
			    	// Mark the request as finished
		    		finishedFlag.set(true);
	
		    		LOGGER.info("Generation done for request " + uuid);
	
			    	// Send completion notice to WebSocket client
			    	requestService.sendMessage(uuid, "{ \"status\": \"Completed\" }");
		    		
			    	// Create FHIR ZIP file for export
			    	LOGGER.info("Creating FHIR ZIP file for request " + uuid + "...");
		    		File tempFhirZipFile = requestService.getTempZipFileObject(uuid, "fhir");
		    		File fhirZipFile = requestService.getZipFileObject(uuid, "fhir");
		    		try {
			    		ZipOutputStream out = new ZipOutputStream(new FileOutputStream(tempFhirZipFile));
			    		
			    		// Add config file
			    		ZipEntry e = new ZipEntry(uuid + "-config.json");
			    		out.putNextEntry(e);
			    		byte[] data = configuration.toString().getBytes();
			    		out.write(data, 0, data.length);
			    		out.closeEntry();
			    		
			    		// Add results file
			    		e = new ZipEntry(uuid + ".json");
			    		out.putNextEntry(e);
			    		
			    		FileInputStream jsonFileInputStream = null;
			    		try {
				    		byte[] byteBuffer = new byte[1024*1024];
				            int bytesRead = -1;
				            jsonFileInputStream = new FileInputStream(jsonFile);
				            while ((bytesRead = jsonFileInputStream.read(byteBuffer)) != -1) {
				            	out.write(byteBuffer, 0, bytesRead);
				            }
			    		} catch(Exception ex) {
				        	LOGGER.error("Error adding JSON results file to FHIR ZIP for request " + uuid, ex);
			    		} finally {
			    			try {
			    				if (jsonFileInputStream != null) {
			    					jsonFileInputStream.close();
			    				}
			    			} catch(IOException ioex) {
					        	LOGGER.error("Error closing JSON results file for request " + uuid, ioex);
			    			}
			    		}
			    				    		
			    		out.closeEntry();
			    		out.close();
			    		
			    		tempFhirZipFile.renameTo(fhirZipFile);
			    		
			    		LOGGER.info("FHIR results written to " + fhirZipFile.toPath());
		    		} catch(Exception ex) {
		    			LOGGER.error("Exception while creating FHIR ZIP file for request " + uuid, ex);
		    		} finally {
		    			
		    			// Delete the temporary JSON file
		    			jsonFile.delete();
		    		}
		    		
		    		// Stop tracking the request
					requestService.removeRequest(uuid);
			    }
			};
		} else {
			
			// Create thread that will capture and process CSV results
			resultThread = new Thread() {
			    public void run() {		    	
			    	int idx = 0;
					int population = generator.options.population;
						
					while (idx < population) {
			    		try {
			    			if(isStopped()) {
			    				
			    				// Interrupt generation
			    				generateThread.interrupt();
			    				
			    				// Remove associated ZIP file
			    				File zipFile = requestService.getZipFileObject(uuid, "csv");
			    				if (zipFile.exists()) {
			    					zipFile.delete();
			    				}
			    				
			    				// Remove associated temporary ZIP file
			    				File tempZipFile = requestService.getTempZipFileObject(uuid, "csv");
			    				if (tempZipFile.exists()) {
			    					tempZipFile.delete();
			    				}
			    				
			    				// Remove the request
			    				requestService.removeRequest(uuid);
			    				
				    			return;
				    		}
			    			
			    			generator.getNextPerson();
			    			++idx;		    			
			    			
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

		    		// Create CSV ZIP file for export
			    	LOGGER.info("Creating CSV ZIP file for request " + uuid + "...");
		    		File tempCsvZipFile = requestService.getTempZipFileObject(uuid, "csv");
		    		File csvZipFile = requestService.getZipFileObject(uuid, "csv");
		    		try {
			    		ZipOutputStream out = new ZipOutputStream(new FileOutputStream(tempCsvZipFile));
			    		
			    		// Add config file
			    		ZipEntry e = new ZipEntry(uuid + "-config.json");
			    		out.putNextEntry(e);
			    		byte[] data = configuration.toString().getBytes();
			    		out.write(data, 0, data.length);
			    		out.closeEntry();
			    		
			    		// Add the CSV files			    		
			    		String[] csvFilenames = new String[]{"patients.csv", "encounters.csv", "procedures.csv", "careplans.csv", "conditions.csv"};
			    		File csvFile;
			    		FileInputStream csvFileInputStream;
			    		
			    		for (String csvFilename : csvFilenames) {
				    		csvFile = requestService.getCsvFileObject(uuid, csvFilename);
				    		if (csvFile != null) {
					    		e = new ZipEntry(csvFilename);
					    		out.putNextEntry(e);
					    		csvFileInputStream = null;
					    		
					    		try {
						    		byte[] byteBuffer = new byte[1024*1024];
						            int bytesRead = -1;
						            csvFileInputStream = new FileInputStream(csvFile);
						            while ((bytesRead = csvFileInputStream.read(byteBuffer)) != -1) {
						            	out.write(byteBuffer, 0, bytesRead);
						            }
					    		} catch(Exception ex) {
						        	LOGGER.error("Error adding CSV file " + csvFilename + " to ZIP for request " + uuid, ex);
					    		} finally {
					    			try {
					    				if (csvFileInputStream != null) {
					    					csvFileInputStream.close();
					    				}
					    			} catch(IOException ioex) {
							        	LOGGER.error("Error closing CSV file " + csvFilename + " for request " + uuid, ioex);
					    			}
					    		}
					    				    		
					    		out.closeEntry();
				    		} else {
				    			LOGGER.error("Error getting CSV file " + csvFilename + " for request " + uuid);
				    		}
			    		}
			    		
			    		out.close();
			    		
			    		tempCsvZipFile.renameTo(csvZipFile);
			    		
			    		LOGGER.info("CSV results written to " + csvZipFile.toPath());
		    		} catch(Exception ex) {
		    			LOGGER.error("Exception while creating CSV ZIP file for request " + uuid, ex);
		    		}
		    		
		    		// Stop tracking the request
					requestService.removeRequest(uuid);
			    }
			};		
		}
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
	
	public boolean isStopped() {
		return stopFlag.get();
	}
	
	public boolean isStarted() {
		return startedFlag.get();
	}
	
	public boolean isFinished() {
		return finishedFlag.get();
	}
	
	public void stop() {
		stopFlag.set(true);
	}
	
	public boolean generateCSV() {
		return generateCSV;
	}
}
