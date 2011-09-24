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
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.management.JMException;

import org.apache.commons.io.IOUtils;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

import com.palantir.opensource.sysmon.Monitor;
import com.palantir.opensource.sysmon.util.JMXUtils;
import com.palantir.opensource.sysmon.util.PropertiesUtils;


/**
 * <p>
 * Monitors disk space on a Linux system.  Requires the 
 * <a href='http://linux.die.net/man/1/df'>df</a> utility (part of the coreutils package
 * on Redhat based systems.
 * </p><p>
 * Reads the output from 'df -P -B M' that looks like this:
 * 
 * <pre>
 *  Filesystem         1048576-blocks      Used Available Capacity Mounted on
 *  /dev/md0                19689M     5275M    13414M      29% /
 *  none                     4021M        0M     4021M       0% /dev/shm
 *  /dev/md1                51376M    48506M      262M     100% /u1
 * </pre>
 * 
 * It then looks up the filesystem type by reading /proc/self/mounts (for maximum portability).
 * </p>
 * 
 * <h3>JMX Data Path</h3>
 * Each device will be at:<br/>
 * <code>sysmon.linux.beanpath:type=filesystem,devicename=&lt;devicename&gt;</code>
 *
 * <h3>Configuration parameters</h3>
 * <em>Note that any value not set in the config file will use the default value.</em>
 * <table cellspacing=5 cellpadding=5><tr><th>Config Key</th><th>Description</th><th>Default Value</th><th>Constant</th></tr>
 * <tr><td>sysmon.linux.df.df.path</td>
 * <td>path to <code>df</code> binary</td>
 * <td><code>df</code></td>
 * <td>{@link #CONFIG_KEY_DF_PATH}</td></tr>
 * <tr><td>sysmon.linux.df.df.block.opts</td>
 * <td>options passed to <code>df</code> when checking free space</td>
 * <td><code>-P -B M</code></td>
 * <td>{@link #CONFIG_KEY_DF_OPTIONS}</td></tr>
 * <tr><td>sysmon.linux.df.df.inode.opts</td>
 * <td>options passed to <code>df</code> when checking inodes</td>
 * <td><code>-P -i</code></td>
 * <td>{@link #CONFIG_KEY_DF_INODE_OPTIONS}</td></tr>
 * <tr><td>sysmon.linux.df.df.period</td>
 * <td>Period between checks, in seconds</td>
 * <td><code>10</code></td>
 * <td>{@link #CONFIG_KEY_DF_PERIOD}</td></tr>
 * <tr><td>sysmon.linux.df.df.deviceNameFilter</td>
 * <td>Comma-separated list of device names (full path) to ignore when making free space calculations.</td>
 * <td><code></code></td>
 * <td>{@link #CONFIG_KEY_DF_DEVICE_NAME_FILTER}</td></tr>
 * <tr><td>sysmon.linux.df.df.fsTypeFilter</td>
 * <td>Comma-separated list of filesystem types to ignore when making free space calculations.</td>
 * <td><code>iso9660,proc,sysfs,tmpfs</code></td>
 * <td>{@link #CONFIG_KEY_DF_FS_TYPE_FILTER}</td></tr>
 * <tr><td>sysmon.linux.df.mtab.path</td>
 * <td>path to <code>mtab</code> file</td>
 * <td><code>/etc/mtab</code></td>
 * <td>{@link #CONFIG_KEY_MTAB_PATH}</td></tr>
 * </tr></table>
* 
 * @see Monitor Lifecycle documentation
 * 
 */
public class LinuxDiskspaceJMXWrapper extends Thread implements Monitor {

	private static final Logger log = LogManager.getLogger(LinuxDiskspaceJMXWrapper.class);

	static final String CONFIG_KEY_PREFIX = LinuxMonitor.CONFIG_KEY_PREFIX + ".df";
	
