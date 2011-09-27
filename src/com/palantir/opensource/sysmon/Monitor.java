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

import com.palantir.opensource.sysmon.linux.LinuxIOStatJMXWrapper;

/**
 * <p>
 * An interface for metric-specific montiors, each reading and publishing values
 * from a source on the machine.
 * </p><p>
 * Construction of a Monitor object must not start any
 * background processes.  {@link #startMonitoring()} must be called to
 * start up the background routines.
 * </p><p>
 * A call to {@link #stopMonitoring()} will shut down and clean up any background processes and
 * data processing threads.
 * </p><p>
 * A Monitor object may not be restarted. Construct a new instance rather
 * calling {@link #startMonitoring()} after calling {@link #stopMonitoring()}.
 * </p>
 *
 */
public interface Monitor {
	public void startMonitoring() throws SysmonException;
	public void stopMonitoring() throws InterruptedException;
}
