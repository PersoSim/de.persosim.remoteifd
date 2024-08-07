package de.persosim.websocket;

import java.nio.ByteBuffer;

import de.persosim.simulator.utils.HexString;
import de.persosim.simulator.utils.Utils;

public class Frame {

	enum Opcode {
		CONTINUATION(0),
		TEXT(1),
		BINARY(2),
		CLOSE(8),
		PING(9),
		PONG(10);
		
		private int value;

		Opcode(int value){
			this.value = value;
		}
		
		public int getValue() {
			return value;
		}
		
		public boolean isControl() {
			return 0x8 <= getValue() && getValue() <= 0xF;
		}
		
		static Opcode forValue(int value) {
			for (Opcode opcode : Opcode.values()) {
				if (opcode.value == value) {
					return opcode;
				}
			}
			throw new IllegalArgumentException("No opcode for this value");
		}
	}
	
	
	private boolean fin;
	private byte[] payload = new byte [0];
	private Opcode opcode;

	public void setFin(boolean fin) {
		this.fin = fin;
	}

	public void setRSV1(boolean rsv1) {
		// TODO Auto-generated method stub
		
	}

	public void setRSV2(boolean rsv2) {
		// TODO Auto-generated method stub
		
	}

	public void setRSV3(boolean rsv3) {
		// TODO Auto-generated method stub
		
	}

	public void setPayload(byte [] payload) {
		this.payload = payload.clone();
	}

	public boolean getFin() {
		return fin;
	}
	
	public void setOpcode(Opcode opcode) {
		this.opcode = opcode;
	}
	
	
	public void appendFrame(Frame frame) {
		//TODO check frame types
		if (frame.getOpcode() != Opcode.CONTINUATION) {
			throw new IllegalArgumentException("Can not append frame that does not have continuation opcode");
		}
		
		payload = Utils.concatByteArrays(payload, frame.getPayload());
		fin = frame.getFin();
	}

	public Opcode getOpcode() {
		return opcode;
	}

	public byte[] getPayload() {
		return payload.clone();
	}

	@Override
	public String toString() {
		return new StringBuilder().append("Opcode: " + getOpcode() + "(" + getOpcode().getValue() + ")," + System.lineSeparator() + "Payload:" + System.lineSeparator() + HexString.dump(payload)).toString();
	}

	public byte[] getHeaderBytes() {
		short header = 0;

		header |= getFin() ? 0x8000 : 0;
		// IMPL set RSV
		header |= (getOpcode().getValue() << 8);

		if (payload.length <= 125) {
			header |= payload.length;
		} else if (payload.length <= 65535) {
			header |= 126;
		} else {
			header |= 127;
		}
		ByteBuffer messageHeader = ByteBuffer.allocate(16);
		messageHeader.putShort(header);

		if (payload.length <= 65535 && payload.length > 125) {
			messageHeader.putShort((short) (0xFFFF & payload.length));
		} else if (payload.length > 65535) {
			messageHeader.putLong(payload.length);
		} else {
			//No length field needed, already encoded in header
		}

		messageHeader.flip();
		byte [] toWrite = new byte [messageHeader.remaining()];
		messageHeader.get(toWrite);
		return toWrite;
	}
	
}
