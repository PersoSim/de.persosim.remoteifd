 
package de.persosim.remoteifd.ui.handlers;

import javax.inject.Inject;

import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.workbench.modeling.EPartService;
import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;

import de.persosim.driver.connector.service.NativeDriverConnector;
import de.persosim.driver.connector.ui.parts.ReaderPart;
import de.persosim.websocket.WebsocketComm;

public class RemoteIfdHandler {
	NativeDriverConnector connector;

	@Inject
	private EPartService partService;
	
	@Execute
	public void execute() {

		// ID of part as defined in fragment.e4xmi application model
		MPart readerPart = partService.findPart("de.persosim.driver.connector.ui.parts.reader");

		
		if (readerPart.getObject() instanceof ReaderPart) {
			ReaderPart readerPartObject = (ReaderPart) readerPart.getObject();
			
			readerPartObject.switchReaderType(new WebsocketComm(null, de.persosim.remoteifd.ui.Activator.getRemoteIfdConfig()));
		}
		
		BasicLogger.log(this.getClass(), "Switch to use RemoteIfd", LogLevel.INFO);
	}
		
}