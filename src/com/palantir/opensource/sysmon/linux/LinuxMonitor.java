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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.palantir.opensource.sysmon.Monitor;
import com.palantir.opensource.sysmon.SysmonDaemon.ShutdownTask;
import com.palantir.opensource.sysmon.SysmonException;
import com.palantir.opensource.sysmon.SystemMonitor;

/**
 * <p>
 * Implementation of Linux monitoring, this class monitors a Linux host system, starts and stops monitors.
 * </p>
 */
public class LinuxMonitor implements SystemMonitor {

	static final Logger log = LogManager.getLogger(LinuxMonitor.class);
	
	/**
	 * Prefix for configuration key for the {@link LinuxMonitor}.
	 * Prefix: {@value}
	 */
	public static final String CONFIG_KEY_PREFIX =  SystemMonitor.CONFIG_KEY_PREFIX + ".linux";
	/**
	 * Default path for JMX Beans for this monitor.
	 * Path: {@value}
	 */
	public static final String DEFAULT_JMX_BEAN_PATH = SystemMonitor.DEFAULT_JMX_BEAN_PATH + ".linux";
	
	public static final String CONFIG_KEY_JMX_BEAN_PATH = CONFIG_KEY_PREFIX + ".beanpath";
	
	private final Collection<Monitor> monitors = new ArrayList<Monitor>(); 
	

	/**
	 * Starts up monitoring for a Linux VM. The failure of any monitor during configuration and startup
	 * results in a fatal error and all monitors will be shutdown.
	 * 
	 * @throws SysmonException if this platform doesn't match this monitor.  The method 
	 * {@link #verifyExecutionEnvironment(Properties)}
	 * @throws LinuxMonitoringException - on error with configuration or startup of a specific monitor.
	 * should be called first to check for potential mismatches between platform and monitor.
	 */
	public void startPlatformSpecificMonitoring(Properties config) throws SysmonException, LinuxMonitoringException {

		// copy config so we can change it
		config = (Properties)config.clone();
		
		// set bean path to default, if not overridden
		if(!config.containsKey(CONFIG_KEY_JMX_BEAN_PATH)){
			config.setProperty(CONFIG_KEY_JMX_BEAN_PATH, DEFAULT_JMX_BEAN_PATH);
		}
		
		verifyExecutionEnvironment(config);
		
		log.info("Starting platform-specific monitoring for Linux.");
		try {
			try {
				LinuxVMStatJMXWrapper vmstatWrapper = new LinuxVMStatJMXWrapper(config);
				vmstatWrapper.startMonitoring();
				monitors.add(vmstatWrapper);
			} catch (LinuxMonitoringException e) {
				log.error("Error starting vmstat monitoring.", e);
				throw e;
			}
			try {
				LinuxIOStatJMXWrapper iostatWrapper = new LinuxIOStatJMXWrapper(config);
				iostatWrapper.startMonitoring();
				monitors.add(iostatWrapper);
			} catch (LinuxMonitoringException e) {
				log.error("Error starting iostat monitoring.", e);
				throw e;
			}
			try {
				LinuxNetStatJMXWrapper netstatWrapper = new LinuxNetStatJMXWrapper(config);
				netstatWrapper.startMonitoring();
				monitors.add(netstatWrapper);
			} catch (LinuxMonitoringException e) {
				log.error("Error starting netstat monitoring.", e);
				throw e;
			}
			try {
				LinuxDiskspaceJMXWrapper diskspaceWrapper = new LinuxDiskspaceJMXWrapper(config);
				diskspaceWrapper.startMonitoring();
				monitors.add(diskspaceWrapper);
			} catch(LinuxMonitoringException e) {
				log.error("Error starting diskspace monitoring.", e);
				throw e;
			}
			try {
				LinuxLoadAverageJMXWrapper loadAverageWrapper = new LinuxLoadAverageJMXWrapper(config);
				loadAverageWrapper.startMonitoring();
				monitors.add(loadAverageWrapper);
			} catch(LinuxMonitoringException e) {
				log.error("Error starting load average monitoring.", e);
			}
			try {
				LinuxEntropyLevelJMXWrapper entropyWrapper = new LinuxEntropyLevelJMXWrapper(config);
				entropyWrapper.start();
				monitors.add(entropyWrapper);
			} catch(LinuxMonitoringException e) {
				log.error("Error starting entropy level monitoring.", e);
			}
			log.info("Platform-specific monitoring for Linux Started.");
		} catch (LinuxMonitoringException e) {
			// re-catching an earlier exception
			for(Monitor m : monitors) {
				log.info("Shutting down monitor " + m.getClass().getSimpleName() + 
				         " due to startup errors with another monitor");
				try {
					m.stopMonitoring();
				} catch (InterruptedException e1) {
					Thread.currentThread().interrupt();
				}
			}
		}
	}

	/**
	 * Shuts down and cleans up all the Linux monitors mananged by this class.
	 */
	public void stopPlatformSpecificMonitoring() {
		ExecutorService executor = Executors.newFixedThreadPool(monitors.size(), new ThreadFactory() {
			
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r);
				t.setName("Shutdown thread");
				return t;
			}
		});
		
		
		// shutdown in parallel
		for(Monitor s : monitors){
			executor.submit(new ShutdownTask(s));
		}
		executor.shutdown();
		try {
			executor.awaitTermination(5, TimeUnit.SECONDS);
		} catch (InterruptedException e) {
			System.out.println("Skipping orderly shutdown due to interrupt.");
		}
		
	}
	
	/**
	 * Runs any tests to verify that the currently running VM is appropriate for this monitor.
	 * In particular, this monitors checks that the VM is running on <code>os.name</code> of 'Linux'
	 * and that the <code>os.version</code> starts with '2.6'.
	 * @param config configuration parameters to be used with this monitor.  Currently ignored by 
	 * this particular implementation.
	 * @throws SysmonException if the execution environment is not apropriate for this monitor.
	 */
	public void verifyExecutionEnvironment(Properties config) throws SysmonException {
		String osName = System.getProperty("os.name","UNKNOWN"); // use default to avoid NPE
		String osVersion = System.getProperty("os.version","UNKNOWN"); // use default to avoid NPE
		
		if(!osName.equals("Linux")) {
			throw new SysmonException("Linux monitoring can only run on Linux.  Platform: " + osName);
		}
		if(!osVersion.startsWith("2.6")) {
			log.warn("Linux monitoring has only been tested on Linux 2.6, " +
			         "so some things may not work. Detected version: " + osVersion);
		}
	}
}
