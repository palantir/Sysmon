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

import java.util.Timer;
import java.util.TimerTask;

import org.apache.log4j.LogManager;
import org.apache.log4j.Logger;

/**
 * <p>
 * Task class that wraps the act of setting a timer to 
 * trigger a thread interrupt a later date.
 * </p>
 * 
 */
public class InterruptTimerTask extends TimerTask {

	static final Logger log = LogManager.getLogger(InterruptTimerTask.class);
	static int id = 0;

	private static Timer interruptTimer = new Timer("Thread Interrupt Timer",true);
	
	final Thread threadToInterrupt;

	private InterruptTimerTask(Thread threadToInterrupt) {
		this.threadToInterrupt = threadToInterrupt;
	}

	@Override
	public void run() {
		log.debug("Sending interrupt to thread " + threadToInterrupt.getName());
		threadToInterrupt.interrupt();
	}

	/**
	 * Cancels this timer and clears the 
	 * interrupt state of this thread .
	 */
	@Override
	public boolean cancel() {
		return this.cancel(true);
	}
	
	public boolean cancel(boolean clearInterrupt){
		boolean rc = super.cancel();
		log.trace("Interrupt timer was cancelled, no interrupt will be sent");
		if(clearInterrupt){
			if(Thread.currentThread() != threadToInterrupt){
				throw new IllegalStateException("Cancel must be called from the same thread that set the timer.\n" +
						"Original Thread: " + threadToInterrupt.getName() + ", Currect Thread: " + Thread.currentThread().getName());
			}
			Thread.interrupted();
		}
		return rc;
	}
	
	public static InterruptTimerTask setInterruptTimer(long timeToSleep) {
		InterruptTimerTask tt = new InterruptTimerTask(Thread.currentThread());
		interruptTimer.schedule(tt, timeToSleep);
		return tt;
	}
	
}