	/**
	 * Path to the df executable. Defaults to "df" (uses $PATH to find executable).
	 * Set this config value in the to override where to find df.
	 * 
	 * Config key: {@value}
	 * @see LinuxDiskspaceJMXWrapper#DEFAULT_DF_PATH default value for this config parameter
	 * @see <a href='http://linux.die.net/man/1/df'>df(1) on your local linux box</a>
	 */	
	public static final String CONFIG_KEY_DF_PATH = CONFIG_KEY_PREFIX + ".df.path";
	/**
	 * Options passed to df.  
	 * Set this key in config file to override defaults options.
	 * Config key: {@value}
	 * @see #DEFAULT_DF_OPTIONS
	 * @see <a href='http://linux.die.net/man/1/df'>df(1) on your local linux box</a>
	 */
	public static final String CONFIG_KEY_DF_OPTIONS = CONFIG_KEY_PREFIX + ".df.block.opts";
	/**
	 * Options passed to df when calculating free inodes.  
	 * Set this key in config file to override defaults options.
	 * Config key: {@value}
	 * @see #DEFAULT_DF_INODE_OPTIONS
	 * @see <a href='http://linux.die.net/man/1/df'>df(1) on your local linux box</a>
	 */	
	public static final String CONFIG_KEY_DF_INODE_OPTIONS = CONFIG_KEY_PREFIX + ".df.inode.opts";
	/**
	 * How often to run space calculations, in seconds.  
	 * Set this key in config file to override defaults options.
	 * Config key: {@value}
	 * @see #DEFAULT_DF_PERIOD
	 * @see <a href='http://linux.die.net/man/1/df'>df(1) on your local linux box</a>
	 */	
	public static final String CONFIG_KEY_DF_PERIOD = CONFIG_KEY_PREFIX + ".df.period";
	/**
	 * Comma-separated list of device names (full path) to ignore when 
	 * making free space calculations.
	 *   
	 * Set this key in config file to override defaults options.
	 * Config key: {@value}
	 * @see #DEFAULT_DF_DEVICE_NAME_FILTER
	 * @see <a href='http://linux.die.net/man/1/df'>df(1) on your local linux box</a>
	 */	
	public static final String CONFIG_KEY_DF_DEVICE_NAME_FILTER = CONFIG_KEY_PREFIX + ".df.deviceNameFilter";
	/**
	 * Comma-separated list of file system types to ignore when making free space calculations.
	 *   
	 * Set this key in config file to override defaults options.
	 * Config key: {@value}
	 * @see #DEFAULT_DF_FS_TYPE_FILTER
	 * @see <a href='http://linux.die.net/man/1/df'>df(1) on your local linux box</a>
	 */	
	public static final String CONFIG_KEY_DF_FS_TYPE_FILTER = CONFIG_KEY_PREFIX + ".df.fsTypeFilter";
	/**
	 * Path to the mtab file.
	 *   
	 * Set this key in config file to override defaults options.
	 * Config key: {@value}
	 * @see #DEFAULT_MTAB_PATH
	 * @see <a href='http://linux.die.net/man/8/mount'>mount(8) on your local linux box</a>
	 */	
	public static final String CONFIG_KEY_MTAB_PATH = CONFIG_KEY_PREFIX + ".mtab.path";
	/**
	 * For each device name and the passed JMX bean path, the a bean will be mounted
	 * with the name '{@value}&lt;devicename&gt;'
	 */
	public static final String OBJECT_NAME_PREFIX = ":type=filesystem,devicename=";
	/**
	 * Default's to using a bare path to allow use of $PATH to find the df executable.
	 * Default value: {@value}
	 * @see #CONFIG_KEY_DF_PATH for information on overriding the default value.
	 */
	public static final String DEFAULT_DF_PATH = "df"; // let the shell figure it out
	/**
	 * Default options passed to df when making disk space calculations.
	 * Default value: {@value}
	 * @see #CONFIG_KEY_DF_OPTIONS for information on overriding the default value.
	 */
	public static final String DEFAULT_DF_OPTIONS = "-P -B M";
	/**
	 * Default options passed to df when making inode calculations.
	 * Default value: {@value}
	 * @see #CONFIG_KEY_DF_INODE_OPTIONS for information on overriding the default value.
	 */
	public static final String DEFAULT_DF_INODE_OPTIONS = "-P -i";
	/**
	 * Default period, in seconds.
	 * Default value: {@value}
	 * @see #CONFIG_KEY_DF_PERIOD for information on overriding the default value.
	 */
	public static final int DEFAULT_DF_PERIOD = 10;
	/**
	 * Default device filter.
	 * Default value: {@value}
	 * @see #CONFIG_KEY_DF_DEVICE_NAME_FILTER for information on overriding the default value.
	 */
	public static final String DEFAULT_DF_DEVICE_NAME_FILTER = "";
	/**
	 * Default filesystem type filter.
	 * Default value: {@value}
	 * @see #CONFIG_KEY_DF_FS_TYPE_FILTER for information on overriding the default value.
	 */
	public static final String DEFAULT_DF_FS_TYPE_FILTER = "iso9660,proc,sysfs,tmpfs";
	/**
	 * Default path to mtab file
	 * Default value: {@value}
	 * @see #CONFIG_KEY_MTAB_PATH for information on overriding the default value.
	 */
	public static final String DEFAULT_MTAB_PATH = "/etc/mtab";
	/**
	 * {@link Pattern} for recognizing the df header.
	 */
	private static final Pattern DF_HEADER_PATTERN = 
		Pattern.compile("^\\s*Filesystem\\s+\\d+-blocks\\s+Used\\s+Available\\s+Capacity\\s+Mounted on\\s*$");
	/**
	 * {@link Pattern} for parsing df data.
	 */
	private static final Pattern DF_INODE_DATA_PATTERN = 
		Pattern.compile("^\\s*(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S(.*?\\S)?)\\s*$");
	/**
	 * {@link Pattern} for recognizing the df inode header.
	 */
	private static final Pattern DF_DATA_PATTERN = 
		Pattern.compile("^\\s*(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S+)\\s+(\\S(.*?\\S)?)\\s*$");
	/**
	 * {@link Pattern} for parsing df inode data.
	 */
	private static final Pattern DF_INODE_HEADER_PATTERN = 
		Pattern.compile("^\\s*Filesystem\\s+Inodes\\s+IUsed\\s+IFree\\s+IUse%\\s+Mounted on\\s*$");
	/**
	 * {@link Pattern} for parsing of mtab data.
	 */
	private static final Pattern MTAB_DATA = 
		Pattern.compile("^\\s*(\\S+)\\s+\\S(.*?\\S)?\\s+(\\S+)\\s+\\S+\\s+\\d+\\s+\\d+\\s*$");

	
	// instance variables to hold all the config options
	final String dfCmd[];
	final String dfInodeCmd[];
	/**
	 * Stored in millis, but configured in seconds.
	 */
	final long period;
	final String dfPath;
	final String dfOptions; 
	final String dfInodeOptions; 
	final File mtabPath;
	final Set<String> dfDeviceNameFilter = new HashSet<String>();
	final Set<String> dfFsTypeFilter = new HashSet<String>();
	/**
	 * volatile - to be used for simple inter-thread communication
	 */
	volatile boolean shutdown = false;
	/**
	 * Where to put the data in the JMX tree.
	 */
	final String beanPathPrefix;
	/**
	 * Current set of beans, for update purposes.  JMX requires update of the beans in place, so
	 * we hold a reference to them here.
	 */
	final Map<String, LinuxFileSystem> filesystems = new HashMap<String, LinuxFileSystem>();

