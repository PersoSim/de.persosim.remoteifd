package de.persosim.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.UnrecoverableKeyException;
import java.security.cert.Certificate;
import java.security.cert.CertificateException;
import java.security.interfaces.RSAPrivateKey;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;

/**
 * Load from a given keystore and do not persist. This implementation is used for test purposes.
 * @author boonk.martin
 *
 */
public class KeystoreRemoteIfdConfigManager implements RemoteIfdConfigManager {
	private KeyStore keyStore;
	private char[] privateKeyPassword;

	public KeystoreRemoteIfdConfigManager(InputStream keyStoreStream, char[] keyStorePassword,
			char[] privateKeyPassword) {
		this.privateKeyPassword = privateKeyPassword;
		try {
			keyStore = KeyStore.getInstance("JKS");
			keyStore.load(keyStoreStream, keyStorePassword);
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
			throw new IllegalArgumentException("The given keystore could not be loaded", e);
		}
	}

	@Override
	public Certificate getHostCertificate() {
		try {
			return keyStore.getCertificate("default");
		} catch (KeyStoreException e) {
			throw new IllegalStateException("Could not get own certificate", e);
		}
	}

	@Override
	public RSAPrivateKey getHostPrivateKey() {
		try {
			return (RSAPrivateKey) keyStore.getKey("default", privateKeyPassword);
		} catch (UnrecoverableKeyException | KeyStoreException | NoSuchAlgorithmException e) {
			throw new IllegalStateException("Could not get private key", e);
		}
	}

	@Override
	public Map<Certificate, String> getPairedCertificates() {
		try {
			Enumeration<String> aliases = keyStore.aliases();

			Map<Certificate, String> retVal = new HashMap<Certificate, String>();

			while (aliases.hasMoreElements()) {
				String current = aliases.nextElement();
				if (current != "default") {
					retVal.put(keyStore.getCertificate(current), "no name for tests");
				}
			}

			return retVal;
		} catch (KeyStoreException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void addPairedCertificate(Certificate certificate) {
		try {
			keyStore.setCertificateEntry(Integer.toHexString(certificate.hashCode()), certificate);
		} catch (KeyStoreException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void deletePairedCertificate(Certificate certificate) {
		try {
			keyStore.deleteEntry(Integer.toHexString(certificate.hashCode()));
		} catch (KeyStoreException e) {
			throw new IllegalStateException(e);
		}
	}

	@Override
	public void updateUdNameForCertificate(Certificate certificate, String ifdName) {
		// intentionally ignored in tests
	}

	@Override
	public String getName() {
		return "RemoteIfdTest";
	}

}
