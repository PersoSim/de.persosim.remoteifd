package de.persosim.remoteifd.ui.parts;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.util.Map;

import org.eclipse.e4.ui.model.application.ui.basic.MPart;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.swt.SWT;
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
import org.eclipse.swt.widgets.Spinner;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.TableItem;
import org.eclipse.swt.widgets.Text;
import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;
import org.globaltester.logging.tags.LogTag;

import de.persosim.driver.connector.ui.parts.ReaderPart;
import de.persosim.driver.connector.ui.parts.ReaderPart.ReaderType;
import de.persosim.remoteifd.ui.Activator;
import de.persosim.remoteifd.ui.PreferenceConstants;
import de.persosim.simulator.log.PersoSimLogTags;
import de.persosim.simulator.preferences.PersoSimPreferenceManager;
import de.persosim.websocket.HandshakeResultListener;
import de.persosim.websocket.RemoteIfdConfigManager;
import de.persosim.websocket.WebsocketComm;

public class ConfigRemoteIfdDialog extends Dialog
{
	private static final String START_PAIRING_TEXT = "Start pairing";
	private static final String STOP_PAIRING_TEXT = "Stop pairing";
	private static final String DIALOG_NAME = "PersoSim - Configure Remote IFD";

	private MPart readerPart;
	private WebsocketComm comm;
	private Text ifdName;
	private Table certificatesTable;
	private Spinner spinnerPIN;
	private Label pinInfo;
	private Button startPairing;


	public ConfigRemoteIfdDialog(Shell parentShell, MPart readerPart)
	{
		super(parentShell);
		this.readerPart = readerPart;
		BasicLogger.log(DIALOG_NAME + " dialog opened", LogLevel.INFO, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
	}

	@Override
	protected void createButtonsForButtonBar(Composite parent)
	{
		Button button = createButton(parent, IDialogConstants.OK_ID, IDialogConstants.OK_LABEL, true);
		button.addListener(SWT.Selection, e -> BasicLogger.log(DIALOG_NAME + " dialog closed", LogLevel.INFO, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID)));
	}

	@Override
	protected boolean isResizable()
	{
		return true;
	}

