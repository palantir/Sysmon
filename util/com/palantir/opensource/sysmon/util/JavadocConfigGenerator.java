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

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.palantir.opensource.sysmon.linux.LinuxDiskspaceJMXWrapper;
import com.palantir.opensource.sysmon.linux.LinuxEntropyLevelJMXWrapper;
import com.palantir.opensource.sysmon.linux.LinuxIOStatJMXWrapper;
import com.palantir.opensource.sysmon.linux.LinuxLoadAverageJMXWrapper;
import com.palantir.opensource.sysmon.linux.LinuxMonitor;
import com.palantir.opensource.sysmon.linux.LinuxNetStatJMXWrapper;
import com.palantir.opensource.sysmon.linux.LinuxVMStatJMXWrapper;


public class JavadocConfigGenerator {

	static Class<?>[] classes = new Class<?>[]{
					LinuxDiskspaceJMXWrapper.class,
					LinuxEntropyLevelJMXWrapper.class,
					LinuxIOStatJMXWrapper.class,
					LinuxLoadAverageJMXWrapper.class,
					LinuxNetStatJMXWrapper.class,
					LinuxVMStatJMXWrapper.class,
	};
	public static void main(String[] args) throws Exception {
		
		for(Class<?> c : classes) {
			String name = c.getSimpleName();
			System.out.println("----- " + name + " -----");
			printConfigSection(c);
			System.out.println("\n");
			printJMXDataPathSection(c);
			System.out.println("----- " + name + " -----\n");
		}
	}
	
	static final void printJMXDataPathSection(Class<?> c) throws Exception {
		String name = c.getSimpleName();
		String beanpath = null;
		try {
			Field f = c.getDeclaredField("OBJECT_NAME");
			beanpath = LinuxMonitor.CONFIG_KEY_JMX_BEAN_PATH + f.get(null);
		} catch (NoSuchFieldException nsfe){
			// ignore
		}

		try {
			Field f = c.getDeclaredField("OBJECT_NAME_PREFIX");
			beanpath = LinuxMonitor.CONFIG_KEY_JMX_BEAN_PATH + f.get(null) + "TODO";
		} catch (NoSuchFieldException nsfe){
			// ignore
		}

		
		if(beanpath == null){
			throw new Exception("No beanpath found for " + name);
		}
		
		System.out.println(" * <h3>JMX Data Path</h3>");
		System.out.println(" * <code>" + beanpath + "</code>");
		
	}
	static final void printConfigSection(Class<?> c) throws Exception {
		Field fields[] = c.getFields();
		ArrayList<Field> configFields = new ArrayList<Field>();
		for(Field f : fields){
			int flags = f.getModifiers();
			if(f.getName().startsWith("CONFIG_") &&
               Modifier.isFinal(flags) && Modifier.isStatic(flags)) {
				configFields.add(f);
			}
		}
		
		
		System.out.println(" * <h3>Configuration parameters</h3>");
		System.out.println(" * <em>Note that any value not set in the config file will use the default value.</em>");
		System.out.println(" * <table cellspacing=5 cellpadding=5><tr><th>Config Key</th><th>Description</th><th>Default Value</th><th>Constant</th></tr>");
		for(Field f : configFields){
			System.out.println(" * <tr><td>" + f.get(null) + "</td>");
			System.out.println(" * <td>TODO</td>");
			System.out.println(" * " + getDefaultValue(f));
			System.out.println(" * <td>{@link #" + f.getName() + "}</td></tr>");
		}
		System.out.println(" * </tr></table>");
	}
	
	static final Pattern CONFIG_EXTRACTOR = Pattern.compile("^CONFIG_KEY(_.*)$");
	
	static String getDefaultValue(Field f) throws Exception{
		try {
			String name = f.getName();
			Matcher m = CONFIG_EXTRACTOR.matcher(name);
			if(!m.matches()) {
				throw new Exception("No default value for " + name);
			}
			String fieldName = "DEFAULT" + m.group(1);
			Class<?> declaringClass = f.getDeclaringClass();
			Field def = declaringClass.getDeclaredField(fieldName);
			return "<td><code>" + def.get(null) + "</code></td>";
		} catch (Exception e) {
			System.err.println(f.getDeclaringClass().getSimpleName() + "#" + f.getName());
			throw e;
		}
	}
}
