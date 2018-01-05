package de.persosim.websocket;

import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;
import org.json.JSONArray;
import org.json.JSONObject;

import de.persosim.driver.connector.NativeDriverInterface;
import de.persosim.driver.connector.UnsignedInteger;
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
	private static final String HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMAJOR_OK = "http://www.bsi.bund.de/ecard/api/1.1/resultmajor#ok";
	private static final String HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMINOR_OK = "http://www.bsi.bund.de/ecard/api/1.1/resultminor#ok";
	
	
	private static final byte CCID_FUNCTION_GET_READER_PACE_CAPABITILIES = 1;
	private static final byte CCID_FUNCTION_DESTROY_PACE_CHANNEL = 3;
	private static final byte CCID_FUNCTION_ESTABLISH_PACE_CHANNEL = 2;
	
	String contextHandle = "PersoSimContextHandle";
	String slotHandle = null;
	private List<PcscListener> listeners;
	private String deviceName;

	UnsignedInteger lun = new UnsignedInteger(1);
	private String slotName;

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
		
		BasicLogger.log(getClass(), "Received Json message with type: " + messageType + ", ContextHandle: " + contextHandle + ", SlotHandle: " + slotHandle, LogLevel.TRACE);

		JSONObject response = new JSONObject();
		switch (messageType) {
		case IFD_ESTABLISH_CONTEXT:

			response.put(MSG, IFD_ESTABLISH_CONTEXT_RESPONSE);

			response.put(CONTEXT_HANDLE, this.contextHandle);

			response.put(IFD_NAME, deviceName);

			response.put(RESULT_MAJOR, HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMAJOR_OK);
			response.put(RESULT_MINOR, HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMINOR_OK);
			break;
		case IFD_CONNECT:
			String slotName = jsonMessage.getString(SLOT_NAME);

			// TODO check for correct slot name
			
			response.put(MSG, IFD_CONNECT_RESPONSE);
			response.put(CONTEXT_HANDLE, this.contextHandle);

			// Governikus AusweisApp expects slot handle == slot name
			// slotHandle = getSlotName(10);
			this.slotHandle = slotName;

			response.put(SLOT_HANDLE, this.slotHandle);

			pcscPowerIcc(PcscConstants.IFD_POWER_UP);

			response.put(RESULT_MAJOR, HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMAJOR_OK);
			response.put(RESULT_MINOR, HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMINOR_OK);
			break;
		case IFD_DISCONNECT:

			response.put(MSG, IFD_DISCONNECT_RESPONSE);

			response.put(CONTEXT_HANDLE, this.contextHandle);
			response.put(SLOT_HANDLE, this.slotHandle);

			pcscPowerIcc(PcscConstants.IFD_POWER_DOWN);

			response.put(RESULT_MAJOR, HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMAJOR_OK);
			response.put(RESULT_MINOR, HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMINOR_OK);

			this.slotHandle = null;
			break;
		case IFD_TRANSMIT:

			response.put(MSG, IFD_TRANSMIT_RESPONSE);

			response.put(CONTEXT_HANDLE, this.contextHandle);
			response.put(SLOT_HANDLE, this.slotHandle);

			response.put(RESULT_MAJOR, HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMAJOR_OK);
			response.put(RESULT_MINOR, HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMINOR_OK);

			JSONArray commandApdus = jsonMessage.getJSONArray(COMMAND_APDUS);

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
			
			break;
		case IFD_GET_STATUS:
			response.put(MSG, "IFDStatus");

			// TODO Handle differing slot handle
			
			response.put(CONTEXT_HANDLE, this.contextHandle);
			this.slotName = "PersoSim Slot 1";
			response.put(SLOT_NAME, this.slotName);

			response.put(PIN_CAPABILITIES, convertPscsCapabilitiesToJson(pcscPerformGetReaderPaceCapabilities()));
			response.put(MAX_APDU_LENGTH, Short.MAX_VALUE);
			response.put(CONNECTED_READER, true);
			response.put(CARD_AVAILABLE, getCardStatus());
			response.put(EFATR, "");
			response.put(EFDIR, "");
			break;
		case IFD_ESTABLISH_PACE_CHANNEL:
			response.put(MSG, IFD_ESTABLISH_PACE_CHANNEL_RESPONSE);

			response.put(CONTEXT_HANDLE, this.contextHandle);
			response.put(SLOT_HANDLE, this.slotHandle);

			response.put("OutputData", HexString.encode(
					pcscPerformEstablishPaceChannel(HexString.toByteArray(jsonMessage.getString("InputData")))));

			response.put(RESULT_MAJOR, HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMAJOR_OK);
			response.put(RESULT_MINOR, HTTP_WWW_BSI_BUND_DE_ECARD_API_1_1_RESULTMINOR_OK);

			response.put(CONTEXT_HANDLE, this.contextHandle);
			break;
		}

		BasicLogger.log(getClass(), "Send JSON message: " + System.lineSeparator() + response.toString(),
				LogLevel.TRACE);
		return response.toString();
	}
	
	private boolean getCardStatus() {
		// As long as this is used only in the PersoSim-GUI application, this is a reasonable assumption
		return true;
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
			parameters.add(convertCcidToPcscInputBuffer(apdu));
		}
		
		parameters.add(Utils.toShortestUnsignedByteArray(Integer.MAX_VALUE));

		PcscCallResult result = doPcsc(new PcscCallData(NativeDriverInterface.PCSC_FUNCTION_DEVICE_CONTROL, lun, parameters));
		
		if (PcscConstants.IFD_SUCCESS.equals(result.getResponseCode())) {
			UnsignedInteger paceErrorCode = new UnsignedInteger(Arrays.copyOfRange(result.getData().get(0), 0, 4));
			if (PersoSimPcscProcessor.RESULT_NO_ERROR.equals(paceErrorCode)) {
				if (result.getData().get(0).length > 6) {
					return convertPcscToCcidOutputBuffer(paceErrorCode, Arrays.copyOfRange(result.getData().get(0), 6, result.getData().get(0).length));	
				} else {
					return null;
				}
			}
			
		}
		throw new IllegalStateException("Call for performing establish pace channel was not successful: "
				+ result.getResponseCode().getAsHexString());
	}
	
	private byte[] convertPcscToCcidOutputBuffer(UnsignedInteger errorCode, byte[] pcscOutputBuffer) {
		int offset = 0;
		short status = Utils.getShortFromUnsignedByteArray(Arrays.copyOfRange(pcscOutputBuffer, offset, offset += 2));
		byte [] cardAccess = Utils.getValueFlippedByteOrder(pcscOutputBuffer, offset, 2);
		offset += cardAccess.length + 2;
		byte [] carCurrent = Utils.getValueFlippedByteOrder(pcscOutputBuffer, offset, 1);
		offset += carCurrent.length + 1;
		byte [] carPrevious = Utils.getValueFlippedByteOrder(pcscOutputBuffer, offset, 1);
		offset += carPrevious.length + 1;
		byte [] idicc = Utils.getValueFlippedByteOrder(pcscOutputBuffer, offset, 2);
		offset += idicc.length + 1;

		
		TlvDataObject errorCodeTlv = new ConstructedTlvDataObject(TlvConstants.TAG_A1, new PrimitiveTlvDataObject(TlvConstants.TAG_OCTET_STRING, errorCode.getAsByteArray()));
		TlvDataObject statusTlv = new ConstructedTlvDataObject(TlvConstants.TAG_A2, new PrimitiveTlvDataObject(TlvConstants.TAG_OCTET_STRING, Utils.toUnsignedByteArray(status)));
		TlvDataObject cardAccessTlv = new ConstructedTlvDataObject(TlvConstants.TAG_A3, new ConstructedTlvDataObject(cardAccess));
		ConstructedTlvDataObject sequence = new ConstructedTlvDataObject(TlvConstants.TAG_SEQUENCE, errorCodeTlv, statusTlv, cardAccessTlv);
		
		if (idicc != null && idicc.length > 0) {
			sequence.addTlvDataObject(new ConstructedTlvDataObject(TlvConstants.TAG_A4, new PrimitiveTlvDataObject(TlvConstants.TAG_OCTET_STRING, idicc)));
		}		
		if (carCurrent != null && carCurrent.length > 0) {
			sequence.addTlvDataObject(new ConstructedTlvDataObject(TlvConstants.TAG_A5, new PrimitiveTlvDataObject(TlvConstants.TAG_OCTET_STRING, carCurrent)));
		}		
		if (carPrevious != null && carPrevious.length > 0) {
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
		
		PcscCallResult result = doPcsc(new PcscCallData(NativeDriverInterface.PCSC_FUNCTION_DEVICE_CONTROL, lun, parameters));
		
		if (PcscConstants.IFD_SUCCESS.equals(result.getResponseCode())) {
			Map<Byte, UnsignedInteger> defs = new HashMap<>();
			if (result.getData().size() > 0) {
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

		PcscCallResult result = doPcsc(new PcscCallData(NativeDriverInterface.PCSC_FUNCTION_DEVICE_CONTROL, lun, parameters));

		if (PcscConstants.IFD_SUCCESS.equals(result.getResponseCode())) {
			if (PersoSimPcscProcessor.RESULT_NO_ERROR.equals(new UnsignedInteger(Arrays.copyOfRange(result.getData().get(0), 0, 4)))) {
				if (result.getData().size() > 0) {
					return Arrays.copyOfRange(result.getData().get(0), 6, 8);	
				}
			}
			
		}
		throw new IllegalStateException("Call for performing establish pace channel was not successful: "
				+ result.getResponseCode().getAsHexString());
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

	private void pcscPowerIcc(UnsignedInteger pcscPowerFunction) {
		List<byte[]> parameters = new LinkedList<>();

		parameters.add(pcscPowerFunction.getAsByteArray());
		parameters.add(Utils.toShortestUnsignedByteArray(Integer.MAX_VALUE));

		PcscCallResult result = doPcsc(
				new PcscCallData(NativeDriverInterface.PCSC_FUNCTION_POWER_ICC, lun, parameters));

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

	// will be used when AusweisApp handles slot names correctly
	@SuppressWarnings("unused")
	private String getSlotName(int length) {
		String result = "";
		for (int i = 0; i < length; i++) {
			result += SLOT_HANDLE_CHARACTERS
					.charAt(ThreadLocalRandom.current().nextInt(SLOT_HANDLE_CHARACTERS.length()));
		}
		return result;
	}

}
