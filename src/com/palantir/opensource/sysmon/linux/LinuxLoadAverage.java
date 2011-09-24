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
 * Data container and JMX MBean implementation for load average measurements used by 
 * {@link LinuxLoadAverageJMXWrapper}.
 */
public class LinuxLoadAverage implements LinuxLoadAverageMBean {

	private Double oneMinute;
	private Double tenMinute;
	private Double fifteenMinute;

	public LinuxLoadAverage(
			Double oneMinute,
			Double tenMinute,
			Double fifteenMinute
			) {
		this.oneMinute = oneMinute;
		this.tenMinute = tenMinute;
		this.fifteenMinute = fifteenMinute;
	}

	public Double getOneMinute() {
		return oneMinute;
	}

	public Double getTenMinute() {
		return tenMinute;
	}

	public Double getFifteenMinute() {
		return fifteenMinute;
	}

	public synchronized void updateValues(
			Double oneMinute,
			Double tenMinute,
			Double fifteenMinute
			) {
		this.oneMinute = oneMinute;
		this.tenMinute = tenMinute;
		this.fifteenMinute = fifteenMinute;
	}

}
