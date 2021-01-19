package de.persosim.remoteifd.ui.parts;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Map;
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
import org.eclipse.swt.widgets.TableColumn;
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
	private Text ifdName;
	private Table certificatesTable;
	private Label pin;
	private Button startPairing;

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
		if (comm != null) {
			comm.stop();	
		}
	}
	
	@Override
	protected void buttonPressed(int buttonId) {
		super.buttonPressed(buttonId);
	}
	
	@Override
	protected Control createDialogArea(Composite parentComposite) {
		final Composite parent = parentComposite;

		Composite container = (Composite) super.createDialogArea(parent);
		container.setLayout(new GridLayout(2, false));

		certificatesTable = new Table(container, SWT.MULTI | SWT.BORDER | SWT.V_SCROLL | SWT.H_SCROLL);
		GridData layoutData = new GridData();
		layoutData.horizontalSpan = 2;
		layoutData.grabExcessHorizontalSpace = true;
		layoutData.grabExcessVerticalSpace = true;
		layoutData.horizontalAlignment = SWT.FILL;
		layoutData.verticalAlignment = SWT.FILL;
		layoutData.minimumHeight = 200;
		layoutData.minimumWidth = 400;
		certificatesTable.setLayoutData(layoutData);
		
		TableColumn colName = new TableColumn(certificatesTable, SWT.RIGHT);
	    colName.setText("UDName");
	    TableColumn colCert = new TableColumn(certificatesTable, SWT.RIGHT);
	    colCert.setText("Certificate");
				
		refreshTable(certificatesTable);
		certificatesTable.requestLayout();

		Menu menu = new Menu(certificatesTable);
		certificatesTable.setMenu(menu);

		MenuItem delete = new MenuItem(menu, SWT.PUSH);
		delete.setText("Delete certificate");
		
		startPairing = new Button(container, SWT.NONE);
		startPairing.setText("Start pairing");
		GridData ifdNameLabelLayoutData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
		ifdNameLabelLayoutData.minimumWidth = 80;
		startPairing.setLayoutData(ifdNameLabelLayoutData);
		
		pin = new Label(container, SWT.NONE);
		pin.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));
		
		Label ifdNameLabel = new Label(container, SWT.NONE);
		ifdNameLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		ifdNameLabel.setText("IFD name:");
		
		ifdName = new Text(container, SWT.BORDER);
		String readerNameFromPrefs = PersoSimPreferenceManager.getPreference(PreferenceConstants.READER_NAME_PREFERENCE);
		
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
		
		if (readerNameFromPrefs == null){
			readerNameFromPrefs = "PersoSim";
			try {
				readerNameFromPrefs += "_" + InetAddress.getLocalHost().getHostName();
			} catch (UnknownHostException e) {
				//NOSONAR: The host name is only used for display purposes
			}
		}
		
		ifdName.setText(readerNameFromPrefs == null ? "" : readerNameFromPrefs);
		ifdName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
		ifdName.setEnabled(true);
		
		ifdName.addModifyListener(new ModifyListener() {
			@Override
			public void modifyText(ModifyEvent e) {
				if (!ifdName.getText().isEmpty()){
					PersoSimPreferenceManager.storePreference(PreferenceConstants.READER_NAME_PREFERENCE, ifdName.getText());	
				}
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
					String pairingCode = getPairingCode();
					createCommObject(pairingCode);
					comm.start();
					pin.setText("Pairing is active with pin: " + pairingCode);
					ifdName.setEnabled(false);
					container.requestLayout();
					parentComposite.layout();
				} else {
					if (comm != null) {
						comm.stop();	
					}
					startPairing.setText("Start pairing");
					pin.setText("");
					container.pack();
					ifdName.setEnabled(true);
					container.requestLayout();
					parentComposite.layout();
				}

			}



			@Override
			public void widgetDefaultSelected(SelectionEvent e) {
				// do nothing
			}
		});
		
		return container;
	}

	private void createCommObject(String pairingCode) {
		comm = new WebsocketComm(pairingCode, ifdName.getText(), Activator.getRemoteIfdConfig(), new HandshakeResultListener() {
			
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
						ifdName.setEnabled(true);
					}
				});

				 
			}

			@Override
			public void onConnectionClosed() {
				// intentionally ignore
				
			}
		});
	}
	
	private static String getPairingCode() {
		return String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
	}

	protected void refreshTable(Table certificatesTable) {
		certificatesTable.removeAll();

		RemoteIfdConfigManager configManager = Activator.getRemoteIfdConfig();
		
		Map<Certificate, String> certs = configManager.getPairedCertificates();
		for (Certificate cert : certs.keySet()) {
			TableItem item = new TableItem(certificatesTable, SWT.NONE);

			String udName = certs.get(cert);
			
			String certDescription = "";
			if (cert instanceof X509Certificate) {
				X509Certificate x509cert = (X509Certificate) cert;
				certDescription += x509cert.getSubjectDN();
			} else {
				certDescription = Integer.toHexString(cert.hashCode());
			}
			
			
			item.setText(new String[] { udName, certDescription });
			item.setData(cert);
		}

		certificatesTable.getColumn(0).pack();
		certificatesTable.getColumn(1).pack();
	}

	@Override
	protected void configureShell(Shell newShell) {
		super.configureShell(newShell);
		newShell.setText("PersoSim - Configure RemoteIFD");
	}

}
