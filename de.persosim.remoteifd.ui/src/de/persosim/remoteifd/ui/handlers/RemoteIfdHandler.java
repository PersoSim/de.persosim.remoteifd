
package de.persosim.remoteifd.ui.handlers;

import org.eclipse.e4.core.di.annotations.CanExecute;
import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.e4.ui.model.application.ui.menu.MItem;
import org.eclipse.e4.ui.workbench.modeling.EPartService;
import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;
import org.globaltester.logging.tags.LogTag;

import de.persosim.driver.connector.ui.parts.ReaderPart;
import de.persosim.simulator.log.PersoSimLogTags;
import de.persosim.websocket.WebsocketComm;
import jakarta.inject.Inject;

public class RemoteIfdHandler
{
	private EPartService partService;

	@Inject
	public RemoteIfdHandler(EPartService partService)
	{
		this.partService = partService;
	}

	@CanExecute
	public boolean checkState(final MItem mItem)
	{
		// ID of part as defined in fragment.e4xmi application model
		MPart readerPart = partService.findPart("de.persosim.driver.connector.ui.parts.reader");
		if (readerPart.getObject() instanceof ReaderPart mPart) {
			ReaderPart readerPartObject = mPart;
			mItem.setSelected(WebsocketComm.NAME.equals(readerPartObject.getCurrentCommType()));
		}
		return true;
	}

	@Execute
	public void execute()
	{
		BasicLogger.log("Remote IFD interface selected", LogLevel.INFO, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
		// ID of part as defined in fragment.e4xmi application model
		MPart readerPart = partService.findPart("de.persosim.driver.connector.ui.parts.reader");
		if (readerPart.getObject() instanceof ReaderPart mPart) {
			ReaderPart readerPartObject = mPart;
			readerPartObject.switchReaderType(new WebsocketComm(null, de.persosim.remoteifd.ui.Activator.getRemoteIfdConfig()));
		}
	}

}
