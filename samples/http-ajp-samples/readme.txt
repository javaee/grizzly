The sample demonstrates how easy Grizzly HttpServer could be configured to process both HTTP and AJP requests.
All we need to do is register AjpAddOn on corresponding HTTP NetworkListener (see the code).

Here we'd provide instructions on how this could be tested and how Apache server should be configured to
redirect HTTP(S) request to Grizzly HttpServer using AJP.

For simplicity here we assume Apache and Grizzly AJP sample are running on the same machine, so localhost will be
used everywhere.

Ok, let's prepare our environment. Feel free to skip some steps if you have Apache installed and/or configured.

1) Install Apache
http://httpd.apache.org/docs/2.0/install.html

assume $APACHE_HOME is the directory, where we installed Apache
on my machine it's
/home/myhome/apps/httpd-2.2.17/

Note: don't forget to replace $APACHE_HOME occurrences with the real path.


2) Download and install mod_jk
http://tomcat.apache.org/download-connectors.cgi

install mod_jk module
For example, mod_jk-1.2.31-httpd-2.2.x.so

to Apache modules directory
$APACHE_HOME/modules/


3) Configure Apache-to-Grizzly communication (Apache workers)
(Took from Amy's blog http://weblogs.java.net/blog/amyroh/archive/2009/06/running_glassfi.html)

For example, $APACHE_HOME/conf/workers.properties

# Define 1 real worker using ajp13
worker.list=worker1
# Set properties for worker1 (ajp13)
worker.worker1.type=ajp13
worker.worker1.host=localhost
worker.worker1.port=8009

$APACHE_HOME/conf/httpd.conf

LoadModule jk_module $APACHE_HOME/modules/mod_jk-1.2.31-httpd-2.2.x.so
JkWorkersFile $APACHE_HOME/conf/workers.properties
# Where to put jk logs
JkLogFile $APACHE_HOME/logs/mod_jk.log
# Set the jk log level [debug/error/info]
JkLogLevel debug
# Select the log format
JkLogStampFormat "[%a %b %d %H:%M:%S %Y] "
# JkOptions indicate to send SSL KEY SIZE,
JkOptions +ForwardKeySize +ForwardURICompat -ForwardDirectories
# JkRequestLogFormat set the request format
JkRequestLogFormat "%w %V %T"
# Send everything for context /examples to worker named worker1 (ajp13)
JkMount /grizzly* worker1


4) Start Apache HTTP Server
$APACHE_HOME/bin/apachectl start (you might need sudo)


5) Open our favorite browser and do

http://localhost:8080/grizzly
we see "Hello World", which means direct HTTP request to Grizzly HttpServer passed.

http://localhost/grizzly
we see "Hello World", which means Apache accepted the HTTP request, passed it to
Grizzly using AJP protocol, which Grizzly successfully handled and returned the
expected response.

That's it :)
