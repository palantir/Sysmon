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

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

import javax.management.JMException;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.palantir.opensource.sysmon.Monitor;
import com.palantir.opensource.sysmon.util.InterruptTimerTask;
import com.palantir.opensource.sysmon.util.JMXUtils;
import com.palantir.opensource.sysmon.util.PropertiesUtils;

/**
 * Monitors entropy pools that feed secure random number generation.
 * <p>
 * Entropy pool size is reported in bytes
 * and is read out of the /proc filesystem on a configurable period.
 * </p>
 * <h3>JMX Data Path</h3>
 * <code>sysmon.linux.beanpath:type=EntropyLevel</code>
 * <h3>Configuration parameters</h3>
 * <em>Note that any value not set in the config file will use the default value.</em>
 * <table cellspacing=5 cellpadding=5><tr><th>Config Key</th><th>Description</th><th>Default Value</th><th>Constant</th></tr>
 * <tr><td>sysmon.linux.entropyLevel.period</td>
 * <td>Period, in seconds, between checks of the entropy pool</td>
 * <td><code>10</code></td>
 * <td>{@link #CONFIG_KEY_ENTROPY_LEVEL_PERIOD}</td></tr>
 * </tr></table>
 *
 * @see Monitor Lifecycle documentation
 * @see <a href='http://linux.die.net/man/4/random'>the random(4) man page for more information on entropy pools</a>
 *
 */
public class LinuxEntropyLevelJMXWrapper extends Thread implements Monitor {

	static final Logger log = LogManager.getLogger(LinuxEntropyLevelJMXWrapper.class);

	static final String CONFIG_KEY_PREFIX = LinuxMonitor.CONFIG_KEY_PREFIX + ".entropyLevel";

	/**
	 * Set this value in the configuration file to set how often (in seconds) the entropy
	 * levels are checked.
	 * Key: {@value}
	 * @see #DEFAULT_ENTROPY_LEVEL_PERIOD for default value for the configuration parameter.
	 */
	public static final String CONFIG_KEY_ENTROPY_LEVEL_PERIOD = CONFIG_KEY_PREFIX + ".period";
	/**
	 * Default value for how often (in seconds) the entropy
	 * levels are checked.
	 * Default: {@value}
	 * @see #CONFIG_KEY_ENTROPY_LEVEL_PERIOD for instructions on overriding the default value.
	 */
	public static final int DEFAULT_ENTROPY_LEVEL_PERIOD = 10;
	/**
	 * Path to check in proc for entropy pool status.
	 * Path: {@value}
	 */
	static final File DATA_PATH = new File("/proc/sys/kernel/random/entropy_avail");
	/**
	 * Path where this bean publishes its values.
	 * Path: {@value}
	 */
	public static final String OBJECT_NAME = ":type=EntropyLevel";
	/**
	 * Start background thread to monitor entropy levels.
	 *
	 * @throws LinuxMonitoringException
	 */
	public void startMonitoring() {
		// Start monitoring on a separate thread.
		start();
	}
	/**
	 * Signals for the background thread to shutdown and waits for its execution to finish.
	 * @throws InterruptedException if the calling thread is interrupted while waiting for
	 * the background thread to {@link Thread#join()}.
	 */
	public void stopMonitoring() throws InterruptedException {
		this.shutdown = true;
		this.join();
	}

	/**
	 * MX Bean holding entropy pool size.  Defaults to 0 before any readings.
	 *
	 */
	final LinuxEntropyLevel bean = new LinuxEntropyLevel(0);

	/**
	 * How long to sleep between reads of {@link #DATA_PATH}.
	 */
	final int period;
	/**
	 * Cross-thread shutdown flag.
	 */
	volatile boolean shutdown = false;

	/**
	 * Path to place the entropy pool data.
	 */
	final String beanPath;

