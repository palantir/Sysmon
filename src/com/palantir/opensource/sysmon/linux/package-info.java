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
/**
 * <p>
 * The Linux Sysmon package implements platform monitoring for Java VMs running on Linux hosts.
 * </p>
 * <p>
 * {@link com.palantir.opensource.sysmon.linux.LinuxMonitor} is the single-entry point class that
 * will start and stop all of the implemented platform monitors.  This normally done either as a
 * service embedded in a larger program or in standalone-daemon mode, via {@link com.palantir.opensource.sysmon.SysmonDaemon}.
 * </p>
 * <p>
 * Currently, the following specific monitors are implemented:
 * <ul>
 * <li><strong>Diskspace</strong> ({@link com.palantir.opensource.sysmon.linux.LinuxDiskspaceJMXWrapper})
 * - how full the different block storage devices in the system are.</li>
 * <li><strong>Entropy Pool</strong> ({@link com.palantir.opensource.sysmon.linux.LinuxEntropyLevelJMXWrapper})
 * - how much entropy is available in the <a href='http://linux.die.net/man/4/random'>entropy pool</a>.</li>
 * <li><strong>IO Statistics</strong> ({@link com.palantir.opensource.sysmon.linux.LinuxIOStatJMXWrapper}) -
 * measurements of reads, writes, and disk utilization, as provided by
 * <a href='http://linux.die.net/man/1/iostat'>iostat</a>.</li>
 * <li><strong>Load Average</strong> ({@link com.palantir.opensource.sysmon.linux.LinuxLoadAverageJMXWrapper}) -
 * the one, ten, and fifteen minuted load averages.</li>
 * <li><strong>Network statistics</strong> ({@link com.palantir.opensource.sysmon.linux.LinuxNetStatJMXWrapper}) -
 * measurements of network traffic.</li>
 * <li><strong>Linux VM statistics</strong> ({@link com.palantir.opensource.sysmon.linux.LinuxVMStatJMXWrapper})-
 * performance measurements of the Linux virtual machine (not the Java VM), as provided by
 * <a href='http://linux.die.net/man/8/vmstat'>vmstat</a>.</li>
 * </ul>
 * </p>
 * <p>
 * See individual monitor documentation for details on configuration,
 * {@link com.palantir.opensource.sysmon.SysmonDaemon} for examples and usage.
 * </p>
 */
package com.palantir.opensource.sysmon.linux;
