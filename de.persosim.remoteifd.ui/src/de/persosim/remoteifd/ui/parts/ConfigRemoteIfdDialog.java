package de.persosim.remoteifd.ui.parts;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.layout.FormLayout;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;

public class ConfigRemoteIfdDialog extends Dialog {
	
	public ConfigRemoteIfdDialog(Shell parentShell) {
		super(parentShell);
	}

	@Override
	protected Control createDialogArea(Composite parentComposite) {
		final Composite parent = parentComposite;
		
		parent.setLayout(new GridLayout(1, false));

		Composite container = (Composite) super.createDialogArea(parent);
		container.setLayout(new GridLayout(1, true));

		Composite upper = new Composite(container, SWT.NONE);

		FormLayout layout = new FormLayout();
		layout.marginHeight = 5;
		layout.marginWidth = 5;
		upper.setLayout(layout);
		
		Label label2 = new Label(upper, SWT.NONE);
		label2.setText("Martin, hier kannst Du dich austoben ;-)");

		return container;
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("PersoSim - Configure RemoteIFD");
	}

}