	private Process process = null; // Any currently running process, protected by synchronized(this).

	public LinuxDiskspaceJMXWrapper(Properties config) throws LinuxMonitoringException {
		// initialize thread
		super("LinuxDiskspaceMonitor");
		this.setDaemon(true);

		// configure
		this.beanPathPrefix = config.getProperty(LinuxMonitor.CONFIG_KEY_JMX_BEAN_PATH, 
		                                         LinuxMonitor.DEFAULT_JMX_BEAN_PATH) +
		                                         OBJECT_NAME_PREFIX;
		this.dfPath = config.getProperty(CONFIG_KEY_DF_PATH,DEFAULT_DF_PATH);
		this.dfOptions = config.getProperty(CONFIG_KEY_DF_OPTIONS, DEFAULT_DF_OPTIONS);
		this.dfInodeOptions = config.getProperty(CONFIG_KEY_DF_INODE_OPTIONS, DEFAULT_DF_INODE_OPTIONS);
		try {
			int periodSeconds = PropertiesUtils.extractInteger(config, 
			                                                   CONFIG_KEY_DF_PERIOD, 
			                                                   DEFAULT_DF_PERIOD);
			this.period = periodSeconds * 1000; // millis
		} catch (NumberFormatException e) {
			throw new LinuxMonitoringException("Invalid config value for " + CONFIG_KEY_DF_PERIOD,e);
		}
		this.mtabPath = new File(config.getProperty(CONFIG_KEY_MTAB_PATH, DEFAULT_MTAB_PATH));

		// configure filters
		String filters = config.getProperty(CONFIG_KEY_DF_DEVICE_NAME_FILTER,
		                                    DEFAULT_DF_DEVICE_NAME_FILTER);
		for (String filter : filters.split(",")) {
			if ("".equals(filter)) {
				continue;
			}
			dfDeviceNameFilter.add(filter);
		}
		filters = config.getProperty(CONFIG_KEY_DF_FS_TYPE_FILTER,DEFAULT_DF_FS_TYPE_FILTER);
		for (String filter : filters.split(",")) {
			if ("".equals(filter)) {
				continue;
			}
			dfFsTypeFilter.add(filter);
		}

		// build command line
		String[] opts = dfOptions.split("\\s+");
		dfCmd = new String[opts.length + 1];
		dfCmd[0] = dfPath;
		System.arraycopy(opts, 0, dfCmd, 1, opts.length);

		// build command line (inode)
		opts = dfInodeOptions.split("\\s+");
		dfInodeCmd = new String[opts.length + 1];
		dfInodeCmd[0] = dfPath;
		System.arraycopy(opts, 0, dfInodeCmd, 1, opts.length);

		// read once to throw config exceptions on calling thread
		// QA-28918: Previously used InterruptTimerTask, which wasn't properly interrupting the df process.  Switching to a process kill.
		KillTimerTask timer = setKillTimer(4 * period);
		try {
			readData();
		} finally {
			timer.cancel();	
		}			
	}

