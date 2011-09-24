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

import java.io.FileInputStream;
import java.lang.management.ManagementFactory;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Properties;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;



public class NetStatTest extends LinuxBaseTest {

	LinuxNetStatJMXWrapper netstatWrapper = null;
	
	public static final int TEST_PERIOD = 1; // one second

	public static final String OBJECT_NAME_PREFIX = LinuxMonitor.DEFAULT_JMX_BEAN_PATH + 
													LinuxNetStatJMXWrapper.OBJECT_NAME_PREFIX;
	
	public static final String ATTRIBUTES[] = {
		"BytesPerSecondReceived",
		"InterfaceName",
		"BytesReceived",
		"PacketsReceived",
		"RecieveErrors",
		"DroppedReceivedPackets",
		"ReceiveFIFOErrors",
		"ReceiveFrameErrors",
		"CompressedPacketsReceived",
		"MulticastFramesReceived",
		"BytesSent",
		"PacketsSent",
		"SendErrors",
		"DroppedSentPackets",
		"SentFIFOErrors",
		"Collisions",
		"CarrierDrops",
		"CompressedPacketsTransmitted",
		"BytesPerSecondSent",
		"PacketsPerSecondReceived",
		"PacketsPerSecondSent",
		"Timespan",
		};
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		// turnOnDebugLogging();
		installLoggingErrorDetector(LinuxNetStatJMXWrapper.class, Level.ERROR,Level.WARN);
		netstatWrapper = new LinuxNetStatJMXWrapper(generateConfig());
		System.out.println("Starting netstat wrapper");
		netstatWrapper.startMonitoring();
		Thread.sleep(TEST_PERIOD * 1000L * 2); // wait for startup
	}
	
	@Override
	protected void tearDown() throws Exception {
		if(netstatWrapper != null) {
			netstatWrapper.stopMonitoring();
			System.out.println("netstat wrapper shutdown");
			netstatWrapper = null;
		}
		checkForErrorMessages();
		// turnOffDebugLogging();
	}
	
	public void testBeanIsPublished() throws Exception {
		System.out.println("Bean prefix: " + OBJECT_NAME_PREFIX);
		ObjectName objectName = new ObjectName(OBJECT_NAME_PREFIX + "*");
		final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		Set<ObjectName> mbeans = server.queryNames(objectName, null);
		assertTrue("No MBeans found matching the netstat wrapper!",mbeans.size() > 0);
		
		for(ObjectName diskspaceObjectName : mbeans) {
			doPublishCheck(diskspaceObjectName.getCanonicalName(), ATTRIBUTES);
		}
	}
	
	public void testValuesAreChangingOverTime() throws Exception {
		String objectName = OBJECT_NAME_PREFIX + "lo"; // localhost
		Long firstByteCounterReading = (Long)lookupJMXValue(objectName, "BytesSent");
		new LoopbackTrafficGenerator().generateLoopbackTraffic();
		Thread.sleep(TEST_PERIOD * 1000L * 2);
		Long secondByteCounterReading = (Long)lookupJMXValue(objectName, "BytesSent");
		
		assertTrue("Byte counters did not rise after pushing traffic across interface",
		           secondByteCounterReading > firstByteCounterReading);
	}
	
	
	static final int LO_PORT = 10234;
	static final InetAddress LOCALHOST;
	static {
		InetAddress _localhost = null;
		try {
			_localhost = InetAddress.getByAddress(new byte[] {127,0,0,1});
		} catch (UnknownHostException e) {
			// not going to happen
		}
		LOCALHOST = _localhost;
	}
	
	/**
	 * Class to read and write some data on the loopback interface to bump
	 * the device counters to make sure stuff is actually working.
	 * 
	 * 
	 *
	 */
	class LoopbackTrafficGenerator extends Thread {

		final byte[] zeroes;

		public LoopbackTrafficGenerator() throws Exception {
			super(LoopbackTrafficGenerator.class.getSimpleName());
			setDaemon(true);
			FileInputStream devZero = new FileInputStream("/dev/zero");
			zeroes = new byte[8 * 1024]; // read 8K
			devZero.read(zeroes);
			devZero.close();
		}

		 void generateLoopbackTraffic() throws Exception {
			ServerSocket server = new ServerSocket(LO_PORT,1,LOCALHOST);
			start();
			Socket endpoint = server.accept();
			System.out.println("\t[server] accepted connection");
			endpoint.getOutputStream().write(zeroes);
			System.out.println("\t[server] wrote " + zeroes.length + " bytes");
			endpoint.getInputStream().read(new byte[zeroes.length]);
			System.out.println("\t[server] read " + zeroes.length + " bytes");
			endpoint.close();
			server.close();
			join(TEST_PERIOD * 1000L * 4);
			assertFalse("Background thread did not exit!",isAlive());
		}

		@Override
		public void run() {
			try {
				Socket client = new Socket(LOCALHOST, LO_PORT);
				System.out.println("\t[client] connected");
				client.getInputStream().read(new byte[zeroes.length]);
				System.out.println("\t[client] read " + zeroes.length + " bytes");
				client.getOutputStream().write(zeroes);
				System.out.println("\t[client] wrote " + zeroes.length + " bytes");
				client.close();
			} catch(Exception e) {
				String msg = "Error writing data in background for test (" + 
							 NetStatTest.class.getCanonicalName() + ")";
				LogManager.getLogger(LinuxNetStatJMXWrapper.class).error(msg ,e);
			}
		}
	}
	
	public Properties generateConfig() {
		Properties p = new Properties(); // pick up defaults
		p.setProperty(LinuxNetStatJMXWrapper.CONFIG_KEY_NETSTAT_PERIOD,
		              Integer.toString(TEST_PERIOD)); // make it run fast for testing
		return p;
	}
	
	Level originalLoggingLevel = null;
	public void turnOnDebugLogging() {
		Logger logger = LogManager.getLogger(LinuxNetStatJMXWrapper.class);
		originalLoggingLevel = logger.getLevel();
		logger.setLevel(Level.DEBUG);
	}
	
	public void turnOffDebugLogging() {
		Logger logger = LogManager.getLogger(LinuxNetStatJMXWrapper.class);
		if(originalLoggingLevel != null) {
			logger.setLevel(originalLoggingLevel);
		}
	}
}
