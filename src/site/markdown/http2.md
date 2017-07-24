## HTTP/2 Overview

Starting with 2.4.0, Grizzly offers support for HTTP/2. The goal of HTTP/2
is to reduce web page load time. This is achieved by prioritizing and
multiplexing the transfer of web page resources so that only one
connection per client is required.

### How it works

The HTTP/2 protocol implementation is represented, at least in the server
case, a Filter that sits between the HTTP/1.1 Filter and the HTTP server
filter.  All the Filters upstream to HTTP/2 filter receive HTTP messages
for processing, so they are not even aware of HTTP/2 protocol.

Grizzly supports HTTP/2 over plain text and TLS.  However, to support
HTTP/2 over TLS, you will be required to include a special library on
the JVM's bootclasspath to override the default SSL/TLS handshake
implementation with support for Application-Layer Protocol Negotiation
 Extension. (see <https://tools.ietf.org/html/rfc7540#section-3.3>
for details on ALPN).  Because this requires modifying internal JDK
classes, the ALPN implementation is sensitive to version of the Oracle
JDK that's being used.  As such, the current Grizzly ALPN implementation
requires JDK 1.8.0_(121,131,141).  Later versions of JDK8 *may* work, but if the
classes change in a significant way, it will result in a runtime issue.

<table>
<caption>Application-Level Protocol Negotiation JARs</caption>
<thead>
<tr class="header">
<th align="left">Dependency</th>
<th align="left">Description</th>
</tr>
</thead>
<tbody>
<tr class="odd">
<td align="left"><a href="http://search.maven.org/remotecontent?filepath=org/glassfish/grizzly/grizzly-npn-api/1.7/grizzly-npn-api-1.7.jar">grizzly-npn-api-1.7.jar</a></td>
<td align="left">This JAR exposes the Grizzly-side of the ALPN API. Typically won't be needed by developers unless they wish to expose custom protocols via ALPN.</td>
</tr>
<tr class="even">
<td align="left"><a href="http://search.maven.org/remotecontent?filepath=org/glassfish/grizzly/grizzly-npn-bootstrap/1.7/grizzly-npn-bootstrap-1.7.jar">grizzly-npn-bootstrap-1.7.jar</a></td>
<td align="left">Includes both the ALPN API and the SSL implementation overrides. This JAR must be specified on the bootclasspath (-Xbootclasspath/p:&lt;path_to_and_including_grizzly-npn-bootstrap-1.7.jar&gt;) in order for ALPN to function.</td>
</tr>
<tr class="odd">
<td align="left"><a href="http://search.maven.org/remotecontent?filepath=org/glassfish/grizzly/grizzly-npn-osgi/1.7/grizzly-npn-osgi-1.7.jar">grizzly-npn-osgi-1.7.jar</a></td>
<td align="left">This JAR is an OSGi bundle fragment. It's used to ensure the ALPN API classes are properly available to an OSGi runtime.</td>
</tr>
</tbody>
</table>


In order to simplify HttpServer HTTP/2 configuration, there is a HTTP/2AddOn
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

// Create default HTTP/2 configuration and provide it to the AddOn
Http2Configuration configuration = Http2Configuration.builder().build();
Http2AddOn http2Addon = new Http2AddOn(configuration); 

// Register the Addon.
listener.registerAddOn(http2Addon);

httpServer.addListener(listener);
```

The Http2Configuration class provides the following properties:

<table>
<caption>Http2Configuration Properties</caption>
<tbody>
<tr class="odd">
<td align="left">maxConcurrentStreams</td>
<td align="left">Configures how many streams may be multiplexed over a single connection. The default is 100.</td>
</tr>
<tr class="even">
<td align="left">initialWindowSize</td>
<td align="left">Configures how much memory, in bytes,  each stream will consume on the server side. The default is 64KB.</td>
</tr>
<tr class="odd">
<td align="left">maxFramePayLoadSize</td>
<td align="left">Configures the upper bound, in bytes, on allowable frame sizes. Frames above this bound will be rejected.
If not explicitly configured, the default will be the largest allowed by the RFC.</td>
</tr>
<tr class="even">
<td align="left">maxHeaderListSize</td>
<td align="left">Configures the maximum size, in bytes, of the headers.  The default is 4096.</td>
</tr>
<tr class="odd">
<td align="left">disableCipherCheck</td>
<td align="left">The HTTP/2 RFC defines a set of cipher suites that shouldn't be allowed to
be used to establish a secure connection.  Set this to true to disable this security protection.</td>
</tr>
<tr class="even">
<td align="left">priorKnowledge</td>
<td align="left">This property is relevant to the client only.  It connects directly to a
server using special magic instead of using the HTTP upgrade mechanism.  Only use this
if the target server is known to support HTTP/2.</td>
</tr>
<tr class="odd">
<td align="left">threadPoolConfig</td>
<td align="left">If specified, a new thread pool will be created to process
HTTP streams and push requests.</td>
</tr>
<tr class="even">
<td align="left">executorService</td>
<td align="left">Provide an existing ExecutorService for processing HTTP/2 streams
and push requests.</td>
</tr>
</tbody>
</table>


### HTTP/2 Server Push

Starting with 2.4.0, Grizzly offers support for HTTP/2 server push mechanism
<https://tools.ietf.org/html/rfc7540#section-8.2>.

> Quote:  
HTTP/2 allows a server to pre-emptively send (or "push") responses
(along with corresponding "promised" requests) to a client in
association with a previous client-initiated request.  This can be
useful when the server knows the client will need to have those
responses available in order to fully process the response to the
original request.

To support this feature, we've provided an API similar to the PushBuilder
defined in Servlet 4.0.  This mechanism allows the developer to issue
a push for an existing static or dynamic resource.

Consider the following example:

```java
final HttpHandler mainHandler = new HttpHandler() {
    @Override
    public void service(final Request request, final Response response) throws Exception {
        final PushBuilder builder = request.newPushBuilder();
        builder.path("/resource1");
        builder.push();
        builder.path("/resource2");
        builder.push();
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/plain");
        response.getWriter().write("main");
    }
};

final HttpHandler resource1 = new HttpHandler() {
    @Override
    public void service(final Request request, final Response response) throws Exception {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/plain");
        response.getWriter().write("resource1");
    }
};

final HttpHandler resource2 = new HttpHandler() {
    @Override
    public void service(final Request request, final Response response) throws Exception {
        response.setCharacterEncoding("UTF-8");
        response.setContentType("text/plain");
        response.getWriter().write("resource2");
    }
};
```

In the example above, three _HttpHandler_s are defined.  The first, _mainHandler_,
pushes two resources to the client prior to sending its own response (NOTE: the _push()_
calls do not block).  The runtime will send the HTTP/2 PUSH_PROMISE frame to the peer
and then dispatch the request back through the FilterChain for normal request/response
processing.

The PushBuilder provides several APIs for generating conditional requests so that
content isn't needlessly pushed to the peer.

Care should be taken that the call to _Request.newPushBuilder()_ will return null
if HTTP/2 push has been disabled for this session.

See the _org.glassfish.grizzly.http.server.http2.PushBuilder_ javadocs for more details
on what features it provides.