	/**
	 * Starts background thread that will actually be performing monitoring runs.
	 */
	public void startMonitoring() {
		this.start();
	}
	
	/**
	 * Signals shutdown to background thread and then waits for thread to die. 
	 * @throws InterruptedException if interrupted while waiting for thread to die.
	 */
	public void stopMonitoring() throws InterruptedException {
		this.shutdown = true;
		this.interrupt();
		this.join(this.period * 2);
	}
	
	@Override
	public void run() {
		try {
			do {
				readData();
				Thread.sleep(this.period);
			} while(!shutdown);
		} catch (Exception e) {
			if(!shutdown) {
				log.error("Shutting down filesytem monitoring due to error.",e);
			}
		}
	}

	public void kill() {
		synchronized(this) {
			if (process != null) {
				process.destroy();
				process = null;
			}
		}
	}



	private void readData() throws LinuxMonitoringException {
		Map<String, String> fsTypeMap = readFileSystemTypes();
		Map<String, DfData> dfDataMap = readDfData(dfCmd, DF_HEADER_PATTERN, DF_DATA_PATTERN);
		Map<String, DfData> dfInodeDataMap = readDfData(dfInodeCmd, DF_INODE_HEADER_PATTERN, DF_INODE_DATA_PATTERN);
		updateBeans(fsTypeMap, dfDataMap, dfInodeDataMap);
	}

