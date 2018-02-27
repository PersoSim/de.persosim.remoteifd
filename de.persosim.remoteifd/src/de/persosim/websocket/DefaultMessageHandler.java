package de.persosim.websocket;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;
import org.json.JSONArray;
import org.json.JSONObject;

import de.persosim.driver.connector.IfdInterface;
import de.persosim.driver.connector.UnsignedInteger;
import de.persosim.driver.connector.features.ModifyPinDirect;
import de.persosim.driver.connector.features.PersoSimPcscProcessor;
import de.persosim.driver.connector.pcsc.PcscCallData;
import de.persosim.driver.connector.pcsc.PcscCallResult;
import de.persosim.driver.connector.pcsc.PcscConstants;
import de.persosim.driver.connector.pcsc.PcscListener;
import de.persosim.driver.connector.pcsc.SimplePcscCallResult;
import de.persosim.simulator.apdu.CommandApdu;
import de.persosim.simulator.apdu.CommandApduFactory;
import de.persosim.simulator.platform.Iso7816Lib;
import de.persosim.simulator.tlv.ConstructedTlvDataObject;
import de.persosim.simulator.tlv.PrimitiveTlvDataObject;
import de.persosim.simulator.tlv.TlvConstants;
import de.persosim.simulator.tlv.TlvDataObject;
import de.persosim.simulator.tlv.TlvDataObjectContainer;
import de.persosim.simulator.utils.HexString;
import de.persosim.simulator.utils.Utils;

public class DefaultMessageHandler implements MessageHandler {

	private static final String IFD_GET_STATUS = "IFDGetStatus";
	private static final String IFD_ESTABLISH_PACE_CHANNEL_RESPONSE = "IFDEstablishPACEChannelResponse";
	private static final String IFD_ESTABLISH_PACE_CHANNEL = "IFDEstablishPACEChannel";
	private static final String IFD_NAME = "IFDName";
	private static final String IFD_TRANSMIT = "IFDTransmit";
	private static final String IFD_TRANSMIT_RESPONSE = "IFDTransmitResponse";
	private static final String IFD_ESTABLISH_CONTEXT = "IFDEstablishContext";
	private static final String IFD_ESTABLISH_CONTEXT_RESPONSE = "IFDEstablishContextResponse";
	private static final String IFD_CONNECT = "IFDConnect";
	private static final String IFD_CONNECT_RESPONSE = "IFDConnectResponse";
	private static final String IFD_DISCONNECT = "IFDDisconnect";
	private static final String IFD_DISCONNECT_RESPONSE = "IFDDisconnectResponse";
	private static final String IFD_ERROR = "IFDERROR";
	private static final String IFD_MODIFY_PIN = "IFDModifyPIN";
	private static final String IFD_MODIFY_PIN_RESPONSE = "IFDModifyPINResponse";
	
	private static final String MSG = "msg";
	private static final String RESULT_MINOR = "ResultMinor";
	private static final String RESULT_MAJOR = "ResultMajor";
	private static final String SLOT_HANDLE = "SlotHandle";
	private static final String CONTEXT_HANDLE = "ContextHandle";
	private static final String SLOT_NAME = "SlotName";
	private static final String COMMAND_APDUS = "CommandAPDUs"; 
	private static final String INPUT_APDU = "InputAPDU";
	private static final String ACCEPTABLE_STATUS_CODES = "AcceptableStatusCodes";
	private static final String EFDIR = "EFDIR";
	private static final String EFATR = "EFATR";
	private static final String CARD_AVAILABLE = "CardAvailable";
	private static final String CONNECTED_READER = "ConnectedReader";
	private static final String MAX_APDU_LENGTH = "MaxAPDULength";
	private static final String PIN_CAPABILITIES = "PINCapabilities";
	
	
	private static final byte CCID_FUNCTION_GET_READER_PACE_CAPABITILIES = 1;
	private static final byte CCID_FUNCTION_DESTROY_PACE_CHANNEL = 3;
	private static final byte CCID_FUNCTION_ESTABLISH_PACE_CHANNEL = 2;
	
	String contextHandle = "PersoSimContextHandle";
	String slotHandle = null;
	private List<PcscListener> listeners;
	private String deviceName;

	UnsignedInteger lun = new UnsignedInteger(1);
	private String slotName = "PersoSim Slot 1";

	private static final String SLOT_HANDLE_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvw0123456789";

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

