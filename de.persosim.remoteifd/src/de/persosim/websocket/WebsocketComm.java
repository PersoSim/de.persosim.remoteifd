package de.persosim.websocket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import de.persosim.driver.connector.NativeDriverComm;
import de.persosim.driver.connector.pcsc.PcscListener;

public class WebsocketComm implements NativeDriverComm {

	private static final int DEFAULT_SERVER_PORT = 1234;
	private boolean running = false;
	AnnouncementMessageBuilder builder;
	private Thread announcer;
	private boolean isPairing;
	private List<PcscListener> listeners;
	private boolean stopped = false;

	public WebsocketComm(boolean pairing) {
		isPairing = pairing;
	}

	@Override
	public void start() {
		ServerSocket serverSocket;
		try {

			String name = "PersoSim_" + InetAddress.getLocalHost().getHostName();
			String id = "PersoSim_" + Integer.toHexString(ThreadLocalRandom.current().nextInt());
			
			while (!stopped ) {
				announcer = new Thread(new Announcer(new DefaultAnnouncementMessageBuilder(name, id, DEFAULT_SERVER_PORT)));
				announcer.start();
				
				serverSocket = new ServerSocket(DEFAULT_SERVER_PORT);
				
				
				Socket client = serverSocket.accept();
				announcer.interrupt();
				
				TlsHandshaker handshaker = null;
				
				if (isPairing) {
					handshaker = new PairingServer("4567", client);
				} else {
					handshaker = new DefaultHandshaker(client);
				}
				
				handshaker.performHandshake();

				WebSocketProtocol websocket = new WebSocketProtocol(handshaker.getInputStream(), handshaker.getOutputStream(), new DefaultMessageHandler(listeners));
				
				websocket.handleConnection();
				
				handshaker.closeConnection();	
			}
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	@Override
	public void stop() {
		announcer.interrupt();

		running = false;
	}

	@Override
	public boolean isRunning() {
		return running;
	}

	@Override
	public void setListeners(List<PcscListener> listeners) {
		this.listeners = listeners;
	}
}
