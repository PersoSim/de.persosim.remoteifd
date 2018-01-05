package de.persosim.websocket;

import java.security.cert.Certificate;
import java.security.interfaces.RSAPrivateKey;
import java.util.Collection;

/**
 * Implementations of this interface supply configuration information for the SaK interface implementation.
 * @author boonk.martin
 *
 */
public interface RemoteIfdConfigManager {

	Certificate getHostCertificate();

	RSAPrivateKey getHostPrivateKey();

	Collection<Certificate> getPairedCertificates();

	void addPairedCertificate(Certificate certificate);

	void deletePairedCertificate(Certificate certificate);

}