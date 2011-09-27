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

import java.lang.reflect.Field;


/**
 * Data container and JMX MBean implementation for
 * <a href='http://linux.die.net/man/1/iostat'>vmstat</a> data used by
 * {@link LinuxVMStatJMXWrapper}.
 */
public class LinuxVMStat implements LinuxVMStatMBean {

	/*
	 * Processes
	 */
	volatile Integer runningProcesses = null;
	volatile Integer sleepingProcesses = null;
	/*
	 * Memory
	 */
	volatile Integer swappedMemory = null;
	volatile Integer freeMemory = null;
	volatile Integer buffersMemory = null;
	volatile Integer cacheMemory = null;
	volatile Integer swapIn = null;
	volatile Integer swapOut = null;
	/*
	 * I/O
	 */
	volatile Integer blocksRead = null;
	volatile Integer blocksWritten = null;
	/*
	 * System
	 */
	volatile Integer interrupts = null;
	volatile Integer contextSwitches = null;
	/*
	 * CPU
	 */
	volatile Integer userPercentCPU = null;
	volatile Integer sysPercentCPU = null;
	volatile Integer idlePercentCPU = null;
	volatile Integer waitPercentCPU = null;
	volatile Integer stolenFromVMCPU = null;

	public synchronized void takeValues(LinuxVMStat dataBean) throws LinuxMonitoringException {
		try {
			Field[] fields = dataBean.getClass().getDeclaredFields();
			for(Field f : fields ) {
				f.set(this, f.get(dataBean));
			}
		} catch (Exception e) {
			throw new LinuxMonitoringException("Error while refletively copying fields",e);
		}
	}

	/* (non-Javadoc)
	 * @see com.palantir.monitoring.client.linux.LinuxVMStatMBean#getRunningProcesses()
	 */
	public synchronized Integer getRunningProcesses() {
		return runningProcesses;
	}


	/* (non-Javadoc)
	 * @see com.palantir.monitoring.client.linux.LinuxVMStatMBean#getSleepingProcesses()
	 */
	public synchronized Integer getSleepingProcesses() {
		return sleepingProcesses;
	}


	/* (non-Javadoc)
	 * @see com.palantir.monitoring.client.linux.LinuxVMStatMBean#getSwappedMemory()
	 */
	public synchronized Integer getSwappedMemory() {
		return swappedMemory;
	}


	/* (non-Javadoc)
	 * @see com.palantir.monitoring.client.linux.LinuxVMStatMBean#getFreeMemory()
	 */
	public synchronized Integer getFreeMemory() {
		return freeMemory;
	}


	/* (non-Javadoc)
	 * @see com.palantir.monitoring.client.linux.LinuxVMStatMBean#getBuffersMemory()
	 */
	public synchronized Integer getBuffersMemory() {
		return buffersMemory;
	}


	/* (non-Javadoc)
	 * @see com.palantir.monitoring.client.linux.LinuxVMStatMBean#getCacheMemory()
	 */
	public synchronized Integer getCacheMemory() {
		return cacheMemory;
	}


	/* (non-Javadoc)
	 * @see com.palantir.monitoring.client.linux.LinuxVMStatMBean#getInactiveMemory()
	 */
	public synchronized Integer getSwappedIn() {
		return swapIn;
	}


	/* (non-Javadoc)
	 * @see com.palantir.monitoring.client.linux.LinuxVMStatMBean#getActiveMemory()
	 */
	public synchronized Integer getSwappedOut() {
		return swapOut;
	}


	/* (non-Javadoc)
	 * @see com.palantir.monitoring.client.linux.LinuxVMStatMBean#getBlocksRead()
	 */
	public synchronized Integer getBlocksRead() {
		return blocksRead;
	}


	/* (non-Javadoc)
	 * @see com.palantir.monitoring.client.linux.LinuxVMStatMBean#getBlocksWritten()
	 */
	public synchronized Integer getBlocksWritten() {
		return blocksWritten;
	}


	/* (non-Javadoc)
	 * @see com.palantir.monitoring.client.linux.LinuxVMStatMBean#getInterrupts()
	 */
	public synchronized Integer getInterrupts() {
		return interrupts;
	}


	/* (non-Javadoc)
	 * @see com.palantir.monitoring.client.linux.LinuxVMStatMBean#getContextSwitches()
	 */
	public synchronized Integer getContextSwitches() {
		return contextSwitches;
	}


	/* (non-Javadoc)
	 * @see com.palantir.monitoring.client.linux.LinuxVMStatMBean#getUserPercentCPU()
	 */
	public synchronized Integer getUserPercentCPU() {
		return userPercentCPU;
	}


	/* (non-Javadoc)
	 * @see com.palantir.monitoring.client.linux.LinuxVMStatMBean#getSysPercentCPU()
	 */
	public synchronized Integer getSysPercentCPU() {
		return sysPercentCPU;
	}


	/* (non-Javadoc)
	 * @see com.palantir.monitoring.client.linux.LinuxVMStatMBean#getIdlePercentCPU()
	 */
	public synchronized Integer getIdlePercentCPU() {
		return idlePercentCPU;
	}


	/* (non-Javadoc)
	 * @see com.palantir.monitoring.client.linux.LinuxVMStatMBean#getWaitPercentCPU()
	 */
	public synchronized Integer getWaitPercentCPU() {
		return waitPercentCPU;
	}


	/* (non-Javadoc)
	 * @see com.palantir.monitoring.client.linux.LinuxVMStatMBean#getStolenFromVMCPU()
	 */
	public synchronized Integer getStolenFromVMCPU() {
		return stolenFromVMCPU;
	}

}
