package de.persosim.websocket;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Random;
import java.util.Scanner;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;

import de.persosim.simulator.utils.HexString;
import de.persosim.websocket.Frame.Opcode;

public class WebSocketProtocol {

	private DataInputStream inputStream;
	private DataOutputStream outputStream;
	private boolean stopped = false;
	private Random random = new SecureRandom();
	private MessageHandler messageHandler;

	public WebSocketProtocol(InputStream inputStream, OutputStream outputStream, MessageHandler messageHandler) {
		this.inputStream = new DataInputStream(inputStream);
		this.outputStream = new DataOutputStream(outputStream);
		this.messageHandler = messageHandler;
	}

	public void handleConnection() {

		ConnectionState connectionState = ConnectionState.NEW;

		while (!stopped) {
			switch (connectionState) {
			case NEW:
				if (handleHandshake()) {
					connectionState = ConnectionState.ESTABLISHED;
				}
				break;
			case ESTABLISHED:
				connectionState = handleFragmentedFrames();
				break;
			case CLOSED:
				return;
			default:
				break;
			}
		}
	}

	private boolean handleHandshake() {

		try {
			// Do not close since it closes the underlying inputStream which is still needed
			Scanner scanner = new Scanner(inputStream, StandardCharsets.UTF_8.name());
			scanner.useDelimiter("\\r\\n\\r\\n");

			String data = scanner.next();

			BasicLogger.log(getClass(), "Received message for websocket handshake: " + System.lineSeparator() + data,
					LogLevel.DEBUG);

			Matcher get = Pattern.compile("^GET").matcher(data);

			if (!get.find()) {
				BasicLogger.log(getClass(), "No GET found in handshake message");
				return false;
			}

			Matcher match = Pattern.compile("Sec-WebSocket-Key: (.*)").matcher(data);

			if (!match.find()) {

				BasicLogger.log(getClass(), "No Sec-WebSocket-Key found in handshake message");
				return false;
			}

			String challengeResponse = Base64.getEncoder().encodeToString(MessageDigest.getInstance("SHA-1").digest(
					(match.group(1) + "258EAFA5-E914-47DA-95CA-C5AB0DC85B11").getBytes(StandardCharsets.UTF_8)));
			String response = ("HTTP/1.1 101 Switching Protocols\r\n" + "Connection: Upgrade\r\n"
					+ "Upgrade: websocket\r\n" + "Sec-WebSocket-Accept: " + challengeResponse + "\r\n\r\n");

			BasicLogger.log(getClass(),
					"Sending response message for websocket handshake: " + System.lineSeparator() + response,
					LogLevel.DEBUG);
			byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
			outputStream.write(responseBytes, 0, responseBytes.length);
			outputStream.flush();
			return true;
		} catch (NoSuchAlgorithmException | IOException e) {
			BasicLogger.logException(getClass(), e);
			return false;
		}
	}

	private ConnectionState handleFragmentedFrames() {
		Frame joinedFrame = readFrame();

		while (!joinedFrame.getFin()) {
			joinedFrame.appendFrame(readFrame());
		}

		return handleFrame(joinedFrame);
	}

	private ConnectionState handleFrame(Frame curFrame) {
		switch (curFrame.getOpcode()) {
		case CLOSE:
			// Echo first to bytes on close (status code, see RFC6455 5.5.1)
			// FIXME allow closed socket on other side
			if (curFrame.getPayload().length > 2) {
				writeFrame(createBasicFrame(Opcode.CLOSE,
						new byte[] { curFrame.getPayload()[1], curFrame.getPayload()[2] }), getMaskingKey());

			} else {

				writeFrame(createBasicFrame(Opcode.CLOSE, new byte[0]), getMaskingKey());
			}
			return ConnectionState.CLOSED;
		case PING:
			writeFrame(createBasicFrame(Opcode.PONG), getMaskingKey());
			return ConnectionState.ESTABLISHED;
		case TEXT:
			// writeFrame(handleTextFrame(curFrame), getMaskingKey());
			writeFrame(handleTextFrame(curFrame), null);
			return ConnectionState.ESTABLISHED;
		default:
			BasicLogger.log(getClass(), "Got message with unhandled opcode: " + curFrame.toString(), LogLevel.WARN);
			writeFrame(createBasicFrame(Opcode.CLOSE), getMaskingKey());
			return ConnectionState.CLOSED;
		}
	}

	private Frame createBasicFrame(Opcode opcode, byte[] payload) {
		Frame result = createBasicFrame(opcode);
		result.setPayload(payload);
		return result;
	}

