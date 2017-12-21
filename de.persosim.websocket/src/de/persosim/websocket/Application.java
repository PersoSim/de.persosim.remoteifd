package de.persosim.websocket;

import java.io.IOException;

import de.persosim.driver.connector.SimulatorManager;
import de.persosim.driver.connector.service.NativeDriverConnector;
import de.persosim.driver.connector.service.NativeDriverConnectorImpl;
import de.persosim.simulator.PersoSim;

public class Application {
	public static void main(String[] args) throws IOException {
		PersoSim sim = new PersoSim();
		sim.startPersoSim();
		
		SimulatorManager.setSimulator(sim);
		
		NativeDriverConnector connector = new NativeDriverConnectorImpl();

		WebsocketComm comm = new WebsocketComm(true);
		
		connector.connect(comm);
	}
}
