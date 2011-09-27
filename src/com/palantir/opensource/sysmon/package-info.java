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
 * The Sysmon package allows a Java program to monitor the health of the
 * host system that the Java VM is running on and access that data via
 * <a href='http://download.oracle.com/javase/7/docs/technotes/guides/jmx/index.html'>Java Management Extensions</a>
 * (non-Javadoc documentation available in the <a href='http://github.com/palantir/Sysmon/wiki'>GitHub Project Wiki</a>).
 * </p>
 * <p>
 * The Sysmon package can be used as a library inside an existing program or
 * as a standalone daemon that can be accessed via the standard JMX mechanisms
 * for network access.
 * </p>
 * <p>Currently, it only implements monitoring for Linux-based hosts (see
 * {@link com.palantir.opensource.sysmon.linux.LinuxMonitor}), but could easily be
 * extended to support any platform.
 * </p>
 */
package com.palantir.opensource.sysmon;
