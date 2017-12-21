package de.persosim.websocket;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateEncodingException;
import java.security.cert.CertificateException;
import java.security.interfaces.RSAPrivateKey;

import org.bouncycastle.crypto.params.AsymmetricKeyParameter;
import org.bouncycastle.crypto.params.RSAKeyParameters;
import org.bouncycastle.tls.Certificate;
import org.bouncycastle.tls.DefaultTlsServer;
import org.bouncycastle.tls.TlsCredentialedDecryptor;
import org.bouncycastle.tls.TlsServerProtocol;
import org.bouncycastle.tls.crypto.TlsCertificate;
import org.bouncycastle.tls.crypto.TlsCrypto;
import org.bouncycastle.tls.crypto.impl.bc.BcDefaultTlsCredentialedDecryptor;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCertificate;
import org.bouncycastle.tls.crypto.impl.bc.BcTlsCrypto;
import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;

public class DefaultHandshaker implements TlsHandshaker {

	private static String STORETYPE = "JKS";
	private static String KEYSTORE = "keystore.jks";
	private static String STOREPASSWORD = "storepassword";
	private static String KEYPASSWORD = "keypassword";

	private Socket clientSocket;
	private TlsServerProtocol protocol;

	public DefaultHandshaker(Socket client) {
		this.clientSocket = client;
	}

	@Override
	public boolean performHandshake() {
		KeyStore ks_temp = null;
		try {
			ks_temp = KeyStore.getInstance(STORETYPE);
			File kf = new File(KEYSTORE);
			ks_temp.load(new FileInputStream(kf), STOREPASSWORD.toCharArray());
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
			BasicLogger.logException(getClass(), e);
		}

		KeyStore ks = ks_temp;

		TlsCrypto crypto = new BcTlsCrypto(new SecureRandom());

		try {

			protocol = new TlsServerProtocol(clientSocket.getInputStream(),
					clientSocket.getOutputStream());

			protocol.accept(new DefaultTlsServer(crypto) {

				protected TlsCredentialedDecryptor getRSAEncryptionCredentials() throws IOException {
					byte[] certificateFromStore;
					try {
						certificateFromStore = ks.getCertificate("default").getEncoded();
						TlsCertificate[] certs = new TlsCertificate[1];
						certs[0] = new BcTlsCertificate((BcTlsCrypto) getCrypto(), certificateFromStore);
						Certificate cert = new Certificate(certs);

						RSAPrivateKey keyFromStore = (RSAPrivateKey) ks.getKey("default", KEYPASSWORD.toCharArray());

						AsymmetricKeyParameter key = new RSAKeyParameters(true, keyFromStore.getModulus(),
								keyFromStore.getPrivateExponent());

						return new BcDefaultTlsCredentialedDecryptor((BcTlsCrypto) getCrypto(), cert, key);
					} catch (CertificateEncodingException | KeyStoreException | UnrecoverableKeyException
							| NoSuchAlgorithmException e) {
						BasicLogger.logException(getClass(), e, LogLevel.ERROR);
					}
					return null;
				};

				public void notifyClientCertificate(Certificate arg0) throws IOException {
					validateCertificate(arg0);
				};

				private void validateCertificate(Certificate cert) {
//							String alias;
//							try {
//								byte[] encoded = cert.getCertificateList()[0].getEncoded();
//								java.security.cert.Certificate jsCert = CertificateFactory.getInstance("X.509")
//										.generateCertificate(new ByteArrayInputStream(encoded));
//								alias = ks.getCertificateAlias(jsCert);
//								if (alias == null) {
//									throw new IllegalArgumentException("Unknown cert " + jsCert);
//								}
//							} catch (KeyStoreException | CertificateException | IOException e) {
//								BasicLogger.logException(getClass(), e);
//							}
						}

				@Override
				public void notifyHandshakeComplete() throws IOException {
					super.notifyHandshakeComplete();
					BasicLogger.log(getClass(), "Handshake done", LogLevel.DEBUG);
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
			BasicLogger.log(getClass(), "Closing TLS connection", LogLevel.DEBUG);
			protocol.close();
		} catch (IOException e) {
			throw new IllegalStateException("Closing of tls connection failed", e);
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
