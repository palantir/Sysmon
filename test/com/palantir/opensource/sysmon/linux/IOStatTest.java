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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.management.ManagementFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;


public class IOStatTest extends LinuxBaseTest {

	/**
	 * use tested class's logger for error detection purposes
	 * @see #installLoggingErrorDetector(Class, Level...)
	 * @see #setUp()
	 */
	static final Logger log = LogManager.getLogger(LinuxIOStatJMXWrapper.class);
	
	private static final String OBJECT_NAME_PREFIX = LinuxMonitor.DEFAULT_JMX_BEAN_PATH +
													 LinuxIOStatJMXWrapper.OBJECT_NAME_PREFIX;
	

	private static final Integer TEST_PERIOD = 2;
	
	LinuxIOStatJMXWrapper iostatWrapper = null;

	static final String[] ATTRIBUTES = {
		"Device",
		"SamplePeriodInSeconds",
		"MergedReadRequestsPerSecond",
		"MergedWriteRequestsPerSecond",
		"ReadRequestsPerSecond",
		"WriteRequestsPerSecond",
		"KilobytesReadPerSecond",
		"KilobytesWrittenPerSecond",
		"AverageRequestSizeInSectors",
		"AverageQueueLengthInSectors",
		"AverageWaitTimeInMillis",
		"AverageServiceTimeInMillis",
		"BandwidthUtilizationPercentage",
	};
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		installLoggingErrorDetector(LinuxIOStatJMXWrapper.class, Level.ERROR,Level.WARN);
		iostatWrapper = new LinuxIOStatJMXWrapper(generateConfig());
		System.out.println("Starting iostat wrapper");
		iostatWrapper.startMonitoring();
		Thread.sleep(TEST_PERIOD * 1000L * 2); // wait for startup
	}
	
	@Override
	protected void tearDown() throws Exception {
		System.out.println("Stopping load average wrapper");
		iostatWrapper.stopMonitoring();
		iostatWrapper = null;
		checkForErrorMessages();
	}
	
	public Properties generateConfig() {
		Properties p = new Properties(); // pick up defaults
		p.setProperty(LinuxIOStatJMXWrapper.CONFIG_KEY_IOSTAT_PERIOD,
		              Integer.toString(TEST_PERIOD)); // make it run fast for testing
		p.setProperty(LinuxDiskspaceJMXWrapper.CONFIG_KEY_DF_PERIOD,
		              Integer.toString(TEST_PERIOD)); // make it run fast for testing
		return p;
	}
	
	public void testBeanIsPublished() throws Exception {
		System.out.println("Bean prefix: " + OBJECT_NAME_PREFIX);
		ObjectName objectName = new ObjectName(OBJECT_NAME_PREFIX + "*");
		final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		Set<ObjectName> mbeans = server.queryNames(objectName, null);
		assertTrue("No MBeans found matching the disk space wrapper!",mbeans.size() > 0);
		
		for(ObjectName diskspaceObjectName : mbeans) {
			validateBean(diskspaceObjectName);
		}
	}
	
	void validateBean(ObjectName diskspaceObjectName) throws Exception {
		doPublishCheck(diskspaceObjectName.getCanonicalName(), ATTRIBUTES);
	}
	


	/**
	 * Attribute to check for changes over time
	 */
	static final String ATTRIB = "KilobytesWrittenPerSecond";
	/**
	 * Max half-sample periods to check before failing the test
	 */
	static final int SAMPLE_PERIODS = 10;
	/**
	 * Theory of operation: 
	 * <ol>
	 * <li>Start iostat monitoring</li>
	 * <li>Fire up io load generator</li>
	 * <li>Start reading the bean, looking for changes between reads</li>
	 * <li>Cleanup</li>
	 * </ol>
	 * @throws Exception
	 */
	public void testValuesChangeOverTime() throws Exception {
		System.out.println("Bean prefix: " + OBJECT_NAME_PREFIX);

		
		Thread.sleep(TEST_PERIOD * 1000L * 2);
		
		IOLoadGenerator loadGenerator = new IOLoadGenerator();

		final ObjectName searchPattern = new ObjectName(OBJECT_NAME_PREFIX + "*");
		final MBeanServer server = ManagementFactory.getPlatformMBeanServer();

		final Map<ObjectName,Float> previousReadings = new HashMap<ObjectName, Float>();
		boolean sawDifference = false;
		// Keep sampling until we see a difference in a reading
		// it's a race with when the bean gets updated, so we check each
		// half period
		for(int i = 0; i < SAMPLE_PERIODS && !sawDifference; i++) {
			Set<ObjectName> mbeans = server.queryNames(searchPattern, null);
			for(ObjectName device : mbeans) {
				
				Float previousReading = previousReadings.get(device);
				Float currentReading = (Float)lookupJMXValue(device.getCanonicalName(), ATTRIB);
				System.out.println(device.getKeyProperty("devicename") + " " + ATTRIB + "=" + 
								   currentReading + " (prev=" + previousReading + ")");
				if(previousReading != null) { 
					System.out.println(ATTRIB + ": old=" + previousReading + " new=" + currentReading);
					if(currentReading.equals(previousReading)) {
						sawDifference = true;
						break;
					} 
					System.out.println("No change in " + ATTRIB + " since last reading");
				}
				previousReadings.put(device, currentReading);
			}
			Thread.sleep(TEST_PERIOD * 1000L / 2);
		}
		
		loadGenerator.shutdown = true;
		loadGenerator.interrupt();
		loadGenerator.join(TEST_PERIOD * 1000L / 2);
		
		assertTrue("No difference observed in readings of " + ATTRIB + " across " + 
				   (SAMPLE_PERIODS / 2) + " sample periods",
				   sawDifference);
		
	}
	
	public void testShutdownTime() throws Exception {
		Properties p = new Properties(); // pick up defaults
		p.setProperty(LinuxVMStatJMXWrapper.CONFIG_KEY_VMSTAT_PERIOD,
		              Integer.toString(60)); // make it run fast for testing
		iostatWrapper.stopMonitoring();
		log.info("Restarting vmstat wrapper");
		iostatWrapper = new LinuxIOStatJMXWrapper(p);
		iostatWrapper.startMonitoring();
		log.info("Sleeping while isstat starts up.");
		Thread.sleep(2000L);
		long startOfShutdown = System.currentTimeMillis();
		log.info("Shutting down.");
		iostatWrapper.stopMonitoring();
		long endOfShutdown = System.currentTimeMillis();
		long shutdownMillis = endOfShutdown - startOfShutdown;
		log.info("Shutdown took " + shutdownMillis+ "ms");
		assertTrue("Shutdown took longer than a second",shutdownMillis < 1000);
	}
	

	static final int BUF_SIZE = 8 * 1024 * 1024;
	class IOLoadGenerator extends Thread {
		volatile boolean shutdown = false;

		
		public IOLoadGenerator() {
			super(IOLoadGenerator.class.getSimpleName());
			start();
		}
		
		@Override
		public void run() {
			System.out.println(IOLoadGenerator.class.getSimpleName() + " started");
			// write to temp file
			File tempFile = null;
			try {
				tempFile = File.createTempFile(getClass().getSimpleName(), ".raw");
				tempFile.deleteOnExit();
				FileInputStream devZero = new FileInputStream("/dev/zero");
				byte[] zeroes = new byte[BUF_SIZE]; // read 8 MB
				devZero.read(zeroes);
				devZero.close();

				while(!shutdown) {
					// write the file 8 times == 32 MB
					for(int i = 0; i < 8; i++) {
						FileOutputStream tmp = new FileOutputStream(tempFile);
						tmp.write(zeroes,0,(int)(Math.random() * BUF_SIZE));
						tmp.close();
						if(Math.random() > 0.7) {
							FileInputStream fis = new FileInputStream(tempFile);
							fis.read(new byte[(int)(Math.random() * BUF_SIZE)]);
						}
					}
					sleep((long)(TEST_PERIOD * 1000L * Math.random()));
				}
			} catch(InterruptedException e) {
				// signal - ignore and shutdown
			} catch(Exception e) {
			
				e.printStackTrace();
			} finally {
				if(tempFile != null) {
					tempFile.delete();
				}
			}
			System.out.println(IOLoadGenerator.class.getSimpleName() + " shutdown");
		}
	}

}
