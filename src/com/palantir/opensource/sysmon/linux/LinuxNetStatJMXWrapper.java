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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.JMException;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.palantir.opensource.sysmon.Monitor;
import com.palantir.opensource.sysmon.util.InterruptTimerTask;
import com.palantir.opensource.sysmon.util.JMXUtils;
import com.palantir.opensource.sysmon.util.PropertiesUtils;

/**
 * <p>Monitors network interface statistics.</p>
 * <p>
 * This class reads information from<code>/proc/net/dev</code> to publish statistics via
 * per-interface beans.
 * </p>
 * <h3>JMX Data Path</h3>
 * Each device will be at:<br/>
 * <code>sysmon.linux.beanpath:type=net-device,devicename=&lt;devicename&gt;</code>
 *
 * <h3>Configuration parameters</h3>
 * <em>Note that any value not set in the config file will use the default value.</em>
 * <table cellspacing=5 cellpadding=5><tr><th>Config Key</th><th>Description</th><th>Default Value</th><th>Constant</th></tr>
 * <tr><td>sysmon.linux.netstat.periodMillis</td>
 * <td>period, in milliseconds, between interface statistics checks</td>
 * <td><code>2000</code></td>
 * <td>{@link #CONFIG_KEY_NETSTAT_PERIOD}</td></tr>
 * </tr></table>
 * @see Monitor Lifecycle documentation
 * @see <a href='http://linux.die.net/man/5/proc'>proc(5)</a> for information on <code>/proc/net/dev</code>
 */
public class LinuxNetStatJMXWrapper extends Thread implements Monitor {

	static final Logger log = LogManager.getLogger(LinuxNetStatJMXWrapper.class);

	/**
	 * Prefix used for config options for this class in config files.
	 * Prefix: {@value}
	 */
	static final String CONFIG_KEY_PREFIX = LinuxMonitor.CONFIG_KEY_PREFIX + ".netstat";
	/**
	 * Configuration parameter that controls the time between checks for this monitor.
	 * Config key: {@value}
	 * @see #DEFAULT_NETSTAT_PERIOD default value
	 * 
	 */
	public static final String CONFIG_KEY_NETSTAT_PERIOD = CONFIG_KEY_PREFIX + ".periodMillis";
	/**
	 * Regex string to match the first header line in the /proc/net/dev files
	 */
	public static final String FIRST_LINE_RE = "^\\s*Inter-\\|\\s*Receive\\s*\\|\\s*Transmit\\s*$";
	/**
	 * Compiled {@link Pattern} to match the first header line in the /proc/net/dev files
	 */
	public static final Pattern FIRST_LINE = Pattern.compile(FIRST_LINE_RE);
	/**
	 * Regex string to match the second header line in the /proc/net/dev files
	 */
	public static final String SECOND_LINE_RE = 
		"\\s*face\\s*\\|\\s*bytes\\s*packets\\s*errs\\s*drop\\s*fifo\\s*frame\\s*compressed\\s*" +
		"multicast\\s*\\|\\s*bytes\\s*packets\\s*errs\\s*drop\\s*fifo\\s*colls\\s*carrier\\s*" +
		"compressed\\s*$";
	/**
	 * Compiled {@link Pattern} to match the second header line in the /proc/net/dev files
	 */
	public static final Pattern SECOND_LINE = Pattern.compile(SECOND_LINE_RE);

	/**
	 * Default value for how often to update values, in milliseconds.
	 * Default: {@value}
	 * @see #CONFIG_KEY_NETSTAT_PERIOD for config key to override default value.
	 */
	public static final long DEFAULT_NETSTAT_PERIOD = 2000;

	public static final String OBJECT_NAME_PREFIX =  ":type=net-device,devicename=";
	static final Pattern DATA_PAT;

	// build up the 16 field regex programmatically.
	static {
		int numFields = 16;
		StringBuilder regex = new StringBuilder("^\\s*(\\S+):\\s*");
		for(int i = 0; i < numFields; i++) {
			regex.append("(\\d+)");
			if(i < numFields - 1) {
				regex.append("\\s+");
			}
			else {
				// make whitespace optional at the end
				regex.append("\\s*");
			}
		}
		regex.append("$");
		DATA_PAT = Pattern.compile(regex.toString());
	}


	static final File DATA_PATH = new File("/proc/net/dev");

	/**
	 * How long to sleep between reads of {@link #DATA_PATH}.
	 */
	final long period;
	final String beanPrefix;
	volatile boolean shutdown = false;
	private long freshnessTimestamp = System.currentTimeMillis();

	final Map<String, LinuxNetworkInterface> interfaces = new HashMap<String, LinuxNetworkInterface>();


	/**
	 * Constructs a new monitor.  Checks config and throws errors if there are problems.
	 * Does not start the background thread.  
	 * @param config
	 * @throws LinuxMonitoringException
	 */
	public LinuxNetStatJMXWrapper(Properties config) throws LinuxMonitoringException {
		super(LinuxNetStatJMXWrapper.class.getSimpleName());
		this.setDaemon(true);
		if(config == null) {
			config = new Properties();
		}

		final String beanPath = config.getProperty(LinuxMonitor.CONFIG_KEY_JMX_BEAN_PATH, 
		                                           LinuxMonitor.DEFAULT_JMX_BEAN_PATH);
		
		this.beanPrefix = beanPath + OBJECT_NAME_PREFIX;
		
		this.period = PropertiesUtils.extractLong(config,CONFIG_KEY_NETSTAT_PERIOD, 
		                                          DEFAULT_NETSTAT_PERIOD);

		// check to make sure it will all work
		if(!DATA_PATH.exists()) {
			throw new LinuxMonitoringException("No such path: " + DATA_PATH.getAbsolutePath() +
							". Can't read network statistics. (Is /proc mounted?)");
		}
		if(!DATA_PATH.canRead()) {
			throw new LinuxMonitoringException("Permission denied: " + DATA_PATH.getAbsolutePath());
		}

		// tick once to detect any errors
		InterruptTimerTask timer = InterruptTimerTask.setInterruptTimer(4 * period);
		try {
			readData();
		}
		finally {
			timer.cancel();	
		}
	}

