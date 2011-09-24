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

/**
 * MBean interface for {@link LinuxNetStatJMXWrapper}.
 */
public interface LinuxNetworkInterfaceMBean {

	public abstract String getInterfaceName();

	public abstract long getBytesReceived();

	public abstract long getPacketsReceived();

	public abstract long getRecieveErrors();

	public abstract long getDroppedReceivedPackets();

	public abstract long getReceiveFIFOErrors();

	public abstract long getReceiveFrameErrors();

	public abstract long getCompressedPacketsReceived();

	public abstract long getMulticastFramesReceived();

	public abstract long getBytesSent();

	public abstract long getPacketsSent();

	public abstract long getSendErrors();

	public abstract long getDroppedSentPackets();

	public abstract long getSentFIFOErrors();

	public abstract long getCollisions();

	public abstract long getCarrierDrops();

	public abstract long getCompressedPacketsTransmitted();
	
	public abstract long getBytesPerSecondReceived();
	public abstract long getBytesPerSecondSent();
	public abstract long getPacketsPerSecondReceived();
	public abstract long getPacketsPerSecondSent();
	public abstract long getTimespan();


}