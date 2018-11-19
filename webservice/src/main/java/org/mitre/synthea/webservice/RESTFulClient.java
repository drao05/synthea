package org.mitre.synthea.webservice;

import java.io.File;
import java.io.FileOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.http.HttpEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.HttpStatus;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

/**
 * This client application demonstrates how to submit a request to VA Synthea to generate CSV results (and retrieve the zipped results) via the web service's RESTful API.
 */
public class RESTFulClient {
	public static void main(String[] args) {
		
		final String baseUrl = "http://localhost:8080/va-synthea/";
		String uuid;
		
	    CloseableHttpClient client = HttpClients.createDefault();
		CloseableHttpResponse response;
		
	    try {
	    	
	    	// Submit the request to generate CSV results
			int population = 1000;
		    String jsonRequest = "{\"population\": " + population + ",\"generateCSV\": true}";

	    	HttpPost httpPost = new HttpPost(baseUrl + "/generate");
		    httpPost.setEntity(new StringEntity(jsonRequest));
		    httpPost.setHeader("accept", "application/json");
		    httpPost.setHeader("content-type", "application/json");
	    	response = client.execute(httpPost);
		    if (response.getStatusLine().getStatusCode() == HttpStatus.SC_OK) {
		    	JSONObject obj = new JSONObject(EntityUtils.toString(response.getEntity()));
		    	uuid = obj.getString("uuid");
		    	System.out.println("UUID: " + uuid);
		    } else {
		    	System.out.println("Submission of generation request failed");
		    	return;
		    }
		    
		    // Wait for and retrieve the results
		    HttpGet httpGet = new HttpGet(baseUrl + "zip/" + uuid);
	    	response = client.execute(httpGet);
	    	int statusCode = response.getStatusLine().getStatusCode();
		    while (statusCode != HttpStatus.SC_OK) { // GET succeeded
		    	if (statusCode == HttpStatus.SC_ACCEPTED) { // Request is still in progress
		    		
		    		// Try again
		    		response.close();		    		
		    		System.out.println("Waiting for request to finish...");
		    		Thread.sleep(10000); // 10 seconds
		    		response = client.execute(httpGet);
			    	statusCode = response.getStatusLine().getStatusCode();
		    	} else { // There was an error of some kind
		    		switch(statusCode) {
		    			case HttpStatus.SC_BAD_REQUEST:
		    				System.out.println("Could not retrieve results due to missing or malformed UUID");
		    				break;
		    			case HttpStatus.SC_NOT_FOUND:
		    				System.out.println("Results not found for UUID " + uuid);
		    				break;
		    			case HttpStatus.SC_INTERNAL_SERVER_ERROR:
		    				System.out.println("Could not retrieve results due to internal server error");
		    				break;
		    			default:
		    				break;
		    		}
		    		
		    		return;
		    	}
		    }
		    
		    // Save the results locally
    		System.out.println("Saving results...");
			HttpEntity entity = response.getEntity();
			if (entity != null) {
				File zipfile = new File(uuid + ".zip");
				FileOutputStream outs = new FileOutputStream(zipfile);
				entity.writeTo(outs);
				outs.close();
				System.out.println("Results written to " + zipfile + ". Goodbye.");
			}
		} catch (Exception e) {
			e.printStackTrace();
		} finally {
			IOUtils.closeQuietly(client);
		}
	}
}