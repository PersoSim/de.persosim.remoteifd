package de.persosim.websocket;

import java.io.InputStreamReader;
import java.io.OutputStream;

public abstract class HandshakeHandler {
	
	OutputStream outputStream;
	InputStreamReader reader;

	public HandshakeHandler(OutputStream outputStream, InputStreamReader reader) {
		super();
		this.outputStream = outputStream;
		this.reader = reader;
	}
	
	abstract boolean handle();

}
