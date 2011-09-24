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

import java.util.Properties;


/**
 * Interface for platform monitors.
 * 
 *
 */
public interface SystemMonitor {

	/**
	 * Root of Sysmon JMX beans in the bean server hierarchy.
	 * Bean root: {@value}
	 */
	public static final String DEFAULT_JMX_BEAN_PATH = "sysmon";
	/**
	 * Prefix for configuration keys found in the {@link Properties} objects passed to
	 * {@link #startPlatformSpecificMonitoring(Properties)}.  Implementations should use
	 * this constant to build up their own config prefixes so as to avoid conflicts in the 
	 * configuration namespace.
	 * Config prefix: {@value}
	 */
	public static final String CONFIG_KEY_PREFIX = "sysmon";
	
	/**
	 * Start monitoring for this specific platform (as implemented by instances of this interface).
	 * @param config configuration information for the specific monitor implementations.  Must empty
	 * and/or null {@link Properties} objects as a way of signalling that default values should be
	 * used.
	 */
	public void startPlatformSpecificMonitoring(Properties config) throws SysmonException;
	/**
	 * Stop monitoring for this specific platform (as implemented by instances of this interface).
	 */
	public void stopPlatformSpecificMonitoring();
	
	/**
	 * Tells the platform specific monitor to check that it believes that it is compatible
	 * with the current VM's execution environments.  This is where the platform monitor
	 * might check versions and paths that it expects to exist.
	 * @throws Exception to specify errors with execution environment.
	 */
	public void verifyExecutionEnvironment(Properties config) throws SysmonException;
	
}