	private Frame createBasicFrame(Opcode opcode) {
		Frame result = new Frame();
		result.setFin(true);
		result.setOpcode(opcode);
		return result;
	}

	private Frame handleTextFrame(Frame frame) {
		return createBasicFrame(Opcode.TEXT, messageHandler
				.message(new String(frame.getPayload(), StandardCharsets.UTF_8)).getBytes(StandardCharsets.UTF_8));
	}

	private byte[] getMaskingKey() {
		byte[] result = new byte[4];
		random.nextBytes(result);
		return result;
	}

	private void writeFrame(Frame frame, byte[] maskingKey) {

		short header = 0;

		header |= frame.getFin() ? 0x8000 : 0;
		// TODO set RSV
		header |= (frame.getOpcode().getValue() << 8);

		if (maskingKey != null) {
			header |= 0x80; // MASK bit
		}

		if (frame.getPayload().length <= 125) {
			header |= frame.getPayload().length;
		} else if (frame.getPayload().length <= 65535) {
			header |= 126;
		} else {
			header |= 127;
		}

		StringBuffer logMessage = new StringBuffer();
		ByteBuffer message = ByteBuffer.allocate(1024);

		try {
			message.putShort(header);

			if (maskingKey != null) {
				message.put(maskingKey);
			}

			logMessage.append("Sent frame: " + HexString.hexifyShort(header));

			if (frame.getPayload().length <= 65535) {
				message.putShort((short) (0xFFFF & frame.getPayload().length));
				logMessage.append(HexString.hexifyShort((frame.getPayload().length)));
			} else {
				message.putLong(frame.getPayload().length);
				logMessage.append(Long.toHexString((frame.getPayload().length)));
			}

			int sentBytes = 0;
			for (byte current : frame.getPayload()) {
				if (maskingKey != null) {
					current = (byte) (current ^ maskingKey[sentBytes % 4]);
				}
				message.put((byte) (0xFF & current));
			}

			message.flip();
			byte[] toWrite = new byte[message.remaining()];
			message.get(toWrite);
			outputStream.write(toWrite);

			for (byte current : toWrite) {
				logMessage.append(HexString.hexifyByte(current));
			}
			BasicLogger.log(getClass(), logMessage.toString(), LogLevel.TRACE);

		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	private Frame readFrame() {
		try {
			Frame result = new Frame();

			int header = inputStream.readUnsignedShort();

			boolean fin = (header & 0x8000) == 0x8000;
			boolean rsv1 = (header & 0x4000) == 0x4000;
			boolean rsv2 = (header & 0x2000) == 0x2000;
			boolean rsv3 = (header & 0x1000) == 0x1000;
			int opcode = (header & 0xF00) >>> 8;

			boolean mask = (header & 0x80) == 0x80;

			long payloadLength = (header & 0x7F);

			if (payloadLength == 126) {
				payloadLength = inputStream.readShort();
			} else if (payloadLength == 127) {
				payloadLength = inputStream.readLong();
			}

			byte[] maskingKey = new byte[4];

			if (mask) {
				int readMaskingKeyBytes = inputStream.read(maskingKey);
				if (readMaskingKeyBytes != 4) {
					throw new IllegalArgumentException("Masking key incomplete");
				}
			}

			String logMessage = "Received Frame Header:" + System.lineSeparator() + "FIN: " + fin
					+ System.lineSeparator() + "Opcode: " + opcode + System.lineSeparator() + "Mask: " + mask
					+ System.lineSeparator() + "Payload Length: " + payloadLength;
			BasicLogger.log(getClass(), logMessage, LogLevel.DEBUG);

			// TODO handle extension data

			int readBytes = 0;

			ByteArrayOutputStream payload = new ByteArrayOutputStream();

			while (readBytes < payloadLength) {
				byte current = inputStream.readByte();
				if (mask) {
					current = (byte) (current ^ maskingKey[readBytes % 4]);
				}
				readBytes++;
				payload.write(current);
			}

			logMessage = "Received message:" + System.lineSeparator();

			for (byte b : payload.toByteArray()) {
				logMessage += Integer.toHexString((byte) (0xFF & b));
			}

			BasicLogger.log(getClass(), logMessage, LogLevel.TRACE);

			result.setFin(fin);
			result.setRSV1(rsv1);
			result.setRSV2(rsv2);
			result.setRSV3(rsv3);
			result.setOpcode(Opcode.forValue(opcode));
			result.setPayload(payload.toByteArray());

			return result;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return null;
	}

}
