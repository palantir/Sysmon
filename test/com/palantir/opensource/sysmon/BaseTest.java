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

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.management.Attribute;
import javax.management.AttributeList;
import javax.management.BadAttributeValueExpException;
import javax.management.BadBinaryOpValueExpException;
import javax.management.BadStringOperationException;
import javax.management.InstanceNotFoundException;
import javax.management.InvalidApplicationException;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanServer;
import javax.management.MalformedObjectNameException;
import javax.management.ObjectName;
import javax.management.QueryExp;
import javax.management.ReflectionException;

import junit.framework.TestCase;

import org.apache.log4j.Appender;
import org.apache.log4j.AppenderSkeleton;
import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Level;
import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.spi.LoggingEvent;


public abstract class BaseTest extends TestCase {

	static {
		configureLog4J();
	}
	
	ErrorDetectingAppender eda = null;
	
	/**
	 * Installs a log4j {@link Appender} that will detect any logging
	 * at the specified levels and fail the test.
	 * 
	 *
	 */
	public class ErrorDetectingAppender extends AppenderSkeleton {
		
		final Set<Level> recordLevels;
		final List<String> errorMessages = new ArrayList<String>();
		final Logger logger;
		final PatternLayout layout = new PatternLayout("%p [%t] %c{1} - %m%n");
		public ErrorDetectingAppender(Class<?> klass, Level...levels) {
			this(klass.getCanonicalName(),levels);
		}
		
		public ErrorDetectingAppender(String logger, Level...levels) {
			recordLevels = new HashSet<Level>();
			for(Level l : levels) {
				recordLevels.add(l);
			}
			this.logger = LogManager.getLogger(logger);
		}
		
		ArrayList<String> recordedEvents = new ArrayList<String>();
		@Override
		protected synchronized void append(LoggingEvent arg0) {
			if(recordLevels.contains(arg0.getLevel())) {
				errorMessages.add(layout.format(arg0));
			}
		}
		
		@Override
		public void close() {
			errorMessages.clear();
		}
		
		@Override
		public boolean requiresLayout() {
			return false;
		}

		public void checkForErrorMessages() {
			if(errorMessages.size() > 0) {
				System.err.println("Detected the following logged errors:");
				for(String msg : errorMessages) {
					System.err.println("\t" + msg);
				}
				fail("Errors were logged.  See console for list of error messages");
			}
		}
		
		/**
		 * Don't do this in constructor - this pointer should not escape.
		 */
		public void install() {
			logger.addAppender(this);
		}
		
		public void cleanup() {
			logger.removeAppender(this);
		}
	}
	
	public void installLoggingErrorDetector(Class<?> klass, Level...levels) {
		this.eda = new ErrorDetectingAppender(klass, levels);
		eda.install();
	}

	public void checkForErrorMessages() {
		try {
			this.eda.checkForErrorMessages();
		} finally {
			this.eda.cleanup();
			this.eda = null;
		}
	}

	/**
	 * Class that just runs the CPU full throttle in the background to drive up load.
	 * 
	 */
	public class CPULoadDriver extends Thread {
		final int millionsOfOperations;
		volatile boolean shutdown = false;
		
		public CPULoadDriver(int millionsOfOperations) {
			this.millionsOfOperations = millionsOfOperations;
			System.out.println("Starting random number load generator");
			start();
		}
		
		@Override
		public void run() {
			for(int j = 0; j < millionsOfOperations; j++) {
				while(!shutdown) {
					for(int i = 0 ; i < 1000000; i++) {
						Math.random(); // make work!
					}
				}
			}
		}
		
		public void shutdown() throws InterruptedException{
			System.out.println("Shutting down random number load generator");
			this.shutdown = true;
			join(1000);
			if(isAlive()) {
				fail("Background thread failed to shutdown!");
			}
		}
	}
	public Object lookupJMXValue(String objectName, String attributeName) throws InstanceNotFoundException, MalformedObjectNameException, ReflectionException, NullPointerException {
		final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		List<Attribute> attributes = server.getAttributes(new ObjectName(objectName), new String[] {attributeName}).asList();
		Attribute attrib = attributes.iterator().next();
		return attrib.getValue();
	}
	
	public void doPublishCheck(String objectName, String[] knownAttributes) throws Exception {
		System.out.println("Bean name: " + objectName);
		final MBeanServer server = ManagementFactory.getPlatformMBeanServer();
		final ObjectName mbeanName = new ObjectName(objectName);
		
		MBeanInfo metadata = server.getMBeanInfo(mbeanName);
		for(MBeanAttributeInfo attrib : metadata.getAttributes()) {
			final String attribName = attrib.getName();
			System.out.println("\t"+attribName + ": " + server.getAttribute(mbeanName, attribName));
		}
		
		AttributeList attributes = server.getAttributes(mbeanName, knownAttributes);
		Set<String> expectedAttributeNames = new HashSet<String>();
		for(String expectedAttribute : knownAttributes) {
			expectedAttributeNames.add(expectedAttribute);
		}
		
		for(Attribute returnedAttribute : attributes.asList()) {
			final boolean foundAttribute = expectedAttributeNames.remove(returnedAttribute.getName());
			assertTrue("Got unexpected attribute: " + returnedAttribute.getName(),foundAttribute);
		}
		
		if(expectedAttributeNames.size() > 0) {
			System.out.println("The following expected attributes were not found:");
			for(String expectedAttributeName : expectedAttributeNames) {
				System.out.println("\t" + expectedAttributeName);
			}
		}
		assertEquals("Did not find all expected attributes. See console for details",
		             0, 
		             expectedAttributeNames.size());
	}

	public static final class SelectAllQueryExp implements QueryExp {
		
		
		private static final long serialVersionUID = 1L;

		public boolean apply(ObjectName name) throws BadStringOperationException,
												BadBinaryOpValueExpException,
												BadAttributeValueExpException,
												InvalidApplicationException {
			return true;
		}
		
		public void setMBeanServer(MBeanServer s) {
			// noop
		}
	}
	
	static void configureLog4J() {
		PatternLayout layout = new PatternLayout("%d{ISO8601} %p [%t] %c{1} - %m%n");
		ConsoleAppender console = new ConsoleAppender(layout);
		console.setThreshold(Level.DEBUG);
		LogManager.getRootLogger().setLevel(Level.ERROR);
		LogManager.getLogger("com.palantir").setLevel(Level.WARN);
		LogManager.getLogger("com.palantir.opensource.sysmon").setLevel(Level.INFO);
		LogManager.getRootLogger().addAppender(console);
		System.out.println("Configured default logging for test.");
	}
}
