Dependencies
============

Grizzly is built, assembled and installed using Maven. Grizzly is
deployed to the Maven Central repository at the following location:
<http://repo1.maven.org/>. Jars, Jar sources, Jar JavaDoc and samples
are all available on the Maven Central repository.

An application depending on Grizzly requires that it in turn includes
the set of jars that Grizzly depends on. Grizzly has a pluggable
component architecture so the set of jars required to be include in the
class path can be different for each application.

All Grizzly components are built using Java SE 6 compiler. It means, you
will also need at least Java SE 6 to be able to compile and run your
application.

Developers using maven are likely to find it easier to include and
manage dependencies of their applications than developers using ant or
other build technologies. This document will explain to both maven and
non-maven developers how to depend on Jersey for their application. Ant
developers are likely to find the Ant Tasks for Maven very useful.

In general, if you're not using Maven, most probably you'd need to
download dependencies (jar files) directly from the Maven repository.

Grizzly's runtime dependencies are categorized into the following:

-   Core framework. The Grizzly core module. The rest of Grizzly modules
    depend on it;

-   HTTP framework. The HTTP Codec implementation;

-   HTTP Server framework. Grizzly HTTP server implementation;

-   HTTP Servlet framework. Basic Grizzly based Servlet support;

-   Port unification;

-   Comet;

-   WebSockets;

-   AJP;

-   JAX-WS.

Core framework
==============

Maven developers require a dependency on the grizzly core module. The
following dependency needs to be added to the pom:

```xml
<dependency>
    <groupId>org.glassfish.grizzly</groupId>
    <artifactId>grizzly-framework</artifactId>
    <version>2.3</version>
</dependency>
```

Non-maven developers require:

-   [grizzly-framework.jar](https://maven.java.net/content/repositories/releases/org/glassfish/grizzly/grizzly-framework/2.3/grizzly-framework-2.3.jar)

-   [gmbal-api-only.jar](http://download.java.net/maven/2/org/glassfish/gmbal/gmbal-api-only/3.0.0-b023/gmbal-api-only-3.0.0-b023.jar)

HTTP framework
==============

Maven developers require a dependency on the http module. The following
dependency needs to be added to the pom:

```xml
<dependency>
     <groupId>org.glassfish.grizzly</groupId>
     <artifactId>grizzly-http</artifactId>
     <version>2.3</version>
</dependency>
```

Non-maven developers will need to download the core jars as well as:

-   [grizzly-http.jar](https://maven.java.net/content/repositories/releases/org/glassfish/grizzly/grizzly-http/2.3/grizzly-http-2.3.jar)

HTTP Server framework
=====================

Maven developers require a dependency on the http-server module. The
following dependency needs to be added to the pom:

```xml
<dependency>
     <groupId>org.glassfish.grizzly</groupId>
     <artifactId>grizzly-http-server</artifactId>
     <version>2.3</version>
</dependency>
```

Non-maven developers will need to download the http and core jars as well as:

-   [grizzly-http-server.jar](https://maven.java.net/content/repositories/releases/org/glassfish/grizzly/grizzly-http-server/2.3/grizzly-http-server-2.3.jar)

HTTP Servlet framework
======================

Maven developers require a dependency on the http-servlet module. The
following dependency needs to be added to the pom:

```xml
<dependency>
     <groupId>org.glassfish.grizzly</groupId>
     <artifactId>grizzly-http-servlet</artifactId>
     <version>2.3</version>
</dependency>
```

Non-maven developers will need to download the http-server, http, and core jars
as well as:

-   [grizzly-http-servlet.jar](https://maven.java.net/content/repositories/releases/org/glassfish/grizzly/grizzly-http-servlet/2.3/grizzly-http-servlet-2.3.jar)

-   [servlet-api.jar](http://mirrors.ibiblio.org/pub/mirrors/maven2/javax/servlet/servlet-api/2.5/servlet-api-2.5.jar)

Port unification
================

Maven developers require a dependency on the portunif module. The
following dependency needs to be added to the pom:

```xml
<dependency>
     <groupId>org.glassfish.grizzly</groupId>
     <artifactId>grizzly-portunif</artifactId>
     <version>2.3</version>
</dependency>
```

Non-maven developers will need to download the core jars as well as:

-   [grizzly-portunif.jar](https://maven.java.net/content/repositories/releases/org/glassfish/grizzly/grizzly-portunif/2.3/grizzly-portunif-2.3.jar)

Comet
=====

Maven developers require a dependency on the http-comet module. The
following dependency needs to be added to the pom:

```xml
<dependency>
     <groupId>org.glassfish.grizzly</groupId>
     <artifactId>grizzly-comet</artifactId>
     <version>2.3</version>
</dependency>
```

Non-maven developers will need to download the core, http, and http-server jars
as well as:

-   [grizzly-comet.jar](https://maven.java.net/content/repositories/releases/org/glassfish/grizzly/grizzly-comet/2.3/grizzly-comet-2.3.jar)

WebSockets
==========

Maven developers require a dependency on the websockets module. The
following dependency needs to be added to the pom:

```xml
<dependency>
     <groupId>org.glassfish.grizzly</groupId>
     <artifactId>grizzly-websockets</artifactId>
     <version>2.3</version>
</dependency>
```

Non-maven developers will need to download the core, http, and http-server jars
as well as:

-   [grizzly-websockets.jar](https://maven.java.net/content/repositories/releases/org/glassfish/grizzly/grizzly-websockets/2.3/grizzly-websockets-2.3.jar)

AJP
===

Maven developers require a dependency on the ajp module. The following
dependency needs to be added to the pom:

```xml
<dependency>
     <groupId>org.glassfish.grizzly</groupId>
     <artifactId>grizzly-http-ajp</artifactId>
     <version>2.3</version>
</dependency>
```

Non-maven developers will need to download the core, http, and http-server jars
as well as:

-   [grizzly-http-ajp.jar](https://maven.java.net/content/repositories/releases/org/glassfish/grizzly/grizzly-http-ajp/2.3/grizzly-http-ajp-2.3.jar)

JAX-WS
======

Maven developers require a dependency on the Grizzly jax-ws module. The
following dependency needs to be added to the pom:

```xml
<dependency>
     <groupId>org.glassfish.grizzly</groupId>
     <artifactId>grizzly-http-server-jaxws</artifactId>
     <version>2.3</version>
</dependency>
```

Non-maven developers will need to download the core, http, and http-server jars
as well as:

-   [grizzly-http-server-jaxws.jar](https://maven.java.net/content/repositories/releases/org/glassfish/grizzly/grizzly-http-server-jaxws/2.3/grizzly-http-server-jaxws-2.3.jar)


