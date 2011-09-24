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

import junit.framework.Test;
import junit.framework.TestSuite;



public class AllLinuxTests extends TestSuite {

	public static Test suite() {
		TestSuite suite = new TestSuite("All Linux Sysmon Tests");
		suite.addTestSuite(VMStatTest.class);
		suite.addTestSuite(IOStatTest.class);
		suite.addTestSuite(DiskspaceTest.class);
		suite.addTestSuite(LinuxFileSystemTest.class);
		suite.addTestSuite(LoadAverageTest.class);
		suite.addTestSuite(EntropyLevelTest.class);
		suite.addTestSuite(NetStatTest.class);
		return suite;
	}
	
}
