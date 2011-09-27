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

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * <p>
 * Data container and JMX MBean implementation for I/O statistics used by
 * {@link LinuxIOStatJMXWrapper}.
 * </p>
 * <p>
 * iostat output looks like this:
 * <pre>
 * Linux 2.6.26.5-28.fc8 (nosuchhost.palantir.com)      11/12/2008
 *
 *
 * Device:         rrqm/s   wrqm/s     r/s     w/s    rMB/s    wMB/s avgrq-sz avgqu-sz   await  svctm  %util
 * sda               0.14     0.68    0.41    0.72     0.01     0.03    62.52     0.06   55.69   3.15   0.35
 * sda1              0.00     0.00    0.00    0.00     0.00     0.00    21.42     0.00   13.80  10.32   0.00
 * sda2              0.04     0.54    0.30    0.54     0.00     0.00    19.19     0.04   43.17   3.10   0.26
 * sda3              0.00     0.00    0.00    0.00     0.00     0.00    30.38     0.00   40.20  34.95   0.00
 * sda4              0.00     0.00    0.00    0.00     0.00     0.00     2.00     0.00   13.33  13.33   0.00
 * sda5              0.10     0.14    0.10    0.18     0.00     0.02   189.29     0.03   92.34   3.94   0.11
 * </pre>
 * @see <a href='http://linux.die.net/man/1/iostat'>iostat(1) for more on the meaning
 * of each measurement</a>
 */
public class LinuxIOStat implements LinuxIOStatMBean {

	static final Logger log = LogManager.getLogger(LinuxIOStat.class);

	volatile long timestamp;

	volatile String device;

	final String objectName;


	/**
	 * Over what period the aveages were computed, in seconds;
	 */
	volatile int samplePeriodInSeconds;

	/**
	 * The number of read requests merged per second that were queued to the device.
	 */
	volatile float mergedReadRequestsPerSecond;

	/**
     * The number of write requests merged per second that were queued to the device.
     */
	volatile float mergedWriteRequestsPerSecond;

	/**
	 *The number of read requests that were issued to the device per second.
	 */
	volatile float readRequestsPerSecond;
	/**
     * The number of write requests that were issued to the device per second.
     */
	volatile float writeRequestsPerSecond;
	/**
	 * The number of megabytes read from the device per second.
	 */
	volatile float kilobytesReadPerSecond;
	/**
	 * The number of megabytes written to the device per second.
	 */
	volatile float kilobytesWrittenPerSecond;

	/**
	 * The average size (in sectors) of the requests that were issued to the device.
	 */
	volatile float averageRequestSizeInSectors;
	/**
	 * The average queue length of the requests that were issued to the device.
	 */
	volatile float averageQueueLengthInSectors;
	/**
	 * The average time (in milliseconds) for I/O requests issued to the device to be served.
	 * This includes the time spent by the requests in queue and the time spent servicing them.
	 *
	 */
	volatile float averageWaitTimeInMillis;
	/**
	 * The average service time (in milliseconds) for I/O requests that were issued to the device.
	 */
	volatile float averageServiceTimeInMillis;
	/**
	 * Percentage of CPU time during which I/O requests were issued to the device (bandwidth
	 * utilization for the device). Device saturation occurs when this value is close to 100%.
	 */
	volatile float bandwidthUtilizationPercentage;


	public LinuxIOStat(String objectName) {
		this.objectName = objectName;
	}

	public synchronized void takeValues(LinuxIOStatMBean dataBean) throws LinuxMonitoringException {
		try {
			Field[] fields = dataBean.getClass().getDeclaredFields();
			for(Field f : fields ) {
				if((f.getModifiers() & Modifier.FINAL) != Modifier.FINAL) {
					f.set(this, f.get(dataBean));
				}
			}
		} catch (Exception e) {
			throw new LinuxMonitoringException("Error while refletively copying fields",e);
		}
	}


	public synchronized final String getDevice() {
		return device;
	}


	public synchronized final int getSamplePeriodInSeconds() {
		return samplePeriodInSeconds;
	}


	public synchronized final float getMergedReadRequestsPerSecond() {
		return mergedReadRequestsPerSecond;
	}


	public synchronized final float getMergedWriteRequestsPerSecond() {
		return mergedWriteRequestsPerSecond;
	}


	public synchronized final float getReadRequestsPerSecond() {
		return readRequestsPerSecond;
	}


	public synchronized final float getWriteRequestsPerSecond() {
		return writeRequestsPerSecond;
	}


	public synchronized final float getKilobytesReadPerSecond() {
		// QA-26161: PEM: Weird spike in stat
		if (kilobytesReadPerSecond > 1000000000000.0f) {
			log.error("Unexpected value for kilobytesReadPerSecond, " + kilobytesReadPerSecond);
		}
		return kilobytesReadPerSecond;
	}


	public synchronized final float getKilobytesWrittenPerSecond() {
		// QA-26161: PEM: Weird spike in stat
		if (kilobytesWrittenPerSecond > 1000000000000.0f) {
			log.error("Unexpected value for kilobytesWrittenPerSecond, " + kilobytesWrittenPerSecond);
		}
		return kilobytesWrittenPerSecond;
	}


	public synchronized final float getAverageRequestSizeInSectors() {
		// QA-26161: PEM: Weird spike in stat
		if (averageRequestSizeInSectors > 1000000000000.0f) {
			log.error("Unexpected value for averageRequestSizeInSectors, " + averageRequestSizeInSectors);
		}
		return averageRequestSizeInSectors;
	}


	public synchronized final float getAverageQueueLengthInSectors() {
		return averageQueueLengthInSectors;
	}


	public synchronized final float getAverageWaitTimeInMillis() {
		// QA-26161: PEM: Weird spike in stat
		if (averageWaitTimeInMillis > 1000000000000.0f) {
			log.error("Unexpected value for averageWaitTimeInMillis, " + averageWaitTimeInMillis);
		}
		return averageWaitTimeInMillis;
	}


	public synchronized final float getAverageServiceTimeInMillis() {
		return averageServiceTimeInMillis;
	}


	public synchronized final float getBandwidthUtilizationPercentage() {
		// QA-26161: PEM: Weird spike in stat
		if (bandwidthUtilizationPercentage > 1000000000000.0f) {
			log.error("Unexpected value for bandwidthUtilizationPercentage, " + bandwidthUtilizationPercentage);
		}
		return bandwidthUtilizationPercentage;
	}


	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((objectName == null) ? 0 : objectName.hashCode());
		return result;
	}


	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		final LinuxIOStat other = (LinuxIOStat) obj;
		if (objectName == null) {
			if (other.objectName != null)
				return false;
		} else if (!objectName.equals(other.objectName))
			return false;
		return true;
	}

}
