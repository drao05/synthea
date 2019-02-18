package org.mitre.synthea.webservice;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Component
public class SocketHandler extends TextWebSocketHandler {
	
	private final static Logger LOGGER = LoggerFactory.getLogger(SocketHandler.class);

	private Map<String, WebSocketSession> sessions;

	@Autowired
	private RequestService requestService;
	
	public SocketHandler() {
		super();
		sessions = new HashMap<String, WebSocketSession>();
	}
	

	@Override
	public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
		try {
			if (!status.equals(CloseStatus.NORMAL)) {
				session.close();
			}
		} catch (IOException ioex) {
			LOGGER.error("Cannot close session in afterConnectionClosed ", ioex);
		}
		 
		sessions.values().remove(session);
	}
		
	@Override
	public void handleTextMessage(WebSocketSession session, TextMessage message) throws InterruptedException, IOException {
		try {
			JSONObject json = new JSONObject(message.getPayload());
			String operation = json.getString("operation");
			switch(operation) {
				case "configure":
					try {
						String configurationStr = json.get("configuration").toString();
			    		Request request = requestService.createRequest(configurationStr, true);
			    		sessions.put(request.getUuid(), session);
			    		sendMessage(session, "{ \"status\": \"Configured\", \"uuid\": \"" + request.getUuid() +"\", \"configuration\": " + request.getConfiguration().toString() + " }");
			    		return;
			    	} catch(JSONException jex) {
			    		sendMessage(session, "{ \"error\": \"Could not process specified configuration\" }");
			    	}
					break;
				case "update-request":
					try {
						String uuid = json.getString("uuid");
						WebSocketSession mappedSession = sessions.get(uuid);
						Request request = requestService.getRequest(uuid);
				    	if (request == null) {
				    		sendMessage(mappedSession, "{ \"error\": \"Request not found (may be finished)\" }");
				    		return;
				    	}
				    	
				    	if (request.isFinished()) {
				    		sendMessage(mappedSession, "{ \"error\": \"Request has finished\" }");
				    		return;
				    	}
				    	
				    	String configurationStr = json.get("configuration").toString();
			    		request.updateConfig(new JSONObject(configurationStr));
			    		sendMessage(session, "{ \"status\": \"Configured request\" }");
			    		return;
			    	} catch(JSONException jex) {
			    		sendMessage(session, "{ \"error\": \"UUID missing or could not process specified configuration\" }");
			    	}
					break;
				case "start":
					try {
						String uuid = json.getString("uuid");
						WebSocketSession mappedSession = sessions.get(uuid);
						Request request = requestService.getRequest(uuid);
				    	if (request == null) {
				    		sendMessage(mappedSession, "{ \"error\": \"Request not found (may be finished)\" }");
				    		return;
				    	}
				    	
				    	if (request.isFinished()) {
				    		sendMessage(mappedSession, "{ \"error\": \"Request has finished\" }");
				    		return;
				    	}
				    	
				    	if (!request.isStarted()) {
				    		
				    		// Request has not started yet
				    		request.start();
				    		sendMessage(mappedSession, "{ \"status\": \"Started\" }");
				    		return;
				    	}
				    	
				    	sendMessage(mappedSession, "{ \"status\": \"Already running\" }");
				    	return;
					} catch(JSONException jex) {
						sendMessage(session, "{ \"error\": \"UUID required\" }");
					}
					break;
				case "stop":
					try {
						String uuid = json.getString("uuid");
						WebSocketSession mappedSession = sessions.get(uuid);
						Request request = requestService.getRequest(uuid);
				    	if (request == null) {
				    		sendMessage(mappedSession, "{ \"error\": \"Request not found (may be finished)\" }");
				    		return;
				    	}
				    	
				    	if (!request.isStarted()) {
				    		sendMessage(mappedSession, "{ \"error\": \"Request has not started yet\" }");
				    		return;
				    	}
				    	
				    	if (request.isFinished()) {
				    		sendMessage(mappedSession, "{ \"error\": \"Request has finished\" }");
				    		return;
				    	}
				    	
				    	if (request.isStopped()) {
				    		sendMessage(mappedSession, "{ \"status\": \"Already stopped\" }");
				    		return;
				    	} else {
				    		request.stop();
				    		sendMessage(mappedSession, "{ \"status\": \"Stopped\" }");
				    		return;
				    	}
					} catch(JSONException jex) {
						sendMessage(session, "{ \"error\": \"UUID required\" }");
					}
					break;
				default:
					LOGGER.error("Unsupported operation: " + operation );
					sendMessage(session, "{ \"error\": \"Unsupported operation: " + operation + "\" }");
					break;
			}
		} catch(JSONException jex) {
			LOGGER.error("Error processing JSON content", jex);
		}
	}
	
	public void sendMessage(String uuid, String message) {
		WebSocketSession session = sessions.get(uuid);
		if (session != null) {
			sendMessage(session, message);
		} else {
			LOGGER.error("No session found for request " + uuid);
		}
	}
	
	private void sendMessage(WebSocketSession session, String message) {
		try {
			synchronized(session) {
				session.sendMessage(new TextMessage(message));
			}
		} catch (IOException ioex) {
			LOGGER.error("Error while send message", ioex);
		}
	}
}
