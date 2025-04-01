package de.persosim.vsmartcard;

import java.net.Socket;

public class VsmartcardMock {
	
	public static void main(String [] args) throws Exception {
		while (true) {
			try (Socket client = new Socket("localhost", 35963);){
				var is = client.getInputStream();
				var os = client.getOutputStream();
			}
		}	
	}
}
