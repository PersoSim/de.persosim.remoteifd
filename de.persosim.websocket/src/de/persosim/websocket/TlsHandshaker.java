package de.persosim.websocket;

import java.io.InputStream;
import java.io.OutputStream;

public interface TlsHandshaker {

	boolean performHandshake();

	void closeConnection();

	InputStream getInputStream();

	OutputStream getOutputStream();

}
