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

import java.util.Properties;

/**
 * A small library to make dealing with numeric values in a {@link Properties} object easier,
 * cleaner, and type safe.
 */
public class PropertiesUtils {

	/**
	 * Returns the {@link Integer} value at the specified key.
	 * @param p {@link Properties} object to extract values from
	 * @param key key to be extracted
	 * @param defaultValue default value to use if specified key doesn't exist
	 * @return the {@link Integer} stored in the {@link Properties} object.
	 * @throws NumberFormatException if the value stored in the {@link Properties} object
	 * does not parse as an {@link Integer}.
	 */
	public static final int extractInteger(Properties p, String key, int defaultValue) throws NumberFormatException {
		String intProperty = p.getProperty(key);
		if(intProperty == null){
			return defaultValue;
		} else {
			return Integer.parseInt(intProperty);
		}
	}

	/**
	 * Returns the {@link Long} value at the specified key.
	 * @param p {@link Properties} object to extract values from
	 * @param key key to be extracted
	 * @param defaultValue default value to use if specified key doesn't exist
	 * @return the {@link Long} stored in the {@link Properties} object.
	 * @throws NumberFormatException if the value stored in the {@link Properties} object
	 * does not parse as an {@link Long}.
	 */
	public static final long extractLong(Properties p, String key, long defaultValue) throws NumberFormatException {
		String intProperty = p.getProperty(key);
		if(intProperty == null){
			return defaultValue;
		} else {
			return Integer.parseInt(intProperty);
		}
	}

}
