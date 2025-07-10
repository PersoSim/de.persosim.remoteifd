
package de.persosim.remoteifd.ui.handlers;

import org.eclipse.e4.core.di.annotations.CanExecute;
import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.model.application.ui.menu.MItem;
import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;
import org.globaltester.logging.tags.LogTag;

import de.persosim.driver.connector.service.IfdConnector;
import de.persosim.driver.connector.ui.parts.ReaderPart;
import de.persosim.simulator.log.PersoSimLogTags;
import de.persosim.websocket.WebsocketComm;

public class RemoteIfdHandler
{
	IfdConnector connector;

	@CanExecute
	public boolean checkState(final MPart mPart, final MItem mItem)
	{
		if (mPart.getObject() instanceof ReaderPart readerPartObject) {
			readerPartObject = (ReaderPart) mPart.getObject();
			mItem.setSelected(WebsocketComm.NAME.equals(readerPartObject.getCurrentCommType()));
		}
		return true;
	}

	@Execute
	public void execute(final MPart mPart, final MItem mItem)
	{
		if (mItem.isSelected())
			BasicLogger.log("Remote IFD interface selected", LogLevel.INFO, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));

		if (mPart.getObject() instanceof ReaderPart readerPartObject) {
			readerPartObject = (ReaderPart) mPart.getObject();
			readerPartObject.switchReaderType(new WebsocketComm(null, de.persosim.remoteifd.ui.Activator.getRemoteIfdConfig()));
		}
	}

}