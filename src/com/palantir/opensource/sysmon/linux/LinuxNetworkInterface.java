//   Copyright 2011 Palantir Technologies
//
//   Licensed under the Apache License, Version 2.0 (the "License");
//   you may not use this file except in compliance with the License.
//   You may obtain a copy of the License at
// 
//       http://www.apache.org/licenses/LICENSE-2.0
// 
//   Unless required by applicable law or agreed to in writing, software
//   distributed under the License is distributed on an "AS IS" BASIS,
//   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.   
//   See the License for the specific language governing permissions and
//   limitations under the License.
package com.palantir.opensource.sysmon.linux;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

/**
 * Data container and JMX MBean implementation for per-network interface statistics used by
 * {@link LinuxNetStatJMXWrapper}.
 */
public class LinuxNetworkInterface implements LinuxNetworkInterfaceMBean {

	/**
	 * full objectName of this object in the JMX bean hierarchy.
	 */
	final String objectName;
	/**
	 * Freshness timestamp for the data held here.
	 */
	final long timestamp = System.currentTimeMillis();
	volatile long lastUpdated = System.currentTimeMillis();
	/*
	 * Fields from /proc/net/dev, in left-to-right order
	 */
	/**
	 * Interface identifier.
	 */
	 String interfaceName;

	 /*
	  * Received counters
	  */
	 /**
	 * The total number of bytes of data received by the interface.
	 */
	long bytesReceived = 0;
	/**
	 * 	The total number of packets of data received by the interface.
	 */
	long packetsReceived = 0;
	/**
	 * 	The total number of transmit or receive errors detected by the device driver.
	 */
	long recieveErrors = 0;
	/**
	 * The total number of packets dropped by the device driver. 
	 */
	long droppedReceivedPackets = 0;
	/**
	 * The number of FIFO buffer errors.
	 */
	long receiveFIFOErrors = 0;
	/**
	* The number of packet framing errors.
	*/
	long receiveFrameErrors = 0;
	/**
	 * The number of compressed packets received by the device driver.
	 */
	long compressedPacketsReceived = 0;
	/**
	 * The number of multicast frames received by the device driver. 
	 */
	long multicastFramesReceived = 0;

	/*
	 * Sent counters
	 */
	/**
	 * The total number of bytes of data transmitted or received by the interface.
	 */
	long bytesSent = 0;
	/**
	 * 	The total number of packets of data received by the interface.
	 */
	long packetsSent = 0;
	/**
	 * 	The total number of transmit or receive errors detected by the device driver.
	 */
	long sendErrors = 0;
	/**
	 * The total number of packets dropped by the device driver. 
	 */
	long droppedSentPackets = 0;
	/**
	 * The number of FIFO buffer errors.
	 */
	long sentFIFOErrors = 0;
	/**
	 * The number of collisions detected on the interface. 
	 */
	long collisions = 0;
	/**
	 * The number of carrier losses detected by the device driver. 
	 */
	long carrierDrops = 0;
	/**
	 * The number of compressed packets transmitted by the device driver.
	 */
	long compressedPacketsTransmitted = 0;

	/*
	 * Computed stats
	 */
	private long bytesPerSecondReceived = 0;
	private long bytesPerSecondSent = 0;
	private long packetsPerSecondReceived = 0;
	private long packetsPerSecondSent = 0;
	private long timespan = 0;


	LinuxNetworkInterface(String objectName) {
		this.objectName = objectName; 
	}
	
