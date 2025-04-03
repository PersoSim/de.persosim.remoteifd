package de.persosim.vsmartcard;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

public class VSmartcardMock {
	private String host;
	private int port;
	private Socket socket;
	private InputStream is;
	private OutputStream os;
	
	public static void main(String [] args) throws Exception {
		VSmartcardMock mock = new VSmartcardMock("localhost", VSmartcardServer.DEFAULT_PORT);
		while (true) {
			mock.connect();
			mock.send(Commands.POWER_ON.getCommand());
			mock.disconnect();
		}	
	}

	public VSmartcardMock(String host, int port) {
		this.host = host;
		this.port = port;
	}

	public void connect() throws IOException {
		socket = new Socket(host, port);
		is = socket.getInputStream();
		os = socket.getOutputStream();
	}
	
	public void disconnect() throws IOException {
		socket.close();
		socket = null;
	}
	
	public void send(byte [] payload) throws IOException {
		sendLength(payload.length);
		os.write(payload);
		os.flush();
	}
	
	public void sendLength(int length) throws IOException {
		byte [] lengthBytes = new byte [2];
		lengthBytes[0] = (byte) (length >> 8);
		lengthBytes[1] = (byte) (length & 0xff);
        os.write(lengthBytes);		
	}

	public byte [] receive(byte [] payload) throws IOException {
		int length = getLengthFromStream();
		if (length > 0) {
			byte [] data = new byte [length];
			int offset = 0;
			while (length >= 0) {
				int readBytes = is.read(payload, offset, length);
				if (readBytes == -1)
					throw new IOException("Got less than the expected " + length + " bytes of data");
				offset += readBytes;
				length -= readBytes;
			}
			return data;
		}
		return null;
	}

	private int getLengthFromStream() throws IOException {
		int lengthByte1 = is.read();
		int lengthByte2 = is.read();
		
		if (lengthByte1 < 1 || lengthByte2 < 1) {
			return -1;
		}
		
		return (lengthByte1 << 8) + lengthByte2;
	}
}
