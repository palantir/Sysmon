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

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Enumeration;
import java.util.Properties;
import java.util.concurrent.Semaphore;

import org.apache.log4j.Appender;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;

import com.palantir.opensource.sysmon.linux.LinuxMonitor;

/**
 * <p>
 * {@link SysmonDaemon} can be used either as a component of a larger program or as standalone 
 * process (by invoking {@link #main(String[])}).
 * </p><p>
 *  In standalone mode, it is run as a normal Java class.  It will look for a config file to 
 *  override defaults by either consulting a the System property 
 *  {@value #SYSTEM_PROPERTY_CONFIG_FILE} or from it's first non-VM command-line argument.  Although
 *  it's called a daemon, it will not disassociate from the controlling terminal and run in the 
 *  background - this capability is not provided by default by the Java VM. Pressing Ctrl-C will
 *  cause the process to exit.
 * </p><p> 
 * To use the {@link SysmonDaemon} as a component of another program, construct a new instance and
 * pass it a {@link Properties} object with any custom configuration - null is fine to pick up 
 * defaults. Construction will start a non-daemon background thread that can be shut down by 
 * {@link #shutdown()}
 * </p>
 * <xmp>
 * Properties config = new Properties();
 * // ...
 * //  load config file if desired
 * // ...
 * //
 * SysmonDaemon daemon = new SysmonDaemon(config);
 * // background thread is now running
 * //
 * // ...
 * // Do some other stuff
 * // ...
 * //
 * daemon.shutdown();
 * </xmp>
 */
public class SysmonDaemon extends Thread {

	static final Logger log = LogManager.getLogger(SysmonDaemon.class);
	
	/**
	 * used to communicate between threads in order to shutdown the non-daemon
	 * thread used to keep the VM running when this used in pure-daemon mode.
	 */
	private final Semaphore shutdown = new Semaphore(0);
	
	/**
	 * Platform specific monitor to be used on this platform.
	 * 
	 * @see #determinePlatformMonitor() for information about how this value is obtained.
	 */
	private final SystemMonitor sysmon; 
	
	private final Properties config;
	
	/**
	 * Constructs a new {@link SysmonDaemon} object and determines its platform-specific 
	 * {@link SystemMonitor} but does not start any monitoring.
	 * 
	 * @throws SysmonException upon error figuring out which class will be used as the 
	 * {@link SystemMonitor}, initializing an instance of that class, or that instance's 
	 * verification of the execution environment in this VM.
	 */
	public SysmonDaemon(Properties config) throws SysmonException {
		super(SysmonDaemon.class.getName());
		setDaemon(false); // keep the VM alive until this thread dies
		if(config != null) {
			this.config = (Properties)config.clone(); // cloned for safety
		} else {
			this.config = new Properties();
		}
		sysmon = determinePlatformMonitor();
		sysmon.verifyExecutionEnvironment((Properties)this.config.clone()); // cloned for safety
		start(); // jump off into a background thread
	}
	
	/**
	 * Since the number of platforms currently supported is small (one, to be exact), this 
	 * is just a static method that must produce a {@link SystemMonitor} object to be run by 
	 * the daemon.  A second and third platform could be trivially added to this method &mdash;
	 * after that, perhaps some sort of classpath-scanning reflective mechanism should be 
	 * implemented to allow new platforms to be added to the system without changing this class.
	 * 
	 * @return the object to be run by this daemon to monitor the current platform.
	 */
	public static final SystemMonitor determinePlatformMonitor() throws SysmonException {
		
		final String osName = System.getProperty("os.name","UNKNOWN"); // use default to avoid NPE
		final String osVersion = System.getProperty("os.version","UNKNOWN"); // use default to avoid NPE
		
		String platformId = osName + " " + osVersion;
		try {
			if(osName.equals("Linux") && osVersion.startsWith("2.6")) {
				return LinuxMonitor.class.newInstance();
			}
		} catch (Exception e) {
			throw new SysmonException("Error instantiating platform monitor for " + platformId,e);
		}
		
		throw new SysmonException("No platform monitor found for platform " + platformId);
	}
	
