package de.persosim.websocket;

import org.json.JSONObject;

public class DefaultAnnouncementMessageBuilder implements AnnouncementMessageBuilder {

	private String ifdid;
	private int port;
	private String name;

	public DefaultAnnouncementMessageBuilder(String ifname, String ifdid, int port) {
		super();
		this.ifdid = ifdid;
		this.port = port;
		this.name = ifname;
	}

	@Override
	public byte[] build() {
		JSONObject announceMessage = new JSONObject();
		announceMessage.put("msg", "REMOTE_IFD");
		announceMessage.put("IFDName", name);
		announceMessage.put("IFDID", ifdid);
		announceMessage.put("SupportedAPI", new String [] { "IFDInterface_WebSocket_v0" });
		announceMessage.put("port", port);
		return announceMessage.toString().getBytes();
	}
}
