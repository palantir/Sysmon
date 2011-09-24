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

/**
 * MBean interface for {@link LinuxDiskspaceJMXWrapper}.
 */
public interface LinuxFileSystemMBean {

	public abstract String getDevicName();

	public abstract String getFilesytemType();

	public abstract String getMountPoint();

	public abstract Long getTotalMegabytes();

	public abstract Long getUsedMegabytes();

	public abstract Long getAvailableMegabytes();

	public abstract Byte getPercentageSpaceUsed();

	public abstract Long getTotalInodes();

	public abstract Long getUsedInodes();

	public abstract Long getAvailableInodes();

	public abstract Byte getPercentageInodesUsed();

}