Grizzly is built, assembled and installed using Maven. Grizzly is
deployed to the [Maven Central](http://search.maven.org) repository.
Binary, source, javadoc, and sample JARS can all be found there.

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

If you're not using maven, you can download the JARs you need for your
project from maven central.

The latest stable release of Grizzly is @VERSION@.  Older versions of Grizzly
(2.2.x, 1.9.x, 1.0.x) are still available and can be found on
[Maven Central](http://search.maven.org).

---

## Core framework

This JAR is the minimum requirement for all Grizzly applications.  This provides
all core services:  TCP/UDP transports, memory management services/buffers, NIO
event loop/filter chains/filters.

```xml
<dependency>
    <groupId>org.glassfish.grizzly</groupId>
    <artifactId>grizzly-framework</artifactId>
    <version>@VERSION@</version>
</dependency>
```

## HTTP framework

`grizzly-http` contains the base logic for dealing with HTTP messages on both
the server and client sides.

```xml
<dependency>
     <groupId>org.glassfish.grizzly</groupId>
     <artifactId>grizzly-http</artifactId>
     <version>@VERSION@</version>
</dependency>
```

## HTTP Server framework

`grizzly-http-server` provides HTTP server services using an API very similar
to Servlets.

```xml
<dependency>
     <groupId>org.glassfish.grizzly</groupId>
     <artifactId>grizzly-http-server</artifactId>
     <version>@VERSION@</version>
</dependency>
```

## HTTP Servlet framework

`grizzly-http-servlet`, building on top of `grizzly-http-server`, provides
basic Servlet functionality.  NOTE:  This is not a Servlet compliant implementation
and as such, not all features exposed by a typical Servlet container are available
here.  The most recent versions of this library does provide features from the
Servlet 3.1 specification.

```xml
<dependency>
     <groupId>org.glassfish.grizzly</groupId>
     <artifactId>grizzly-http-servlet</artifactId>
     <version>@VERSION@</version>
</dependency>
```

## Port unification

`grizzly-portunif` provides the ability to run multiple protocols
(example: http, https, or other protocols) over a single TCP port.

```xml
<dependency>
     <groupId>org.glassfish.grizzly</groupId>
     <artifactId>grizzly-portunif</artifactId>
     <version>@VERSION@</version>
</dependency>
```

## Comet

`grizzly-comet`, building on top of `grizzly-http-server`, provides a framework
for building scalable Comet-based applications.

```xml
<dependency>
     <groupId>org.glassfish.grizzly</groupId>
     <artifactId>grizzly-comet</artifactId>
     <version>@VERSION@</version>
</dependency>
```

## WebSockets

`grizzly-websockets` provides a custom API (this predates JSR 356) for building
Websocket applications on both the server and client sides.

```xml
<dependency>
     <groupId>org.glassfish.grizzly</groupId>
     <artifactId>grizzly-websockets</artifactId>
     <version>@VERSION@</version>
</dependency>
```

## AJP

`grizzly-http-ajp` provides support for the AJP protocol.

```xml
<dependency>
     <groupId>org.glassfish.grizzly</groupId>
     <artifactId>grizzly-http-ajp</artifactId>
     <version>@VERSION@</version>
</dependency>
```

## JAX-WS

`grizzly-http-server-jaxws`, building on top of `grizzly-http-server`, provides
the ability to create JAX-WS applications.

```xml
<dependency>
     <groupId>org.glassfish.grizzly</groupId>
     <artifactId>grizzly-http-server-jaxws</artifactId>
     <version>@VERSION@</version>
</dependency>
```

## Monitoring

`grizzly-monitory` allows developers to leverage JMX monitoring within
their applications.

```xml
<dependency>
    <groupId>org.glassfish.grizzly</groupId>
    <artifactId>grizzly-framework-monitoring</artifactId>
    <version>@VERSION@</version>
</dependency>
```

```xml
<dependency>
    <groupId>org.glassfish.grizzly</groupId>
    <artifactId>grizzly-http-monitoring</artifactId>
    <version>@VERSION@</version>
</dependency>
```

```xml
<dependency>
    <groupId>org.glassfish.grizzly</groupId>
    <artifactId>grizzly-http-server-monitoring</artifactId>
    <version>@VERSION@</version>
</dependency>
```

## Connection Pool

`connection-pool` provides a robust client-side connection pool implementation.

```xml
<dependency>
    <groupId>org.glassfish.grizzly</groupId>
    <artifactId>connection-pool</artifactId>
    <version>@VERSION@</version>
</dependency>
```

## Server Name Indication (SNI) TLS extension support

```xml
<dependency>
    <groupId>org.glassfish.grizzly</groupId>
    <artifactId>tls-sni</artifactId>
    <version>@VERSION@</version>
</dependency>
```

## OSGi HTTP Service

`grizzly-httpservice` provides an OSGi HTTP Service implementation.  This
JAR includes the Grizzly Servlet, Websocket, port unification, and comet
libraries allowing developers to build common types of HTTP server applications
within an OSGi environment.

```xml
<dependency>
    <groupId>org.glassfish.grizzly.osgi</groupId>
    <artifactId>grizzly-httpservice-bundle</artifactId>
    <version>@VERSION@</version>
</dependency>
```

## HTTP Server Multipart

`grizzly-http-server-multipart` provides a non-blocking API for processing
multipart requests.

```xml
<dependency>
    <groupId>org.glassfish.grizzly</groupId>
    <artifactId>grizzly-http-server-multipart</artifactId>
    <version>@VERSION@</version>
</dependency>
```

## Grizzly HTTP Servlet Extras

`grizzly-http-servlet-extras`; a drop-in Servlet Filter that builds
on the HTTP Server Multipart library providing non-blocking multipart
handling.

```xml
<dependency>
    <groupId>org.glassfish.grizzly</groupId>
    <artifactId>grizzly-http-servlet-extras</artifactId>
    <version>@VERSION@</version>
</dependency>
```

---

## Bundles

In addition to the individual dependency JARs, we also offer bundles which
aggregate multiple modules together into a single JAR for convenience.

---

## The Core Framework Bundle

The `grizzly-core` bundle aggregates the `grizzly-framework` and `grizzly-portunif`
modules.

```xml
<dependency>
    <groupId>org.glassfish.grizzly</groupId>
    <artifactId>grizzly-core</artifactId>
    <version>@VERSION@</version>
</dependency>
```

## The HTTP Server Core Bundle

The `grizzly-http-server-core' bundle aggregates the `grizzly-core` bundle with
the `grizzly-http-server`, `grizzly-http-ajp`, and
`grizzly-http-server-multipart` modules.

```xml
<dependency>
    <groupId>org.glassfish.grizzly</groupId>
    <artifactId>grizzly-http-server-core</artifactId>
    <version>@VERSION@</version>
</dependency>
```

## The HTTP Servlet Bundle

The `grizzly-http-servlet-server` bundle aggregates the `grizzly-http-server-core` bundle
with the `grizzly-http-servlet` module.

```xml
<dependency>
    <groupId>org.glassfish.grizzly</groupId>
    <artifactId>grizzly-http-servlet-server</artifactId>
    <version>@VERSION@</version>
</dependency>
```

## The Grizzly Comet Bundle

The `grizzly-comet-server` bundle aggregates the `grizzly-http-server-core` bundle
with the `grizzly-comet` module.

```xml
<dependency>
    <groupId>org.glassfish.grizzly</groupId>
    <artifactId>grizzly-comet-server</artifactId>
    <version>@VERSION@</version>
</dependency>
```

## The Grizzly Websockets Bundle

The `grizzly-websocket-server` bundle aggregates the `grizzly-http-server-core` bundle
with the `grizzly-websockets` module.

```xml
<dependency>
    <groupId>org.glassfish.grizzly</groupId>
    <artifactId>grizzly-websockets-server</artifactId>
    <version>@VERSION@</version>
</dependency>
```

## The Grizzly HTTP 'All' Bundle

The `grizzly-http-all` bundle aggregates the `grizzly-http-server-core` bundle
with the `grizzly-http-servlet`, `grizzly-comet`, and `grizzly-websocket` modules.

```xml
<dependency>
    <groupId>org.glassfish.grizzly</groupId>
    <artifactId>grizzly-http-all</artifactId>
    <version>@VERSION@</version>
</dependency>
```