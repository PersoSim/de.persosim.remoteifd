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
import org.bouncycastle.tls.TlsContext;
import org.bouncycastle.tls.TlsCredentialedDecryptor;
import org.bouncycastle.tls.TlsCredentials;
import org.bouncycastle.tls.TlsKeyExchange;
import org.bouncycastle.tls.TlsPSKIdentityManager;
import org.bouncycastle.tls.TlsServerProtocol;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.tls.crypto.TlsSecret;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedDecryptor;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;
import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;

public class PairingServer implements TlsHandshaker {

	private byte[] psk;
	private Socket clientSocket;
	private TlsServerProtocol protocol;
	private RemoteIfdConfigManager remoteIfdConfig;

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

				Certificate usedCert = null;

				public TlsKeyExchange getKeyExchange() throws IOException {
					final TlsKeyExchange keyExchange = super.getKeyExchange();
					return new TlsKeyExchange() {
						
						@Override
						public void skipServerKeyExchange() throws IOException {
							keyExchange.skipServerKeyExchange();
						}
						
						@Override
						public void skipServerCredentials() throws IOException {
							keyExchange.skipServerCredentials();
						}
						
						@Override
						public void skipClientCredentials() throws IOException {
							keyExchange.skipClientCredentials();
						}
						
						@Override
						public boolean requiresServerKeyExchange() {
							return keyExchange.requiresServerKeyExchange();
						}
						
						@Override
						public boolean requiresCertificateVerify() {
							return keyExchange.requiresCertificateVerify();
						}
						
						@Override
						public void processServerKeyExchange(InputStream input) throws IOException {
							keyExchange.processServerKeyExchange(input);
						}
						
						@Override
						public void processServerCredentials(TlsCredentials serverCredentials) throws IOException {
							keyExchange.processServerCredentials(serverCredentials);
						}
						
						@Override
						public void processServerCertificate(Certificate serverCertificate) throws IOException {
							keyExchange.processServerCertificate(serverCertificate);
						}
						
						@Override
						public void processClientKeyExchange(InputStream input) throws IOException {
							keyExchange.processClientKeyExchange(input);
						}
						
						@Override
						public void processClientCredentials(TlsCredentials clientCredentials) throws IOException {
							keyExchange.processClientCredentials(clientCredentials);
						}
						
						@Override
						public void processClientCertificate(Certificate clientCertificate) throws IOException {
							keyExchange.processClientCertificate(clientCertificate);
						}
						
						@Override
						public void init(TlsContext context) {
							keyExchange.init(context);
						}
						
						@Override
						public short[] getClientCertificateTypes() {
							return new short [] {ClientCertificateType.rsa_sign};
						}
						
						@Override
						public byte[] generateServerKeyExchange() throws IOException {
							return keyExchange.generateServerKeyExchange();
						}
						
						@Override
						public TlsSecret generatePreMasterSecret() throws IOException {
							return keyExchange.generatePreMasterSecret();
						}
						
						@Override
						public void generateClientKeyExchange(OutputStream output) throws IOException {
							keyExchange.generateClientKeyExchange(output);
						}
					};
				};

				protected int[] getCipherSuites() {
					return new int[] { CipherSuite.TLS_RSA_PSK_WITH_AES_256_CBC_SHA };
				};

				protected TlsCredentialedDecryptor getRSAEncryptionCredentials() throws IOException {
					return new BcDefaultTlsCredentialedDecryptor((BcTlsCrypto) getCrypto(),
							CertificateConverter.fromJavaCertificateToBcTlsCertificate(remoteIfdConfig.getHostCertificate()),
							CertificateConverter.fromJavaKeyToBcAsymetricKeyParameter(remoteIfdConfig.getHostPrivateKey()));
				};

				public void notifyClientCertificate(Certificate cert) throws IOException {
					usedCert = cert;
				};

				@Override
				public void notifyHandshakeComplete() throws IOException {
					super.notifyHandshakeComplete();
					remoteIfdConfig.addPairedCertificate(CertificateConverter.fromBcTlsCertificateToJavaCertificate(usedCert));
					BasicLogger.log(getClass(), "Handshake done", LogLevel.DEBUG);
				}

				public CertificateRequest getCertificateRequest() {
					Vector<Object> signatureAndHash = new Vector<>();
					signatureAndHash.add(new SignatureAndHashAlgorithm(HashAlgorithm.sha256, SignatureAlgorithm.rsa));
					return new CertificateRequest(new short[] { ClientCertificateType.rsa_sign }, signatureAndHash, null);
				}

				@Override
				public void notifyOfferedCipherSuites(int[] arg0) throws IOException {
					super.notifyOfferedCipherSuites(arg0);
					String cipherSuites = "Offered cipher suites:";
					for (int i : arg0) {
						cipherSuites += System.lineSeparator() + Integer.toHexString(i);
					}
					BasicLogger.log(getClass(), cipherSuites, LogLevel.DEBUG);
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

}
