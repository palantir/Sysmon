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

import java.util.Properties;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;


public class VMStatTest extends LinuxBaseTest {

	static final Logger log = LogManager.getLogger(VMStatTest.class);
	
	private static final String	OBJECT_NAME	= LinuxMonitor.DEFAULT_JMX_BEAN_PATH + LinuxVMStatJMXWrapper.OBJECT_NAME;

	LinuxVMStatJMXWrapper vmstatWrapper;

	static final String ATTRIBUTES[] = {
		"RunningProcesses",
		"SleepingProcesses",
		"SwappedMemory",
		"FreeMemory",
		"BuffersMemory",
		"CacheMemory",
		"SwappedIn",
		"SwappedOut",
		"BlocksRead",
		"Interrupts",
		"ContextSwitches",
		"UserPercentCPU",
		"SysPercentCPU",
		"WaitPercentCPU",
		"StolenFromVMCPU"
	};
	
	
	static final Integer TEST_PERIOD = 1;
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		installLoggingErrorDetector(LinuxVMStatJMXWrapper.class, Level.ERROR, Level.WARN);
		vmstatWrapper = new LinuxVMStatJMXWrapper(generateConfig());
		System.out.println("Starting vmstat wrapper");
		vmstatWrapper.startMonitoring();
		Thread.sleep(TEST_PERIOD * 1000L * 2);
	}
	
	@Override
	protected void tearDown() throws Exception {
		super.tearDown();
		vmstatWrapper.stopMonitoring();
		System.out.println("Shutdown vmstat wrapper");
		checkForErrorMessages();
	}
	
	public void testBeanIsPublished() throws Exception {
		doPublishCheck(OBJECT_NAME, ATTRIBUTES);
	}
	
	/**
	 * Checks that contextswitches value is changing - shouldn't be the same twice
	 * @throws Exception
	 */
	public void testValuesChangeOverTime() throws Exception {
		Integer firstContextSwitchReading = (Integer)lookupJMXValue(OBJECT_NAME, "ContextSwitches");
		Thread.sleep(TEST_PERIOD * 1000L * 2); // sleep two periods to be sure it changed
		Integer secondContextSwitchReading = (Integer)lookupJMXValue(OBJECT_NAME, "ContextSwitches");
		assertFalse("Got same reading of " + firstContextSwitchReading + 
		            " for two periods of sampling.",
		            firstContextSwitchReading.equals(secondContextSwitchReading));
	}
	
	public void testShutdownTime() throws Exception {
		Properties p = new Properties(); // pick up defaults
		p.setProperty(LinuxVMStatJMXWrapper.CONFIG_KEY_VMSTAT_PERIOD,
		              Integer.toString(60)); // make it run fast for testing
		vmstatWrapper.stopMonitoring();
		log.info("Restarting vmstat wrapper");
		vmstatWrapper = new LinuxVMStatJMXWrapper(p);
		vmstatWrapper.startMonitoring();
		log.info("Sleeping while vmstat starts up.");
		Thread.sleep(2000L);
		long startOfShutdown = System.currentTimeMillis();
		log.info("Shutting down.");
		vmstatWrapper.stopMonitoring();
		long endOfShutdown = System.currentTimeMillis();
		long shutdownMillis = endOfShutdown - startOfShutdown;
		log.info("Shutdown took " + shutdownMillis+ "ms");
		assertTrue("Shutdown took longer than a second",shutdownMillis < 1000);
	}
	
	public Properties generateConfig() {
		Properties p = new Properties(); // pick up defaults
		p.setProperty(LinuxVMStatJMXWrapper.CONFIG_KEY_VMSTAT_PERIOD,
		              Integer.toString(TEST_PERIOD)); // make it run fast for testing
		return p;
	}
}
