package de.persosim.websocket;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;
import org.json.JSONArray;
import org.json.JSONObject;

import de.persosim.driver.connector.pcsc.PcscListener;
import de.persosim.simulator.utils.HexString;

public class DefaultMessageHandler implements MessageHandler {

	private static final String EXCLUSIVE = "exclusive";
	private static final String IFD_ESTABLISH_CONTEXT = "IFDEstablishContext";
	private static final String IFD_ESTABLISH_CONTEXT_RESPONSE = "IFDEstablishContextResponse";
	private static final String IFD_CONNECT = "IFDConnect";
	private static final String IFD_CONNECT_RESPONSE = "IFDConnectResponse";
	private static final String IFD_DISCONNECT = "IFDDisconnect";
	private static final String IFD_DISCONNECT_RESPONSE = "IFDDisconnectResponse";
	
	
	private static final String MSG = "msg";
	private static final String RESULT_MINOR = "ResultMinor";
	private static final String RESULT_MAJOR = "ResultMajor";
	private static final String PROTOCOL = "Protocol";
	private static final String SLOT_HANDLE = "SlotHandle";
	private static final String CONTEXT_HANDLE = "ContextHandle";
	private static final String SLOT_NAME = "SlotName";
	String contextHandle = "PersoSimContextHandle";
	String slotHandle = null;
	private List<PcscListener> listeners;
	
	private static String SLOT_HANDLE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvw0123456789";
		
	public DefaultMessageHandler(List<PcscListener> listeners) {
		this.listeners = listeners;
	}


	@Override
	public String message(String incomingMessage) {
		BasicLogger.log(getClass(), "Received JSON message: " + System.lineSeparator() + incomingMessage, LogLevel.TRACE);
		
		JSONObject jsonMessage = new JSONObject(incomingMessage);

		String messageType = jsonMessage.getString(MSG);

		String contextHandle = null;
		if (jsonMessage.has(CONTEXT_HANDLE)) {
			contextHandle = jsonMessage.getString(CONTEXT_HANDLE);	
		}
		
		String slotHandle = null;
		if (jsonMessage.has(SLOT_HANDLE)) {
			slotHandle = jsonMessage.getString(SLOT_HANDLE);
		}
		
		JSONObject response = new JSONObject();
		switch (messageType) {
		case IFD_ESTABLISH_CONTEXT:
			String protocol = jsonMessage.getString(PROTOCOL);
			
			response.put(MSG, IFD_ESTABLISH_CONTEXT_RESPONSE);
			
			response.put(CONTEXT_HANDLE, this.contextHandle);
			
			response.put(RESULT_MAJOR, 0);
			response.put(RESULT_MINOR, 0);
			break;
		case IFD_CONNECT:
			String slotName = jsonMessage.getString(SLOT_NAME);
			boolean exclusive = jsonMessage.getBoolean(EXCLUSIVE);

			response.put(MSG, IFD_CONNECT_RESPONSE);
			
			response.put(CONTEXT_HANDLE, this.contextHandle);
			
			//Governikus AusweisApp expects slot handle == slot name
			//slotHandle = getSlotName(10);
			this.slotHandle = slotName;
			
			response.put(SLOT_HANDLE, this.slotHandle);
			
			response.put(RESULT_MAJOR, 0);
			response.put(RESULT_MINOR, 0);
			break;
		case IFD_DISCONNECT:

			response.put(MSG, IFD_DISCONNECT_RESPONSE);
			
			response.put(CONTEXT_HANDLE, this.contextHandle);
			
			response.put(SLOT_HANDLE, slotHandle);
			
			response.put(RESULT_MAJOR, 0);
			response.put(RESULT_MINOR, 0);
			
			slotHandle = null;
			break;
		case "Transmit":

			response.put(MSG, "IFDTransmitResponse");
			
			response.put(CONTEXT_HANDLE, this.contextHandle);
			
			response.put(SLOT_HANDLE, this.slotHandle);
			
			response.put(RESULT_MAJOR, 0);
			response.put(RESULT_MINOR, 0);
			
			JSONArray commandApdus = jsonMessage.getJSONArray("CommandAPDUs");
			
			for (int i = 0; i < commandApdus.length(); i++) {
				JSONObject currentApdu = commandApdus.getJSONObject(i);
				byte [] inputApdu = HexString.toByteArray(currentApdu.getString("InputAPDU"));
				short [] acceptableStatusCodes = getStatusCodesFromJson(currentApdu.getJSONArray("AcceptableStatusCodes"));
				//TODO implement transmit
			}
			
			response.put("ResponseAPDUs", new String [] {});
			
			slotHandle = null;
			break;
		}

		BasicLogger.log(getClass(), "Send JSON message: " + System.lineSeparator() + response.toString(), LogLevel.TRACE);
		return response.toString();
	}


	private short[] getStatusCodesFromJson(JSONArray jsonArray) {
		short [] result = new short [jsonArray.length()];
		for (int i = 0; i < jsonArray.length(); i++) {
			result[i] = Short.parseShort(jsonArray.getString(i), 16);
		}
		return result;
	}


	private String getSlotName(int length) {
		String result = "";
		for (int i = 0; i < length; i++) {
			result += SLOT_HANDLE_CHARACTERS.charAt(ThreadLocalRandom.current().nextInt(SLOT_HANDLE_CHARACTERS.length())); 
		}
		return result;
	}

}
