 
package de.persosim.remoteifd.ui.handlers;

import org.eclipse.e4.core.di.annotations.Execute;
import org.eclipse.swt.widgets.Shell;

import de.persosim.remoteifd.ui.parts.ConfigRemoteIfdDialog;

public class ConfigureRemoteIfdHandler {
	
	@Execute
	public void execute(Shell shell) {
		ConfigRemoteIfdDialog dialog = new ConfigRemoteIfdDialog(shell);
		dialog.open();
	}
		
}