package de.persosim.websocket;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;
import org.json.JSONArray;
import org.json.JSONObject;

import de.persosim.driver.connector.NativeDriverInterface;
import de.persosim.driver.connector.UnsignedInteger;
import de.persosim.driver.connector.pcsc.PcscCallData;
import de.persosim.driver.connector.pcsc.PcscCallResult;
import de.persosim.driver.connector.pcsc.PcscConstants;
import de.persosim.driver.connector.pcsc.PcscListener;
import de.persosim.driver.connector.pcsc.SimplePcscCallResult;
import de.persosim.simulator.platform.Iso7816Lib;
import de.persosim.simulator.utils.HexString;
import de.persosim.simulator.utils.Utils;

public class DefaultMessageHandler implements MessageHandler {

	private static final String HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMAJOR_OK = "http://www.bsi.bund.de/ecard/api/1.1/resultmajor#ok";
	private static final String HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMINOR_OK = "http://www.bsi.bund.de/ecard/api/1.1/resultminor#ok";
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
	private String deviceName;

	UnsignedInteger lun = new UnsignedInteger(1);

	private static String SLOT_HANDLE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvw0123456789";

	public DefaultMessageHandler(List<PcscListener> listeners, String deviceName) {
		this.listeners = listeners;
		this.deviceName = deviceName;
	}

	@Override
	public String message(String incomingMessage) {
		BasicLogger.log(getClass(), "Received JSON message: " + System.lineSeparator() + incomingMessage,
				LogLevel.TRACE);

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

			// NOT IN SPEC
			response.put("IFDName", deviceName);

			response.put(RESULT_MAJOR, HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMAJOR_OK);
			response.put(RESULT_MINOR, HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMINOR_OK);
			break;
		case IFD_CONNECT:
			String slotName = jsonMessage.getString(SLOT_NAME);
			boolean exclusive = jsonMessage.getBoolean(EXCLUSIVE);

			response.put(MSG, IFD_CONNECT_RESPONSE);

			response.put(CONTEXT_HANDLE, this.contextHandle);

			// Governikus AusweisApp expects slot handle == slot name
			// slotHandle = getSlotName(10);
			this.slotHandle = slotName;

			response.put(SLOT_HANDLE, this.slotHandle);

			pcscPowerIcc();

			response.put(RESULT_MAJOR, HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMAJOR_OK);
			response.put(RESULT_MINOR, HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMINOR_OK);
			break;
		case IFD_DISCONNECT:

			response.put(MSG, IFD_DISCONNECT_RESPONSE);

			response.put(CONTEXT_HANDLE, this.contextHandle);

			response.put(SLOT_HANDLE, slotHandle);

			response.put(RESULT_MAJOR, HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMAJOR_OK);
			response.put(RESULT_MINOR, HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMINOR_OK);

			slotHandle = null;
			break;
		case "IFDTransmit":

			response.put(MSG, "IFDTransmitResponse");

			response.put(CONTEXT_HANDLE, this.contextHandle);

			response.put(SLOT_HANDLE, this.slotHandle);

			response.put(RESULT_MAJOR, HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMAJOR_OK);
			response.put(RESULT_MINOR, HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMINOR_OK);

			JSONArray commandApdus = jsonMessage.getJSONArray("CommandAPDUs");

			List<String> responseApdus = new LinkedList<>();

			for (int i = 0; i < commandApdus.length(); i++) {
				JSONObject currentApdu = commandApdus.getJSONObject(i);
				byte[] inputApdu = HexString.toByteArray(currentApdu.getString("InputAPDU"));

				Number[] acceptableStatusCodes = null;
				if (!currentApdu.isNull("AcceptableStatusCodes")) {
					JSONArray statusCodes = currentApdu.getJSONArray("AcceptableStatusCodes");
					acceptableStatusCodes = getStatusCodesFromJson(statusCodes);
				}

				byte[] responseApdu = pcscTransmit(inputApdu);

				short actualStatusCode = Iso7816Lib.getStatusWord(responseApdu);

				boolean statusCodeAcceptable = true;

				if (acceptableStatusCodes != null) {
					statusCodeAcceptable = false;
					for (Number currentAcceptable : acceptableStatusCodes) {
						boolean isByteAndAcceptable = currentAcceptable instanceof Byte
								&& currentAcceptable.equals(Utils.getFirstByteOfShort(actualStatusCode));
						boolean isShortAndAcceptable = currentAcceptable instanceof Short
								&& currentAcceptable.equals(actualStatusCode);
						if (isByteAndAcceptable || isShortAndAcceptable) {
							statusCodeAcceptable = true;
							break;
						}
					}
				}

				if (!statusCodeAcceptable) {
					break;
				}
				responseApdus.add(HexString.encode(responseApdu));
			}

			response.put("ResponseAPDUs", responseApdus);

			slotHandle = null;
			break;
		case "IFDGetStatus":
			slotName = jsonMessage.getString("SlotName");

			response.put(MSG, "IFDStatus");

			JSONObject caps = new JSONObject();
			caps.put("PACE", false);
			caps.put("eID", false);
			caps.put("eSign", false);
			caps.put("Destroy", false);
			response.put("PINCapabilities", caps);
			response.put("MaxAPDULength", Short.MAX_VALUE);
			response.put("ConnectedReader", true);
			response.put("CardAvailable", true);
			response.put("EFATR", "");
			response.put("EFDIR", "");

			response.put(CONTEXT_HANDLE, this.contextHandle);
			break;
		}

