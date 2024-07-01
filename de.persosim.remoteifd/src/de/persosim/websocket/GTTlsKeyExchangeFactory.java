package de.persosim.websocket;

import java.io.IOException;

import org.bouncycastle.tls.DefaultTlsKeyExchangeFactory;
import org.bouncycastle.tls.TlsKeyExchange;
import org.bouncycastle.tls.TlsPSKIdentityManager;
import org.bouncycastle.tls.crypto.TlsDHConfig;
import org.bouncycastle.tls.crypto.TlsECConfig;

public class GTTlsKeyExchangeFactory extends DefaultTlsKeyExchangeFactory {

	@Override
	public TlsKeyExchange createPSKKeyExchangeServer(int keyExchange, TlsPSKIdentityManager pskIdentityManager,
			TlsDHConfig dhConfig, TlsECConfig ecConfig) throws IOException {
		return new GTTlsPSKKeyExchange(keyExchange, pskIdentityManager, dhConfig, ecConfig);
	}
}
