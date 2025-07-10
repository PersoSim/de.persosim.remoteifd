package de.persosim.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Vector;

import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.CertificateRequest;
import org.bouncycastle.tls.ClientCertificateType;
import org.bouncycastle.tls.DefaultTlsServer;
import org.bouncycastle.tls.HashAlgorithm;
import org.bouncycastle.tls.SignatureAlgorithm;
import org.bouncycastle.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.tls.TlsCredentialedSigner;
import org.bouncycastle.tls.TlsServerProtocol;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.tls.crypto.TlsCryptoParameters;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedSigner;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;
import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;
import org.globaltester.logging.tags.LogTag;

import de.persosim.simulator.log.PersoSimLogTags;
import de.persosim.simulator.utils.HexString;

public class DefaultHandshaker implements TlsHandshaker
{

	private Socket clientSocket;
	private TlsServerProtocol protocol;
	private RemoteIfdConfigManager remoteIfdConfig;
	protected Certificate clientCert;

	public DefaultHandshaker(RemoteIfdConfigManager remoteIfdConfig, Socket client)
	{
		this.clientSocket = client;
		this.remoteIfdConfig = remoteIfdConfig;
	}

	@Override
	public boolean performHandshake()
	{
		TlsCrypto crypto = new BcTlsCrypto(new SecureRandom());

		try {

			protocol = new TlsServerProtocol(clientSocket.getInputStream(), clientSocket.getOutputStream());

			protocol.accept(new DefaultTlsServer(crypto) {

				@Override
				protected TlsCredentialedSigner getRSASignerCredentials() throws IOException
				{
					return new BcDefaultTlsCredentialedSigner(new TlsCryptoParameters(context), (BcTlsCrypto) getCrypto(),
							CertificateConverter.fromJavaKeyToBcAsymetricKeyParameter(remoteIfdConfig.getHostPrivateKey()),
							CertificateConverter.fromJavaCertificateToBcTlsCertificate(remoteIfdConfig.getHostCertificate()),
							new SignatureAndHashAlgorithm(HashAlgorithm.sha256, SignatureAlgorithm.rsa));
				}

				@Override
				public void notifyClientCertificate(Certificate arg0) throws IOException
				{
					validateCertificate(arg0);
					clientCert = arg0;
				}

				private void validateCertificate(Certificate cert)
				{
					java.security.cert.Certificate javaCert = CertificateConverter.fromBcTlsCertificateToJavaCertificate(cert);
					if (!remoteIfdConfig.getPairedCertificates().keySet().contains(javaCert)) {
						BasicLogger.log("The certificate with serial 0x" + HexString.encode(cert.getCertificateAt(0).getSerialNumber()) + " is not paired", LogLevel.ERROR,
								new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
						throw new IllegalArgumentException("Unknown cert " + cert);
					}
				}

				@Override
				public CertificateRequest getCertificateRequest()
				{
					Vector<Object> signatureAndHash = new Vector<>();
					signatureAndHash.add(new SignatureAndHashAlgorithm(HashAlgorithm.sha256, SignatureAlgorithm.rsa));
					return new CertificateRequest(new short[] { ClientCertificateType.rsa_sign }, signatureAndHash, null);
				}

				@Override
				public void notifyHandshakeComplete() throws IOException
				{
					super.notifyHandshakeComplete();
					BasicLogger.log("Handshake done", LogLevel.DEBUG, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
				}

				@Override
				public void notifyOfferedCipherSuites(int[] arg0) throws IOException
				{
					super.notifyOfferedCipherSuites(arg0);

					StringBuilder cipherSuites = new StringBuilder("Offered cipher suites:");
					for (int i : arg0) {
						cipherSuites.append(System.lineSeparator()).append(Integer.toHexString(i));
					}
					BasicLogger.log(cipherSuites.toString(), LogLevel.DEBUG, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
				}

			});
			return true;

		}
		catch (IOException e) {
			BasicLogger.logException("Other side closed the connection", e, LogLevel.WARN, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
		}
		return false;
	}

	@Override
	public void closeConnection()
	{
		try {
			BasicLogger.log("Closing TLS connection", LogLevel.DEBUG, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
			protocol.close();
		}
		catch (IOException e) {
			// Expected when peer closes socket too early
			BasicLogger.log("Exception during closing of tls server, probably due to early close: " + e.getMessage(), LogLevel.INFO, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
		}
	}

	@Override
	public InputStream getInputStream()
	{
		return protocol.getInputStream();
	}

	@Override
	public OutputStream getOutputStream()
	{
		return protocol.getOutputStream();
	}

	@Override
	public Certificate getClientCertificate()
	{
		return clientCert;
	}

}
