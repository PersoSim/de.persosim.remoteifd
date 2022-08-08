package de.persosim.websocket;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;

import de.persosim.simulator.utils.Base64;

public class DefaultHandshakeHandler extends HandshakeHandler {

	public DefaultHandshakeHandler(OutputStream outputStream, InputStreamReader reader) {
		super(outputStream, reader);
	}

	private String readToDelimiter(char[] cs) {
				
		int indexToDelimiter = 0;
		StringBuilder data = new StringBuilder();
		
		do {
			try {
				int read = reader.read();
				char readChar = (char) read;
				
				data.append(readChar);
				
				if (read == -1) {
					throw new IllegalStateException("Reading reached EOF unexpectedly");
				}
				
				if (readChar == cs[indexToDelimiter]) {
					indexToDelimiter++;
				} else {
					indexToDelimiter = 0;
				}
				
			} catch (IOException e) {
				throw new IllegalStateException("Reading of handshake message failed with exception", e);
			}
			
		} while (indexToDelimiter != cs.length);
		
		return data.toString();
	}
	
	@Override
	public boolean handle() {

		try {
			BasicLogger.log(getClass(), "Begin handling websocket handshake",
					LogLevel.DEBUG);
			
			String data = readToDelimiter(new char [] {'\r', '\n', '\r', '\n'});

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

			String challengeResponse = Base64.encode(MessageDigest.getInstance("SHA-1").digest(
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

}
