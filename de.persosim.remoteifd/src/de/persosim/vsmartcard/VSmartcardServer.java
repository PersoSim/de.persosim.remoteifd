package de.persosim.vsmartcard;

import java.io.IOException;
import java.math.BigInteger;
import java.net.ServerSocket;

import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;

import de.persosim.simulator.utils.HexString;

public class VSmartcardServer {

	public static int DEFAULT_PORT = 35963;
	
	private Thread serverThread;
	private ServerSocket serverSocket;
	
	public VSmartcardServer() throws IOException {
		this(DEFAULT_PORT);
	}

	public VSmartcardServer(int port) throws IOException {
		serverSocket = new ServerSocket(port);
	}

	void start() {
		Runnable r = new Runnable() {
			
			@Override
			public void run() {
				while (true) {
					try {
						var socket = serverSocket.accept();
						var is = socket.getInputStream();
						var os = socket.getOutputStream();

						while (true) {
							var lengthField = new byte[2];
							var readBytes = is.read(lengthField);
							System.out.println("Received length field (" + readBytes + "):" + HexString.encode(lengthField));
							var data = new byte[new BigInteger(lengthField).intValue()];
							readBytes = is.read(data);
							System.out.println("Received data (" + readBytes + "):" + HexString.encode(data));

							if (data.length > 1) {
								System.err.println("    Got APDU");
								os.write(new byte[] { 0x00, 0x02, (byte) 0x90, 0x00 });
							} else if (data.length == 1) {
								switch (data[0]) {
								case 0:
									System.err.println("    Got power off");
									break;
								case 1:
									System.err.println("    Got power on");
									break;
								case 2:
									System.err.println("    Got reset");
									break;
								}
							}
						}
					} catch (Exception e) {
						e.printStackTrace();
					}
				}
			}
		};
		serverThread = new Thread(r);
		serverThread.start();
	}
	
	void stop() {
		serverThread.interrupt();
		try {
			serverThread.join();
		} catch (InterruptedException e) {
			BasicLogger.logException("Waiting for server thread join failed", e, LogLevel.ERROR);
		}
	}
}
