package de.persosim.remoteifd.ui;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.Key;
import java.security.KeyPair;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.SignatureException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.cert.CertificateFactory;
import java.security.interfaces.RSAPrivateKey;
import java.util.Collection;
import java.util.HashSet;

import org.globaltester.base.PreferenceHelper;
import org.globaltester.lib.bctls.TlsCertificateGenerator;

import de.persosim.simulator.utils.HexString;
import de.persosim.websocket.RemoteIfdConfigManager;

/**
 * Stores remote interface device configuration in eclipse preferences.
 * @author boonk.martin
 *
 */
public class EclipseRemoteIfdConfigManager implements RemoteIfdConfigManager {

	private static final String PREFERENCE_KEY_KEYSTORE_HEX = "remote.ifd.config.keystore.hex";
	private static final String PREFERENCE_KEY_KEYSTORE_STORE_PASSWORD = "remote.ifd.config.keystore.password";
	private static final String PREFERENCE_KEY_KEYSTORE_KEY_PASSWORD = "remote.ifd.config.keystore.key.password";
	private static final String PREFERENCE_KEY_PAIRED_CERTS = "remote.ifd.config.pairedcerts";
	private static final String PREFERENCE_KEY_HOST_CERT_ALIAS = "default";

	private static final String STORETYPE = "JKS";
	
	private KeyStore keyStore;
	private char[] privateKeyPassword = new char[0];
	private String bundleId;

	public EclipseRemoteIfdConfigManager(String bundleId) {
		this.bundleId = bundleId;
		String keystoreHexData = PreferenceHelper.getPreferenceValue(bundleId, PREFERENCE_KEY_KEYSTORE_HEX);
		String keystorePasswordPreferenceValue = PreferenceHelper.getPreferenceValue(bundleId,
				PREFERENCE_KEY_KEYSTORE_STORE_PASSWORD);
		char[] keystorePassword = new char[0];
		if (keystorePasswordPreferenceValue != null) {
			keystorePassword = keystorePasswordPreferenceValue.toCharArray();
		}
		String privateKeyPreferenceValue = PreferenceHelper.getPreferenceValue(bundleId,
				PREFERENCE_KEY_KEYSTORE_KEY_PASSWORD);
		if (privateKeyPreferenceValue != null) {
			privateKeyPassword = privateKeyPreferenceValue.toCharArray();
		}

		try {
			keyStore = KeyStore.getInstance(STORETYPE);
			if (keystoreHexData != null) {
				keyStore.load(new ByteArrayInputStream(HexString.toByteArray(keystoreHexData)), keystorePassword);
			} else {
				keyStore.load(null, null);

				createSelfSignedCertificate();

				ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
				keyStore.store(byteArrayOutputStream, keystorePassword);
				PreferenceHelper.setPreferenceValue(bundleId, PREFERENCE_KEY_KEYSTORE_HEX,
						HexString.encode(byteArrayOutputStream.toByteArray()));
			}
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException | InvalidKeyException | IllegalStateException | NoSuchProviderException | SignatureException e) {
			throw new IllegalStateException("Could not instantiate config manager", e);
		}

	}

	private void createSelfSignedCertificate() throws KeyStoreException, NoSuchAlgorithmException, CertificateEncodingException, InvalidKeyException, IllegalStateException, NoSuchProviderException, SignatureException {
		KeyPair keyPair = TlsCertificateGenerator.generateKeyPair();
		Certificate certificate = TlsCertificateGenerator.generateTlsCertificate(keyPair);
		Certificate[] certChain = new Certificate[1];
		certChain[0] = certificate;
		keyStore.setKeyEntry("default", (Key) keyPair.getPrivate(), privateKeyPassword, certChain);
	}

	@Override
	public Certificate getHostCertificate() {
		try {
			return keyStore.getCertificate(PREFERENCE_KEY_HOST_CERT_ALIAS);
		} catch (KeyStoreException e) {
			throw new IllegalStateException("Could not get own certificate", e);
		}
	}

	@Override
	public RSAPrivateKey getHostPrivateKey() {
		try {
			return (RSAPrivateKey) keyStore.getKey(PREFERENCE_KEY_HOST_CERT_ALIAS, privateKeyPassword);
		} catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e) {
			throw new IllegalStateException("Could not get private key", e);
		}
	}

	@Override
	public Collection<Certificate> getPairedCertificates() {
		String pairedCerts = PreferenceHelper.getPreferenceValue(bundleId, PREFERENCE_KEY_PAIRED_CERTS);

		Collection<Certificate> certs = new HashSet<>();
		if (pairedCerts != null && !pairedCerts.isEmpty()) {
			for (String currentCert : pairedCerts.split(":")) {
				if (currentCert.isEmpty()) {
					continue;
				}
				try {
					certs.add(CertificateFactory.getInstance("X.509")
							.generateCertificate(new ByteArrayInputStream(HexString.toByteArray(currentCert))));
				} catch (CertificateException e) {
					throw new IllegalStateException("Parsing of paired certificates failed", e);
				}
			}
		}
		return certs;
	}

	@Override
	public void addPairedCertificate(Certificate certificate) {
		String pairedCerts = PreferenceHelper.getPreferenceValue(bundleId, PREFERENCE_KEY_PAIRED_CERTS);
		if (pairedCerts == null) {
			pairedCerts = "";
		}
		try {
			if (!pairedCerts.isEmpty()) {
				pairedCerts += ":";
			}
			pairedCerts += HexString.encode(certificate.getEncoded());
			
			PreferenceHelper.setPreferenceValue(bundleId, PREFERENCE_KEY_PAIRED_CERTS, pairedCerts);
			PreferenceHelper.flush(bundleId);
		} catch (CertificateEncodingException e) {
			throw new IllegalStateException("Adding of a new paired certificate failed", e);
		}
	}

	@Override
	public void deletePairedCertificate(Certificate certificate) {
		String pairedCerts = PreferenceHelper.getPreferenceValue(bundleId, PREFERENCE_KEY_PAIRED_CERTS);

		String toDelete;
		try {
			toDelete = HexString.encode(certificate.getEncoded());
			
			pairedCerts = pairedCerts.replace(toDelete, "");
			pairedCerts = cleanupCerts(pairedCerts);

			PreferenceHelper.setPreferenceValue(bundleId, PREFERENCE_KEY_PAIRED_CERTS,pairedCerts);
			PreferenceHelper.flush(bundleId);
		} catch (CertificateEncodingException e) {
			throw new IllegalStateException("Deletion of a paired certificate failed", e);
		}
	}

	private String cleanupCerts(String pairedCerts) {
		while (pairedCerts.contains("::")) {
			pairedCerts = pairedCerts.replaceAll("::", ":");
		}
		if (pairedCerts.startsWith(":")) {
			pairedCerts = pairedCerts.substring(1);
		}
		if (pairedCerts.endsWith(":")) {
			pairedCerts = pairedCerts.substring(0, pairedCerts.length() - 2);
		}
		return pairedCerts;
	}
}
