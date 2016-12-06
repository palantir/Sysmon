Sysmon - lightweight platform monitoring for Java VMs
=====================================================
Sysmon is a lightweight platform monitoring tool.  It's designed to gather performance data (CPU, disks, network, etc.) from the host running the Java VM. This data is gathered, packaged, and published via Java Management Extensions (JMX) for access using the JMX APIs and standard tools (such as [jconsole](http://download.oracle.com/javase/6/docs/technotes/guides/management/jconsole.html) or [jmxtrans](http://code.google.com/p/jmxtrans/)).

Sysmon can be run as a standalone daemon or as a library to add platform monitoring to any application.

Currently, Linux is the only platform for which monitoring has been implemented.  However, building new or porting existing monitors to other Unix or Unix-like platforms should be pretty straightforward.

Resources
---------
* [Project Wiki](https://github.com/palantir/Sysmon/wiki)
* [API docs](http://palantir.github.io/Sysmon/apidocs/)

License
-------
This project is made available under the [Apache 2.0 License](http://www.apache.org/licenses/LICENSE-2.0).
