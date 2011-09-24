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
import java.lang.reflect.Modifier;

/**
 * <p>
 * Data container and JMX MBean implementation for file system storage measurements used by {@link LinuxDiskspaceJMXWrapper}.
 * </p>
 * 
 * @see  <a href='http://linux.die.net/man/1/df'>df(1) for detailed information on each measurement</a>
 */
public class LinuxFileSystem implements LinuxFileSystemMBean {

	/**
	 * JMX path for this object.
	 */
	public final String objectName;
	
	private final String deviceName;
	private final String filesytemType;
	private final String mountPoint;
	
	private Long totalMegabytes;
	private Long usedMegabytes;
	private Long availableMegabytes;
	private Byte percentageSpaceUsed;

	private Long totalInodes;
	private Long usedInodes;
	private Long availableInodes;
	private Byte percentageInodesUsed;
	
	public LinuxFileSystem(
			String objectName,
			String filesystemName, 
			String filesytemType, 
			String mountPoint,
			Long totalMegabytes, 
			Long usedMegabytes, 
			Long availableMegabytes,
			Byte percentageSpaceUsed,
			Long totalInodes,
			Long usedInodes,
			Long availableInodes,
			Byte percentageInodesUsed
			) {
		this.objectName = objectName;
		this.deviceName = filesystemName;
		this.filesytemType = filesytemType;
		this.mountPoint = mountPoint;
		this.totalMegabytes = totalMegabytes;
		this.usedMegabytes = usedMegabytes;
		this.availableMegabytes = availableMegabytes;
		this.percentageSpaceUsed = percentageSpaceUsed;
		this.totalInodes = totalInodes;
		this.usedInodes = usedInodes;
		this.availableInodes = availableInodes;
		this.percentageInodesUsed = percentageInodesUsed;
	}
	
	public String getDevicName() {
		return deviceName;
	}
	
	public String getFilesytemType() {
		return filesytemType;
	}
	
	public String getMountPoint() {
		return mountPoint;
	}
	
	public Long getTotalMegabytes() {
		return totalMegabytes;
	}
	
	public Long getUsedMegabytes() {
		return usedMegabytes;
	}
	
	public Long getAvailableMegabytes() {
		return availableMegabytes;
	}
	
	public Byte getPercentageSpaceUsed() {
		return percentageSpaceUsed;
	}

	public Long getTotalInodes() {
		return totalInodes;
	}

	public Long getUsedInodes() {
		return usedInodes;
	}

	public Long getAvailableInodes() {
		return availableInodes;
	}

	public Byte getPercentageInodesUsed() {
		return percentageInodesUsed;
	}

	public synchronized void takeValues(LinuxFileSystemMBean dataBean) throws LinuxMonitoringException {
		try {
			Field[] fields = dataBean.getClass().getDeclaredFields();
			for(Field f : fields ) {
				if((f.getModifiers() & Modifier.FINAL) != Modifier.FINAL) {
					f.set(this, f.get(dataBean));
				}
			}
		} catch (Exception e) {
			throw new LinuxMonitoringException("Error while reflectively copying fields",e);
		}
	}
}
