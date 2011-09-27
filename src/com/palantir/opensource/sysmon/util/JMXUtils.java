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
package com.palantir.opensource.sysmon.util;

import java.lang.management.ManagementFactory;

import javax.management.JMException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;

import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * A small collection of utility methods to simplify bean registration and removal.
 *
 * @see <a href='http://download.oracle.com/javase/6/docs/technotes/guides/jmx/'>Java Management Extensions (JMX)</a>
 * @see MBeanServer
 *
 *
 */
public final class JMXUtils {

	private static final Logger log = LogManager.getLogger(JMXUtils.class);

	private JMXUtils() {
		/* empty */
	}

	/**
	 * Simple method to register a bean with the server. The server used is the
	 * one returned by {@code ManagementFactory.getPlatformMBeanServer()}.
	 *
	 * This method unconditionally registers the passed bean by unregistering any
	 * existing bean at the specified objectName.
	 *
	 * @param mbean The MBean to be mounted on the server.
	 * @param objectName the path to mount the MBean at.
	 * @throws JMException upon error with JMX server operations
	 */
	public static void registerMBean(final Object mbean, final String objectName) throws JMException {
		final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		final ObjectName on = new ObjectName(objectName);
		if(server.isRegistered(on)){
			server.unregisterMBean(on);
		}
		server.registerMBean(mbean, on);
	}

	/**
	 * Unconditionally attempts to unregister the specified MX Bean.  Logs
	 * any exceptions at level {@link Level#ERROR} via log4j.
	 *
	 * @param objectName String path to the bean to be removed.
	 * @see <a href='https://logging.apache.org/log4j/1.2/'>Log4J</a>
	 */
	public static void unregisterMBeanCatchAndLogExceptions(final String objectName) {
		final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		try {
			final ObjectName on = new ObjectName(objectName);
			server.unregisterMBean(on);
		} catch (final Exception e) {
			log.error("Failed to unregister mbean for name " + objectName, e);
		}
	}

	/**
	 * Prints out all the attributes for the passed JMX MBean
	 * @param mbean The bean whose attributes will be pretty printed.
	 * @throws JMException on error with JMX operations
	 */
	public static final void prettyPrintMbean(ObjectName mbean) throws JMException {
		final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		MBeanInfo metadata = server.getMBeanInfo(mbean);

		System.out.println(mbean.getCanonicalName());

		final MBeanAttributeInfo[] attributesMetadata = metadata.getAttributes();
		for(MBeanAttributeInfo attributeMetadata : attributesMetadata) {
			final String attributeName = attributeMetadata.getName();
			Object attributeValue = server.getAttribute(mbean, attributeName);
			// attribute value may be null, but + is null safe
			System.out.println("\t" + attributeName + " = " + attributeValue);
		}
		System.out.println();
	}


}
