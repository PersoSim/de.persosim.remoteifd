package de.persosim.websocket;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.SecureRandom;
import java.util.Vector;

import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.CertificateRequest;
import org.bouncycastle.tls.CipherSuite;
import org.bouncycastle.tls.ClientCertificateType;
import org.bouncycastle.tls.HashAlgorithm;
import org.bouncycastle.tls.PSKTlsServer;
import org.bouncycastle.tls.SignatureAlgorithm;
import org.bouncycastle.tls.SignatureAndHashAlgorithm;
import org.bouncycastle.tls.TlsCredentialedDecryptor;
import org.bouncycastle.tls.TlsKeyExchangeFactory;
import org.bouncycastle.tls.TlsPSKIdentityManager;
import org.bouncycastle.tls.TlsServerProtocol;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedDecryptor;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;
import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;


public class PairingServer implements TlsHandshaker {

	private byte[] psk;
	private Socket clientSocket;
	private TlsServerProtocol protocol;
	private RemoteIfdConfigManager remoteIfdConfig;
	private Certificate clientCert = null;

	public PairingServer(String psk, RemoteIfdConfigManager remoteIfdConfig, Socket client) {
		this.psk = psk.getBytes();
		this.clientSocket = client;
		this.remoteIfdConfig = remoteIfdConfig;
	}

	@Override
	public boolean performHandshake() {
		TlsCrypto crypto = new BcTlsCrypto(new SecureRandom());

		TlsPSKIdentityManager identityManager = new SimpleTlsPSKIdentityManager(psk);

		try {

			protocol = new TlsServerProtocol(clientSocket.getInputStream(), clientSocket.getOutputStream());

			protocol.accept(new PSKTlsServer(crypto, identityManager) {
				@Override
			    public TlsKeyExchangeFactory getKeyExchangeFactory() throws IOException {
			        return new GTTlsKeyExchangeFactory();
			    }
				
				@Override
				public int[] getCipherSuites() {
					return new int[] { CipherSuite.TLS_RSA_PSK_WITH_AES_256_CBC_SHA };
				}

				@Override
				protected TlsCredentialedDecryptor getRSAEncryptionCredentials() throws IOException {
					return new BcDefaultTlsCredentialedDecryptor((BcTlsCrypto) getCrypto(),
							CertificateConverter.fromJavaCertificateToBcTlsCertificate(remoteIfdConfig.getHostCertificate()),
							CertificateConverter.fromJavaKeyToBcAsymetricKeyParameter(remoteIfdConfig.getHostPrivateKey()));
				}

				@Override
				public void notifyClientCertificate(Certificate cert) throws IOException {
					clientCert = cert;
				}

				@Override
				public void notifyHandshakeComplete() throws IOException {
					super.notifyHandshakeComplete();
					remoteIfdConfig.addPairedCertificate(CertificateConverter.fromBcTlsCertificateToJavaCertificate(clientCert));
					BasicLogger.log(getClass(), "Handshake done", LogLevel.DEBUG);
				}

				@Override
				public CertificateRequest getCertificateRequest() {
					Vector<Object> signatureAndHash = new Vector<>();
					signatureAndHash.add(new SignatureAndHashAlgorithm(HashAlgorithm.sha256, SignatureAlgorithm.rsa));
					return new CertificateRequest(new short[] { ClientCertificateType.rsa_sign }, signatureAndHash, null);
				}

				@Override
				public void notifyOfferedCipherSuites(int[] arg0) throws IOException {
					super.notifyOfferedCipherSuites(arg0);
					StringBuilder cipherSuites = new StringBuilder();
					cipherSuites.append("Offered cipher suites:");
					for (int i : arg0) {
						cipherSuites.append(System.lineSeparator()).append(Integer.toHexString(i));
					}
					BasicLogger.log(getClass(), cipherSuites.toString(), LogLevel.DEBUG);
				}
			});
			
			return true;
		} catch (IOException e) {
			BasicLogger.logException(getClass(), e);
		}
		return false;
	}

	@Override
	public void closeConnection() {
		try {
			BasicLogger.log(getClass(), "Closing PSK TLS connection", LogLevel.DEBUG);
			protocol.close();
		} catch (IOException e) {
			// NOSONAR: Other side closed the socket prematurely
		}
	}

	@Override
	public InputStream getInputStream() {
		return protocol.getInputStream();
	}

	@Override
	public OutputStream getOutputStream() {
		return protocol.getOutputStream();
	}

	@Override
	public Certificate getClientCertificate() {
		return clientCert;
	}

}