	@Override
	protected void handleShellCloseEvent()
	{
		super.handleShellCloseEvent();
		if (comm != null) {
			comm.stop();
			BasicLogger.log("Pairing stopped", LogLevel.INFO, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
		}
		BasicLogger.log(DIALOG_NAME + " closed", LogLevel.INFO, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
	}

	@Override
	protected Control createDialogArea(Composite parentComposite)
	{
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
		startPairing.setText(START_PAIRING_TEXT);
		GridData ifdNameLabelLayoutData = new GridData(SWT.BEGINNING, SWT.CENTER, false, false);
		ifdNameLabelLayoutData.minimumWidth = 80;
		startPairing.setLayoutData(ifdNameLabelLayoutData);

		pinInfo = new Label(container, SWT.NONE);
		pinInfo.setLayoutData(new GridData(SWT.CENTER, SWT.CENTER, false, false));

		Label pinLabel = new Label(container, SWT.NONE);
		pinLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		pinLabel.setText("Pairing PIN:");

		spinnerPIN = new Spinner(container, SWT.BORDER);
		spinnerPIN.setToolTipText("4-digit pairing PIN. If necessary, leading zeros will be prepended.");
		// Set minimum and maximum values
		spinnerPIN.setMinimum(0000);
		spinnerPIN.setMaximum(9999);
		// Set the maximum number of characters (4 digits)
		spinnerPIN.setTextLimit(4);
		// Set the initial selection value
		spinnerPIN.setSelection(1234);

		Label ifdNameLabel = new Label(container, SWT.NONE);
		ifdNameLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
		ifdNameLabel.setText("IFD name:");

		ifdName = new Text(container, SWT.BORDER);
		String readerNameFromPrefs = PersoSimPreferenceManager.getPreference(PreferenceConstants.READER_NAME_PREFERENCE);

		delete.addSelectionListener(new SelectionListener() {

			@Override
			public void widgetSelected(SelectionEvent e)
			{
				int selectedItemIndex = certificatesTable.getSelectionIndex();
				if (selectedItemIndex == -1)
					return;
				TableItem selectedItem = certificatesTable.getItem(selectedItemIndex);
				Map.Entry<Certificate, String> cert = (Map.Entry<Certificate, String>) selectedItem.getData();
				Activator.getRemoteIfdConfig().deletePairedCertificate(cert.getKey());
				BasicLogger.log("Pairing certificate deleted: '" + selectedItem.getText(), LogLevel.WARN, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
				refreshTable(certificatesTable);
			}

			@Override
			public void widgetDefaultSelected(SelectionEvent e)
			{
				// Do nothing
			}
		});

		if (readerNameFromPrefs == null) {
			readerNameFromPrefs = "PersoSim";
			try {
				readerNameFromPrefs += "_" + InetAddress.getLocalHost().getHostName();
			}
			catch (UnknownHostException e) {
				// NOSONAR: The host name is only used for display purposes
			}
		}

		ifdName.setText(readerNameFromPrefs == null ? "" : readerNameFromPrefs);
		ifdName.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, true));
		ifdName.setEnabled(true);

		ifdName.addModifyListener(e -> {
			if (!ifdName.getText().isEmpty()) {
				PersoSimPreferenceManager.storePreference(PreferenceConstants.READER_NAME_PREFERENCE, ifdName.getText());
			}
		});

		startPairing.addSelectionListener(new SelectionListener() {

			@Override
			public void widgetSelected(SelectionEvent e)
			{
				if (pinInfo.getText().isEmpty()) {
					startPairing.setText(STOP_PAIRING_TEXT);

					if (readerPart.getObject() instanceof ReaderPart readerPartObject) {
						readerPartObject.switchReaderType(ReaderType.NONE);
					}
					String pairingCode = String.format("%04d", spinnerPIN.getSelection());
					createCommObject(pairingCode);
					comm.start();
					pinInfo.setText("Pairing is active with PIN: " + pairingCode);
					BasicLogger.log("Pairing is active with PIN: " + pairingCode + " (IFD name: '" + ifdName.getText() + "')", LogLevel.INFO,
							new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
					ifdName.setEnabled(false);
					container.requestLayout();
					parentComposite.layout();
				}
				else {
					if (comm != null) {
						comm.stop();
						BasicLogger.log("Pairing stopped", LogLevel.INFO, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
					}
					startPairing.setText(START_PAIRING_TEXT);
					pinInfo.setText("");
					container.pack();
					ifdName.setEnabled(true);
					container.requestLayout();
					parentComposite.layout();
				}

			}


			@Override
			public void widgetDefaultSelected(SelectionEvent e)
			{
				// do nothing
			}
		});

		return container;
	}

	private void createCommObject(String pairingCode)
	{
		comm = new WebsocketComm(pairingCode, Activator.getRemoteIfdConfig(), new HandshakeResultListener() {

			@Override
			public void onHandshakeFinished(boolean success)
			{
				Display.getDefault().asyncExec(() -> {
					MessageBox mb;
					if (success) {
						mb = new MessageBox(getParentShell(), SWT.ICON_INFORMATION);
						mb.setMessage("Pairing successful");
						mb.setText("Pairing successful");
					}
					else {
						mb = new MessageBox(getParentShell(), SWT.ICON_ERROR);
						mb.setMessage("Pairing failed");
						mb.setText("Pairing failed");
					}
					mb.open();

					refreshTable(certificatesTable);
					startPairing.setText(START_PAIRING_TEXT);
					pinInfo.setText("");
					ifdName.setEnabled(true);
				});
			}

			@Override
			public void onConnectionClosed()
			{
				// intentionally ignore

			}
		});
	}

	protected void refreshTable(Table certificatesTable)
	{
		certificatesTable.removeAll();

		RemoteIfdConfigManager configManager = Activator.getRemoteIfdConfig();

		Map<Certificate, String> certs = configManager.getPairedCertificates();
		for (Map.Entry<Certificate, String> cert : certs.entrySet()) {
			TableItem item = new TableItem(certificatesTable, SWT.NONE);
			String udName = cert.getValue();
			String certDescription = "";
			if (cert instanceof X509Certificate x509cert) {
				certDescription += x509cert.getSubjectX500Principal();
			}
			else {
				certDescription = Integer.toHexString(cert.hashCode());
			}

			item.setText(new String[] { udName, certDescription });
			item.setData(cert);
			BasicLogger.log("Pairing certificate: '" + udName + "' / '" + certDescription + "'", LogLevel.INFO, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
		}

		certificatesTable.getColumn(0).pack();
		certificatesTable.getColumn(1).pack();
	}

	@Override
	protected void configureShell(Shell newShell)
	{
		super.configureShell(newShell);
		newShell.setText(DIALOG_NAME);
	}

}
