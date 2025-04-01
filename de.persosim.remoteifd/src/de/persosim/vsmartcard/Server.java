package de.persosim.vsmartcard;

import java.io.IOException;
import java.math.BigInteger;
import java.net.ServerSocket;

import de.persosim.simulator.utils.HexString;

public class Server {

	public Server() throws IOException {
		while (true) {
			try (var serverSocket = new ServerSocket(35963)) {
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
}
