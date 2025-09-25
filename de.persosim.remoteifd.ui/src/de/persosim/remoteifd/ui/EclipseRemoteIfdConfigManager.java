package de.persosim.remoteifd.ui;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPrivateKey;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.globaltester.base.PreferenceHelper;
import org.globaltester.cryptoprovider.bc.TlsCertificateGenerator;
import org.json.JSONException;
import org.json.JSONObject;

import de.persosim.simulator.preferences.PersoSimPreferenceManager;
import de.persosim.simulator.utils.HexString;
import de.persosim.websocket.RemoteIfdConfigManager;

/**
 * Stores remote interface device configuration in eclipse preferences.
 *
 * @author boonk.martin
 *
 */
public class EclipseRemoteIfdConfigManager implements RemoteIfdConfigManager
{

	private static final String PREFERENCE_KEY_KEYSTORE_HEX = "remote.ifd.config.keystore.hex";
	private static final String PREFERENCE_KEY_KEYSTORE_STORE_PASSWORD = "remote.ifd.config.keystore.password";
	private static final String PREFERENCE_KEY_KEYSTORE_KEY_PASSWORD = "remote.ifd.config.keystore.key.password";
	private static final String PREFERENCE_KEY_PAIRED_CERTS = "remote.ifd.config.pairedcerts";
	private static final String PREFERENCE_KEY_HOST_CERT_ALIAS = "default";

	private static final String STORETYPE = "JKS";

	private KeyStore keyStore;
	private char[] privateKeyPassword = new char[0];
	private String bundleId;

	public EclipseRemoteIfdConfigManager(String bundleId)
	{
		this.bundleId = bundleId;
		String keystoreHexData = PreferenceHelper.getPreferenceValue(bundleId, PREFERENCE_KEY_KEYSTORE_HEX);
		String keystorePasswordPreferenceValue = PreferenceHelper.getPreferenceValue(bundleId, PREFERENCE_KEY_KEYSTORE_STORE_PASSWORD);
		char[] keystorePassword = new char[0];
		if (keystorePasswordPreferenceValue != null) {
			keystorePassword = keystorePasswordPreferenceValue.toCharArray();
		}
		String privateKeyPreferenceValue = PreferenceHelper.getPreferenceValue(bundleId, PREFERENCE_KEY_KEYSTORE_KEY_PASSWORD);
		if (privateKeyPreferenceValue != null) {
			privateKeyPassword = privateKeyPreferenceValue.toCharArray();
		}

		try {
			keyStore = KeyStore.getInstance(STORETYPE);
			if (keystoreHexData != null) {
				keyStore.load(new ByteArrayInputStream(HexString.toByteArray(keystoreHexData)), keystorePassword);
			}
			else {
				keyStore.load(null, null);

				createSelfSignedCertificate();

				ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
				keyStore.store(byteArrayOutputStream, keystorePassword);
				PreferenceHelper.setPreferenceValue(bundleId, PREFERENCE_KEY_KEYSTORE_HEX, HexString.encode(byteArrayOutputStream.toByteArray()));
			}
		}
		catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | IllegalStateException e) {
			throw new IllegalStateException("Could not instantiate config manager", e);
		}

	}

	private void createSelfSignedCertificate() throws KeyStoreException, NoSuchAlgorithmException, IllegalStateException
	{
		KeyPair keyPair = TlsCertificateGenerator.generateKeyPair();
		Certificate certificate = TlsCertificateGenerator.generateTlsCertificate(keyPair);
		Certificate[] certChain = new Certificate[1];
		certChain[0] = certificate;
		keyStore.setKeyEntry(PREFERENCE_KEY_HOST_CERT_ALIAS, keyPair.getPrivate(), privateKeyPassword, certChain);
	}

	@Override
	public Certificate getHostCertificate()
	{
		try {
			return keyStore.getCertificate(PREFERENCE_KEY_HOST_CERT_ALIAS);
		}
		catch (KeyStoreException e) {
			throw new IllegalStateException("Could not get own certificate", e);
		}
	}

	@Override
	public RSAPrivateKey getHostPrivateKey()
	{
		try {
			return (RSAPrivateKey) keyStore.getKey(PREFERENCE_KEY_HOST_CERT_ALIAS, privateKeyPassword);
		}
		catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e) {
			throw new IllegalStateException("Could not get private key", e);
		}
	}

	@Override
	public Map<Certificate, String> getPairedCertificates()
	{
		HashMap<Certificate, String> retVal = new HashMap<>();

		JSONObject jsonData = new JSONObject();
		String data = PreferenceHelper.getPreferenceValue(bundleId, PREFERENCE_KEY_PAIRED_CERTS);
		if (data != null) {
			jsonData = new JSONObject(data);
		}


		for (Iterator<String> certIter = jsonData.keys(); certIter.hasNext();) {
			String curCertString = certIter.next();

			try {
				Certificate cert = CertificateFactory.getInstance("X.509").generateCertificate(new ByteArrayInputStream(HexString.toByteArray(curCertString)));
				String udName = jsonData.getString(curCertString);

				retVal.put(cert, udName);
			}
			catch (CertificateException e) {
				// ignore this certificate for the future
			}

		}

		return retVal;
	}

	@Override
	public void addPairedCertificate(Certificate newCert)
	{
		Map<Certificate, String> certificates = getPairedCertificates();

		certificates.put(newCert, "unknown (until first use)");

		storeToPrefs(certificates);
	}

	@Override
	public void deletePairedCertificate(Certificate delCert)
	{
		Map<Certificate, String> certificates = getPairedCertificates();

		certificates.remove(delCert);

		storeToPrefs(certificates);
	}

	@Override
	public void updateUdNameForCertificate(Certificate cert, String udName)
	{
		Map<Certificate, String> certificates = getPairedCertificates();

		certificates.put(cert, udName);

		storeToPrefs(certificates);
	}

	private void storeToPrefs(Map<Certificate, String> data)
	{
		JSONObject jsonData = new JSONObject();

		for (Certificate cert : data.keySet()) {
			try {
				jsonData.put(HexString.encode(cert.getEncoded()), (data.get(cert)));
			}
			catch (CertificateEncodingException | JSONException e) {
				// ignore this certificate for the future
			}
		}

		PreferenceHelper.setPreferenceValue(bundleId, PREFERENCE_KEY_PAIRED_CERTS, jsonData.toString());
		PreferenceHelper.flush(bundleId);

	}

	@Override
	public String getName()
	{
		String retVal = PersoSimPreferenceManager.getPreference(PreferenceConstants.READER_NAME_PREFERENCE);

		if (retVal == null) {
			retVal = "PersoSim";
			try {
				retVal += "_" + InetAddress.getLocalHost().getHostName();
			}
			catch (UnknownHostException e) {
				// NOSONAR: The host name is only used for display purposes
			}
		}

		return retVal;
	}

}
