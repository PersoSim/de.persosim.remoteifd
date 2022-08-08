package de.persosim.websocket;

import static org.junit.Assert.assertEquals;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.Test;

import de.persosim.simulator.utils.Utils;
import de.persosim.websocket.Frame.Opcode;

public class WebsocketProtocolTest {

	HandshakeHandler handshakeNullHandler = new HandshakeHandler(null, null) {
		
		@Override
		public boolean handle() {
			return true;
		}
	};
	
	private static String getStringOfLength(int length) {
		char[] value = new char[length];
		Arrays.fill(value, ' ');
		return new String(value);
	}
	
	@Test
	public void testMaxShort() {
		testMessage(getStringOfLength(Short.MAX_VALUE));
	}
	
	@Test
	public void testLongerThanShort() {
		testMessage(getStringOfLength(Short.MAX_VALUE+1));
	}
	
	@Test
	public void testDoubleShort() {
		testMessage(getStringOfLength(Short.MAX_VALUE*2));
	}
	
	@Test
	public void testBiggerThanDoubleShort() {
		testMessage(getStringOfLength(Short.MAX_VALUE*2+1));
	}
	
	@Test
	public void testMaxUnsignedShort() {
		testMessage(getStringOfLength(65535));
	}
	
	@Test
	public void testBiggerThanUnsignedShort() {
		testMessage(getStringOfLength(65535+1));
	}
	
	@Test
	public void testSmall() {
		testMessage(getStringOfLength(32));
	}
	
	public void testMessage(String message) {
		Frame frame = new Frame();
		frame.setFin(true);
		frame.setOpcode(Opcode.TEXT);
		frame.setPayload(message.getBytes(StandardCharsets.UTF_8));
		
		byte [] inputMessage = Utils.concatByteArrays(frame.getHeaderBytes(), frame.getPayload());		
		
		InputStream inputStream = new ByteArrayInputStream(inputMessage);
		OutputStream outputStream = new ByteArrayOutputStream();
		MessageHandler messageHandler = new MessageHandler() {
			int counter = 0;
			@Override
			public String message(String incomingMessage) {
				counter ++;
				assertEquals(message, incomingMessage);
				assertEquals("Expecting only one message", 1, counter);
				return "";
			}
			
			@Override
			public boolean isIccAvailable() {
				return true;
			}
			
			@Override
			public String getStatusMessage() {
				return "";
			}
		};
		WebSocketProtocol protocol = new WebSocketProtocol(inputStream, outputStream, messageHandler, handshakeNullHandler);
		protocol.handleConnection();
	}
}
