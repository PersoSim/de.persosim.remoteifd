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
import org.globaltester.logging.tags.LogTag;

import de.persosim.simulator.log.PersoSimLogTags;
import de.persosim.simulator.utils.HexString;
import de.persosim.simulator.utils.Utils;
import de.persosim.websocket.Frame.Opcode;

public class WebSocketProtocol
{

	private DataInputStream inputStream;
	private DataOutputStream outputStream;
	private MessageHandler messageHandler;
	private HandshakeHandler handshakeHandler;

	private Frame joinedFrame = null;

	public WebSocketProtocol(InputStream inputStream, OutputStream outputStream, MessageHandler messageHandler, HandshakeHandler handshakeHandler)
	{
		this.inputStream = new DataInputStream(inputStream);
		this.outputStream = new DataOutputStream(outputStream);
		this.messageHandler = messageHandler;
		this.handshakeHandler = handshakeHandler;
	}

	public void handleConnection()
	{
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
							public void run()
							{
								while (!Thread.interrupted()) {
									try {
										Thread.sleep(1000);
									}
									catch (InterruptedException e) {
										// Expected when peer closes socket
										BasicLogger.log("Sleeping of icc polling thread interrupted. " + e.getMessage(), LogLevel.INFO, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
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
						BasicLogger.log("Handling control frame", LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
						connectionState = handleFrame(currentFrame);
					}
					else {
						if (joinedFrame == null) {
							if (!Opcode.CONTINUATION.equals(currentFrame.getOpcode())) {
								BasicLogger.log("Starting new joined frame", LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
								joinedFrame = currentFrame;
							}
							else {
								BasicLogger.log("Expected normal frame but got " + currentFrame.getOpcode().toString(), LogLevel.WARN,
										new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
							}
						}
						else {
							if (Opcode.CONTINUATION.equals(currentFrame.getOpcode())) {
								BasicLogger.log("Appending to joined frame", LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
								joinedFrame.appendFrame(currentFrame);
							}
							else {
								BasicLogger.log("Expected contination frame but got " + currentFrame.getOpcode().toString(), LogLevel.WARN,
										new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
							}
						}

						if (joinedFrame != null && joinedFrame.getFin()) {
							BasicLogger.log("Joined frame complete, handling now", LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
							final Frame frameToProcess = joinedFrame;
							joinedFrame = null;
							Thread handler = new Thread(() -> handleFrame(frameToProcess));
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

	private ConnectionState handleFrame(Frame curFrame)
	{
		switch (curFrame.getOpcode()) {
			case CLOSE:
				// Echo first two bytes on close (status code, see RFC6455 5.5.1)
				if (curFrame.getPayload().length > 2) {
					writeFrame(createBasicFrame(Opcode.CLOSE, new byte[] { curFrame.getPayload()[1], curFrame.getPayload()[2] }));

				}
				else {
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
				BasicLogger.log("Got message with unhandled opcode: " + curFrame.toString(), LogLevel.WARN, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
				writeFrame(createBasicFrame(Opcode.CLOSE));
				return ConnectionState.CLOSED;
		}
	}

	private Frame createBasicFrame(Opcode opcode, byte[] payload)
	{
		Frame result = createBasicFrame(opcode);
		result.setPayload(payload);
		return result;
	}

	private Frame createBasicFrame(Opcode opcode)
	{
		Frame result = new Frame();
		result.setFin(true);
		result.setOpcode(opcode);
		return result;
	}

	private Frame handleTextFrame(Frame frame)
	{
		String message = messageHandler.message(new String(frame.getPayload(), StandardCharsets.UTF_8));

		if (message != null) {
			return createBasicFrame(Opcode.TEXT, message.getBytes(StandardCharsets.UTF_8));
		}
		return null;
	}

	private void writeFrame(Frame frame)
	{
		BasicLogger.log("Writing frame: " + frame, LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));

		StringBuilder logMessage = new StringBuilder();

		try {
			byte[] toWrite = frame.getHeaderBytes();
			BasicLogger.log("Frame header and length is: " + HexString.encode(toWrite), LogLevel.DEBUG, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
			outputStream.write(toWrite);
			outputStream.write(frame.getPayload());

			logMessage.append("Sent frame bytes: " + HexString.dump(Utils.concatByteArrays(toWrite, frame.getPayload())));
			BasicLogger.log(logMessage.toString(), LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));

		}
		catch (IOException e) {
			BasicLogger.logException("Writing a frame failed", e, LogLevel.ERROR, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
		}
	}

	private Frame readFrame()
	{
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
			}
			else if (payloadLength == 127) {
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
			logMessage.append("Received Frame Header:" + System.lineSeparator() + "FIN: " + fin + System.lineSeparator() + "Opcode: " + opcode.toString() + System.lineSeparator() + "Mask: " + mask
					+ System.lineSeparator() + "Payload Length: " + payloadLength);
			BasicLogger.log(logMessage.toString(), LogLevel.DEBUG, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));

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

			BasicLogger.log(logMessage.toString(), LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));

			result.setFin(fin);
			result.setRSV1(rsv1);
			result.setRSV2(rsv2);
			result.setRSV3(rsv3);
			result.setOpcode(opcode);
			result.setPayload(payload.toByteArray());

			return result;
		}
		catch (IOException e) {
			BasicLogger.logException("Reading and parsing a new frame failed", e, LogLevel.ERROR, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
		}
		return null;
	}

}
