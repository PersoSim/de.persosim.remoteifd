package de.persosim.websocket;

import org.bouncycastle.tls.ClientCertificateType;
import org.bouncycastle.tls.TlsDHGroupVerifier;
import org.bouncycastle.tls.TlsPSKIdentity;
import org.bouncycastle.tls.TlsPSKIdentityManager;
import org.bouncycastle.tls.TlsPSKKeyExchange;
import org.bouncycastle.tls.crypto.TlsDHConfig;
import org.bouncycastle.tls.crypto.TlsECConfig;

public class GTTlsPSKKeyExchange extends TlsPSKKeyExchange {

	public GTTlsPSKKeyExchange(int keyExchange, TlsPSKIdentity pskIdentity, TlsDHGroupVerifier dhGroupVerifier) {
		super(keyExchange, pskIdentity, dhGroupVerifier);
	}

	public GTTlsPSKKeyExchange(int keyExchange, TlsPSKIdentityManager pskIdentityManager, TlsDHConfig dhConfig,
			TlsECConfig ecConfig) {
		super(keyExchange, pskIdentityManager, dhConfig, ecConfig);
	}

	@Override
	public short[] getClientCertificateTypes() {
		return new short[] { ClientCertificateType.rsa_sign };
	}
}
