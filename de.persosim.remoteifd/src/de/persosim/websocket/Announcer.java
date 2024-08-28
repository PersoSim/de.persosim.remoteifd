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

/**
 * Announces the availability of a SaK server on all available interfaces via
 * UDP.
 * 
 * @author boonk.martin
 *
 */
final class Announcer implements Runnable {

	public static final int ANNOUNCE_PORT = 24727; // AusweisApp announce port
	public static final long SLEEPING_TIME = TimeUnit.SECONDS.toMillis(1);
	public static final int REINIT_PACKETS_TO_SEND_THRESHOLD = 10;

	public Announcer(AnnouncementMessageBuilder builder) {
		super();
		this.builder = builder;
	}

	private AnnouncementMessageBuilder builder;

	@Override
	public void run() {
		BasicLogger.log(getClass(), "Announcer has been started.", LogLevel.DEBUG);
		try (DatagramSocket socket = new DatagramSocket()) {
			socket.setBroadcast(true);
			byte[] content = builder.build();

			Set<DatagramPacket> packetsToSend = getPacketsToSend(content);

			Set<DatagramPacket> packetsToSendWork = new HashSet<>();
			packetsToSendWork.addAll(packetsToSend);

			int counter = 0;
			boolean sentFailed = true;
			while (!Thread.interrupted()) {
				Set<DatagramPacket> packetsToSendToRemove = new HashSet<>();
				for (DatagramPacket packet : packetsToSendWork) {
					try {
						BasicLogger.log(getClass(),
								"Sending local UDP broadcast to '" + packet.getSocketAddress().toString() + "'...",
								LogLevel.TRACE);
						socket.send(packet);
					} catch (SocketException se) {
						packetsToSendToRemove.add(packet);
						BasicLogger.log(getClass(), "Sending local UDP broadcast to '"
								+ packet.getSocketAddress().toString() + "' failed: " + se.getMessage(), LogLevel.INFO);
						sentFailed = true;
					}
				}
				packetsToSendWork.removeAll(packetsToSendToRemove);
				if (packetsToSendWork.isEmpty()) {
					// macOS: MTU 9000 Jumbo Frames in appropriate network interface(s) enabled?
					BasicLogger.log(getClass(), "Cannot find any communication partner for pairing via UDP broadcast!",
							LogLevel.WARN);
				}
				try {
					Thread.sleep(SLEEPING_TIME);
				} catch (InterruptedException e) {
					// NOSONAR: This will happen every time the announce is running in a Thread and
					// then stopped
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

			BasicLogger.log(getClass(), "Announcer stopped", LogLevel.DEBUG);
		} catch (IOException e) {
			throw new IllegalStateException("UDP announce failed!", e);
		}
	}

	private Set<DatagramPacket> getPacketsToSend(byte[] content) throws UnknownHostException, SocketException {
		BasicLogger.log(getClass(), "----- Refreshing Network Interfaces information -----", LogLevel.TRACE);
		Set<DatagramPacket> packetsToSend = new HashSet<>();
		packetsToSend.add(
				new DatagramPacket(content, content.length, InetAddress.getByName("255.255.255.255"), ANNOUNCE_PORT));

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

	private void logNetworkInterfaceInformation(NetworkInterface networkInterface) throws SocketException {
		BasicLogger.log(getClass(), "--- Network Interface to send announces to: ---", LogLevel.TRACE);
		BasicLogger.log(getClass(), "Display name: '" + networkInterface.getDisplayName() + "'", LogLevel.TRACE);
		BasicLogger.log(getClass(), "Name: '" + networkInterface.getName() + "'", LogLevel.TRACE);
		Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();
		for (InetAddress inetAddress : Collections.list(inetAddresses)) {
			BasicLogger.log(getClass(), "InetAddress: '" + inetAddress + "'", LogLevel.TRACE);
		}
		// BasicLogger.log(getClass(), "Is Up? " + networkInterface.isUp(),
		// LogLevel.TRACE);
		// BasicLogger.log(getClass(), "Is Loopback? " + networkInterface.isLoopback(),
		// LogLevel.TRACE);
		// BasicLogger.log(getClass(), "Is PointToPoint? " +
		// networkInterface.isPointToPoint(), LogLevel.TRACE);
		// BasicLogger.log(getClass(), "Supports multicast? " +
		// networkInterface.supportsMulticast(), LogLevel.TRACE);
		BasicLogger.log(getClass(), "Is Virtual? " + networkInterface.isVirtual(), LogLevel.TRACE);
		byte[] mac = networkInterface.getHardwareAddress();
		String macAsString = "NONE";
		if (mac != null) {
			StringBuilder sb = new StringBuilder();
			for (int i = 0; i < mac.length; i++) {
				sb.append(String.format("%02X%s", mac[i], (i < mac.length - 1) ? "-" : ""));
			}
			macAsString = sb.toString();
		}
		BasicLogger.log(getClass(), "Hardware address: " + macAsString, LogLevel.TRACE);
		BasicLogger.log(getClass(), "MTU: " + Integer.toString(networkInterface.getMTU()), LogLevel.TRACE);
	}

}