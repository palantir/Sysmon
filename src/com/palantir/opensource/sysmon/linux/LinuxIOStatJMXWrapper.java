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
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
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
 * <p>Monitors I/O statistics as reported by <a href='http://linux.die.net/man/1/iostat'>iostat</a></p>
 * <p>
 * This class that fires up <a href='http://linux.die.net/man/1/iostat'>iostat</a>
 * in a background process, reads its output, and publishes it via JMX MBeans.
 * </p>
 * 
 * <h3>JMX Data Path</h3>
 * Each device will be placed at: 
 * <code>sysmon.linux.beanpath:type=io-device,devicename=&lt;devicename&gt;</code> 
 *  
 * <h3>Configuration parameters</h3>
 * <em>Note that any value not set in the config file will use the default value.</em>
 * <table cellspacing=5 cellpadding=5><tr><th>Config Key</th><th>Description</th><th>Default Value</th><th>Constant</th></tr>
 * <tr><td>sysmon.linux.iostat.path</td>
 * <td>path to <code>iostat</code> binary</td>
 * <td><code>iostat</code></td>
 * <td>{@link #CONFIG_KEY_IOSTAT_PATH}</td></tr>
 * <tr><td>sysmon.linux.iostat.opts</td>
 * <td>options passed to iostat</td>
 * <td><code>-d -x -k</code></td>
 * <td>{@link #CONFIG_KEY_IOSTAT_OPTIONS}</td></tr>
 * <tr><td>sysmon.linux.iostat.period</td>
 * <td>period, in seconds, between iostat reports</td>
 * <td><code>60</code></td>
 * <td>{@link #CONFIG_KEY_IOSTAT_PERIOD}</td></tr>
 * </tr></table>
 * @see Monitor Lifecycle documentation
 * @see <a href='http://linux.die.net/man/1/iostat'>iostat(1)</a> for more information on <code>iostat</code>.
 */
public class LinuxIOStatJMXWrapper extends Thread implements Monitor {

	static final Logger log = LogManager.getLogger(LinuxIOStatJMXWrapper.class);

	static final String CONFIG_KEY_PREFIX = LinuxMonitor.CONFIG_KEY_PREFIX + ".iostat";
	
	/**
	 * Path to iostat executable. Defaults to "iostat" (uses $PATH to find executable).
	 * Set this config value in the to override where to find iostat.
	 * 
	 * Config key: {@value}
	 * @see LinuxIOStatJMXWrapper#DEFAULT_IOSTAT_PATH default value for this config parameter
	 * @see <a href='http://linux.die.net/man/1/iostat'>iostat(1) on your local linux box</a>
	 */
	public static final String CONFIG_KEY_IOSTAT_PATH = CONFIG_KEY_PREFIX + ".path";

	/**
	 * <p>
	 * Options passed to <code>iostat</code> (other than period argument).  
	 * </p>
	 * <p>
	 * Note that passing config values that
	 * change the format of the output from <code>iostat</code> may break this monitor.  Proceed
	 * with caution.
	 * </p><p>
	 * Set this key in the config file to override default values.
	 * </p>
	 * Config key: {@value}
	 * @see LinuxIOStatJMXWrapper#DEFAULT_IOSTAT_OPTIONS default value for this config parameter
	 * @see <a href='http://linux.die.net/man/1/iostat'>iostat(1) on your local linux box</a>
	 */	
	public static final String CONFIG_KEY_IOSTAT_OPTIONS = CONFIG_KEY_PREFIX + ".opts";

	/**
	 * Period for iostat. Set this config value to override how often iostat is outputting values.
	 * 
	 * Config key: {@value}
	 * @see LinuxIOStatJMXWrapper#DEFAULT_IOSTAT_PERIOD default value for this config parameter
	 * @see <a href='http://linux.die.net/man/1/iostat'>iostat(1) on your local linux box</a>
	 */	
	public static final String CONFIG_KEY_IOSTAT_PERIOD = CONFIG_KEY_PREFIX + ".period";

	/**
	 * Default path to iostat executable. Defaults to "iostat" (uses $PATH to find executable).
	 * 
	 * Config key: {@value}
	 * @see LinuxIOStatJMXWrapper#CONFIG_KEY_IOSTAT_PATH instructions on overriding this value.
	 * @see <a href='http://linux.die.net/man/1/iostat'>iostat(1) on your local linux box</a>
	 */
	public static final String DEFAULT_IOSTAT_PATH = "iostat"; // let the shell figure it out

	/**
	 * Default options passed to iostat executable. 
	 * 
	 * Config key: {@value}
	 * @see LinuxIOStatJMXWrapper#CONFIG_KEY_IOSTAT_OPTIONS instructions on overriding this value.
	 * @see <a href='http://linux.die.net/man/1/iostat'>iostat(1) on your local linux box</a>
	 */
	public static final String DEFAULT_IOSTAT_OPTIONS = "-d -x -k";

	/**
	 * Default period between iostat output (in seconds). 
	 * 
	 * Config key: {@value}
	 * @see LinuxIOStatJMXWrapper#CONFIG_KEY_IOSTAT_PERIOD Instructions on overriding this value.
	 * @see <a href='http://linux.die.net/man/1/iostat'>iostat(1) on your local linux box</a>
	 */
	public static final Integer DEFAULT_IOSTAT_PERIOD = Integer.valueOf(60);

	/**
	 * Relative JMX data path where this monitor publishes its data.  This will have the 
	 * individual device name appended to the end in the JMX tree.
	 * Path: {@value}
	 */
	public static final String OBJECT_NAME_PREFIX = ":type=io-device,devicename=" ;


	static final String FIRST_LINE_PREFIX = "Linux 2.6";

	/**
	 * iostat likes to sometimes break things across two lines.  This detects that situation.
	 * Pattern: {@value}
	 */
	static final Pattern DEVICE_ONLY = Pattern.compile("^\\s*\\S+\\s*$");
	/**
	 * regex to match version 7.x of iostat.
	 * {@value}
	 */
	static final String HEADER_V7_RE = 
		"^\\s*Device:\\s+rrqm/s\\s+wrqm/s\\s+r/s\\s+w/s\\s+rkB/s\\s+wkB/s\\s+avgrq-sz\\s+" + 
		"avgqu-sz\\s+await\\s+svctm\\s+%util\\s*$";
	/**
	 * regex to match version 5.x of iostat.
	 * Pattern: {@value}
	 */
	static final String HEADER_V5_RE = 
		"^\\s*Device:\\s+rrqm/s\\s+wrqm/s\\s+r/s\\s+w/s\\s+rsec/s\\s+wsec/s\\s+rkB/s\\s+" + 
		"wkB/s\\s+avgrq-sz\\s+avgqu-sz\\s+await\\s+svctm\\s+%util\\s*$";
	/**
	 * {@link Pattern} to match version 7.x of iostat header output.
	 * Pattern: {@value}
	 */
	static final Pattern HEADER_V7_PAT = Pattern.compile(HEADER_V7_RE);
	/**
	 * {@link Pattern} to match version 5.x of iostat header output.
	 * Pattern: {@value}
	 */
	static final Pattern HEADER_V5_PAT = Pattern.compile(HEADER_V5_RE);
	/**
	 * {@link Pattern} to match version 7.x of iostat data output.
	 * Pattern: {@value}
	 */
	static final Pattern DATA_V7_PAT = buildWhitespaceDelimitedRegex(12);

	/**
	 * Pattern for version 5 of iostat.  It has three additional fields that we ignore in our 
	 * parsing. Because of that, we don't build it programmatically, but use this specially 
	 * rolled regex.  The upshot is that it's group index compatible with the version 7 regex.
	 */
	static final String DATA_V5_RE = "^\\s*(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)" +
									   "\\s+\\S+\\s+\\S+\\s+" + // skipped fields
									   "(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)" +
									   "\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s*$";
	/**
	 * {@link Pattern} to match version 5.x of iostat data output.
	 * Pattern: {@value}
	 */
	static final Pattern DATA_V5_PAT = Pattern.compile(DATA_V5_RE);
	
	long freshnessTimestamp = System.currentTimeMillis();
	final String iostatCmd[];
	final int period;
	final String iostatPath;
	final String beanPath;
	volatile boolean shutdown = false;
	Process iostat = null;
	BufferedReader iostatStdout = null;
	InputStream iostatStderr = null;
	OutputStream iostatStdin = null;
	Pattern dataPattern = null;
	Pattern headerPattern = null;

	final Map<String,LinuxIOStat> beans = new HashMap<String, LinuxIOStat>();

	/**
	 * Constructs a new iostat JMX wrapper.  Does not start monitoring.  Call 
	 * {@link #startMonitoring()} to start monitoring and publishing
	 * JMX data.
	 *  
	 * @param config configuration for this service
	 * @see #CONFIG_KEY_IOSTAT_OPTIONS
	 * @see #CONFIG_KEY_IOSTAT_PATH
	 * @see #CONFIG_KEY_IOSTAT_PERIOD
	 * @throws LinuxMonitoringException upon error in setting up this service.
	 */
	public LinuxIOStatJMXWrapper(Properties config) throws LinuxMonitoringException {
		super(LinuxIOStatJMXWrapper.class.getSimpleName());
		this.setDaemon(true);

		if(config == null) {
			// blank one to get all the defaults
			config = new Properties();
		}

		try {
			final String beanPathPrefix = config.getProperty(LinuxMonitor.CONFIG_KEY_JMX_BEAN_PATH, 
			                                                 LinuxMonitor.DEFAULT_JMX_BEAN_PATH);
			beanPath = beanPathPrefix + OBJECT_NAME_PREFIX;
			
			iostatPath = config.getProperty(CONFIG_KEY_IOSTAT_PATH,DEFAULT_IOSTAT_PATH);
			period = PropertiesUtils.extractInteger(config,
			                                        CONFIG_KEY_IOSTAT_PERIOD, 
			                                        DEFAULT_IOSTAT_PERIOD);
			String iostatOpts = config.getProperty(CONFIG_KEY_IOSTAT_OPTIONS,
			                                       DEFAULT_IOSTAT_OPTIONS);
			String cmd = iostatPath + " " + iostatOpts + " " + period;
			this.iostatCmd = (cmd).split("\\s+");
			log.info("iostat cmd: " + cmd);

		} catch (NumberFormatException e) {
			throw new LinuxMonitoringException("Invalid config Parameter for " + 
			                                   CONFIG_KEY_IOSTAT_PERIOD,e);
		}
	}

	/**
	 * Start iostat as a background process and makes sure header output 
	 * parses correctly.  If no errors are encountered, starts this instance's Thread
	 * to read the data from the iotstat process in the background.
	 * 
	 * @throws LinuxMonitoringException upon error with iostat startup. 
	 */
	public void startMonitoring() throws LinuxMonitoringException {
		if(shutdown){
			throw new LinuxMonitoringException("Do not reuse " + getClass().getSimpleName() + " objects");
		}
		try {
			// check that we can start iostat in the background
			startIOStat();
			// jump off into thread land
			start();
		} catch (LinuxMonitoringException e) {
			cleanup();
			throw e;
		}
	}
			
	/**
	 * Fires up iostat in the background and verifies that the header data parses 
	 * as expected.
	 * 
	 * @throws LinuxMonitoringException upon error starting iostat or parsing output.
	 */
	private void startIOStat() throws LinuxMonitoringException {
		// Convert seconds to milliseconds for setInterruptTimer.
		InterruptTimerTask timer = InterruptTimerTask.setInterruptTimer(1000L * period);
		try {

			iostat = Runtime.getRuntime().exec(iostatCmd);
			iostatStdout = new BufferedReader(new InputStreamReader(iostat.getInputStream()));
			iostatStderr = iostat.getErrorStream();
			iostatStdin = iostat.getOutputStream();

			// first line is discarded 
			String firstLine = iostatStdout.readLine();
			if(firstLine == null) {
				throw new LinuxMonitoringException("Unexpected end of input from iostat: " + 
				"null first line");
			}
			if(!firstLine.trim().startsWith(FIRST_LINE_PREFIX)) {
				log.warn("iostat returned unexpected first line: " + firstLine + 
				         ". Expected something that started with: " + FIRST_LINE_PREFIX);
			}

			String secondLine = iostatStdout.readLine();
			if(secondLine == null) {
				throw new LinuxMonitoringException("Unexpected end of input from iostat: " + 
				"null second line");
			}
			if(!(secondLine.trim().length() == 0)) {
				throw new LinuxMonitoringException("Missing blank second line.  Found this instead: " + 
				                                   secondLine);
			}
			// make sure we're getting the fields we expect
			String headerLine = iostatStdout.readLine();
			if(headerLine == null) {
				throw new LinuxMonitoringException("Unexpected end of input from iostat: " + 
				"null header line");
			}

			if(HEADER_V5_PAT.matcher(headerLine).matches()){
				log.info("Detected iostats version 5.");
				headerPattern = HEADER_V5_PAT;
				dataPattern = DATA_V5_PAT;
			} else if(HEADER_V7_PAT.matcher(headerLine).matches()) {
				log.info("Detected iostats version 7.");
				headerPattern = HEADER_V7_PAT;
				dataPattern = DATA_V7_PAT;
			} else {
				final String msg = "Header line does match expected header! Expected: " + 
				HEADER_V7_PAT.pattern() + "\nGot: " + headerLine + "\n";
				throw new LinuxMonitoringException(msg);
			}

			// ready to read data

		} catch (Exception e) {
			cleanup();
			if(e.getMessage().matches("^.*iostat: not found.*$")) {
				final String errorMsg;
				// first case - absolute path
				if(!iostatPath.equals(DEFAULT_IOSTAT_PATH)) {
					errorMsg = "iostat not found at specified path: " + iostatPath +
					". Perhaps the sysstat package needs to be installed?";
				} else {
					errorMsg = "iostat not found in the executable $PATH for this process." +
					" Perhaps the sysstat package needs to be installed?" + 
					" (Try 'yum install sysstat' as root.)";
				}
				throw new LinuxMonitoringException(errorMsg);
			}
			throw new LinuxMonitoringException("Error initializing iostat",e);
		} finally {
			timer.cancel();
		}

	}

	/**
	 * Shuts down and cleans up both background iostat process and data reading thread.
	 * 
	 * @throws InterruptedException
	 */
	public void stopMonitoring() throws InterruptedException {
		try {
			this.shutdown = true;
			cleanup();
			this.join();
		} finally {
			cleanup();
		}
	}

	@Override
	public void run() {
		boolean wasInterrupted = Thread.interrupted();
		try {
			do {
				String line = null;
				try{
					if(iostatStdout.ready()){
						line = iostatStdout.readLine();
						if(DEVICE_ONLY.matcher(line).matches()) {
							// we have broken lines, put them together
							String remainder = iostatStdout.readLine();
							if(log.isDebugEnabled()) {
								log.debug("Joining '" + line + "' and '" + remainder + "'.");
							}
							line = line + remainder;
						}
					}
				} catch(Exception e) {
					line = null;
					if(!shutdown){
						log.warn("Caught exception while reading line.",e);
					} else {
						log.debug("Exception caused by shutdown",e);
					}
				}
				if(line != null) {
					try {
						processLine(line);
						continue;
					} catch (LinuxMonitoringException e) {
						log.error(e,e);
					}
				}

				try {
					Thread.sleep(1000L);
				} catch (InterruptedException e) {
					wasInterrupted = true;
				}
			} while(!shutdown);
		} catch(Exception e){
			if(!shutdown){
				log.error("Caught unexpected Exception",e);
			} else {
				log.debug("Shutdown caused exception",e);
			}
		}
		finally {
			if (wasInterrupted) {
				Thread.currentThread().interrupt();
			}
			cleanup();
		}
	}

	private void checkFreshness() {
		Iterator<LinuxIOStat> it = beans.values().iterator();
		while(it.hasNext()) {
			LinuxIOStat entry = it.next();
			if(entry.timestamp < freshnessTimestamp) {
				it.remove();
				log.info(entry + " is now considered stale (device removed?)");
				removeBean(entry);
			} 	
		}
		freshnessTimestamp = System.currentTimeMillis();
	}

	private void removeBean(LinuxIOStat bean) {
		log.info("Removing " + bean + " from MBean server");
		JMXUtils.unregisterMBeanCatchAndLogExceptions(bean.objectName);
	}

	private void processLine(String line) throws LinuxMonitoringException {
		Matcher m = null;

		// header line
		m = headerPattern.matcher(line);
		if(m.matches()) {
			log.debug("Processing header line");
			// Data line
			checkFreshness();
			return;
		}

		// data line
		m = dataPattern.matcher(line);
		if(m.matches()) {
			log.debug("Processing data line: " + line);
			String objectName = m.group(1);
			LinuxIOStat dataRow = new LinuxIOStat(beanPath + objectName);
			dataRow.timestamp = System.currentTimeMillis();
			dataRow.device = m.group(1);
			dataRow.samplePeriodInSeconds = period;
			dataRow.mergedReadRequestsPerSecond = parseFloat(m.group(2)); 
			dataRow.mergedWriteRequestsPerSecond = parseFloat(m.group(3));
			dataRow.readRequestsPerSecond = parseFloat(m.group(4)); 
			dataRow.writeRequestsPerSecond = parseFloat(m.group(5));
			dataRow.kilobytesReadPerSecond = parseFloat(m.group(6));
			dataRow.kilobytesWrittenPerSecond = parseFloat(m.group(7));
			dataRow.averageRequestSizeInSectors = parseFloat(m.group(8));
			dataRow.averageQueueLengthInSectors = parseFloat(m.group(9));
			dataRow.averageWaitTimeInMillis = parseFloat(m.group(10));
			dataRow.averageServiceTimeInMillis = parseFloat(m.group(11));
			dataRow.bandwidthUtilizationPercentage = parseFloat(m.group(12));
			updateBean(dataRow);
			return;
		} 



		// blank line
		if(line.trim().length() == 0) {
			// ignore
			log.debug("Processing blank line");
			return;
		}

		// unexpected input
		throw new LinuxMonitoringException("Found unexpected input: " + line);
	}

	private void updateBean(LinuxIOStat bean) throws LinuxMonitoringException {
		LinuxIOStat jmxBean = beans.get(bean.objectName);
		if(jmxBean == null) { // new device
			try {
				JMXUtils.registerMBean(bean, bean.objectName);
				beans.put(bean.objectName, bean);
			} catch (JMException e) {
				throw new LinuxMonitoringException("Error while registering bean for " + 
												   bean.objectName,e);
			}
		} else {
			jmxBean.takeValues(bean);
		}
	}

	/**
	 * Shuts down background iostat process and cleans up related I/O resources related to IPC 
	 * with said process.
	 */
	private synchronized void cleanup() {
		try {

			if(iostat != null) {
				iostat.destroy();
			}
			
			IOUtils.closeQuietly(iostatStdout);
			iostatStdout = null;

			IOUtils.closeQuietly(iostatStderr);
			iostatStderr = null;

			IOUtils.closeQuietly(iostatStdin);
			iostatStdin = null;

			iostat = null;
		} catch (Exception e) {
			log.warn("Encountered error while shutting down and cleaning up state",e);
		}
	}

	private static float parseFloat(String s) {
		float result = Float.parseFloat(s);
		// deal with occasionally weird values coming out of iostat
		if (result > 1000000000000.0f) {
			result = Float.NaN;
		}
		return result;
	}

	private static final Pattern buildWhitespaceDelimitedRegex(int numFields) {
		StringBuilder regex = new StringBuilder("^\\s*");
		for(int i = 0; i < numFields; i++) {
			regex.append("(\\S+)");
			if(i < numFields - 1) {
				regex.append("\\s+");
			}
			else {
				regex.append("\\s*");
			}
		}
		regex.append("$");
		return Pattern.compile(regex.toString());
	}
}