	/**
	 * Here's where the sausage gets made - run df in separate process and read its output into
	 * {@link DfData} structures.
	 * 
	 * @param cmd
	 * @param headerPattern
	 * @param dataPattern
	 * @return parsed output of the df command
	 * @throws LinuxMonitoringException
	 */
	private Map<String, DfData> readDfData(String[] cmd, Pattern headerPattern, Pattern dataPattern) throws LinuxMonitoringException {
		Map<String, DfData> result = new HashMap<String, DfData>();
		BufferedReader stdout = null;
		InputStream stderr = null;
		OutputStream stdin = null;
		try {
			synchronized(this) {
				process = Runtime.getRuntime().exec(cmd); // (authorized)
				stdout = new BufferedReader(new InputStreamReader(process.getInputStream()));
				stderr = process.getErrorStream();
				stdin = process.getOutputStream();
			}
			String line = stdout.readLine();
			if (line == null) {
				throw new LinuxMonitoringException("No data read from df process!");
			}
			// Check header.
			Matcher m = headerPattern.matcher(line);
			if (!m.matches()) {
				throw new LinuxMonitoringException("Unexpected header from df process: " + line + ". " +
				                                   "Did not mach with regex: " + headerPattern.pattern());
			}
			// Read data.
			do {
				line = stdout.readLine();
				if (line != null) { 
					m = dataPattern.matcher(line);
					if (m.matches()) {
						DfData dfData = new DfData(
						                           m.group(1),                       // device name
						                           m.group(6),                       // mount point
						                           parseLongIgnoreAlpha(m.group(2)), // total
						                           parseLongIgnoreAlpha(m.group(3)), // used
						                           parseLongIgnoreAlpha(m.group(4)), // available
						                           parseByteIgnorePcnt(m.group(5))); // percentage used
						result.put(dfData.getDeviceName(), dfData);
					} else {
						String msg = "Df data line did not match: " + line + ". Pattern: " + dataPattern.pattern();
						log.warn(msg);
					}
				}
			} while (line != null);
		} catch (IOException e) {
			throw new LinuxMonitoringException("Error while reading data from df process",e);
		} finally {
			IOUtils.closeQuietly(stdout);
			IOUtils.closeQuietly(stderr);
			IOUtils.closeQuietly(stdin);
			kill();
		}
		return result;
	}

	private void updateBeans(Map<String, String> fsTypeMap,
	                         Map<String, DfData> dfDataMap,
	                         Map<String, DfData> dfInodeDataMap) throws LinuxMonitoringException {
		
		// these are maps df output data, keyed by devicename
		for (String deviceName : dfDataMap.keySet()) {
			String fsType = fsTypeMap.get(deviceName);
			DfData dfData = dfDataMap.get(deviceName);
			DfData dfInodeData = dfInodeDataMap.get(deviceName);

			// Check filters, skipping if necessary
			if (dfDeviceNameFilter.contains(deviceName)) {
				continue;
			}
			if (dfFsTypeFilter.contains(fsType)) {
				continue;
			}

			// May have null values in the case of parse errors.
			if (fsType == null) {
				log.warn("Unexpected null 'fsType' for device '" + deviceName + "'.");
			}
			if (dfData == null) {
				log.warn("Unexpected null 'dfData' for device '" + deviceName + "'.  Will update with empty values.");
				dfData = new DfData(deviceName, null, null, null, null, null);
			}
			if (dfInodeData == null) {
				log.warn("Unexpected null 'dfInodeData' for device '" + deviceName + "'.  Will update with empty values.");
				dfInodeData = new DfData(deviceName, null, null, null, null, null);
			}

			LinuxFileSystem newBean = new LinuxFileSystem(beanPathPrefix + dfData.getMountPoint(),
			                                              deviceName,
			                                              fsType,
			                                              dfData.getMountPoint(),
			                                              dfData.getTotal(),
			                                              dfData.getUsed(),
			                                              dfData.getAvailable(),
			                                              dfData.getPercentageUsed(),
			                                              dfInodeData.getTotal(),
			                                              dfInodeData.getUsed(),
			                                              dfInodeData.getAvailable(),
			                                              dfInodeData.getPercentageUsed());
			updateBean(newBean);
		}
	}

	private void updateBean(LinuxFileSystem fs) throws LinuxMonitoringException {
		LinuxFileSystem jmxBean = filesystems.get(fs.objectName);
		if(jmxBean == null) { // new device
			try {
				JMXUtils.registerMBean(fs, fs.objectName);
				filesystems.put(fs.objectName, fs);
			} catch (JMException e) {
				log.error("Error registering bean for " + fs.objectName,e);
			}
		} else {
			// updates bean in place
			jmxBean.takeValues(fs);
			log.debug(jmxBean.toString());
		}
	}

