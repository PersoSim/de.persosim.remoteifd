package de.persosim.websocket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;
import org.globaltester.logging.tags.LogTag;

import de.persosim.simulator.log.PersoSimLogTags;

/**
 * Announces the availability of a SaK server on all available interfaces via UDP.
 *
 * @author boonk.martin
 *
 */
final class Announcer implements Runnable
{

	public static final int ANNOUNCE_PORT = 24727; // AusweisApp announce port
	public static final long SLEEPING_TIME = TimeUnit.SECONDS.toMillis(1);
	public static final int REINIT_PACKETS_TO_SEND_THRESHOLD = 10;

	public Announcer(AnnouncementMessageBuilder builder)
	{
		super();
		this.builder = builder;
	}

	private AnnouncementMessageBuilder builder;

	@Override
	public void run()
	{
		BasicLogger.log("Announcer has been started.", LogLevel.DEBUG, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
		try (DatagramSocket socket = new DatagramSocket()) {
			doAnnounce(socket);
		}
		catch (IOException e) {
			throw new IllegalStateException("UDP announce failed!", e);
		}
	}

	private void doAnnounce(DatagramSocket socket) throws IOException
	{
		socket.setBroadcast(true);
		byte[] content = builder.build();

		Set<DatagramPacket> packetsToSend = getPacketsToSend(content);

		Set<DatagramPacket> packetsToSendWork = new HashSet<>();
		packetsToSendWork.addAll(packetsToSend);

		int counter = 0;
		boolean sentFailed = false;
		while (!Thread.currentThread().isInterrupted()) {
			Set<DatagramPacket> packetsToSendToRemove = new HashSet<>();
			for (DatagramPacket packet : packetsToSendWork) {
				sentFailed = sendPacket(socket, sentFailed, packetsToSendToRemove, packet);
			}
			packetsToSendWork.removeAll(packetsToSendToRemove);
			if (packetsToSendWork.isEmpty()) {
				// macOS: MTU 9000 Jumbo Frames in appropriate network interface(s) enabled?
				BasicLogger.log("Cannot find any communication partner for pairing via UDP broadcast!", LogLevel.WARN, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
			}
			try {
				Thread.sleep(SLEEPING_TIME);
			}
			catch (InterruptedException e) {
				// This will happen every time the announce is running in a Thread and then stopped
				BasicLogger.log("Announcer interrupted", LogLevel.DEBUG, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
				Thread.currentThread().interrupt();
				break;
			}
			counter++;
			if (sentFailed && counter == REINIT_PACKETS_TO_SEND_THRESHOLD) {
				packetsToSend = getPacketsToSend(content);
				packetsToSendWork = new HashSet<>();
				packetsToSendWork.addAll(packetsToSend);
				sentFailed = false;
				counter = 0;
			}
		}

		BasicLogger.log("Announcer stopped", LogLevel.DEBUG, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
	}

	private boolean sendPacket(DatagramSocket socket, boolean sentFailed, Set<DatagramPacket> packetsToSendToRemove, DatagramPacket packet) throws IOException
	{
		try {
			BasicLogger.log("Sending local UDP broadcast to '" + packet.getSocketAddress().toString() + "'...", LogLevel.TRACE,
					new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
			socket.send(packet);
		}
		catch (SocketException se) {
			packetsToSendToRemove.add(packet);
			BasicLogger.log("Sending local UDP broadcast to '" + packet.getSocketAddress().toString() + "' failed: " + se.getMessage(), LogLevel.INFO,
					new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
			sentFailed = true;
		}
		return sentFailed;
	}

	private Set<DatagramPacket> getPacketsToSend(byte[] content) throws UnknownHostException, SocketException
	{
		BasicLogger.log("----- Refreshing Network Interfaces information -----", LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
		Set<DatagramPacket> packetsToSend = new HashSet<>();
		packetsToSend.add(new DatagramPacket(content, content.length, InetAddress.getByName("255.255.255.255"), ANNOUNCE_PORT));

		Enumeration<NetworkInterface> networkInterfaces = NetworkInterface.getNetworkInterfaces();

		while (networkInterfaces.hasMoreElements()) {
			NetworkInterface networkInterface = networkInterfaces.nextElement();
			if (networkInterface.isLoopback() || !networkInterface.isUp()) {
				continue;
			}
			logNetworkInterfaceInformation(networkInterface);
			for (InterfaceAddress address : networkInterface.getInterfaceAddresses()) {
				InetAddress broadcast = address.getBroadcast();
				if (broadcast == null) {
					continue;
				}
				packetsToSend.add(new DatagramPacket(content, content.length, broadcast, ANNOUNCE_PORT));
			}
		}

		return packetsToSend;
	}

	private void logNetworkInterfaceInformation(NetworkInterface networkInterface) throws SocketException
	{
		BasicLogger.log("--- Network Interface to send announces to: ---", LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
		BasicLogger.log("Display name: '" + networkInterface.getDisplayName() + "'", LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
		BasicLogger.log("Name: '" + networkInterface.getName() + "'", LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
		Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
		for (InetAddress inetAddress : Collections.list(inetAddresses)) {
			BasicLogger.log("InetAddress: '" + inetAddress + "'", LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
		}
		// BasicLogger.log("Is Up? " + networkInterface.isUp(),
		// LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_ID));
		// BasicLogger.log("Is Loopback? " + networkInterface.isLoopback(),
		// LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_ID));
		// BasicLogger.log("Is PointToPoint? " +
		// networkInterface.isPointToPoint(), LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_ID));
		// BasicLogger.log("Supports multicast? " +
		// networkInterface.supportsMulticast(), LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_ID));
		BasicLogger.log("Is Virtual? " + networkInterface.isVirtual(), LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
		byte[] mac = networkInterface.getHardwareAddress();
		String macAsString = "NONE";
		if (mac != null) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < mac.length; i++) {
				sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
			}
			macAsString = sb.toString();
		}
		BasicLogger.log("Hardware address: " + macAsString, LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
		BasicLogger.log("MTU: " + Integer.toString(networkInterface.getMTU()), LogLevel.TRACE, new LogTag(BasicLogger.LOG_TAG_TAG_ID, PersoSimLogTags.REMOTE_IFD_TAG_ID));
	}

}