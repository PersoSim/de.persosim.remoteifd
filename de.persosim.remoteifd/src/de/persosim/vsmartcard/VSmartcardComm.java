package de.persosim.vsmartcard;

import java.io.IOException;
import java.util.List;

import de.persosim.driver.connector.IfdComm;
import de.persosim.driver.connector.exceptions.IfdCreationException;
import de.persosim.driver.connector.pcsc.PcscListener;

public class VSmartcardComm implements IfdComm {

	public static final String DEFAULT_HOST = "localhost";
	public static final int DEFAULT_PORT = 35963;
	public static final String NAME = "VSMARTCARD";

	private List<PcscListener> listeners;
	private boolean isRunning = false;

	private VSmartcardServer server;
	
	public VSmartcardComm(int port) throws IfdCreationException {
		try {
			server = new VSmartcardServer(port);
		} catch (IOException e) {
			throw new IfdCreationException("Could not start VSmartcard server", e);
		}
	}
	
	@Override
	public void start() {
		isRunning = true;
		server.start();
	}

	@Override
	public void stop() {
		if (!isRunning) {
			return;
		}
		server.stop();
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
