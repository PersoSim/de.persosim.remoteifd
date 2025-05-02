 
package de.persosim.remoteifd.ui.handlers;

import org.eclipse.e4.core.di.annotations.CanExecute;
import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.model.application.ui.menu.MItem;
import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;

import de.persosim.driver.connector.service.IfdConnector;
import de.persosim.driver.connector.ui.parts.ReaderPart;
import de.persosim.websocket.WebsocketComm;

public class RemoteIfdHandler {
	IfdConnector connector;
	
	@CanExecute
	public boolean checkState(final MPart mPart, final MItem mItem) {
		if (mPart.getObject() instanceof ReaderPart) {
			ReaderPart readerPartObject = (ReaderPart) mPart.getObject();
			mItem.setSelected(readerPartObject.getCurrentCommType() == WebsocketComm.NAME);
		}
		return true;
	}
	
	@Execute
	public void execute(final MPart mPart) {
		BasicLogger.log(this.getClass(), "RemoteIfd menu entry toggled", LogLevel.INFO);
		
		if (mPart.getObject() instanceof ReaderPart) {
			ReaderPart readerPartObject = (ReaderPart) mPart.getObject();
			
			readerPartObject.switchReaderType(new WebsocketComm(null, de.persosim.remoteifd.ui.Activator.getRemoteIfdConfig()));
		}
	}
		
}