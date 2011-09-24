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

import java.security.SecureRandom;
import java.util.Properties;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;


public class EntropyLevelTest extends LinuxBaseTest {
	
	/**
	 * use tested class's logger for error detection purposes
	 * @see #installLoggingErrorDetector(Class, Level...)
	 * @see #setUp()
	 */
	static final Logger log = LogManager.getLogger(LinuxEntropyLevelJMXWrapper.class);
	
	private static final String OBJECT_NAME = LinuxMonitor.DEFAULT_JMX_BEAN_PATH +
											  LinuxEntropyLevelJMXWrapper.OBJECT_NAME;
	

	private static final Integer TEST_PERIOD = 1;
	
	LinuxEntropyLevelJMXWrapper entropyWrapper = null;

	static final String[] ATTRIBUTES = {
		"EntropyLevel"
	};
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		installLoggingErrorDetector(LinuxIOStatJMXWrapper.class, Level.ERROR,Level.WARN);
		entropyWrapper = new LinuxEntropyLevelJMXWrapper(generateConfig());
		System.out.println("Starting entropy wrapper");
		entropyWrapper.startMonitoring();
		Thread.sleep(TEST_PERIOD * 1000L * 2); // wait for startup
	}
	
	@Override
	protected void tearDown() throws Exception {
		System.out.println("Stopping load average wrapper");
		entropyWrapper.stopMonitoring();
		entropyWrapper = null;
		checkForErrorMessages();
	}

	public void testBeanIsPublished() throws Exception {
		doPublishCheck(OBJECT_NAME, ATTRIBUTES);
	}
	
	public void testValueIsChangingOverTime() throws Exception {
		
		Integer firstReading = (Integer)lookupJMXValue(OBJECT_NAME, ATTRIBUTES[0]);
		// this reads high quality randomness out of the entropy pool, changing the value
		SecureRandom.getSeed(6);
		Thread.sleep(TEST_PERIOD * 1000L * 2);
		Integer secondReading = (Integer)lookupJMXValue(OBJECT_NAME, ATTRIBUTES[0]);
		System.out.println("Got entropy level readings: first=" + firstReading + " second=" + secondReading);
		assertFalse("Entropy level did not change as expected",firstReading.equals(secondReading));
	}
	
	public Properties generateConfig() {
		Properties p = new Properties(); // pick up defaults
		p.setProperty(LinuxEntropyLevelJMXWrapper.CONFIG_KEY_ENTROPY_LEVEL_PERIOD,
		              Integer.toString(TEST_PERIOD)); // make it run fast for testing
		return p;
	}
	
}
