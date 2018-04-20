package de.persosim.websocket;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;

import org.globaltester.cryptoprovider.Crypto;
import org.globaltester.cryptoprovider.bc.ProviderBc;
import org.globaltester.logging.BasicLogger;

import de.persosim.driver.connector.SimulatorManager;
import de.persosim.driver.connector.UnsignedInteger;
import de.persosim.driver.connector.features.DefaultListener;
import de.persosim.driver.connector.features.MctReaderDirect;
import de.persosim.driver.connector.features.MctUniversal;
import de.persosim.driver.connector.features.ModifyPinDirect;
import de.persosim.driver.connector.features.PersoSimPcscProcessor;
import de.persosim.driver.connector.features.VerifyPinDirect;
import de.persosim.driver.connector.service.IfdConnector;
import de.persosim.driver.connector.service.IfdConnectorImpl;
import de.persosim.driver.connector.ui.ConsoleUi;
import de.persosim.simulator.PersoSim;
import de.persosim.simulator.perso.DefaultPersoGt;
import de.persosim.simulator.utils.HexString;

/**
 * Demo application instantiating a SaK reachable PersoSim in pairing mode.
 * @author boonk.martin
 *
 */
public class Application {
	
	private static String STORETYPE = "JKS";
	private static String KEYSTORE = "FEEDFEED000000020000000100000001000764656661756C7400000160669DF13F00000500308204FC300E060A2B060104012A021101010500048204E89745BA49D74D2E2D91C775624B61955808ADADC41ADDFDB44213F181E976E3ECF3AE0DB7A94723E25B63A1D816BF13EE84871CEA2C15B47B28BFC9613781C55D3D645DEAE4BFE0DA3D22747A6E60DD26AB58CFD9297457D7DAC39495193E52F80985AF9876408394FF9131BDD4C0635858775079B7A4A653BBA94A4504F4C4F335F2279104BBC94117A46D8672AD1790FABB26712B2DD60B332AA839D9AFD4C53A64078C7874804454BCA0FCF0AFFC2A3EE181086EE720A8C8380ABCE3FA67C4BE6180773E6B9A30DF6E574845DF3648C2D294ECDB045B5739740933290DCE959C2EEBF862FB4217B89F82CAC8072EA88790B4D8362E21D5BBBBE2BD84B293527E0C9312055FF211246B7C07DCCE5D1EB6F4DEBF71FA4F26D8CE1EBD3728A1B0A46079AFCA22AD8D49884B13C9A89A5A7A27B989F0AFDA7FF11F2E19F148DE4BD97D9DCAB139DEC58350584B6D609B94E77DA925A0AFF9DDD7120E431336A52788238EE38DEF7B8CA47A0F4380EEC1621CFB7E9BB7E6545F1543272B136386E641E41270429F7CF7B5EEABCFD3D4BB89EED01D915BAEAC42F765CB62F481A821B9AA7CE73C19D0377A2F5F5ACB53DF510718A51CB92AB4B59C7BD3C0F2779D42A9B2B7C94F20B3F38304D719AA279240EF934DD3DC90096A06536DDFB9F5573BE974CE49448993C82DA87060F38E73D5B6EB2E4EED13B031B2D6577F4BEB495877D2AA2C984ED675E9C98495E5D701F0CA3D3B66CB7E7BBD66E3C1F7E5F2EC1EF655630F39ACE8D23E66DF04C2B7717BCAE78467CCBECEBBDBDFD1C38DD30BE54A0CF05FE58DA804D80E942E7A43107A3804D01650350E69FE2A1C3A68BD7B4D65CFBC896E35F6D515266B8939B0A41530A9A0DA4DEE0E2B80DD19ECA879981905B15E763E75FD443F30A99D59DE8431C72EFC8E927671A5B43BF56EFE60585E2F8CAED08D070970290C768A401581D0138DB4EA7ACEF0332AABF0E237A12361AFC307E554F8B3BC71612370EF015119185EF5A283B27F56099AD6BCBD0CC89E9CB636838DA8887426803432BA187AFB55C81C978788E2CCECAFC6BE4CAC5A11AC05FE7C1821531841C7613AF18A65EF3492C9CE499B0062360D4FA4CDCF1D3A0F2485BC1FC541DAB3766881C8A1BEFEA797235D704BD6DA15FE52499E3351FD8AD0208F3BE9C9BC94C8FB3D026EE97EDFF6742A0FE204D31AD0748A1B479B6275F54304201387E01CEDA687F5B41556DE97D0F51D067F8A050475A532FA1E219075ACFB2D1E60FEC7D43BEEBC0D72C868D5A3C1975960ED1F45F7C255F73CF010058FE7E3A06B1231C81405C109E3BD9CEC412BB8BA2260F96CE181D0BFBB37494BA50E4BBB5400312761B0CA08733F39ABDE653E3535AF1D3F38797009DBB58D22E01DB0560B50E24BB8954ABE9AE94704D0F2765507E6275A5E5F46F9673D6F693337A20F0DE356A6BC0DB42A2537E87266FA961831E3268924821114000921FE3F91D649CBE0DF51AB2278D79EBD6AB18E8D839027D02708A59EC03260FC56C458A8147E9CB2FD65C6745390C68EE92F65426183E4DE969DBAAC2B0FBA4CC05E27BC689B74F493CE8CFF0A4B15ECB2AF3A547CBFDFC8C2C9566AFD17719FD2014D510CB58AB102266089DA826C53F27671FB7FC92478700EDF0F4346378C9EBC7CA3CE37D398DFFED03F230D8298D04CC99C60B72EE8DD3565F36DFED5D9746DBA914E0E79C5F58F0D7F074C9A138813B1F2A7831806DA337817C60C5F934C09C1795CC16EB3000000010005582E353039000003833082037F30820267A00302010202045DA24930300D06092A864886F70D01010B0500307031123010060355040613094D79436F756E7472793111300F060355040813084D79526567696F6E310F300D060355040713064D7943697479310E300C060355040A13054D794F726731123010060355040B13094D794F7267556E697431123010060355040313093132372E302E302E31301E170D3137313231373232333531355A170D3237313231353232333531355A307031123010060355040613094D79436F756E7472793111300F060355040813084D79526567696F6E310F300D060355040713064D7943697479310E300C060355040A13054D794F726731123010060355040B13094D794F7267556E697431123010060355040313093132372E302E302E3130820122300D06092A864886F70D01010105000382010F003082010A0282010100844FC5F2847EA40B711759BE6228895261EF767EDB483A2992B3D30035AEC31B95B937A0B5BEBA903E4699582D0F652C7042222A921EA30FEC076CFB29EEFBE1ADD4C8F4050E3D068D9952C86F6073F4D7F7E62D0E81184474C4C8E06944AB41BBAC3BB153BF7E1AFA6150ED9E7E5D83AD0401ABB417009D968ED78AA0216B1AB5C8217691FF61D270F393281DCF6288924095BA1D928CCA9B4C67B883A5567D63EB6C51DC52FB3D03D0DCD013BF270C4FEDA2EC3D4069DF96407A26016F1363FB324F0B4F3D613158A7E12AF3750E0460BCE7300FA9C4C0C4F762891F39647259E6980ED34BE5F21674A8546C6C5ABA3A9CFF9ADC04640009C4D880A21994D10203010001A321301F301D0603551D0E04160414FE56359208EFF1822E7904C7FC64518CF74B033A300D06092A864886F70D01010B0500038201010041B31DCECC42A96E7FC391DFAEB00460D063A8F0C2483542CAF16F981EC4C72E2716FA71978385EDBEBFEB31C27AEB4BA2AD320B8CB7F2B301BD6CFB2FA89E9AD543C4576410EECE0C4BB4E225E001006665D40489EF3E20C7D6BD5ED035648E122043E783D8A567FC24154B6F1E08D600CF919B8E91C386341417A98A0335136A83657905FDA4BC37161C5B7E97D85242B36B8DC55A5C2665DE9D3D0D0F74DCAD3D67A217D11952715D2359AB7D9453DA31D42B36C21337773AD629A939085415E6FEF1BEBBFB414131264EB794A9C9D1085337D9A8F0330D448EB916A83086CAC1D95E0A0DA903F34CA522D341022B67C6C35B5850CB8FA6AE53DF37F216375645E78EE10321FAE78C6D5DBB7EE85C48461BB7";
	private static String STOREPASSWORD = "storepassword";
	private static String KEYPASSWORD = "keypassword";
	
