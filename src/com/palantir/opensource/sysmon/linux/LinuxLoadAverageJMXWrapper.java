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

import java.io.*;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.JMException;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.palantir.opensource.sysmon.Monitor;
import com.palantir.opensource.sysmon.SysmonException;
import com.palantir.opensource.sysmon.util.InterruptTimerTask;
import com.palantir.opensource.sysmon.util.JMXUtils;
import com.palantir.opensource.sysmon.util.PropertiesUtils;


/**
 * <p>
 * Monitors load average.  Requires the <code>uptime</code> utility.
 * </p><p>
 * Reads the output from 'uptime' that looks like this:
 *
 * <pre>
 *  13:22:52 up 18 days, 20:51,  3 users,  load average: 0.31, 0.14, 0.10
 * </pre>
 * </p> 
 * <p>
 * Currently, the uptime data from <code>uptime</code> is not processed.
 * </p>
 * 
 * <h3>JMX Data Path</h3>
 * <code>sysmon.linux.beanpath:type=LoadAverage</code>
 *  
 * <h3>Configuration parameters</h3>
 * <em>Note that any value not set in the config file will use the default value.</em>
 * <table cellspacing=5 cellpadding=5><tr><th>Config Key</th><th>Description</th><th>Default Value</th><th>Constant</th></tr>
 * <tr><td>sysmon.linux.uptime.path</td>
 * <td>path to <code>uptime</code> binary</td>
 * <td><code>uptime</code></td>
 * <td>{@link #CONFIG_KEY_UPTIME_PATH}</td></tr>
 * <tr><td>sysmon.linux.uptime.period</td>
 * <td>period, in seconds, between load average checks</td>
 * <td><code>10</code></td>
 * <td>{@link #CONFIG_KEY_UPTIME_PERIOD}</td></tr>
 * </tr></table>
 * 
 * @see Monitor Lifecycle documentation
 *
 */
public class LinuxLoadAverageJMXWrapper extends Thread implements Monitor {

	static final Logger log = LogManager.getLogger(LinuxLoadAverageJMXWrapper.class);

	static final String CONFIG_KEY_PREFIX = LinuxMonitor.CONFIG_KEY_PREFIX + ".uptime";

	/**
	 * Config key for the path to uptime.
	 * Key: {@value}
	 */
	public static final String CONFIG_KEY_UPTIME_PATH = CONFIG_KEY_PREFIX + ".path";
	/**
	 * Config key for the period, in seconds, to pass to uptime.
	 * Key: {@value}
	 */
	public static final String CONFIG_KEY_UPTIME_PERIOD = CONFIG_KEY_PREFIX + ".period";
	/**
	 * Default path to find uptime.  Defaults to 'uptime', so that the shell can figure it via its $PATH.
	 * Default: {@value}
	 * @see #CONFIG_KEY_UPTIME_PATH for config key used to override the default path
	 */
	public static final String DEFAULT_UPTIME_PATH = "uptime"; // let the shell figure it out
	/**
	 * Default period, in seconds.
	 * Default: {@value}
	 * @see #CONFIG_KEY_UPTIME_PERIOD for config key used to override the default period
	 */
	public static final Integer DEFAULT_UPTIME_PERIOD = 10;
	/**
	 * {@link Pattern} for parsing uptime data.
	 */
	public static final Pattern UPTIME_DATA = Pattern.compile("^.*\\s+load\\s+average:\\s+([\\d\\.]+),\\s+([\\d\\.]+),\\s+([\\d\\.]+)$");

	public static final String OBJECT_NAME = ":type=LoadAverage";

	long freshnessTimestamp = System.currentTimeMillis();
	final String uptimeCmd[];
	final int periodMillis;
	final String uptimePath;
	volatile boolean shutdown = false;
	final String beanPath;

	LinuxLoadAverage bean = null;

