package de.persosim.websocket;

import org.json.JSONObject;

public class DefaultAnnouncementMessageBuilder implements AnnouncementMessageBuilder {

	private String ifdid;
	private int port;

	public DefaultAnnouncementMessageBuilder(String ifdid, int port) {
		super();
		this.ifdid = ifdid;
		this.port = port;
	}

	@Override
	public byte[] build() {
		JSONObject announceMessage = new JSONObject();
		announceMessage.put("msg", "REMOTE_IFD");
		announceMessage.put("IFDName", "PersoSim");
		announceMessage.put("IFDID", ifdid);
		announceMessage.put("SupportedAPI", new String [] { "IFDInterface_WebSocket_v1"});
		announceMessage.put("port", port);
		return announceMessage.toString().getBytes();
	}
}