	/**
	 * Constructs a new {@link LinuxEntropyLevel} object.  You must call
	 * {@link #startMonitoring()} to start the background thread that will
	 * continually update the entropy pool values.
	 *
	 * Note that the constructor will do a single read of the entropy to verify that everything
	 * will work in the background thread.  Therefore, constructing one of these object is a valid
	 * way to take a one-time reading of the entropy pool size.
	 *
	 * @param config configuration for this JMX wrapper.  Passing null or an empty
	 * {@link Properties} object will just use the default config values.
	 * @throws LinuxMonitoringException on configuration or data read error.
	 *
	 */
	public LinuxEntropyLevelJMXWrapper(Properties config) throws LinuxMonitoringException {
		super(LinuxEntropyLevelJMXWrapper.class.getSimpleName());
		this.setDaemon(true);

		// use empty properties to avoid NPE and pick up defaults
		if(config == null) {
			config = new Properties();
		}

		try {
			String beanPathPrefix = config.getProperty(LinuxMonitor.CONFIG_KEY_JMX_BEAN_PATH,
			                                           LinuxMonitor.DEFAULT_JMX_BEAN_PATH);
			this.beanPath = beanPathPrefix + OBJECT_NAME;
			this.period = PropertiesUtils.extractInteger(config,
			                                             CONFIG_KEY_ENTROPY_LEVEL_PERIOD,
			                                             DEFAULT_ENTROPY_LEVEL_PERIOD);
		} catch (NumberFormatException e) {
			throw new LinuxMonitoringException("Invalid config parameter for " + CONFIG_KEY_ENTROPY_LEVEL_PERIOD, e);
		}

		// Check to make sure it will all work.
		if(!DATA_PATH.exists()) {
			throw new LinuxMonitoringException("No such path: " + DATA_PATH.getAbsolutePath() +
							". Can't read entropy level. (Is /proc mounted?)");
		}
		if(!DATA_PATH.canRead()) {
			throw new LinuxMonitoringException("Permission denied: " + DATA_PATH.getAbsolutePath());
		}


		// Tick once to detect any errors. Convert seconds to milliseconds.
		InterruptTimerTask timer = InterruptTimerTask.setInterruptTimer(4000L * period);
		try {
			// take a reading
			readData();
			JMXUtils.registerMBean(bean, beanPath);
		} catch(JMException e){
			final String msg = "Error while registering bean to path " + beanPath;
			throw new LinuxMonitoringException(msg,e);
		}
		finally {
			timer.cancel();
		}
	}

	@Override
	public void run() {
		try {
			do {
				readData();
				// Convert seconds to milliseconds.
				Thread.sleep(1000L * this.period);
			} while(!shutdown);
		} catch (Exception e) {
			log.error("Shutting down entropy level monitoring due to error.",e);
		}
	}

	/**
	 * Reads the entropy pool out of the /proc file system and publishes value to a JMX MXBean.
	 *
	 * @throws LinuxMonitoringException on error with reading value.
	 */
	void readData() throws LinuxMonitoringException {
		ByteArrayOutputStream baos = null;
		InputStream data = null;
		BufferedReader lines = null;

		try {
			data = new FileInputStream(DATA_PATH);
			baos = new ByteArrayOutputStream();
			IOUtils.copy(data, baos);
			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			InputStreamReader inputStreamReader = new InputStreamReader(bais);
			lines = new BufferedReader(inputStreamReader);

			Integer entropyLevel = null;
			String entropyValue = lines.readLine();
			try {
				entropyLevel = Integer.parseInt(entropyValue);
			} catch (NumberFormatException e) {
				log.warn("Error parsing value: " + entropyValue, e);
			}
			bean.updateValue(entropyLevel);
		} catch (IOException e) {
			throw new LinuxMonitoringException("Unexpected IOException during processing.",e);
		} finally {
			IOUtils.closeQuietly(data);
			IOUtils.closeQuietly(baos);
			IOUtils.closeQuietly(lines);
		}
	}
}
