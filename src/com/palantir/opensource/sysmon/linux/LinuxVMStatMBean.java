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

import javax.management.MXBean;

/**
 * MBean interface for {@link LinuxVMStatJMXWrapper}.
 */
@MXBean
public interface LinuxVMStatMBean {

	public abstract Integer getRunningProcesses();

	public abstract Integer getSleepingProcesses();

	public abstract Integer getSwappedMemory();

	public abstract Integer getFreeMemory();

	public abstract Integer getBuffersMemory();

	public abstract Integer getCacheMemory();

	public abstract Integer getSwappedIn();

	public abstract Integer getSwappedOut();

	public abstract Integer getBlocksRead();

	public abstract Integer getBlocksWritten();

	public abstract Integer getInterrupts();

	public abstract Integer getContextSwitches();

	public abstract Integer getUserPercentCPU();

	public abstract Integer getSysPercentCPU();

	public abstract Integer getIdlePercentCPU();

	public abstract Integer getWaitPercentCPU();

	public abstract Integer getStolenFromVMCPU();

}