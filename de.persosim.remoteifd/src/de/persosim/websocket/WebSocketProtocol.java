package de.persosim.websocket;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;

import de.persosim.simulator.utils.HexString;
import de.persosim.simulator.utils.Utils;
import de.persosim.websocket.Frame.Opcode;

public class WebSocketProtocol {

	private DataInputStream inputStream;
	private DataOutputStream outputStream;
	private MessageHandler messageHandler;
	private HandshakeHandler handshakeHandler;
	
	private Frame joinedFrame = null;

	public WebSocketProtocol(InputStream inputStream, OutputStream outputStream, MessageHandler messageHandler, HandshakeHandler handshakeHandler) {
		this.inputStream = new DataInputStream(inputStream);
		this.outputStream = new DataOutputStream(outputStream);
		this.messageHandler = messageHandler;
		this.handshakeHandler = handshakeHandler;
	}

	public void handleConnection() {
		ConnectionState connectionState = ConnectionState.NEW;
		Thread iccPollingThread = null;

		while (true) {
			switch (connectionState) {
			case NEW:
				if (handshakeHandler.handle()) {
					connectionState = ConnectionState.ESTABLISHED;
					iccPollingThread = new Thread(new Runnable() {
						
						private boolean lastIccState;

						@Override
						public void run() {
							while (!Thread.interrupted()) {
								try {
									Thread.sleep(1000);
								} catch (InterruptedException e) {
									BasicLogger.logException(getClass(), "Sleeping of icc polling thread interrupted", e);
									Thread.currentThread().interrupt();
								}
								if (messageHandler.isIccAvailable() != lastIccState) {
									lastIccState = !lastIccState;
									writeFrame(createBasicFrame(Opcode.TEXT, messageHandler.getStatusMessage().getBytes(StandardCharsets.UTF_8)));	
								}
							}
						}
					});
					iccPollingThread.start();
				}
				
				break;
			case ESTABLISHED:
				Frame currentFrame = readFrame();
				
				if (currentFrame == null) {
					connectionState = ConnectionState.CLOSED;
					break;
				}
				
				if (currentFrame.getOpcode().isControl()) {
					BasicLogger.log(getClass(), "Handling control frame", LogLevel.TRACE);
					connectionState = handleFrame(currentFrame);
				} else {
					if (joinedFrame == null) {
						if (!Opcode.CONTINUATION.equals(currentFrame.getOpcode())) {
							BasicLogger.log(getClass(), "Starting new joined frame", LogLevel.TRACE);
							joinedFrame = currentFrame;
						} else {
							BasicLogger.log(getClass(), "Expected normal frame but got " + currentFrame.getOpcode().toString(), LogLevel.WARN);
						}
					} else {
						if (Opcode.CONTINUATION.equals(currentFrame.getOpcode())){
							BasicLogger.log(getClass(), "Appending to joined frame", LogLevel.TRACE);
							joinedFrame.appendFrame(currentFrame);
						} else {
							BasicLogger.log(getClass(), "Expected contination frame but got " + currentFrame.getOpcode().toString(), LogLevel.WARN);
						}
					}
					
					if (joinedFrame != null && joinedFrame.getFin()) {
						BasicLogger.log(getClass(), "Joined frame complete, handling now", LogLevel.TRACE);
						final Frame frameToProcess = joinedFrame;
						joinedFrame = null;
						Thread handler = new Thread(new Runnable() {

							@Override
							public void run() {
								handleFrame(frameToProcess);
							}});
						handler.start();
					}
				}
				
				break;
			case CLOSED:
				if (iccPollingThread != null) {
					iccPollingThread.interrupt();
				}
				return;
			default:
				break;
			}
		}
	}

	private ConnectionState handleFrame(Frame curFrame) {
		switch (curFrame.getOpcode()) {
		case CLOSE:
			// Echo first two bytes on close (status code, see RFC6455 5.5.1)
			if (curFrame.getPayload().length > 2) {
				writeFrame(createBasicFrame(Opcode.CLOSE,
						new byte[] { curFrame.getPayload()[1], curFrame.getPayload()[2] }));

			} else {
				writeFrame(createBasicFrame(Opcode.CLOSE, new byte[0]));
			}
			return ConnectionState.CLOSED;
		case PING:
			writeFrame(createBasicFrame(Opcode.PONG, curFrame.getPayload()));
			return ConnectionState.ESTABLISHED;
		case TEXT:
			Frame responseFrame = handleTextFrame(curFrame);
			if (responseFrame != null) {
				writeFrame(responseFrame);	
			}
			
			return ConnectionState.ESTABLISHED;
		default:
			BasicLogger.log(getClass(), "Got message with unhandled opcode: " + curFrame.toString(), LogLevel.WARN);
			writeFrame(createBasicFrame(Opcode.CLOSE));
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
		String message = messageHandler
				.message(new String(frame.getPayload(), StandardCharsets.UTF_8));

		if (message != null) {
			return createBasicFrame(Opcode.TEXT, message.getBytes(StandardCharsets.UTF_8));
		}
		return null;
	}

	private void writeFrame(Frame frame) {
		BasicLogger.log(getClass(),  "Writing frame: " + frame, LogLevel.TRACE);

		StringBuilder logMessage = new StringBuilder();

		try {
			byte [] toWrite = frame.getHeaderBytes();
			BasicLogger.log(getClass(),  "Frame header and length is: " + HexString.encode(toWrite), LogLevel.DEBUG);
			outputStream.write(toWrite);
			outputStream.write(frame.getPayload());

			logMessage.append("Sent frame bytes: " + HexString.dump(Utils.concatByteArrays(toWrite, frame.getPayload())));
			BasicLogger.log(getClass(), logMessage.toString(), LogLevel.TRACE);

		} catch (IOException e) {
			BasicLogger.logException(getClass(),  "Writing a frame failed", e);
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
			Opcode opcode = Opcode.forValue((header & 0xF00) >>> 8);

			boolean mask = (header & 0x80) == 0x80;

			long payloadLength = (header & 0x7F);

			if (payloadLength == 126) {
				payloadLength = inputStream.readUnsignedShort();
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

			StringBuilder logMessage = new StringBuilder();
			logMessage.append("Received Frame Header:" + System.lineSeparator() + "FIN: " + fin
					+ System.lineSeparator() + "Opcode: " + opcode.toString() + System.lineSeparator() + "Mask: " + mask
					+ System.lineSeparator() + "Payload Length: " + payloadLength);
			BasicLogger.log(getClass(), logMessage.toString(), LogLevel.DEBUG);

			// IMPL handle extension data

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
			
			logMessage = new StringBuilder();

			logMessage.append("Received frame payload bytes:" + System.lineSeparator());

			logMessage.append(HexString.dump(payload.toByteArray()));

			BasicLogger.log(getClass(), logMessage.toString(), LogLevel.TRACE);

			result.setFin(fin);
			result.setRSV1(rsv1);
			result.setRSV2(rsv2);
			result.setRSV3(rsv3);
			result.setOpcode(opcode);
			result.setPayload(payload.toByteArray());

			return result;
		} catch (IOException e) {
			BasicLogger.logException(getClass(), "Reading and parsing a new frame failed", e);
		}
		return null;
	}

}