	private Map<String, String> readFileSystemTypes() throws LinuxMonitoringException {
		BufferedReader mounts = null;
		final Map<String,String> fsTypeMap = new HashMap<String, String>(); 
		try {
			mounts = new BufferedReader(new InputStreamReader(new FileInputStream(mtabPath)));

			String line = null;
			do {
				line = mounts.readLine();
				if(line != null) {
					// parse out the fields
					// sample line: /dev/sda2 / ext3 rw 0 0
					Matcher m = MTAB_DATA.matcher(line);
					if(m.matches()) {
						String fsName = m.group(1);
						String fsType = m.group(3);
						fsTypeMap.put(fsName, fsType);
					}
				}
			} while(line != null);
		} catch (IOException e) {
			throw new LinuxMonitoringException("Error while reading data from " + 
			                                   mtabPath.getAbsolutePath(),
			                                   e);
		} finally {
			IOUtils.closeQuietly(mounts);
		}
		return fsTypeMap;
	}
	
	
	/**
	 * Data container representing a device/mount point and all its attendant data.
	 * Values are unitless and are used to represent either megabytes or inodes - the output 
	 * of a df line.
	 */
	private static class DfData {
		private final String deviceName;
		private final String mountPoint;
		private final Long total;
		private final Long used;
		private final Long available;
		private final Byte percentageUsed;
		public DfData(
				String deviceName, 
				String mountPoint,
				Long total,
				Long used,
				Long available,
				Byte percentageUsed
				) {
			this.deviceName = deviceName;
			this.mountPoint = mountPoint;
			this.total = total;
			this.used = used;
			this.available = available;
			this.percentageUsed = percentageUsed;
		}
		public String getDeviceName() {
			return deviceName;
		}
		public String getMountPoint() {
			return mountPoint;
		}
		/**
		 * @return Size of this device in megabytes or inodes.
		 */
		public Long getTotal() {
			return total;
		}
		/**
		 * @return used resource (megabytes or inodes) on this device.
		 */
		public Long getUsed() {
			return used;
		}
		/**
		 * @return megabytes or inodes free on this device.
		 */
		public Long getAvailable() {
			return available;
		}
		/**
		 * @return Percentage of megabytes or in use.
		 */
		public Byte getPercentageUsed() {
			return percentageUsed;
		}
	}

	private static final Timer TIMER = new Timer("DiskspaceMonitor Kill Timer", true);
	
	/**
	 * Sets a background task to kill the child process of this monitor
	 * if it's taking too long to produce data.  Caller holds reference to passed
	 * object and calls {@link KillTimerTask#cancel()} once background work has completed.
	 * 
	 * @param delay millis to wait before killing background process
	 * @see Timer
	 * @return {@link KillTimerTask} object used to cancel timer.
	 */
	private KillTimerTask setKillTimer(long delay) {
		KillTimerTask tt = new KillTimerTask();
		TIMER.schedule(tt, delay);
		return tt;
	}

	/**
	 * Class used to kill background tasks that are taking too long to execute.
	 * 
	 *
	 */
	private class KillTimerTask extends TimerTask {
		@Override
		public void run() {
			if (log.isDebugEnabled()) {
				log.debug("Killing DiskspaceMonitor child process.");
			}
			// kill destroys the child process of the thread, not the thread itself
			LinuxDiskspaceJMXWrapper.this.kill();
		}
	}


	private static Long parseLongIgnoreAlpha(String longValue) {
		try {
			return Long.parseLong(longValue.replaceAll("[A-Za-z]", ""));
		} catch(NumberFormatException e) {
			if (log.isDebugEnabled()) {
				log.debug("Error parsing value: " + longValue,e);
			}
		}
		return null;
	}

	private static Byte parseByteIgnorePcnt(String byteValue) {
		try {
			return Byte.parseByte(byteValue.replaceAll("%", ""));
		} catch(NumberFormatException e) {
			if (log.isDebugEnabled()) {
				log.debug("Error parsing value: " + byteValue,e);
			}
		}
		return null;
	}
}
