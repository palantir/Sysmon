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
package com.palantir.opensource.sysmon.example;

import java.lang.management.ManagementFactory;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Set;

import javax.management.MBeanServer;
import javax.management.ObjectName;

import com.palantir.opensource.sysmon.SysmonDaemon;
import com.palantir.opensource.sysmon.SystemMonitor;
import com.palantir.opensource.sysmon.util.JMXUtils;


/**
 * Runs the {@link SysmonDaemon} for twenty seconds, reading all of the available measurements
 * every two seconds and printing them out to console.
 * 
 * 
 *
 */
public class SimpleSysmonExample {

	public static final void main(String[] args) throws Exception {
		
		SysmonDaemon.configureDefaultLogging();
		
		// empty config picks up the defaults
		SysmonDaemon daemon = new SysmonDaemon(null);
		
		// daemon is now running
		final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		ObjectName allSysmonObjectsPattern = new ObjectName(SystemMonitor.DEFAULT_JMX_BEAN_PATH + ".*:*");
		
		// take data every two seconds, twice 
		for (int i = 0; i < 10; i++){
			Thread.sleep(2000);
			String timestamp = getTimestamp();
			System.out.println("------------------- START " + timestamp + " -------------------");
			Set<ObjectName> sysmonObjects = server.queryNames(allSysmonObjectsPattern, null);
			for(ObjectName mbean : sysmonObjects) {
				JMXUtils.prettyPrintMbean(mbean);
			}
			System.out.println("-------------------- END " + timestamp + " --------------------\n\n");
		}
		System.out.println("Took ten samples across 20 seconds.");
		System.out.println("Shutting down daemon");
		long shutdownStart = System.currentTimeMillis();
		daemon.shutdown();
		long shutdownDuration = System.currentTimeMillis() - shutdownStart;
		System.out.println("Shutdown completed in " + shutdownDuration + "ms");
		
	}
	
	
	static String getTimestamp(){
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ");
		return df.format(new Date());
	}

}
