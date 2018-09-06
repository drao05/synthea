package org.mitre.synthea.webservice;

import org.springframework.web.bind.annotation.RestController;

import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import javax.servlet.http.HttpServletRequest;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import org.mitre.synthea.engine.Generator;
import org.mitre.synthea.helpers.Config;
import org.mitre.synthea.world.agents.Person;

@RestController
@RequestMapping("/va-synthea")
public class Controller {

	private static final Logger LOGGER = LoggerFactory.getLogger(Controller.class);

    @RequestMapping("/")
    public String index() {
        return "Nothing to see here yet";
    }

    @PostMapping(value = "/generate", consumes = APPLICATION_JSON_VALUE)
	public ResponseEntity<String> generate(HttpServletRequest request, HttpEntity<String> httpEntity) {
    	try {
			JSONObject configuration = new JSONObject(httpEntity.getBody());
			
			LOGGER.info("Requested generator configuration: " + configuration.toString());
			
			// Configure the generator
		    Generator.GeneratorOptions options = new Generator.GeneratorOptions();
			
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
			
			// Start generating
			Generator generator = new Generator(options);
			Thread generatorThread = new Thread() {
			    public void run() {
			    	generator.run();			    }
			};
			generatorThread.start();
					    
			// Watch for results
		    try {
		    	int population = generator.options.population;
		    	int idx = 0;
		    	Person person;
		    	while (idx < population) {
			    	person = generator.getNextPerson();
		    		++idx;
		    		LOGGER.info("Got person: " + person.attributes.get(Person.NAME));
	            }
	    		LOGGER.info("Generation done");
	        } catch(InterruptedException iex) {
	        	LOGGER.info("Interrupted while waiting for results");
	        }
		      
			return new ResponseEntity<>(HttpStatus.OK);
		} catch(JSONException jex) {
			LOGGER.error("Error while processing JSON", jex);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
	}
}