	public static void main(String[] args) throws IOException {
		Crypto.setCryptoProvider(ProviderBc.getInstance().getCryptoProviderObject());
		
		PersoSim sim = new PersoSim();
		
		sim.startSimulator();
		sim.loadPersonalization(new DefaultPersoGt());
		
		SimulatorManager.setSimulator(sim);
		
		IfdConnector connector = new IfdConnectorImpl();

		connector.addListener(new DefaultListener());
		connector.addListener(new VerifyPinDirect(new UnsignedInteger(0x42000DB2)));
		connector.addListener(new ModifyPinDirect(new UnsignedInteger(0x42000DB3)));
		connector.addListener(new MctReaderDirect(new UnsignedInteger(0x42000DB4)));
		connector.addListener(new MctUniversal(new UnsignedInteger(0x42000DB5)));
		connector.addListener(new PersoSimPcscProcessor(new UnsignedInteger(0x42000DCC)));
		
		connector.addUi(new ConsoleUi());
		
		KeyStore ks_temp = null;
		try {
			ks_temp = KeyStore.getInstance(STORETYPE);
			ks_temp.load(new ByteArrayInputStream(HexString.toByteArray(KEYSTORE)), STOREPASSWORD.toCharArray());
		} catch (KeyStoreException | NoSuchAlgorithmException | CertificateException | IOException e) {
			BasicLogger.logException(Application.class, e);
		}
		
		WebsocketComm comm = new WebsocketComm(null, null, new KeystoreRemoteIfdConfigManager(new ByteArrayInputStream(HexString.toByteArray(KEYSTORE)), STOREPASSWORD.toCharArray(), KEYPASSWORD.toCharArray()));
		
		connector.connect(comm);
	}
}
