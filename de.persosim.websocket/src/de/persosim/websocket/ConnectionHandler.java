package de.persosim.websocket;

import java.net.Socket;

public interface ConnectionHandler {

	void handleConnection(Socket accept);

}