	/**
	 * Starts platform specific monitoring and then blocks in a non-daemon thread, waiting
	 * for a call to {@link #shutdown()} to unblock it (by releasing a semaphore) and allowing
	 * the thread to exit.  This will keep the VM alive even as the platform monitors themselves
	 * are only running daemon threads.
	 * 
	 * @see Thread#setDaemon(boolean)
	 */
	@Override
	public void run() {
		try {
			try {
				sysmon.startPlatformSpecificMonitoring((Properties)config.clone()); // cloned for safety
			} catch (SysmonException e) {
				log.error("Error while starting monitoring.  Exiting.",e);
				return;
			}
			// just block until someone calls shutdown.
			shutdown.acquire();
			log.info("Shutdown signalled. Exiting.");
		} catch (InterruptedException e) {
			log.warn("Thread was interrupted while waiting for shutdown signal.  Daemon may now exit.",e);
		}
		log.info("Exiting " + getName() + " thread.");
	}
	

	/**
	 * Shuts down this {@link SysmonDaemon} instance.  Shutdown depends on all platform monitors to
	 * only start daemon threads.
	 * @see Thread#setDaemon(boolean)
	 */
	public void shutdown(){
		sysmon.stopPlatformSpecificMonitoring();
		shutdown.release(1);
	}
	
	/**
	 * Sets up log4j logging in the case where no external configuration has been specified.
	 * 
	 * This is only called when this class is invoked via {@link #main(String[])}.
	 * 
	 * It sets up a single {@link ConsoleAppender}, thresholded at {@link Level#WARN}, and adds
	 * it to the root logger.
	 */
	public static void configureDefaultLogging() {
		
		Logger rootLogger = LogManager.getRootLogger();
		@SuppressWarnings("unchecked")
		Enumeration<Appender> appenders = rootLogger.getAllAppenders();
		int appenderCount = 0;
		while(appenders.hasMoreElements()) {
			appenders.nextElement(); 
			appenderCount++;
		}
		
		if(appenderCount == 0) {
			PatternLayout consoleLayout = new PatternLayout("%d{ISO8601} [%p] %c{1} %m%n");
			ConsoleAppender ca = new ConsoleAppender(consoleLayout);
			ca.setThreshold(Level.WARN);
			rootLogger.addAppender(ca);
			LogManager.getLogger("com.palantir").setLevel(Level.WARN);
		}
	}
	
	/**
	 * System property used to specify the config file path.  Can be overridden by command line 
	 * argument.
	 * Property: {@value}
	 * @see System#getProperties()
	 */
	public static final String SYSTEM_PROPERTY_CONFIG_FILE = "sysmon.configPath";
	/**
	 * Runs the SysmonDaemon as a process.  Config file specified on command line takes precedence
	 * over {@link #SYSTEM_PROPERTY_CONFIG_FILE}.
	 * @param args takes single argument: the path to the config file.
	 */
	public static void main(String[] args) {
		
		configureDefaultLogging();
		
		// use passed configuration file
		final String configFileParam;
		if(args.length > 0){
			configFileParam = args[0];
		} else {
			configFileParam = System.getProperty("sysmon.configPath",null);
		}
		
		// start with empty config for defaults behavior
		final Properties config = new Properties();
		try {
			if(configFileParam != null){
				// load from path
				File configFile = new File(configFileParam);
				String configMsg = "Using config file: " + configFile.getAbsolutePath();
				System.out.println(configMsg);
				log.info(configMsg);
				config.load(new FileReader(configFile));
			} else {
				String configMsg = "No config file specified.  Using platform defaults.";
				System.out.println(configMsg);
				log.info(configMsg);
			}
			new SysmonDaemon(config);  // starts new thread
			System.out.println("Sysmon daemon started. Ctrl-C to quit.");
		} catch (IOException e) {
			String msg = "Error while loading config file: " + configFileParam;
			log.error(msg,e);
			System.err.println(msg);
			e.printStackTrace(System.err);
		} catch (SysmonException e) {
			String msg = "Error while starting daemon with config file: " + configFileParam;
			log.error(msg,e);
			System.err.println(msg);
			e.printStackTrace(System.err);
		}
		
	}
	
	/**
	 * Simple task for parallel shutdown of monitors.  Helpfully manipulates thread names to 
	 * show state of shutdown.
	 */
	public static final class ShutdownTask implements Runnable {
		
		private final Monitor service;

		public ShutdownTask(Monitor service) {
			this.service = service;
		}
		
		public void run() {
			try {
				Thread.currentThread().setName("Shutdown thread: " + service.getClass().getSimpleName());
				this.service.stopMonitoring();
			} catch (InterruptedException e) {
				log.warn("Shutdown interrupted",e);
			} finally {
				Thread.currentThread().setName(service.getClass().getSimpleName() + " Shutdown");
			}
		}
	}
}