	public LinuxLoadAverageJMXWrapper(Properties config) throws LinuxMonitoringException {
		// initialize thread
		super(LinuxEntropyLevelJMXWrapper.class.getSimpleName());
		this.setDaemon(true);

		// configure
		beanPath = config.getProperty(LinuxMonitor.CONFIG_KEY_JMX_BEAN_PATH, 
		                              LinuxMonitor.DEFAULT_JMX_BEAN_PATH) + OBJECT_NAME;
		this.uptimePath = config.getProperty(CONFIG_KEY_UPTIME_PATH, DEFAULT_UPTIME_PATH);
		try {
			this.periodMillis = PropertiesUtils.extractInteger(config,
			                                                   CONFIG_KEY_UPTIME_PERIOD, 
			                                                   DEFAULT_UPTIME_PERIOD) * 1000;
		} catch (NumberFormatException e) {
			throw new LinuxMonitoringException("Invalid config parameter for " + 
							CONFIG_KEY_UPTIME_PERIOD,e);
		}
		// build command line
		uptimeCmd = new String[1];
		uptimeCmd[0] = uptimePath;

		// read once to throw config exceptions on calling thread
		InterruptTimerTask timer = InterruptTimerTask.setInterruptTimer(4 * periodMillis);
		try {
			readData();
		}
		finally {
			timer.cancel();	
		}			
	}

	public void startMonitoring() throws SysmonException {
		start();
	}
	
	public void stopMonitoring() throws InterruptedException {
		shutdown = true;
		this.join(periodMillis * 4);
		if(this.isAlive()) {
			log.error("Background thread failed to shutdown in a reasonable amount of time");
		}
	}
	
	@Override
	public void run() {
		try {
			do {
				readData();
				Thread.sleep(this.periodMillis);
			} while(!shutdown);
		} catch (Exception e) {
			log.error("Shutting down load average monitoring due to error.",e);
		}
	}


	void readData() throws LinuxMonitoringException {
		Process process = null;
		BufferedReader stdout = null;
		InputStream stderr = null;
		OutputStream stdin = null;

		try {
			// start process
			process = Runtime.getRuntime().exec(uptimeCmd); // (authorized)
			// initialize stream
			stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
			stderr = process.getErrorStream();
			stdin = process.getOutputStream();

			// pull what should be the header line
			String line = stdout.readLine();
			if(line == null) {
				throw new LinuxMonitoringException("No data read from uptime process!");
			}

			processUptimeLine(line);
		} catch (IOException e) {
			throw new LinuxMonitoringException("Error while reading data from uptime process",e);
		} finally {
			IOUtils.closeQuietly(stdout);
			IOUtils.closeQuietly(stderr);
			IOUtils.closeQuietly(stdin);
			if (process != null) {
				process.destroy();
			}
		}

	}

	void processUptimeLine(final String line) throws LinuxMonitoringException {
		Matcher m = UPTIME_DATA.matcher(line);
		if(m.matches()) {
			final Double oneMinuteLoadAvg = parseDouble(m.group(1));
			final Double tenMinuteloadAvg = parseDouble(m.group(2));
			final Double fifteenMinuteLoadAvg = parseDouble(m.group(3));
			if (bean == null) {
				bean = new LinuxLoadAverage(oneMinuteLoadAvg, tenMinuteloadAvg, fifteenMinuteLoadAvg);
				try {
					JMXUtils.registerMBean(bean, beanPath);
				} catch (JMException e) {
					throw new LinuxMonitoringException("Error while registering MX Bean at path " + 
													   beanPath,e);
				}
			} else {
				bean.updateValues(oneMinuteLoadAvg, tenMinuteloadAvg, fifteenMinuteLoadAvg);
			}
		} else {
			String msg = "Data line did not match: " +line + ". Pattern: " + UPTIME_DATA.pattern();
			log.warn(msg);
		}
	}

	static Double parseDouble(String doubleValue) {
		try {
			return Double.parseDouble(doubleValue);
		} catch(NumberFormatException e) {
			log.warn("Error parsing value: " + doubleValue, e);
		}
		return null;
	}
}
