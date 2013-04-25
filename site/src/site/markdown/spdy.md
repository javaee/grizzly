SPDY
====

Overview
========

Starting with 2.3, Grizzly offers support for SPDY/3. The goal of SPDY
is to reduce web page load time. This is achieved by prioritizing and
multiplexing the transfer of web page resources so that only one
connection per client is required.

Currently, Grizzly implements only SPDY/3 support per
<http://tools.ietf.org/html/draft-mbelshe-httpbis-spdy-00> with a few
caveats.

-   As Grizzly is primarily providing SPDY support for typical HTTP/1.1
    use cases, the implementation ignores header frames that are sent
    out-of-band of the typical request/response cycle.

-   Expect/100-Continue is not currently supported. This is a weak area
    of the specification. There has been some discussion on this topic
    with the protocol specification authors, but no resolution has been
    published.

-   No support of Credential frames. Give this, we recommend that the
    user-agent, if possible, not share SPDY sessions between multiple
    origins. Additionally, if mutual certificate authentication is
    required, we recommend that the Grizzly SSL configuration set
    client-auth to need.

-   As SPDY requires the Next Protocol Negotiation (NPN) TLS extension,
    Grizzly's spdy implementation will only operate with OpenJDK 7u14.
    Later versions of the OpenJDK should work, but there may be some
    gotchas that would require us to release new versions of the Grizzly
    NPN implementation.

How it works
============

The SPDY protocol implementation is mainly represented by two Filters:
SpdyFramingFilter, SpdyHandlerFilter. The SpdyFramingFilter is
responsible for constructing/deconstructing SPDY frame messages and
SpdyHandlerFilter contains the actual processing logic, which works as a
codec between SPDY and HTTP messages. All the Filters upstream to
SpdyHandlerFilter receive HTTP messages for processing, so they are not
even aware of SPDY protocol.

In order to simplify HttpServer SPDY configuration, there is a SpdyAddOn
available, which may be registered on the required HttpServer's
NetworkListener like:

```java
HttpServer httpServer = new HttpServer();
NetworkListener listener =
         new NetworkListener("grizzly",
                             NetworkListener.DEFAULT_NETWORK_HOST,
                             PORT);
listener.setSecure(true);

// Include environmental specific SSL configuration.
listener.setSSLEngineConfig(...);

SpdyAddOn sdpyAddon = new SpdyAddOn(); // optionally configure
listener.registerAddOn(spdyAddon);

httpServer.addListener(listener);
```

SPDY Configuration (Grizzly standalone)
=======================================

Here's the high-level overview of getting SPDY working with a standalone
Grizzly HTTP application.

-   Include the grizzly-npn-bootstrap-1.0.jar in the bootclasspath of
    the Grizzly application: -Xbootclasspath/p:\<path-to-jar\>.

-   Register the SpdyAddon with the NetworkListener.

The SpdyAddon currently exposes two properties that primarily control
the memory characteristics of SPDY on the server side.

<table>
<caption>SpdyAddOn Properties</caption>
<tbody>
<tr class="odd">
<td align="left">maxConcurrentStreams</td>
<td align="left">Configures how many streams may be multiplexed over a single connection. The default is 100.</td>
</tr>
<tr class="even">
<td align="left">initialWindowSizeInBytes</td>
<td align="left">Configures how much memory each stream will consume on the server side. The default is 64KB.</td>
</tr>
<tr class="odd">
<td align="left">maxFrameLength</td>
<td align="left">Configures the upper bound on allowable frame sizes. Frames above this bound will be rejected.</td>
</tr>
</tbody>
</table>

SPDY Configuration (GlassFish 4)
================================

There's a little more involved when configuring GlassFish 4 for SPDY
support.

-   Copy the grizzly-npn-osgi-1.0.jar to the GF\_HOME/modules directory.

-   Copy the grizzly-spdy-2.3.jar to the GF\_HOME/modules
    directory.

-   Update the domain.xml's JVM configuration section to include a
    reference to the grizzly-npn-bootstrap-1.0.jar within
    -Xbootclasspath (-Xbootclasspath/p:\<path-to-jar\>. We recommend
    adding a new transport to the domain.xml that sets the io-strategy
    property on the transport named TCP will need to be set to
    "org.glassfish.grizzly.strategies.SameThreadIOStrategy.

-   Start the server.

-   Run the asadmin command enable-spdy. Here's an example of the
    command being run against the default ssl listener in GlassFish:

    *asadmin enable-spdy http-listener-2*

When running enable-spdy, it is possible to pass SpdyAddon configuration
values via --max-concurrent-streams, --initial-window-size-bytes, and
max-frame-length-in-bytes. These properties may also be set
after-the-fact using *asadmin set*.

Dependencies
============

As stated previously, in order to use SPDY with Grizzly, you will need
to use OpenJDK 7u14. The following JARs are also required. See the
following table for details:

<table>
<caption>SPDY Dependencies</caption>
<thead>
<tr class="header">
<th align="left">Dependency</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td align="left"><a href="http://search.maven.org/remotecontent?filepath=org/glassfish/grizzly/grizzly-npn-api/1.0/grizzly-npn-api-1.0.jar">grizzly-npn-api-1.0.jar</a></td>
<td align="left">This JAR exposes the Grizzly-side of the Next Protocol Negotiation API. Typically won't be needed by developers unless they wish to expose custom protocols via Next Protocol Negotiation.</td>
</tr>
<tr class="even">
<td align="left"><a href="http://search.maven.org/remotecontent?filepath=org/glassfish/grizzly/grizzly-npn-bootstrap/1.0/grizzly-npn-bootstrap-1.0.jar">grizzly-npn-bootstrap-1.0.jar</a></td>
<td align="left">Includes both the Next Protocol Negotiation API and the SSL implementation overrides. This JAR must be specified on the bootclasspath in order for Next Protocol Negotiation to function.</td>
</tr>
<tr class="odd">
<td align="left"><a href="http://search.maven.org/remotecontent?filepath=org/glassfish/grizzly/grizzly-npn-osgi/1.0/grizzly-npn-osgi-1.0.jar">grizzly-npn-osgi-1.0.jar</a></td>
<td align="left">This JAR is an OSGi bundle fragment. It's used to ensure the Next Protocol Negotiation API classes are properly available to an OSGi runtime.</td>
</tr>
</tbody>
</table>

One item to keep in mind is that the SSL part of the NPN implementation
is sensitive the the OpenJDK version being used. It won't work with
older update releases, and may not work with newer. If testing with a
newer OpenJDK release and an issue is found, please log a
[bug](http://java.net/jira/browse/GRIZZLY).
