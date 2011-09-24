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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.log4j.Level;


public class DiskspaceTest extends LinuxBaseTest {

	static final String OBJECT_NAME_PREFIX = LinuxMonitor.DEFAULT_JMX_BEAN_PATH +
											 LinuxDiskspaceJMXWrapper.OBJECT_NAME_PREFIX;
	
	private static final Integer TEST_PERIOD = 2;
	
	LinuxDiskspaceJMXWrapper diskspaceWrapper = null;
	
	static final String[] ATTRIBUTES = {
		"MountPoint",
		"DevicName",
		"FilesytemType",
		"TotalMegabytes",
		"UsedMegabytes",
		"AvailableMegabytes",
		"PercentageSpaceUsed",
		"TotalInodes",
		"UsedInodes",
		"AvailableInodes",
		"PercentageInodesUsed",
	};
	
	@Override
	protected void setUp() throws Exception {
		super.setUp();
		installLoggingErrorDetector(LinuxDiskspaceJMXWrapper.class, Level.ERROR,Level.WARN);
		diskspaceWrapper = new LinuxDiskspaceJMXWrapper(generateConfig());
		System.out.println("Starting disk space wrapper");
		diskspaceWrapper.startMonitoring();
		Thread.sleep(TEST_PERIOD * 1000L * 2); // wait for startup
	}
	
	@Override
	protected void tearDown() throws Exception {
		System.out.println("Stopping load average wrapper");
		diskspaceWrapper.stopMonitoring();
		diskspaceWrapper = null;
		checkForErrorMessages();
	}
	
	public Properties generateConfig() {
		Properties p = new Properties(); // pick up defaults
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
	
	public void testValuesChangeOverTime() throws Exception {
		System.out.println("Bean prefix: " + OBJECT_NAME_PREFIX);
		// pattern matching, FTW
		ObjectName objectName = new ObjectName(OBJECT_NAME_PREFIX + "*");
		final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		Set<ObjectName> mbeans = server.queryNames(objectName, null);
		
		// index mountpoints to figure out where our temp file is
		Map<String, ObjectName> mountPoints = new HashMap<String, ObjectName>();
		for(ObjectName filesystem : mbeans) {
			String mountPoint = server.getAttribute(filesystem, "MountPoint").toString();
			mountPoints.put(mountPoint,filesystem);
		}
		
		File tempFile = File.createTempFile(getClass().getSimpleName(), ".raw");
		tempFile.deleteOnExit();
		
		String mountPoint= null;
		// looking for the longest substring 
		// since /tmp/foo could be under / or /tmp
		for(String fileSystemName : mountPoints.keySet()) {
			if(tempFile.getAbsolutePath().startsWith(fileSystemName)) {
				if(mountPoint == null) {
					mountPoint = fileSystemName;
				} else {
					if(mountPoint.length() < fileSystemName.length()) {
						mountPoint = fileSystemName;
					}
				}
			}
		}
		
		assertNotNull("Couldn't file filesystem that " + tempFile + " is on",mountPoint);
		System.out.println("Found filesystem that contains tempfile " + tempFile.getAbsolutePath() +
		                   ": " + mountPoint);
		ObjectName tmpFilesystem = mountPoints.get(mountPoint);
		
		tempFile.delete(); // decrement inode count
		
		// take initial reading
		Long firstBytes = (Long)lookupJMXValue(tmpFilesystem.getCanonicalName(),"UsedMegabytes");
		Long firstInodes = (Long)lookupJMXValue(tmpFilesystem.getCanonicalName(),"UsedInodes");
		
		
		// write to temp file

		List<File> tempFiles = new ArrayList<File>();
		try {

			
			FileInputStream devZero = new FileInputStream("/dev/zero");
			byte[] zeroes = new byte[8 * 1024 * 1024]; // read 8 MB
			devZero.read(zeroes);
			devZero.close();

			for(int i = 0; i < 10; i++){
				tempFile = File.createTempFile(getClass().getSimpleName(), ".raw");
				tempFile.deleteOnExit();
				tempFiles.add(tempFile);
				FileOutputStream tmp = new FileOutputStream(tempFile);
				tmp.write(zeroes);
				tmp.close();
			}
			
			// wait for update
			Thread.sleep(TEST_PERIOD * 1000L * 2);
			
			// take second reading

			Long secondBytes= (Long)lookupJMXValue(tmpFilesystem.getCanonicalName(),"UsedMegabytes");
			Long secondInodes = (Long)lookupJMXValue(tmpFilesystem.getCanonicalName(),"UsedInodes");
			
			// This can fail if someone deletes a file > 8 MB during the test time window
			System.out.println("Filesystem size grew from " + firstBytes + " to " + secondBytes + " (mb)");
			assertFalse("Filesystem size did not change as expected. (This might be due to other system activity)",
			           firstBytes.equals(secondBytes));
			System.out.println("Filesystem inode use grew from " + firstInodes + " to " + secondInodes);
			assertFalse("Filesystem inodes did not change as expected. (This might be due to other system activity)",
			           firstInodes.equals(secondInodes));
			
		} finally {
			for(File tmp : tempFiles){
				tmp.delete();
			}
		}
	}
}