	/**
	 * Starts the background thread that does the work of this monitor.
	 */
	public void startMonitoring() {
		start();
	}

	/**
	 * Shuts down the background thread that does the work of this monitor.
	 * @throws InterruptedException if interrupted while waiting for 
	 * background thread to exit.
	 */
	public void stopMonitoring() throws InterruptedException {
		shutdown = true;
		this.join(this.period * 4);
		if(this.isAlive()){
			log.error(this.getName() + " did not die after four periods.");
		}
	}
	
	@Override
	public void run() {
		try {
			do {
				readData();
				Thread.sleep(this.period);
			} while(!shutdown);
		} catch (Exception e) {
			log.error("Shutting down network monitoring due to error.",e);
		}
	}

	void readData() throws LinuxMonitoringException {
		ByteArrayOutputStream baos = null;
		InputStream data = null;
		BufferedReader lines = null;

		try {
			data= new FileInputStream(DATA_PATH);
			baos = new ByteArrayOutputStream();
			IOUtils.copy(data, baos);
			ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
			InputStreamReader inputStreamReader = new InputStreamReader(bais);
			lines = new BufferedReader(inputStreamReader);

			String line = lines.readLine();
			if(line != null) {
				do {
					processLine(line);
					line = lines.readLine();
				} while(line != null);
			}
		} catch (IOException e) {
			throw new LinuxMonitoringException("Unexpected IOException during processing.",e);
		} finally {
			IOUtils.closeQuietly(data);
			IOUtils.closeQuietly(baos);
			IOUtils.closeQuietly(lines);
		}

	}

	void processLine(final String line) throws LinuxMonitoringException {
		Matcher m = null;
		m = FIRST_LINE.matcher(line);
		if(m.matches()) {
			if(log.isDebugEnabled())
				log.debug("Found first header line: " + line);
			removeStaleBeans();
		} else {
			m = SECOND_LINE.matcher(line);
			if(m.matches()) {
				if(log.isDebugEnabled())
					log.debug("Found second header line: " + line);
				// ignore.
			} else {
				m = DATA_PAT.matcher(line);
				if(m.matches()) {
					// data!!!
					if(log.isDebugEnabled())
						log.debug("Processing data line: " + line);
					processData(m);
				} else {
					log.warn("Line did not match: \n\t" + line);
				}
			}
		}
	}

	void processData(Matcher m) throws LinuxMonitoringException {
		final String interfaceName = m.group(1);
		String objectName = beanPrefix + interfaceName;
		LinuxNetworkInterface bean = new LinuxNetworkInterface(objectName);
		bean.interfaceName = interfaceName;
		bean.bytesReceived = Long.parseLong(m.group(2));
		bean.packetsReceived = Long.parseLong(m.group(3));
		bean.recieveErrors = Long.parseLong(m.group(4));
		bean.droppedReceivedPackets = Long.parseLong(m.group(5));
		bean.receiveFIFOErrors = Long.parseLong(m.group(6));
		bean.receiveFrameErrors = Long.parseLong(m.group(7));
		bean.compressedPacketsReceived = Long.parseLong(m.group(8));
		bean.multicastFramesReceived = Long.parseLong(m.group(9));

		bean.bytesSent = Long.parseLong(m.group(10));
		bean.packetsSent = Long.parseLong(m.group(11));
		bean.sendErrors = Long.parseLong(m.group(12));
		bean.droppedSentPackets = Long.parseLong(m.group(13));
		bean.sentFIFOErrors = Long.parseLong(m.group(14));
		bean.collisions = Long.parseLong(m.group(15));
		bean.carrierDrops = Long.parseLong(m.group(16));
		bean.compressedPacketsTransmitted = Long.parseLong(m.group(17));

		updateBean(bean);
	}

	void updateBean(LinuxNetworkInterface iface) throws LinuxMonitoringException {
		LinuxNetworkInterface jmxBean = interfaces.get(iface.objectName);
		if(jmxBean == null) { // new device
			try {
				JMXUtils.registerMBean(iface, iface.objectName);
				interfaces.put(iface.objectName, iface);
			} catch (JMException e) {
				throw new LinuxMonitoringException("Error while registering MXBean " + 
												   iface.objectName,e);
			}
		} else {
			jmxBean.compute(iface);
			jmxBean.takeValues(iface);
			log.debug(jmxBean.toString());
		}
	}

	private void removeStaleBeans() {
		Iterator<LinuxNetworkInterface> it = interfaces.values().iterator();
		while(it.hasNext()) {
			LinuxNetworkInterface entry = it.next();
			if(entry.lastUpdated < freshnessTimestamp) {
				it.remove();
				log.info(entry + " is now considered stale (device removed?)");
				removeBean(entry);
			} 	
		}
		freshnessTimestamp = System.currentTimeMillis();
	}

	private void removeBean(LinuxNetworkInterface bean) {
		log.info("Removing " + bean + " from MBean server");
		JMXUtils.unregisterMBeanCatchAndLogExceptions(bean.objectName);
	}

	public void cleanup() {
		this.shutdown = true;
	}
}


