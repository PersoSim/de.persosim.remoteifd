package de.persosim.remoteifd.ui.parts;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.concurrent.ThreadLocalRandom;

import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.MessageBox;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;

import de.persosim.driver.connector.ui.parts.ReaderPart;
import de.persosim.driver.connector.ui.parts.ReaderPart.ReaderType;
import de.persosim.remoteifd.ui.Activator;
import de.persosim.remoteifd.ui.PreferenceConstants;
import de.persosim.simulator.preferences.PersoSimPreferenceManager;
import de.persosim.websocket.HandshakeResultListener;
import de.persosim.websocket.RemoteIfdConfigManager;
import de.persosim.websocket.WebsocketComm;

public class ConfigRemoteIfdDialog extends Dialog {

	private MPart readerPart;
	private WebsocketComm comm;

	public ConfigRemoteIfdDialog(Shell parentShell, MPart readerPart) {
		super(parentShell);
		this.readerPart = readerPart;
	}
	
	
	
	@Override
	protected boolean isResizable() {
		return true;
	}

	@Override
	protected void handleShellCloseEvent() {
		super.handleShellCloseEvent();
		comm.stop();
	}
	
	@Override
	protected void buttonPressed(int buttonId) {
		super.buttonPressed(buttonId);
		comm.stop();
	}
	
	@Override
	protected Control createDialogArea(Composite parentComposite) {
		final Composite parent = parentComposite;

		Composite container = (Composite) super.createDialogArea(parent);
		container.setLayout(new GridLayout(2, true));

		Table certificatesTable = new Table(container, SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		GridData layoutData = new GridData(GridData.FILL_BOTH);
		layoutData.horizontalSpan = 2;
		layoutData.heightHint = 200;
		layoutData.widthHint = 400;
		certificatesTable.setLayoutData(layoutData);
		refreshTable(certificatesTable);
		certificatesTable.requestLayout();

		Menu menu = new Menu(certificatesTable);
		certificatesTable.setMenu(menu);

		MenuItem delete = new MenuItem(menu, SWT.PUSH);
		delete.setText("Delete certificate");

		delete.addSelectionListener(new SelectionListener() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				Activator.getRemoteIfdConfig().deletePairedCertificate(
						(Certificate) certificatesTable.getItem(certificatesTable.getSelectionIndex()).getData());
				refreshTable(certificatesTable);
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				// Do nothing
			}
		});
		
		Button startPairing = new Button(container, SWT.NONE);
		startPairing.setText("Start pairing");
		startPairing.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		
		Label pin = new Label(container, SWT.NONE);
		pin.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
		
		Label readerNameLabel = new Label(container, SWT.NONE);
		readerNameLabel.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
		readerNameLabel.setText("Reader name:");
		
		Text readerName = new Text(container, SWT.NONE);
		String readerNameFromPrefs = PersoSimPreferenceManager.getPreference(PreferenceConstants.READER_NAME_PREFERENCE);
		
		if (readerNameFromPrefs == null){
			readerNameFromPrefs = "PersoSim";
			try {
				readerNameFromPrefs += "_" + InetAddress.getLocalHost().getHostName();
			} catch (UnknownHostException e) {
				//NOSONAR: The host name is only used for display purposes
			}
		}
		
		boolean nameChangeAllowed = Activator.getRemoteIfdConfig().getPairedCertificates().isEmpty();
		
		readerName.setText(readerNameFromPrefs);
		readerName.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		readerName.setEnabled(nameChangeAllowed);
		
		readerName.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (!readerName.getText().isEmpty()){
					PersoSimPreferenceManager.storePreference(PreferenceConstants.READER_NAME_PREFERENCE, readerName.getText());	
				}
			}
		});

		String pairingCode = getPairingCode();
		comm = new WebsocketComm(pairingCode, readerName.getText(), Activator.getRemoteIfdConfig(), new HandshakeResultListener() {
			
			@Override
			public void onHandshakeFinished(boolean success) {
				Display.getDefault().asyncExec(new Runnable() {
					
					@Override
					public void run() {
						MessageBox mb;
						if (success) {
							mb = new MessageBox(getParentShell(), SWT.ICON_INFORMATION);
							mb.setMessage("Pairing successfull");
							mb.setText("Pairing successfull");
						} else {
							mb = new MessageBox(getParentShell(), SWT.ICON_ERROR);
							mb.setMessage("Pairing failed");
							mb.setText("Pairing failed");

						}
						mb.open();

						refreshTable(certificatesTable);
						startPairing.setText("Start pairing");
						pin.setText("");
						readerName.setEnabled(true && nameChangeAllowed);
					}
				});

				 
			}
		});
		
		startPairing.addSelectionListener(new SelectionListener() {
			
			@Override
			public void widgetSelected(SelectionEvent e) {
				if (pin.getText().isEmpty()) {
					startPairing.setText("Stop pairing");

					if (readerPart.getObject() instanceof ReaderPart) {
						ReaderPart readerPartObject = (ReaderPart) readerPart.getObject();

						readerPartObject.switchReaderType(ReaderType.NONE);
					}
					
					comm.start();
					pin.setText("Pairing is active with pin: " + pairingCode);
					readerName.setEnabled(false);
				} else {
					if (comm != null) {
						comm.stop();	
					}
					startPairing.setText("Start pairing");
					pin.setText("");
					readerName.setEnabled(true && nameChangeAllowed);
				}

			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				// do nothing
			}
		});

		return container;
	}
	
	private static String getPairingCode() {
		return String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
	}

	protected void refreshTable(Table certificatesTable) {
		certificatesTable.removeAll();

		RemoteIfdConfigManager configManager = Activator.getRemoteIfdConfig();
		for (Certificate cert : configManager.getPairedCertificates()) {
			TableItem item = new TableItem(certificatesTable, SWT.NONE);
			
			String certDescription = "";
			
			if (cert instanceof X509Certificate) {
				X509Certificate x509cert = (X509Certificate) cert;
				certDescription += x509cert.getSubjectDN();
			} else {
				certDescription = Integer.toHexString(cert.hashCode());
			}
			
			
			item.setText(new String[] { certDescription });
			item.setData(cert);
		}
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("PersoSim - Configure RemoteIFD");
	}

}