		BasicLogger.log(getClass(), "Send JSON message: " + System.lineSeparator() + response.toString(),
				LogLevel.TRACE);
		return response.toString();
	}

	private PcscCallResult doPcsc(PcscCallData callData) {
		PcscCallResult result = null;
		try {
			if (listeners != null) {
				for (PcscListener listener : listeners) {
					try {
						PcscCallResult currentResult = listener.processPcscCall(callData);
						if (result == null && currentResult != null) {
							// ignore all but the first result
							result = currentResult;
						}
					} catch (RuntimeException e) {
						BasicLogger.logException(getClass(),
								"Something went wrong while processing of the PCSC data by listener \""
										+ listener.getClass().getName() + "\"!\"",
								e, LogLevel.ERROR);
					}
				}
			} else {
				BasicLogger.log(getClass(), "No PCSC listeners registered!", LogLevel.WARN);
			}
		} catch (RuntimeException e) {
			BasicLogger.logException(getClass(), "Something went wrong while parsing the PCSC data!", e,
					LogLevel.ERROR);
		}
		if (result == null) {
			result = new SimplePcscCallResult(PcscConstants.IFD_NOT_SUPPORTED);
		}
		return result;
	}

	private void pcscPowerIcc() {
		UnsignedInteger function = new UnsignedInteger(NativeDriverInterface.VALUE_PCSC_FUNCTION_POWER_ICC);

		List<byte[]> parameters = new LinkedList<>();

		parameters.add(PcscConstants.IFD_POWER_UP.getAsByteArray());
		parameters.add(Utils.toShortestUnsignedByteArray(Integer.MAX_VALUE));

		PcscCallResult result = doPcsc(new PcscCallData(function, lun, parameters));

		if (!PcscConstants.IFD_SUCCESS.equals(result.getResponseCode())) {
			throw new IllegalStateException(
					"Call for power icc was not successful: " + result.getResponseCode().getAsHexString());
		}
	}

	private byte[] pcscTransmit(byte[] inputApdu) {
		UnsignedInteger function = new UnsignedInteger(NativeDriverInterface.VALUE_PCSC_FUNCTION_TRANSMIT_TO_ICC);

		List<byte[]> parameters = new LinkedList<>();

		parameters.add(inputApdu.clone());
		parameters.add(Utils.toShortestUnsignedByteArray(Integer.MAX_VALUE));

		PcscCallResult result = doPcsc(new PcscCallData(function, lun, parameters));

		if (PcscConstants.IFD_SUCCESS.equals(result.getResponseCode())) {
			return result.getData().get(0);
		}
		throw new IllegalStateException(
				"Call for transmit was not successful: " + result.getResponseCode().getAsHexString());
	}

	private Number[] getStatusCodesFromJson(JSONArray jsonArray) {
		Number[] result = new Number[jsonArray.length()];
		for (int i = 0; i < jsonArray.length(); i++) {
			int statusCodeLength = jsonArray.getString(i).length();
			if (statusCodeLength == 1) {
				result[i] = Byte.parseByte(jsonArray.getString(i), 16);
			} else if (statusCodeLength == 2) {
				result[i] = Short.parseShort(jsonArray.getString(i), 16);
			} else {
				throw new IllegalArgumentException("Status code can not have length " + statusCodeLength);
			}
		}
		return result;
	}

	private String getSlotName(int length) {
		String result = "";
		for (int i = 0; i < length; i++) {
			result += SLOT_HANDLE_CHARACTERS
					.charAt(ThreadLocalRandom.current().nextInt(SLOT_HANDLE_CHARACTERS.length()));
		}
		return result;
	}

}