		String incomingContextHandle = null;
		if (jsonMessage.has(CONTEXT_HANDLE)) {
			incomingContextHandle = jsonMessage.getString(CONTEXT_HANDLE);
		}

		String incomingSlotHandle = null;
		if (jsonMessage.has(SLOT_HANDLE)) {
			incomingSlotHandle = jsonMessage.getString(SLOT_HANDLE);
		}
		
		BasicLogger.log(getClass(), "Received Json message with type: " + messageType + ", ContextHandle: " + incomingContextHandle + ", SlotHandle: " + incomingSlotHandle, LogLevel.TRACE);

		JSONObject response = new JSONObject();
		switch (messageType) {
		case IFD_ESTABLISH_CONTEXT:

			response.put(MSG, IFD_ESTABLISH_CONTEXT_RESPONSE);

			response.put(CONTEXT_HANDLE, this.contextHandle);

			response.put(IFD_NAME, deviceName);

			setOkResult(response);
			break;
		case IFD_CONNECT:
			response.put(MSG, IFD_CONNECT_RESPONSE);
			response.put(CONTEXT_HANDLE, this.contextHandle);
			this.slotName = jsonMessage.getString(SLOT_NAME);
			this.slotHandle = getRandomString(10);
			
			response.put(SLOT_HANDLE, this.slotHandle);

			if (pcscPowerIcc(PcscConstants.IFD_POWER_UP)) {
				setOkResult(response);
			} else {
				setErrorResult(response, Tr03112codes.TERMINAL_RESULT_TERMINAL_NO_CARD);
			}
			break;
		case IFD_DISCONNECT:

			response.put(MSG, IFD_DISCONNECT_RESPONSE);

			response.put(CONTEXT_HANDLE, this.contextHandle);
			response.put(SLOT_HANDLE, this.slotHandle);

			pcscPowerIcc(PcscConstants.IFD_POWER_DOWN);
			setOkResult(response);

			this.slotHandle = null;
			break;
		case IFD_TRANSMIT:

			response.put(MSG, IFD_TRANSMIT_RESPONSE);

			response.put(CONTEXT_HANDLE, this.contextHandle);
			response.put(SLOT_HANDLE, this.slotHandle);

			JSONArray commandApdus = jsonMessage.getJSONArray(COMMAND_APDUS);

			List<String> responseApdus = handleApdus(commandApdus);
			
			response.put("ResponseAPDUs", responseApdus);

			setOkResult(response);
			
			break;
		case IFD_GET_STATUS:
			String incomingSlotName = jsonMessage.getString(SLOT_NAME);
			return getStatusMessage(incomingSlotName);
		case IFD_ESTABLISH_PACE_CHANNEL:
			response.put(MSG, IFD_ESTABLISH_PACE_CHANNEL_RESPONSE);

			response.put(CONTEXT_HANDLE, this.contextHandle);
			response.put(SLOT_HANDLE, this.slotHandle);

			byte[] pcscPerformEstablishPaceChannel = pcscPerformEstablishPaceChannel(HexString.toByteArray(jsonMessage.getString("InputData")));
			
			if (pcscPerformEstablishPaceChannel == null) {
				setErrorResult(response, Tr03112codes.TERMINAL_RESULT_TERMINAL_ACCESS_ERROR);
			} else {
				response.put("OutputData", HexString.encode(
						pcscPerformEstablishPaceChannel));
				setOkResult(response);
			}

			break;
		case IFD_MODIFY_PIN:
			response.put(MSG, IFD_MODIFY_PIN_RESPONSE);

			response.put(CONTEXT_HANDLE, this.contextHandle);
			response.put(SLOT_HANDLE, this.slotHandle);

			response.put("OutputData", HexString.encode(pcscPerformModifyPin(HexString.toByteArray(jsonMessage.getString("InputData")))));

			setOkResult(response);

			break;
		case IFD_ERROR:
			return null;
		default:
			response.put(MSG, IFD_ERROR);
			response.put(RESULT_MAJOR, Tr03112codes.RESULT_MAJOR_ERROR);
			response.put(RESULT_MINOR, Tr03112codes.TERMINAL_RESULT_TERMINAL_UNKNOWN_ACTION);
		}

