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



public class LoadAverageTest extends LinuxBaseTest {

	private static final String	OBJECT_NAME	= LinuxMonitor.DEFAULT_JMX_BEAN_PATH + LinuxLoadAverageJMXWrapper.OBJECT_NAME;

	LinuxLoadAverageJMXWrapper loadAverageWrapper;
	
	static final String ATTRIBUTES[] = {
		"OneMinute",
		"TenMinute",
		"FifteenMinute",
	};
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		installLoggingErrorDetector(LinuxLoadAverageJMXWrapper.class, Level.ERROR, Level.WARN);
		loadAverageWrapper = new LinuxLoadAverageJMXWrapper(generateConfig());
		System.out.println("Starting load average wrapper");
		loadAverageWrapper.startMonitoring();
		Thread.sleep(TEST_PERIOD * 1000L * 2);
	}
	
	
	@Override
	protected void tearDown() throws Exception {
		System.out.println("Stopping load average wrapper");
		loadAverageWrapper.stopMonitoring();
		loadAverageWrapper = null;
		checkForErrorMessages();
	}
	static final Integer TEST_PERIOD = 2;

	
	public void testBeanIsPublished() throws Exception {
		doPublishCheck(OBJECT_NAME, ATTRIBUTES);
	}
	
	/**
	 * This test is not perfect, as it's possible the load average will be the same 
	 * for the two measurements, but it's pretty unlikely.
	 * 
	 * @throws Exception
	 */
	public void testValuesChangeOverTime() throws Exception {
		Double firstLoadAverage = (Double)lookupJMXValue(OBJECT_NAME, "OneMinute");
		System.out.println("Got starting one minute load average: " + firstLoadAverage);
		CPULoadDriver driver = new CPULoadDriver(1000); // generate a billion random numbers
		Thread.sleep(TEST_PERIOD * 1000L * 4); // sleep two periods to be sure it changed
		Double secondLoadAverage = (Double)lookupJMXValue(OBJECT_NAME, "OneMinute");
		System.out.println("Got loaded one minute load average: " + secondLoadAverage);
		driver.shutdown();
		assertFalse("Got same reading of " + firstLoadAverage + 
		            " for two periods of sampling (This may be due to other load on the system).",
		            firstLoadAverage.equals(secondLoadAverage));
	}
	
	
	public Properties generateConfig() {
		Properties p = new Properties(); // pick up defaults
		p.setProperty(LinuxLoadAverageJMXWrapper.CONFIG_KEY_UPTIME_PERIOD,
		              Integer.toString(TEST_PERIOD)); // make it run fast for testing
		return p;
	}
	
}
