package de.persosim.websocket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.util.List;

import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;

import de.persosim.driver.connector.NativeDriverComm;
import de.persosim.driver.connector.pcsc.PcscListener;
import de.persosim.simulator.utils.HexString;

public class WebsocketComm implements NativeDriverComm, Runnable{

	private static final int DEFAULT_SERVER_PORT = 1234;
	private boolean running = false;
	AnnouncementMessageBuilder builder;
	private String pairingCode;
	private List<PcscListener> listeners;
	private Thread serverThread;
	private ServerSocket serverSocket;
	private RemoteIfdConfigManager remoteIfdConfig;
	private TlsHandshaker handshaker;
	private HandshakeResultListener handshakeResultListener;
	


	public WebsocketComm(String pairingCode, RemoteIfdConfigManager remoteIfdConfig, HandshakeResultListener handshakeResultListener) {
		this.pairingCode = pairingCode;
		this.remoteIfdConfig = remoteIfdConfig;
		this.handshakeResultListener = handshakeResultListener;
	}
	
	public WebsocketComm(String pairingCode, RemoteIfdConfigManager remoteIfdConfig) {
		this(pairingCode, remoteIfdConfig, null);
	}

	@Override
	public void start() {
		serverThread = new Thread(this);
		serverThread.start();
		running = true;
	}

	@Override
	public void stop() {
		serverThread.interrupt();
		try {
			if (serverSocket != null) {
				serverSocket.close();
				serverSocket = null;	
			}
		} catch (IOException e) {
			BasicLogger.logException(getClass(), "Exception during closing of the websocket comm server socket", e, LogLevel.WARN);
		}
		try {
			serverThread.join();
		} catch (InterruptedException e) {
			//NOSONAR: stopping the server from the run method interrupts the serverThread
		}
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
	
	@Override
	public void run() {
		
		
		try {
			String name = "PersoSim_" + InetAddress.getLocalHost().getHostName();
			// Hash not yet in Spec (v0.6), but expected by AusweisApp 2 1.13.5
			// toLowerCase() currently needed because of parsing bug in AusweisApp 
			String id = HexString.encode(MessageDigest.getInstance("SHA-256").digest(remoteIfdConfig.getHostCertificate().getEncoded())).toLowerCase();
			
			if (serverSocket != null) {
				throw new IllegalStateException("Server socket should be null at this point, probably not stopped correctly before resetting");
			}
			
			serverSocket = null;
			
			Thread announcer = null;
			serverSocket = new ServerSocket(DEFAULT_SERVER_PORT);
			while (!Thread.interrupted() ) {
				announcer  = new Thread(new Announcer(new DefaultAnnouncementMessageBuilder(name, id, DEFAULT_SERVER_PORT)));
				announcer.start();
				
				
				Socket client = null;
				try {
					 client = serverSocket.accept();
				} catch (SocketException e) {
					// This is expected to happen when the WebsocketComm is stopped
				}
				announcer.interrupt();
				announcer = null;
				
				if (client != null) {
					
					if (pairingCode != null) {
						handshaker = new PairingServer(pairingCode,remoteIfdConfig, client);
					} else {
						handshaker = new DefaultHandshaker(remoteIfdConfig, client);
					}
					
					boolean handshakeResult = handshaker.performHandshake();
					if (handshakeResultListener != null) {
						handshakeResultListener.onHandshakeFinished(handshakeResult);
					}

					if (handshakeResult) {
						WebSocketProtocol websocket = new WebSocketProtocol(handshaker.getInputStream(), handshaker.getOutputStream(), new DefaultMessageHandler(listeners, name));
						
						websocket.handleConnection();
						
						handshaker.closeConnection();	
					}
					
					if (pairingCode != null) {
						stop();
						break;
					}
				}
				
				
			}
		} catch (IOException | CertificateEncodingException | NoSuchAlgorithmException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}

	@Override
	public void reset() {
		if (running) {
			stop();	
		}
		listeners = null;
	}
}
