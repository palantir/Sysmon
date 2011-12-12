![Palantir Logo](/palantir/Sysmon/wiki/palantir-logo.png)
# Sysmon - lightweight platform monitoring for Java VMs #

---

### About #

Sysmon is a lightweight platform monitoring tool.  It's designed to gather performance data (CPU, disks, network, etc.) from the host running the Java VM.  This data is gathered, packaged, and published via Java Management Extensions (JMX) for access using the JMX APIs and standard tools (such as [jconsole](http://download.oracle.com/javase/6/docs/technotes/guides/management/jconsole.html)).

Sysmon can be run as a standalone daemon or as a library to add platform monitoring to any application.

Currently, Linux is the only platform for which monitoring has been implemented.  However, building new or porting existing monitors to other Unix or Unix-like platforms should be pretty straightforward.

### Project Resources #

* The [Wiki](Sysmon/wiki) has all the project documentation.
* API docs are available [here](http://palantir.github.com/Sysmon/apidocs)
* Mailing lists are hosted on Google Groups:
    * [Announce](http://groups.google.com/group/ptoss-sysmon-announce)
    * [General](http://groups.google.com/group/ptoss-sysmon)
* Please file issues on the [Github Issue Tracker](/palantir/Sysmon/issues)
* Email project admin: [Ari Gesher](mailto:agesher@palantir.com)


## License #

Sysmon is made available under the Apache 2.0 License.

>Copyright 2011 Palantir Technologies
>
>Licensed under the Apache License, Version 2.0 (the "License");
>you may not use this file except in compliance with the License.
>You may obtain a copy of the License at
>
><http://www.apache.org/licenses/LICENSE-2.0>
>
>Unless required by applicable law or agreed to in writing, software
>distributed under the License is distributed on an "AS IS" BASIS,
>WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
>See the License for the specific language governing permissions and
>limitations under the License.

<img id="disclaimer" src="http://palantir.com/_ptwp_live_ect0/wp-content/uploads/2011/09/disclaimer.png"/>
