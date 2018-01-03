package de.persosim.websocket;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.security.interfaces.RSAPrivateKey;
import java.util.List;

import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCertificate;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;
import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;

import de.persosim.driver.connector.NativeDriverComm;
import de.persosim.driver.connector.pcsc.PcscListener;
import de.persosim.simulator.utils.HexString;

public class WebsocketComm implements NativeDriverComm, Runnable{

	private static final int DEFAULT_SERVER_PORT = 1234;
	private boolean running = false;
	AnnouncementMessageBuilder builder;
	private boolean isPairing;
	private List<PcscListener> listeners;
	private KeyStore keyStore;
	private char[] keypassword;
	private Thread serverThread;
	private ServerSocket serverSocket;
	


	public WebsocketComm(boolean pairing, KeyStore keyStore, char [] keypassword) {
		isPairing = pairing;
		this.keyStore = keyStore;
		this.keypassword = keypassword;
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
			serverSocket.close();
			serverSocket = null;
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			serverThread.join();
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
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
		byte[] certificateFromStore;
		
		AsymmetricKeyParameter key = null;
		Certificate cert = null;
		
		try {
			certificateFromStore = keyStore.getCertificate("default").getEncoded();
			TlsCertificate[] certs = new TlsCertificate[1];
			
			certs[0] = new BcTlsCertificate(new BcTlsCrypto(new SecureRandom()), certificateFromStore);
			cert = new Certificate(certs);

			RSAPrivateKey keyFromStore = (RSAPrivateKey) keyStore.getKey("default", keypassword);

			key = new RSAKeyParameters(true, keyFromStore.getModulus(),
					keyFromStore.getPrivateExponent());

		} catch (CertificateEncodingException | KeyStoreException | UnrecoverableKeyException
				| NoSuchAlgorithmException | IOException e) {
			BasicLogger.logException(getClass(), e, LogLevel.ERROR);
		}
		
		try {
			String name = "PersoSim_" + InetAddress.getLocalHost().getHostName();
			// Hash not yet in Spec (v0.6), but expected by AusweisApp 2 1.13.5
			// toLowerCase() currently needed because of parsing bug in AusweisApp 
			String id = HexString.encode(MessageDigest.getInstance("SHA-256").digest(keyStore.getCertificate("default").getEncoded())).toLowerCase();
			
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
					TlsHandshaker handshaker = null;
					
					if (isPairing) {
						handshaker = new PairingServer("4567", cert, key, client);
					} else {
						handshaker = new DefaultHandshaker(cert, key, client);
					}
					
					handshaker.performHandshake();

					WebSocketProtocol websocket = new WebSocketProtocol(handshaker.getInputStream(), handshaker.getOutputStream(), new DefaultMessageHandler(listeners, name));
					
					websocket.handleConnection();
					
					handshaker.closeConnection();	
				}
				
				
			}
			
		} catch (IOException | CertificateEncodingException | NoSuchAlgorithmException | KeyStoreException e) {
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
