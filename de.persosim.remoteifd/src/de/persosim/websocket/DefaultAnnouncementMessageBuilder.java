package de.persosim.websocket;

import java.util.Arrays;

import org.json.JSONArray;
import org.json.JSONObject;

public class DefaultAnnouncementMessageBuilder implements AnnouncementMessageBuilder {

	private String ifdid;
	private int port;
	private String name;
	private boolean pairing;

	public DefaultAnnouncementMessageBuilder(String ifname, String ifdid, int port, boolean pairing) {
		super();
		this.ifdid = ifdid;
		this.port = port;
		this.name = ifname;
		this.pairing = pairing;
	}

	@Override
	public byte[] build() {
		JSONObject announceMessage = new JSONObject();
		announceMessage.put("msg", "REMOTE_IFD");
		announceMessage.put("IFDName", name);
		announceMessage.put("IFDID", ifdid);
		announceMessage.put("SupportedAPI", new JSONArray(Arrays.asList(
				"IFDInterface_WebSocket_v0",
				"IFDInterface_WebSocket_v2"
				)));
		announceMessage.put("port", port);
		announceMessage.put("pairing", pairing);
		return announceMessage.toString().getBytes();
	}
}
