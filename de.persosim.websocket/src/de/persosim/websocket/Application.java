package de.persosim.websocket;

import java.io.IOException;

import de.persosim.driver.connector.SimulatorManager;
import de.persosim.simulator.PersoSim;

public class Application {
	public static void main(String[] args) throws IOException {
		PersoSim sim = new PersoSim();
		sim.startPersoSim();
		
		SimulatorManager.setSimulator(sim);
		
		WebsocketComm comm = new WebsocketComm();
		comm.start();
	}
}
