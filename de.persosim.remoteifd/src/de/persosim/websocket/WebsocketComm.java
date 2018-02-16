package de.persosim.websocket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateEncodingException;
import java.util.List;

import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;

import de.persosim.driver.connector.IfdComm;
import de.persosim.driver.connector.pcsc.PcscListener;
import de.persosim.simulator.utils.HexString;

public class WebsocketComm implements IfdComm, Runnable{

	private static final int DEFAULT_SERVER_PORT = 1234;
	private boolean running = false;
	AnnouncementMessageBuilder builder;
	private String pairingCode;
	private List<PcscListener> listeners;
	private Thread serverThread;
	private ServerSocket serverSocket;
	private Socket client;
	private RemoteIfdConfigManager remoteIfdConfig;
	private HandshakeResultListener handshakeResultListener;
	private Thread announcer;
	private String readerName;
	


	public WebsocketComm(String pairingCode, String readerName, RemoteIfdConfigManager remoteIfdConfig, HandshakeResultListener handshakeResultListener) {
		this.pairingCode = pairingCode;
		this.remoteIfdConfig = remoteIfdConfig;
		this.handshakeResultListener = handshakeResultListener;
		this.readerName = readerName;
	}
	
	public WebsocketComm(String pairingCode, String readerName, RemoteIfdConfigManager remoteIfdConfig) {
		this(pairingCode, readerName, remoteIfdConfig, null);
	}

	@Override
	public void start() {
		serverThread = new Thread(this);
		serverThread.start();
		running = true;
	}

	@Override
	public void stop() {
		BasicLogger.log(getClass(), "WebsocketComm stopping", LogLevel.DEBUG);
		if (serverThread != null) {
			serverThread.interrupt();	
		}
		if (announcer != null) {
			announcer.interrupt();
		}

		try {
			if (client != null) {
				client.close();
				client = null;
			}
		} catch (IOException e) {
			BasicLogger.logException(getClass(), "Exception during closing of the websocket client socket", e,
					LogLevel.WARN);
		}
		
		try {
			if (serverSocket != null) {
				serverSocket.close();
				serverSocket = null;	
			}
		} catch (IOException e) {
			BasicLogger.logException(getClass(), "Exception during closing of the websocket comm server socket", e, LogLevel.WARN);
		}
		try {
			if (serverThread != null) {
				serverThread.join();
			}
		} catch (InterruptedException e) {
			//NOSONAR: stopping the server from the run method interrupts the serverThread
			Thread.currentThread().interrupt();
		}
		BasicLogger.log(getClass(), "WebsocketComm has been stopped", LogLevel.DEBUG);
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
			if (readerName == null){
				readerName = "PersoSim_" + InetAddress.getLocalHost().getHostName();	
			}
			// XXX Hash not yet in Spec (v0.6), but expected by AusweisApp 2 1.13.5
			// .toLowerCase() needed because AusweisApp does not accept upper case hashes
			String id = HexString.encode(MessageDigest.getInstance("SHA-256").digest(remoteIfdConfig.getHostCertificate().getEncoded())).toLowerCase();
			
			if (serverSocket != null) {
				throw new IllegalStateException("Server socket should be null at this point, probably not stopped correctly before resetting");
			}
			
			serverSocket = null;
			
			announcer = null;
			serverSocket = new ServerSocket(DEFAULT_SERVER_PORT);
			while (!Thread.interrupted() ) {
				announcer  = new Thread(new Announcer(new DefaultAnnouncementMessageBuilder(readerName, id, DEFAULT_SERVER_PORT)));
				announcer.start();
				
				client = serverSocket.accept();
					 
				announcer.interrupt();
				announcer = null;
					
				TlsHandshaker handshaker;
				if (pairingCode != null) {
					handshaker = new PairingServer(pairingCode, remoteIfdConfig, client);
				} else {
					handshaker = new DefaultHandshaker(remoteIfdConfig, client);
				}

				boolean handshakeResult = handshaker.performHandshake();
				if (handshakeResultListener != null) {
					handshakeResultListener.onHandshakeFinished(handshakeResult);
				}

				if (handshakeResult) {
					WebSocketProtocol websocket = new WebSocketProtocol(handshaker.getInputStream(),
							handshaker.getOutputStream(), new DefaultMessageHandler(listeners, readerName));

					websocket.handleConnection();

					handshaker.closeConnection();
				}

				if (pairingCode != null) {
					stop();
					break;
				}
				
			}
		} catch (IOException | CertificateEncodingException | NoSuchAlgorithmException e) {
			BasicLogger.logException(getClass(), e, LogLevel.WARN);
		} finally {
			if (announcer != null) {
				announcer.interrupt();	
			}
		}
		
	}

	@Override
	public void reset() {
		if (running) {
			stop();	
		}
		listeners = null;
	}

	@Override
	public String getName() {
		return "WEBSOCKET";
	}

	@Override
	public String getUserString() {
		return "RemoteIFD interface";
	}
}
