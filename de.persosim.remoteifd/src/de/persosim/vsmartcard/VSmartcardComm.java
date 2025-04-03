package de.persosim.vsmartcard;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.lang.reflect.Field;
import java.net.Socket;
import java.net.SocketException;
import java.util.List;

import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;

import de.persosim.driver.connector.CommUtils;
import de.persosim.driver.connector.CommUtils.HandshakeMode;
import de.persosim.driver.connector.IfdComm;
import de.persosim.driver.connector.IfdInterface;
import de.persosim.driver.connector.UnsignedInteger;
import de.persosim.driver.connector.exceptions.IfdCreationException;
import de.persosim.driver.connector.pcsc.PcscCallData;
import de.persosim.driver.connector.pcsc.PcscCallResult;
import de.persosim.driver.connector.pcsc.PcscConstants;
import de.persosim.driver.connector.pcsc.PcscListener;
import de.persosim.driver.connector.pcsc.SimplePcscCallResult;
import de.persosim.simulator.utils.HexString;

public class VSmartcardComm implements IfdComm, Runnable {

	public static final String DEFAULT_HOST = "localhost";
	public static final int DEFAULT_PORT = 35963;
	public static final String NAME = "VSMARTCARD";

	private List<PcscListener> listeners;
	private Socket dataSocket;
	private UnsignedInteger lun;
	private boolean isRunning = false;
	private boolean isConnected = false;
	private Thread driverComm;
	private String host;
	private int port;

	private VSmartcardServer server;
	
	public VSmartcardComm(int port) throws IfdCreationException {
		try {
			server = new VSmartcardServer(port);
		} catch (IOException e) {
			throw new IfdCreationException("Could not start VSmartcard server", e);
		}
	}

	public void log(PcscCallResult data) {
		BasicLogger.log(getClass(), "PCSC Out:\t" + data.getEncoded(), LogLevel.TRACE);
	}

	public void log(PcscCallData data) {
		String logmessage = "PCSC In:\t" + getStringRep(data.getFunction()) + IfdInterface.MESSAGE_DIVIDER
				+ data.getLogicalUnitNumber().getAsHexString();
		for (byte[] current : data.getParameters()) {
			logmessage += System.lineSeparator() + IfdInterface.MESSAGE_DIVIDER + HexString.encode(current);
		}
		BasicLogger.log(this.getClass(), logmessage, LogLevel.TRACE);
	}

	String getStringRep(UnsignedInteger value) {
		Field[] fields = IfdInterface.class.getDeclaredFields();
		for (Field field : fields) {
			try {
				if (value.equals(field.get(new IfdInterface() {
				}))) {
					return field.getName();
				}
			} catch (Exception e) {
				continue;
			}
		}
		return value.getAsHexString();
	}
	
	@Override
	public void start() {
		server.start();
	}

	@Override
	public void stop() {
		if (!isRunning) {
			return;
		}

		server.stop();


		try {
			driverComm.join();
		} catch (InterruptedException e) {
			BasicLogger.logException("Waiting for communication thread join failed", e, LogLevel.ERROR);
		}

		isConnected = false;
		dataSocket = null;
		driverComm = null;
		isRunning = false;
	}

	@Override
	public boolean isRunning() {
		return isRunning;
	}

	@Override
	public void setListeners(List<PcscListener> listeners) {
		this.listeners = listeners;
	}

	@Override
	public void run() {
		isRunning = true;
		try (BufferedReader bufferedDataIn = new BufferedReader(new InputStreamReader(dataSocket.getInputStream()));
				BufferedWriter bufferedDataOut = new BufferedWriter(
						new OutputStreamWriter(dataSocket.getOutputStream()));) {

			lun = CommUtils.doHandshake(dataSocket, HandshakeMode.OPEN);
			isConnected = true;
			while (!Thread.interrupted()) {
				try {
					String data = bufferedDataIn.readLine();
					PcscCallResult result = null;
					if (data != null) {
						try {
							PcscCallData callData = new PcscCallData(data);
							log(callData);
							if (listeners != null) {
								for (PcscListener listener : listeners) {
									try {
										PcscCallResult currentResult = listener.processPcscCall(callData);
										if (result == null && currentResult != null) {
											// ignore all but the first result
											result = currentResult;
										}
									} catch (RuntimeException e) {
										BasicLogger.logException(getClass(),
												"Something went wrong while processing of the PCSC data by listener \""
														+ listener.getClass().getName() + "\"!\"",
												e, LogLevel.ERROR);
									}
								}
							} else {
								BasicLogger.log(getClass(), "No PCSC listeners registered!", LogLevel.WARN);
							}
						} catch (RuntimeException e) {
							BasicLogger.logException(getClass(), "Something went wrong while parsing the PCSC data!", e,
									LogLevel.ERROR);
						}
						if (result == null) {
							result = new SimplePcscCallResult(PcscConstants.IFD_NOT_SUPPORTED);
						}

						log(result);

						bufferedDataOut.write(result.getEncoded());
						bufferedDataOut.newLine();
						bufferedDataOut.flush();
					}
				} catch (SocketException e) {
					// expected behavior after interrupting
				}
			}
		} catch (Exception e1) {
			e1.printStackTrace();
		}
	}

	@Override
	public void reset() {
		if (isRunning) {
			stop();
		}
		listeners = null;
	}

	@Override
	public String getName() {
		return NAME;
	}

	@Override
	public String getUserString() {
		return "VSmartcard";
	}

}
