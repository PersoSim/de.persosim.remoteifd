package de.persosim.websocket;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InterfaceAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;
import java.util.HashSet;

import org.globaltester.logging.BasicLogger;
import org.globaltester.logging.tags.LogLevel;

final class Announcer implements Runnable {
	
	public static int ANNOUNCE_PORT = 24727;
	
	public Announcer(AnnouncementMessageBuilder builder) {
		super();
		this.builder = builder;
	}

	private AnnouncementMessageBuilder builder;

	@Override
	public void run() {
		BasicLogger.log(getClass(), "Announcer has been started", LogLevel.INFO);
		try (DatagramSocket socket = new DatagramSocket()) {
			socket.setBroadcast(true);
			byte [] content = builder.build();
			
			HashSet<DatagramPacket> packetsToSend = new HashSet<>();
			packetsToSend.add(new DatagramPacket(content, content.length, InetAddress.getByName("255.255.255.255"), ANNOUNCE_PORT));
			
			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
			
			while (interfaces.hasMoreElements()){
				NetworkInterface iface = interfaces.nextElement();
				if (iface.isLoopback() || !iface.isUp()){
					continue;
				}
				
				for (InterfaceAddress address : iface.getInterfaceAddresses()){
					InetAddress broadcast = address.getBroadcast();
					if (broadcast == null){
						continue;
					}
					packetsToSend.add(new DatagramPacket(content, content.length, broadcast, ANNOUNCE_PORT));
				}
			}
			

			BasicLogger.log(getClass(), "Sending " + packetsToSend.size() + " announcement packets", LogLevel.DEBUG);
			while (!Thread.interrupted()){
				for (DatagramPacket packet : packetsToSend){
					socket.send(packet);
				}
				Thread.sleep(5000);
			}
		} catch (IOException e) {
			throw new IllegalStateException("UDP announce failed", e);
		} catch (InterruptedException e) {
			// Ignore
			BasicLogger.logException(this.getClass(), e, LogLevel.DEBUG);
			Thread.currentThread().interrupt();
		}
	}
}