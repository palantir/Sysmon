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

import com.palantir.opensource.sysmon.BaseTest;


public abstract class LinuxBaseTest extends BaseTest {

	@Override
	protected void setUp() throws Exception {
		super.setUp();
		
		String osName = System.getProperty("os.name","UNKNOWN"); // use default to avoid NPE
		String osVersion = System.getProperty("os.version","UNKNOWN"); // use default to avoid NPE
		
		if(!osName.equals("Linux")) {
			fail("Linux tests can only run on a Linux system.  Platform: " + osName);
		}
		if(!osVersion.startsWith("2.6")) {
			System.err.println("Running on Linux " + osVersion + " - untested.  " +
							   "Good luck! (Things will probably break)");
		}
	}
}
