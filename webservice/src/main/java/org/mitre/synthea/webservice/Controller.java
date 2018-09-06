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
			
		    Generator.GeneratorOptions options = new Generator.GeneratorOptions();

			JSONArray names = configuration.names();
			for (int idx=0; idx<names.length(); ++idx) {
				String name = names.getString(idx);
				String value = configuration.getString(name);
				switch (name) {
				case "state":
					options.state = value;
					default:
						break;
				}
			}
						
			Generator generator = new Generator(options);
		    generator.run();
		      
			return new ResponseEntity<>(HttpStatus.OK);
		} catch(JSONException jex) {
			LOGGER.error("Error while processing JSON", jex);
			return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
		}
	}
}
