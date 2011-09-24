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
package com.palantir.opensource.sysmon;

import junit.framework.AssertionFailedError;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;



public class ErrorDetechingAppenderTest extends BaseTest {

	static final Logger log = LogManager.getLogger(ErrorDetechingAppenderTest.class);
	
	public void testErrorDetectingAppender() throws Exception {
		installLoggingErrorDetector(ErrorDetechingAppenderTest.class, Level.ERROR);
		log.error("This is a test of the error detection system");
		try {
			checkForErrorMessages();
			fail("No errors detected");
		} catch (AssertionFailedError e) {
			// expected
		}
	}
}
