package de.persosim.websocket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.util.List;

import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;

import de.persosim.driver.connector.IfdComm;
import de.persosim.driver.connector.pcsc.PcscListener;
import de.persosim.simulator.utils.Base64;

public class WebsocketComm implements IfdComm, Runnable{

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
			String id = encodeCertificate(remoteIfdConfig.getHostCertificate());
			
			if (serverSocket != null) {
				throw new IllegalStateException("Server socket should be null at this point, probably not stopped correctly before resetting");
			}
			
			serverSocket = null;
			
			announcer = null;
			serverSocket = new ServerSocket(0);
			while (!Thread.interrupted() ) {
				
				System.out.println("Local server port: "+serverSocket.getLocalPort());
				
				announcer  = new Thread(new Announcer(new DefaultAnnouncementMessageBuilder(remoteIfdConfig.getName(), id, serverSocket.getLocalPort(), pairingCode!=null)));
				announcer.start();
				
				client = serverSocket.accept();
					 
				announcer.interrupt();
				announcer = null;
					
				TlsHandshaker handshaker = getHandshaker();

				boolean handshakeResult = handshaker.performHandshake();
				
				notifyListeners(handshakeResult);

				if (handshakeResult) {
					handleWebSocketCommunication(handshaker);
				}

				notifyListenersConnectionClosed();

				if (pairingCode != null) {
					stop();
					break;
				}
				
			}
		} catch (IOException | CertificateEncodingException e) {
			BasicLogger.logException(getClass(), e, LogLevel.WARN);
		} finally {
			if (announcer != null) {
				announcer.interrupt();	
			}
		}
		
	}

	private String encodeCertificate(Certificate hostCertificate) throws CertificateEncodingException {
		StringBuilder retVal = new StringBuilder();
		retVal.append("-----BEGIN CERTIFICATE-----\n");
		retVal.append(Base64.encode(hostCertificate.getEncoded()).replaceAll("(.{64})",  "$1\n"));
		retVal.append("-----END CERTIFICATE-----");
		return retVal.toString();
	}

	private void handleWebSocketCommunication(TlsHandshaker handshaker) {
		WebSocketProtocol websocket = getWebSocketProtocol(handshaker);

		websocket.handleConnection();

		handshaker.closeConnection();
	}

	private WebSocketProtocol getWebSocketProtocol(TlsHandshaker handshaker) {
		return new WebSocketProtocol(handshaker.getInputStream(), handshaker.getOutputStream(), new DefaultMessageHandler(listeners, remoteIfdConfig, handshaker.getClientCertificate()));
	}

	private void notifyListenersConnectionClosed() {
		if (handshakeResultListener != null) {
			handshakeResultListener.onConnectionClosed();
		}
	}

	private void notifyListeners(boolean handshakeResult) {
		if (handshakeResultListener != null) {
			handshakeResultListener.onHandshakeFinished(handshakeResult);
		}
	}

	private TlsHandshaker getHandshaker() {
		if (pairingCode != null) {
			return new PairingServer(pairingCode, remoteIfdConfig, client);
		} else {
			return new DefaultHandshaker(remoteIfdConfig, client);
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
