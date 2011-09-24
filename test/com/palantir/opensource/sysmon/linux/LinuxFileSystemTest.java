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

import junit.framework.TestCase;


public class LinuxFileSystemTest extends TestCase {

	
	LinuxFileSystem slash = new LinuxFileSystem(LinuxDiskspaceJMXWrapper.OBJECT_NAME_PREFIX,
	                                            "/",
	                                            "ext3", 
	                                            "/", 
	                                            1000L, 
	                                            500L, 
	                                            500L, 
	                                            (byte)50, 
	                                            1000L, 
	                                            400L, 
	                                            600L, 
	                                            (byte)40);
	
	LinuxFileSystem usr = new LinuxFileSystem(LinuxDiskspaceJMXWrapper.OBJECT_NAME_PREFIX,
                                              "/usr",
                                              "ext3", 
                                              "/usr", 
                                              2000L, 
                                              1200L, 
                                              800L, 
                                              (byte)60, 
                                              2000L, 
                                              1000L, 
                                              1000L, 
                                              (byte)50);

	LinuxFileSystem usr2 = new LinuxFileSystem(LinuxDiskspaceJMXWrapper.OBJECT_NAME_PREFIX,
	                                           "/usr",
	                                           "ext3", 
	                                           "/usr", 
	                                           1000L, 
	                                           500L, 
	                                           500L, 
	                                           (byte)50, 
	                                           1000L, 
	                                           400L, 
	                                           600L, 
	                                           (byte)40);
	
	public void testTakeValues() throws Exception {
		usr.takeValues(usr2);
		
		assertEquals("Mis-match on getFilesytemType() values",usr2.getFilesytemType(),usr.getFilesytemType());
		assertEquals("Mis-match on getMountPoint() values",usr2.getMountPoint(),usr.getMountPoint());
		assertEquals("Mis-match on getTotalMegabytes() values",usr2.getTotalMegabytes(),usr.getTotalMegabytes());
		assertEquals("Mis-match on getUsedMegabytes() values",usr2.getUsedMegabytes(),usr.getUsedMegabytes());
		assertEquals("Mis-match on getAvailableMegabytes() values",usr2.getAvailableMegabytes(),usr.getAvailableMegabytes());
		assertEquals("Mis-match on getPercentageSpaceUsed() values",usr2.getPercentageSpaceUsed(),usr.getPercentageSpaceUsed());
		assertEquals("Mis-match on getTotalInodes() values",usr2.getTotalInodes(),usr.getTotalInodes());
		assertEquals("Mis-match on getUsedInodes() values",usr2.getUsedInodes(),usr.getUsedInodes());
		assertEquals("Mis-match on getAvailableInodes() values",usr2.getAvailableInodes(),usr.getAvailableInodes());
		assertEquals("Mis-match on getPercentageInodesUsed() values",usr2.getPercentageInodesUsed(),usr.getPercentageInodesUsed());

	}
}
