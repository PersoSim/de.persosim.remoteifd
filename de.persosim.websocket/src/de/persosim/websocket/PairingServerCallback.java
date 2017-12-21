package de.persosim.websocket;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.channels.SocketChannel;

public interface PairingServerCallback {
	public void pairingFinished(SocketChannel socketChannel, InputStream inputStream, OutputStream outputStream);
}
