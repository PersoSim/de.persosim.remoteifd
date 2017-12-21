package de.persosim.websocket;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.List;

import de.persosim.driver.connector.NativeDriverComm;
import de.persosim.driver.connector.pcsc.PcscListener;

public class WebsocketComm implements NativeDriverComm{

	private static final int DEFAULT_SERVER_PORT = 1234;
	private boolean running = false;
	AnnouncementMessageBuilder builder;
	private Thread announcer;
	private boolean isPairing= true;
	private List<PcscListener> listeners;

	@Override
	public void start() {
		announcer = new Thread(new Announcer(new DefaultAnnouncementMessageBuilder("test", DEFAULT_SERVER_PORT)));
		announcer.start();
		
		ServerSocket serverSocket;
		try {
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

			announcer = new Thread(new Announcer(new DefaultAnnouncementMessageBuilder("test", DEFAULT_SERVER_PORT)));
			announcer.start();

			client = serverSocket.accept();
			announcer.interrupt();
			
//			handshaker = null;
//			
//			if (isPairing) {
//				handshaker = new PairingServer("4567", client);
//			} else {
//				handshaker = new DefaultHandshaker(client);
//			}
//			
//			handshaker.performHandshake();
//
//			websocket = new WebSocketProtocol(handshaker.getInputStream(), handshaker.getOutputStream(), new DefaultMessageHandler(listeners));
			
			websocket.handleConnection();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		
		

//		
//		try {
//			
//
//			KeyStore ks;
//			webSocketServer = new WebSocketServerImpl(new InetSocketAddress(DEFAULT_SERVER_PORT),
//					new DefaultMessageHandler(), this) {
//				@Override
//				protected void channelAvailable() {
//					if (isPairing) {
//							pairingServer = new PairingServer("4567", WebsocketComm.this);
//							pairingServer.start(getChannel());
//							
//					}
//				}
//			};
//
//			try {
//				
//
//				if (!isPairing) {
//					ks = KeyStore.getInstance(STORETYPE);
//					File kf = new File(KEYSTORE);
//					ks.load(new FileInputStream(kf), STOREPASSWORD.toCharArray());
//
//					KeyManagerFactory kmf = KeyManagerFactory.getInstance("SunX509");
//					kmf.init(ks, KEYPASSWORD.toCharArray());
//					TrustManagerFactory tmf = TrustManagerFactory.getInstance("SunX509");
//					tmf.init(ks);
//
//					SSLContext sslContext = null;
//					sslContext = SSLContext.getInstance("TLS");
//					sslContext.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
//					webSocketServer.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));				
//				}
//
//				webSocketServer.setBlockForPairing(isPairing, null);
//				webSocketServer.start();
//				
//				running = true;
//			} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException
//					| UnrecoverableKeyException | KeyManagementException e) {
//				BasicLogger.logException(getClass(), e, LogLevel.ERROR);
//			}
//			
//		} catch (IOException e) {
//			// TODO Auto-generated catch block
//			e.printStackTrace();
//		}
	}
//
//	public void switchPairingMode(boolean isPairing, boolean forceStartServer) throws IOException {
//		BasicLogger.log(getClass(), "Initiating " + getClass().getSimpleName() + " pairing mode switch to " + isPairing,
//				LogLevel.DEBUG);
//		if (this.isPairing == isPairing) {
//			return;
//		}
//
//		this.isPairing = isPairing;
//
//		if (!isRunning() & !forceStartServer) {
//			return;
//		}
//
//		if (isPairing) {
//			BasicLogger.log(getClass(), "Initiating pairing server", LogLevel.DEBUG);
//			stopWebsocketServer();
//			startPairingServer();
//		} else {
//			BasicLogger.log(getClass(), "Initiating websocket server", LogLevel.DEBUG);
//			stopPairingServer();
//			startWebsocketServer();
//		}
//	}

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