		BasicLogger.log(getClass(), "Send JSON message: " + System.lineSeparator() + response.toString(),
				LogLevel.TRACE);
		return response.toString();
	}

	private List<String> handleApdus(JSONArray commandApdus) {
		List<String> responseApdus = new LinkedList<>();
		for (int i = 0; i < commandApdus.length(); i++) {
			JSONObject currentApdu = commandApdus.getJSONObject(i);
			byte[] inputApdu = HexString.toByteArray(currentApdu.getString(INPUT_APDU));

			Number[] acceptableStatusCodes = null;
			if (!currentApdu.isNull(ACCEPTABLE_STATUS_CODES)) {
				JSONArray statusCodes = currentApdu.getJSONArray(ACCEPTABLE_STATUS_CODES);
				acceptableStatusCodes = getStatusCodesFromJson(statusCodes);
			}

			byte[] responseApdu = pcscTransmit(inputApdu);

			short actualStatusCode = Iso7816Lib.getStatusWord(responseApdu);

			if (!checkStatusCodes(acceptableStatusCodes, actualStatusCode)) {
				break;
			}
			responseApdus.add(HexString.encode(responseApdu));
		}
		return responseApdus;
	}

	private boolean checkStatusCodes(Number[] acceptableStatusCodes, short actualStatusCode) {
		boolean statusCodeAcceptable = true;
		if (acceptableStatusCodes != null) {
			statusCodeAcceptable  = false;
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
		return statusCodeAcceptable;
	}

	private void setErrorResult(JSONObject response, String resultMinor) {
		response.put(RESULT_MAJOR, Tr03112codes.RESULT_MAJOR_ERROR);
		response.put(RESULT_MINOR, resultMinor);
	}

	private void setOkResult(JSONObject response) {
		response.put(RESULT_MAJOR, Tr03112codes.RESULT_MAJOR_OK);
		response.put(RESULT_MINOR, JSONObject.NULL);
	}

	private JSONObject convertPscsCapabilitiesToJson(byte[] capabilities) {
		// We are expecting one length byte and one byte for the bit field
		if (capabilities.length != 2 || capabilities [0] != 1) {
			throw new IllegalArgumentException("PACE capabilities in unexpected format");
		}
		
		JSONObject jsonCaps = new JSONObject();
		jsonCaps.put("PACE", ((byte)(PersoSimPcscProcessor.BITMAP_IFD_GENERIC_PACE_SUPPORT & capabilities[1])) == PersoSimPcscProcessor.BITMAP_IFD_GENERIC_PACE_SUPPORT);
		jsonCaps.put("eID", ((byte)(PersoSimPcscProcessor.BITMAP_EID_APPLICATION_SUPPORT & capabilities[1])) == PersoSimPcscProcessor.BITMAP_EID_APPLICATION_SUPPORT);
		jsonCaps.put("eSign", ((byte)(PersoSimPcscProcessor.BITMAP_QUALIFIED_SIGNATURE_FUNCTION & capabilities[1])) == PersoSimPcscProcessor.BITMAP_QUALIFIED_SIGNATURE_FUNCTION);
		jsonCaps.put("Destroy", ((byte)(PersoSimPcscProcessor.BITMAP_IFD_DESTROY_CHANNEL_SUPPORT & capabilities[1])) == PersoSimPcscProcessor.BITMAP_IFD_DESTROY_CHANNEL_SUPPORT);
		return jsonCaps;
	}

	private byte[] pcscPerformEstablishPaceChannel(byte[] ccidMappedApdu) {
		CommandApdu apdu = CommandApduFactory.createCommandApdu(ccidMappedApdu);

		UnsignedInteger controlCode = pcscPerformGetFeatures().get(PersoSimPcscProcessor.FEATURE_CONTROL_CODE);


		if (apdu.getP2() != 2) {
			throw new IllegalArgumentException("Unexpected CCID P2 value of " + HexString.hexifyByte(apdu.getP2()));
		}
		
		List<byte[]> parameters = new LinkedList<>();

		parameters.add(controlCode.getAsByteArray());
		
		if (!apdu.getCommandData().isEmpty()) {
			byte[] convertCcidToPcscInputBuffer = convertCcidToPcscInputBuffer(apdu);
			if (convertCcidToPcscInputBuffer == null) {
				return null;
			}
			parameters.add(convertCcidToPcscInputBuffer);
		}
		
		parameters.add(Utils.toShortestUnsignedByteArray(Integer.MAX_VALUE));

		PcscCallResult result = doPcsc(new PcscCallData(IfdInterface.PCSC_FUNCTION_DEVICE_CONTROL, lun, parameters));
		
		if (PcscConstants.IFD_SUCCESS.equals(result.getResponseCode())) {
			UnsignedInteger paceStatusCode = new UnsignedInteger(Arrays.copyOfRange(result.getData().get(0), 0, 4));
			if (result.getData().get(0).length > 6) {
				return convertPcscToCcidOutputBuffer(paceStatusCode,
						Arrays.copyOfRange(result.getData().get(0), 6, result.getData().get(0).length));
			} else {
				return null;
			}
			
		}
		throw new IllegalStateException("Call for performing establish pace channel was not successful: "
				+ result.getResponseCode().getAsHexString());
	}

	private byte [] pcscPerformModifyPin(byte[] byteArray) {
		byte [] failureResult = Utils.toUnsignedByteArray(0x6F00);
		
		if (byteArray.length < 4) {
			return failureResult;
		}
		
		if (!Arrays.equals(Arrays.copyOfRange(byteArray, 0, 4), HexString.toByteArray("FF9A0410"))) {
			return failureResult;
		}
		
		UnsignedInteger controlCode = pcscPerformGetFeatures().get(ModifyPinDirect.FEATURE_TAG);

		List<byte[]> parameters = new LinkedList<>();
		parameters.add(controlCode.getAsByteArray());
		parameters.add(Utils.toShortestUnsignedByteArray(Integer.MAX_VALUE));
		parameters.add(Utils.toShortestUnsignedByteArray(Integer.MAX_VALUE));
		
		PcscCallResult result = doPcsc(new PcscCallData(IfdInterface.PCSC_FUNCTION_DEVICE_CONTROL, lun, parameters));
		
		return result.getData().get(0);
	}

	private byte[] convertPcscToCcidOutputBuffer(UnsignedInteger errorCode, byte[] pcscOutputBuffer) {
		int offset = 2;
		short status = Utils.getShortFromUnsignedByteArray(Arrays.copyOfRange(pcscOutputBuffer, 0, offset));
		byte [] cardAccess = Utils.getValueFlippedByteOrder(pcscOutputBuffer, offset, 2);
		offset += cardAccess.length + 2;
		byte [] carCurrent = Utils.getValueFlippedByteOrder(pcscOutputBuffer, offset, 1);
		offset += carCurrent.length + 1;
		byte [] carPrevious = Utils.getValueFlippedByteOrder(pcscOutputBuffer, offset, 1);
		offset += carPrevious.length + 1;
		byte [] idicc = Utils.getValueFlippedByteOrder(pcscOutputBuffer, offset, 2);

		
		TlvDataObject errorCodeTlv = new ConstructedTlvDataObject(TlvConstants.TAG_A1, new PrimitiveTlvDataObject(TlvConstants.TAG_OCTET_STRING, errorCode.getAsByteArray()));
		TlvDataObject statusTlv = new ConstructedTlvDataObject(TlvConstants.TAG_A2, new PrimitiveTlvDataObject(TlvConstants.TAG_OCTET_STRING, Utils.toUnsignedByteArray(status)));
		TlvDataObject cardAccessTlv = new ConstructedTlvDataObject(TlvConstants.TAG_A3, new ConstructedTlvDataObject(cardAccess));
		ConstructedTlvDataObject sequence = new ConstructedTlvDataObject(TlvConstants.TAG_SEQUENCE, errorCodeTlv, statusTlv, cardAccessTlv);
		
		if (idicc.length > 0) {
			sequence.addTlvDataObject(new ConstructedTlvDataObject(TlvConstants.TAG_A4, new PrimitiveTlvDataObject(TlvConstants.TAG_OCTET_STRING, idicc)));
		}		
		if (carCurrent.length > 0) {
			sequence.addTlvDataObject(new ConstructedTlvDataObject(TlvConstants.TAG_A5, new PrimitiveTlvDataObject(TlvConstants.TAG_OCTET_STRING, carCurrent)));
		}		
		if (carPrevious.length > 0) {
			sequence.addTlvDataObject(new ConstructedTlvDataObject(TlvConstants.TAG_A6, new PrimitiveTlvDataObject(TlvConstants.TAG_OCTET_STRING, carPrevious)));
		}
		
		return Utils.appendBytes(sequence.toByteArray(), (byte) 0x90, (byte)0x00);
	}

	private byte[] convertCcidToPcscInputBuffer(CommandApdu apdu) {
		byte function = -1;
		
		switch (apdu.getP2()) {
		case CCID_FUNCTION_GET_READER_PACE_CAPABITILIES:
			function = PersoSimPcscProcessor.FUNCTION_GET_READER_PACE_CAPABILITIES;
			break;
		case CCID_FUNCTION_DESTROY_PACE_CHANNEL:
			function = PersoSimPcscProcessor.FUNCTION_DESTROY_PACE_CHANNEL;
			break;
		case CCID_FUNCTION_ESTABLISH_PACE_CHANNEL:
			function = PersoSimPcscProcessor.FUNCTION_ESTABLISH_PACE_CHANNEL;
			break;
		default:
			return null;
		}
		
		TlvDataObjectContainer inputData = apdu.getCommandDataObjectContainer();
		
		ConstructedTlvDataObject sequence = (ConstructedTlvDataObject) inputData.getTlvDataObject(TlvConstants.TAG_SEQUENCE);
		byte passwordId = ((ConstructedTlvDataObject)sequence.getTlvDataObject(TlvConstants.TAG_A1)).getTlvDataObject(TlvConstants.TAG_INTEGER).getValueField()[0];
		byte [] transmittedPassword = sequence.containsTlvDataObject(TlvConstants.TAG_A2) ? ((ConstructedTlvDataObject)sequence.getTlvDataObject(TlvConstants.TAG_A2)).getTlvDataObject(TlvConstants.TAG_NUMERIC_STRING).getValueField() : null;
		byte [] chat = sequence.containsTlvDataObject(TlvConstants.TAG_A3) ? ((ConstructedTlvDataObject)sequence.getTlvDataObject(TlvConstants.TAG_A3)).getTlvDataObject(TlvConstants.TAG_OCTET_STRING).getValueField() : null;
		byte [] certificateDescription = sequence.containsTlvDataObject(TlvConstants.TAG_A4) ? ((ConstructedTlvDataObject)sequence.getTlvDataObject(TlvConstants.TAG_A4)).getTlvDataObject(TlvConstants.TAG_SEQUENCE).getValueField() : null;
		byte [] hashOid = sequence.containsTlvDataObject(TlvConstants.TAG_A5) ? ((ConstructedTlvDataObject)sequence.getTlvDataObject(TlvConstants.TAG_A5)).getTlvDataObject(TlvConstants.TAG_OID).getValueField() : null;
		
		byte [] pcscInputData = new byte [] { passwordId };
		if (chat != null) {
			pcscInputData = Utils.concatByteArrays(pcscInputData, Utils.createLengthValueFlippedByteOrder(chat, 1));
		} else {
			pcscInputData = Utils.appendBytes(pcscInputData, (byte) 0);
		}
		if (transmittedPassword != null) {
			pcscInputData = Utils.concatByteArrays(pcscInputData, Utils.createLengthValueFlippedByteOrder(transmittedPassword, 1));
		} else {
			pcscInputData = Utils.appendBytes(pcscInputData, (byte) 0);
		}
		if (certificateDescription != null) {
			pcscInputData = Utils.concatByteArrays(pcscInputData, Utils.createLengthValueFlippedByteOrder(certificateDescription, 2));
		} else {
			pcscInputData = Utils.appendBytes(pcscInputData, (byte) 0, (byte) 0);
		}
		if (hashOid != null) {
			pcscInputData = Utils.concatByteArrays(pcscInputData, Utils.createLengthValueFlippedByteOrder(hashOid, 2));
		} else {
			pcscInputData = Utils.appendBytes(pcscInputData, (byte) 0, (byte) 0);
		}
		

		byte [] pcscFormattedInputData = new byte [] { function };
		pcscFormattedInputData = Utils.concatByteArrays(pcscFormattedInputData, Utils.createLengthValueFlippedByteOrder(pcscInputData, 2));
		return pcscFormattedInputData;
	}
	
	private Map<Byte, UnsignedInteger> pcscPerformGetFeatures(){
		List<byte[]> parameters = new LinkedList<>();

		parameters.add(PcscConstants.CONTROL_CODE_GET_FEATURE_REQUEST.getAsByteArray());

		parameters.add(new byte [0]);
		
		parameters.add(Utils.toShortestUnsignedByteArray(Integer.MAX_VALUE));
		
		PcscCallResult result = doPcsc(new PcscCallData(IfdInterface.PCSC_FUNCTION_DEVICE_CONTROL, lun, parameters));
		
		if (PcscConstants.IFD_SUCCESS.equals(result.getResponseCode())) {
			Map<Byte, UnsignedInteger> defs = new HashMap<>();
			if (!result.getData().isEmpty()) {
				byte[] features = result.getData().get(0);

				for (int i = 0; i < features.length; i += 6) {
					defs.put(features[i], new UnsignedInteger(Arrays.copyOfRange(features, i + 2, i + 6)));
				}

			}
			return defs;
		}

		throw new IllegalStateException("Call for performing get features was not successful: "
				+ result.getResponseCode().getAsHexString());
	}

	private byte[] pcscPerformGetReaderPaceCapabilities() {

		UnsignedInteger controlCode = pcscPerformGetFeatures().get(PersoSimPcscProcessor.FEATURE_CONTROL_CODE);
		
		if (controlCode == null) {
			//no PACE feature
			return new byte [] {1,0};
			
		}

		List<byte[]> parameters = new LinkedList<>();

		parameters.add(controlCode.getAsByteArray());

		parameters.add(new byte [] {PersoSimPcscProcessor.FUNCTION_GET_READER_PACE_CAPABILITIES});
		
		parameters.add(Utils.toShortestUnsignedByteArray(Integer.MAX_VALUE));

		PcscCallResult result = doPcsc(new PcscCallData(IfdInterface.PCSC_FUNCTION_DEVICE_CONTROL, lun, parameters));

		if (PcscConstants.IFD_SUCCESS.equals(result.getResponseCode()) && PersoSimPcscProcessor.RESULT_NO_ERROR
				.equals(new UnsignedInteger(Arrays.copyOfRange(result.getData().get(0), 0, 4))) && !result.getData().isEmpty()) {
			return Arrays.copyOfRange(result.getData().get(0), 6, 8);
		}
		
		throw new IllegalStateException("Call for performing get reader pace capabilities was not successful: "
				+ result.getResponseCode().getAsHexString());
	}

	private PcscCallResult doPcsc(PcscCallData callData) {
		PcscCallResult result = null;
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
		if (result == null) {
			result = new SimplePcscCallResult(PcscConstants.IFD_NOT_SUPPORTED);
		}
		return result;
	}

	private boolean pcscPowerIcc(UnsignedInteger pcscPowerFunction) {
		List<byte[]> parameters = new LinkedList<>();

		parameters.add(pcscPowerFunction.getAsByteArray());
		parameters.add(Utils.toShortestUnsignedByteArray(Integer.MAX_VALUE));

		PcscCallResult result = doPcsc(
				new PcscCallData(IfdInterface.PCSC_FUNCTION_POWER_ICC, lun, parameters));

		return PcscConstants.IFD_SUCCESS.equals(result.getResponseCode());
	}

	private byte[] pcscTransmit(byte[] inputApdu) {
		UnsignedInteger function = new UnsignedInteger(IfdInterface.VALUE_PCSC_FUNCTION_TRANSMIT_TO_ICC);

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

	private String getRandomString(int length) {
		StringBuilder builder = new StringBuilder();
		for (int i = 0; i < length; i++) {
			builder.append(SLOT_HANDLE_CHARACTERS.charAt(ThreadLocalRandom.current().nextInt(SLOT_HANDLE_CHARACTERS.length())));
		}
		return builder.toString();
	}

	@Override
	public boolean isIccAvailable() {
		PcscCallResult result = doPcsc(new PcscCallData(IfdInterface.PCSC_FUNCTION_IS_ICC_PRESENT, new UnsignedInteger(0), Collections.emptyList()));
		return result.getResponseCode().equals(PcscConstants.IFD_ICC_PRESENT);
	}
	
	private String getStatusMessage(String slotName) {
		if (slotName == null || slotName.isEmpty() || (slotName.equals(this.slotName))) {
			JSONObject response = new JSONObject();
			response.put(MSG, "IFDStatus");
			response.put(CONTEXT_HANDLE, this.contextHandle);
			response.put(SLOT_NAME, this.slotName);

			response.put(PIN_CAPABILITIES, convertPscsCapabilitiesToJson(pcscPerformGetReaderPaceCapabilities()));
			response.put(MAX_APDU_LENGTH, Short.MAX_VALUE);
			response.put(CONNECTED_READER, true);
			response.put(CARD_AVAILABLE, isIccAvailable());
			response.put(EFATR, JSONObject.NULL);
			response.put(EFDIR, JSONObject.NULL);
			return response.toString();
		} else {
			return null;
		}
	}

	@Override
	public String getStatusMessage() {
		return getStatusMessage(null);
	}

}