	public synchronized void takeValues(LinuxNetworkInterface dataBean) throws LinuxMonitoringException {
		if(dataBean == null) {
			throw new NullPointerException("Can't copy values from null bean!");
		}
		if(!interfaceName.equals(dataBean.interfaceName)) {
			throw new IllegalArgumentException("interface name mismatch: " + 
			                                   interfaceName + " vs. " + dataBean.interfaceName);
		}
		try {
			Field[] fields = dataBean.getClass().getDeclaredFields();
			for(Field f : fields ) {
				if((f.getModifiers() & Modifier.FINAL) != Modifier.FINAL &&
				   (f.getModifiers() & Modifier.PRIVATE) != Modifier.PRIVATE) {
					f.set(this, f.get(dataBean));
				}
			}
		} catch (Exception e) {
			throw new LinuxMonitoringException("Error while refletively copying fields",e);
		}
	}
	/**
	 * Computes the stats between these two beans, writing to this bean.
	 * @param dataBean - which will be unchanged
	 * @throws LinuxMonitoringException
	 */
	public synchronized void compute(LinuxNetworkInterface dataBean) throws LinuxMonitoringException {
		long timespan = Math.abs(dataBean.lastUpdated - this.lastUpdated);
		long bytesRec = Math.abs(dataBean.bytesReceived - this.bytesReceived);
		long bytesSent = Math.abs(dataBean.bytesSent - this.bytesSent);
		long packetsRec = Math.abs(dataBean.packetsReceived - this.packetsReceived);
		long packetsSent = Math.abs(dataBean.packetsSent - this.packetsSent);
		
		this.timespan = timespan;
		this.bytesPerSecondReceived   = (long)(1000f * ((float) bytesRec / (float) timespan));
		this.bytesPerSecondSent       = (long)(1000f * ((float) bytesSent / (float) timespan));
		this.packetsPerSecondReceived = (long)(1000f * ((float) packetsRec / (float) timespan));
		this.packetsPerSecondSent     = (long)(1000f * ((float) packetsSent / (float) timespan));
	
	}
	
	static final float ONE_MB = 1024 * 1024;
	private String formatByteRates(long bytesPerSec) {
		final float Bps = bytesPerSec;
		final float MBps = Bps / ONE_MB;
		return String.format("%f03 MBps", MBps);
	}
	@Override
	public String toString() {
		StringBuilder out = new StringBuilder(interfaceName);
		if(this.timespan > 0) {
			// build up stats
			out.append(" (rcvd=").append(formatByteRates(getBytesPerSecondReceived()));
			out.append(" sent=").append(formatByteRates(getBytesPerSecondReceived()));
			out.append(" sample=").append(this.timespan).append("ms");
			out.append(")");
		} else {
			out.append(" (not computed)");
		}
		out.append(" - ").append(this.timestamp);
		return out.toString();
	}
	
	public synchronized final long getTimestamp() {
		return timestamp;
	}

	
	public synchronized final String getInterfaceName() {
		return interfaceName;
	}

	
	public synchronized final long getBytesReceived() {
		return bytesReceived;
	}

	
	public synchronized final long getPacketsReceived() {
		return packetsReceived;
	}

	
	public synchronized final long getRecieveErrors() {
		return recieveErrors;
	}

	
	public synchronized final long getDroppedReceivedPackets() {
		return droppedReceivedPackets;
	}

	
	public synchronized final long getReceiveFIFOErrors() {
		return receiveFIFOErrors;
	}

	
	public synchronized final long getReceiveFrameErrors() {
		return receiveFrameErrors;
	}

	
	public synchronized final long getCompressedPacketsReceived() {
		return compressedPacketsReceived;
	}

	
	public synchronized final long getMulticastFramesReceived() {
		return multicastFramesReceived;
	}

	
	public synchronized final long getBytesSent() {
		return bytesSent;
	}

	
	public synchronized final long getPacketsSent() {
		return packetsSent;
	}

	
	public synchronized final long getSendErrors() {
		return sendErrors;
	}

	
	public synchronized final long getDroppedSentPackets() {
		return droppedSentPackets;
	}

	
	public synchronized final long getSentFIFOErrors() {
		return sentFIFOErrors;
	}

	
	public synchronized final long getCollisions() {
		return collisions;
	}

	
	public synchronized final long getCarrierDrops() {
		return carrierDrops;
	}

	
	public synchronized final long getCompressedPacketsTransmitted() {
		return compressedPacketsTransmitted;
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((interfaceName == null) ? 0 : interfaceName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final LinuxNetworkInterface other = (LinuxNetworkInterface) obj;
		if (interfaceName == null) {
			if (other.interfaceName != null)
				return false;
		} else if (!interfaceName.equals(other.interfaceName))
			return false;
		return true;
	}

	
	public synchronized final long getBytesPerSecondReceived() {
		return bytesPerSecondReceived;
	}

	
	public synchronized final long getBytesPerSecondSent() {
		return bytesPerSecondSent;
	}

	
	public synchronized final long getPacketsPerSecondReceived() {
		return packetsPerSecondReceived;
	}

	
	public synchronized final long getPacketsPerSecondSent() {
		return packetsPerSecondSent;
	}

	
	public synchronized final long getTimespan() {
		return timespan;
	}
	
}
