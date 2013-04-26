The HTTP Server Framework
=========================

Overview
========

The Grizzly HTTP server framework builds off the HTTP codec framework to
provide a more useful abstraction for day-to-day work. At a high level,
this framework includes the following:

-   A simple server API for easy embedding of Grizzly within an
    application.

-   Similar abstractions to those offered by the Servlet specification:
    HttpHandler (Servlet), Request (HttpServletRequest), Response
    (HttpServletResponse).

-   The ability to deal with long running HTTP transactions via response
    suspend/resume facilities.

-   Support of non-blocking IO streams (inbound and outbound).

-   A file cache for static content.

Building a Simple Web Server
============================

Components of the Framework
---------------------------

This section will cover the major components of this framework.

<table>
<caption>HTTP Server Components</caption>
<tbody>
<tr class="odd">
<td align="left">HttpServer</td>
<td align="left">This is the Grizzly HTTP server which can be used to create standalong HTTP programs or embed Grizzly within other application to provide HTTP services.</td>
</tr>
<tr class="even">
<td align="left">ServerConfiguration</td>
<td align="left">This class allows developer to add custom HttpHandler implementations to the server as well as exposing JMX/monitoring features.</td>
</tr>
<tr class="odd">
<td align="left">NetworkListener</td>
<td align="left">This is an abstraction of the Grizzly NIOTransport and Filter implementations. It also allows the enabling/disabling of HTTP-related features such as keep-alive, chunked transfer-encoding, cusom addons etc. HttpServer can support multiple NetworkListeners. Also, keep in mind that all HttpHandlers added to the ServerConfiguration will be shared across all listeners.</td>
</tr>
<tr class="even">
<td align="left">HttpHandler</td>
<td align="left">HttpHandler is akin to javax.servlet.Servlet.</td>
</tr>
<tr class="odd">
<td align="left">Request</td>
<td align="left">Request is similar to javax.servlet.http.HttpServletRequest</td>
</tr>
<tr class="even">
<td align="left">Response</td>
<td align="left">Request is similar to javax.servlet.http.HttpServletResponse</td>
</tr>
<tr class="odd">
<td align="left">Session</td>
<td align="left">Session is similar to javax.servlet.http.HttpSession</td>
</tr>
<tr class="even">
<td align="left">HttpServerFilter</td>
<td align="left">This Filter implementation provides the high-level HTTP request/response processing. Note: This Filter is automatically added to the FilterChain used by the NetworkListener, but if a custom chain as well as this level of HTTP processsing, this Filter will need to be added to the chain.</td>
</tr>
<tr class="odd">
<td align="left">FileCacheFilter</td>
<td align="left">This Filter provides static resource caching capabilites. Like the HttpServerFilter, if file caching is enabled, this Filter will be added automatically.</td>
</tr>
<tr class="even">
<td align="left">AddOn</td>
<td align="left">The general interface for HttpServer addons, which suppose to extend basic HttpServer functionality.</td>
</tr>
</tbody>
</table>

Quick Start
-----------

To get started with the HTTP server framework you'll need to include the
module in your project:

```xml
<dependencies>
    <dependency>
        <groupId>org.glassfish.grizzly</groupId>
        <artifactId>grizzly-http-server</artifactId>
        <version>2.3</version>
    </dependency>
</dependencies>
```

Once the dependencies are in place, the absolute simplest, albeit
not-very-useful, server one can create is:

```java
HttpServer server = HttpServer.createSimpleServer();
try {
    server.start();
    System.out.println("Press any key to stop the server...");
    System.in.read();
} catch (Exception e) {
    System.err.println(e);
}
```

This will create a Grizzly HTTP server listening on 0.0.0.0:8080 and
will serve content from the directory in which the JVM was started. As
stated before, while this demonstrates the ease of embedding Grizzly,
it's not very useful.

Let's add a HttpHandler to server the current time.

```java
HttpServer server = HttpServer.createSimpleServer();
server.getServerConfiguration().addHttpHandler(
    new HttpHandler() {
        public void service(Request request, Response response) throws Exception {
            final SimpleDateFormat format = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
            final String date = format.format(new Date(System.currentTimeMillis()));
            response.setContentType("text/plain");
            response.setContentLength(date.length());
            response.getWriter().write(date);
        }
    },
    "/time");
try {
    server.start();
    System.out.println("Press any key to stop the server...");
    System.in.read();
} catch (Exception e) {
    System.err.println(e);
}
```

Line 2 adds a new HttpHandler (think Servlet) to service requests make
to /time. Any other requests to the server will be considered requests
for static content served from the direction in which the JVM was
started.

HTTP Server Configuration
=========================

<table>
<caption>ServerConfiguration Properties</caption>
<tbody>
<tr class="odd">
<td align="left">name</td>
<td align="left">Set the name of this HttpServer instance. If no name is defined, one will be assigned.</td>
</tr>
<tr class="even">
<td align="left">jmxEnabled</td>
<td align="left">Determines whether or not JMX monitoring will be enabled for this HttpServer instance. This property can be changed at runtime.</td>
</tr>
<tr class="odd">
<td align="left">version</td>
<td align="left">Set the version of this server instance. If not explicitly set, the version will be 2.3.</td>
</tr>
</tbody>
</table>

In addition to the properties described above, the ServerConfiguration
allows the addition (addHttpHandler()), removal (removeHttpHandler(),
and listing of (getHttpHandlers()) HttpHandlers.

<table>
<caption>NetworkListener Properties</caption>
<tbody>
<tr class="odd">
<td align="left">name</td>
<td align="left">The logical name of this listener (settable only by constructor).</td>
</tr>
<tr class="even">
<td align="left">host</td>
<td align="left">The network host to which this listener will bind. If not user specified, it will bind to 0.0.0.0 (settable only by constructor).</td>
</tr>
<tr class="odd">
<td align="left">port</td>
<td align="left">The network port to which this listener will bind. If not user specified, it will bind to port 8080 (settable only by constructor).</td>
</tr>
<tr class="even">
<td align="left">portRange</td>
<td align="left">Range of ports to attempt to bind the server to. The first port that can be bound will be used.</td>
</tr>
<tr class="odd">
<td align="left">keepAlive</td>
<td align="left">Returns the keep-alive configuration for this listener. This allows configuration for connection idle timeout (default of 30 seconds) and max keep-alive (default of 256) requests configuration.</td>
</tr>
<tr class="even">
<td align="left">transport</td>
<td align="left">This property will typically be used for fine tuning the transport configuration, however, custom transports can also be provided.</td>
</tr>
<tr class="odd">
<td align="left">addons</td>
<td align="left">The set of addons, which suppose to extend basic HttpServer functionality.</td>
</tr>
<tr class="even">
<td align="left">chunkingEnabled</td>
<td align="left">Enable/disable the chunk transfer-encoding (defaults to enabled).</td>
</tr>
<tr class="odd">
<td align="left">secure</td>
<td align="left">Enable/disable SSL/TLS support (defaults to disabled)</td>
</tr>
<tr class="even">
<td align="left">sslEngineConfig</td>
<td align="left">If SSL/TLS is enabled, an SSLEngineConfigurator will need to be specified by the developer. This controls how the SSLEngine will be created.</td>
</tr>
<tr class="odd">
<td align="left">maxHttpHeaderSize</td>
<td align="left">Specifies, in bytes, the maximum size an http message header may be before being rejected. This configuration is only applicable to incoming requests.</td>
</tr>
<tr class="even">
<td align="left">filterChain</td>
<td align="left">Allows customization of the FilterChain used by this listener instance.</td>
</tr>
<tr class="odd">
<td align="left">fileCache</td>
<td align="left">Allows customization of the FileCache configuration used by this listener instance.</td>
</tr>
<tr class="even">
<td align="left">contentEncodings</td>
<td align="left">The content encodings (gzip as an example) that may be applied for HTTP transactions on this listener. Custom encodings may be provided.</td>
</tr>
<tr class="odd">
<td align="left">maxPendingBytes</td>
<td align="left">Specifies the maximum number of bytes that may be pending to be written to a particular connection. If bytes aren't being consumed and this value is exceeded, the connection will be closed.</td>
</tr>
<tr class="even">
<td align="left">maxRequestHeaders</td>
<td align="left">Maximum number of headers allowed within a particular request. If this value is exceeded, the request will be rejected.</td>
</tr>
<tr class="odd">
<td align="left">maxResponseHeaders</td>
<td align="left">Maximum number of headers a response may send to a client. If this value is exceeded, an error will be sent to the client.</td>
</tr>
<tr class="even">
<td align="left">maxPendingBytes</td>
<td align="left">Maximum number of bytes that may be pending to be written on a single connection. If this value is exceeded, an exception will be raised. Note that this configuration is only relevant when using non-blocking HTTP streams.</td>
</tr>
</tbody>
</table>

Long-lasting HTTP requests (suspend/response)
=============================================

Some HTTP interactions may need to trigger a long running transaction on
the server and wait for the result to generate a response. However, this
can be problematic with an NIO-based server as there are generally a
handful of threads servicing all requests. A long running transaction in
this case would tie up one of the processing threads preventing it from
servicing other requests. If enough of these long running transactions
were initiated, it could lead to a denial of service.

To support these use cases without negatively impacting the server,
Grizzly allows a response to be suspended until such time that the long
running task is complete and the response is ready to be generated.

Let's cover the methods related to response suspend/resume. The
following methods are available on the Response object itself:

```java
/**
 * Suspend the {@link Response}. Suspending a {@link Response} will
 * tell the underlying container to avoid recycling objects associated with
 * the current instance, and also to avoid committing response.
 */
public void suspend() {
    ...
}

/**
 * Suspend the {@link Response}. Suspending a {@link Response} will
 * tell the underlying container to avoid recycling objects associated with
 * the current instance, and also to avoid committing response.
 *
 * @param timeout The maximum amount of time,
 * a {@link Response} can be suspended. When the timeout expires (because
 * nothing has been written or because the {@link Response#resume()}),
 * the {@link Response} will be automatically
 * resumed and committed. Usage of any methods of a {@link Response} that
 * times out will throw an {@link IllegalStateException}.
 * @param timeunit timeout units
 *
 */
public void suspend(final long timeout, final TimeUnit timeunit) {
    ...
}

/**
 * Suspend the {@link Response}. Suspending a {@link Response} will
 * tell the underlying container to avoid recycling objects associated with
 * the current instance, and also to avoid committing response. When the
 * {@link Response#resume()} is invoked, the container will
 * make sure {@link CompletionHandler#completed(Object)}
 * is invoked with the original <tt>attachment</tt>.
 * If the timeout expires, the
 * {@link org.glassfish.grizzly.CompletionHandler#cancelled()} is invoked with
 * the original <tt>attachment</tt> and the {@link Response} committed.
 *
 * @param timeout The maximum amount of time the {@link Response} can be suspended.
 * When the timeout expires (because nothing has been written or because the
 * {@link Response#resume()}), the {@link Response}
 * will be automatically resumed and committed. Usage of any methods of a
 * {@link Response} that times out will throw an {@link IllegalStateException}.
 * @param timeunit timeout units
 * @param completionHandler a {@link org.glassfish.grizzly.CompletionHandler}
 */
public void suspend(final long timeout,
                    final TimeUnit timeunit,
                    final CompletionHandler<Response> completionHandler) {
    ...
}

/**
 * Suspend the {@link Response}. Suspending a {@link Response} will
 * tell the underlying container to avoid recycling objects associated with
 * the current instance, and also to avoid committing response. When the
 * {@link Response#resume()} is invoked, the container will
 * make sure {@link CompletionHandler#completed(Object)}
 * is invoked with the original <tt>attachment</tt>.
 * If the timeout expires, the
 * {@link org.glassfish.grizzly.CompletionHandler#cancelled()} is invoked with the
 * original <tt>attachment</tt> and the {@link Response} committed.
 *
 * @param timeout The maximum amount of time the {@link Response} can be suspended.
 * When the timeout expires (because nothing has been written or because the
 * {@link Response#resume()}), the {@link Response}
 * will be automatically resumed and committed. Usage of any methods of a
 * {@link Response} that times out will throw an {@link IllegalStateException}.
 * @param timeunit timeou
 * @param completionHandler a {@link org.glassfish.grizzly.CompletionHandler}
 * @param timeoutHandler {@link TimeoutHandler} to customize the suspended
 *  <tt>Response</tt> timeout logic.
 */
public void suspend(final long timeout,
                    final TimeUnit timeunit,
                    final CompletionHandler<Response> completionHandler,
                    final TimeoutHandler timeoutHandler) {
    ...
}

/**
 * Complete the {@link Response} and finish/commit it. If a
 * {@link CompletionHandler} has been defined, its
 * {@link CompletionHandler#completed(Object)} will first be invoked,
 * then the {@link Response#finish()}.
 * Those operations commit the response.
 */
public void resume() {
    ...
}

/**
 * Get the context of the suspended <tt>Response</tt>.
 *
 * @return the context of the suspended <tt>Response</tt>.
 */
public SuspendContext getSuspendContext() {
    ...
}

/**
 * Return <tt>true<//tt> if that {@link Response#suspend()} has been
 * invoked and set to <tt>true</tt>
 * @return <tt>true<//tt> if that {@link Response#suspend()} has been
 * invoked and set to <tt>true</tt>
 */
public boolean isSuspended() {
    ...
}
```

The following diagram describes a typical suspend/resume scenario:

![Suspend/Resume](images/httpserverframework/susres.png)

The "Suspended Response Queue" warrants some explaination. If a
suspended Response has defined a timeout, it will be added to the
"Suspended Response Queue". When the queue is processed, the current
time will be evaluated against the timeout as defined within the
Response's SuspendContext. If the timeout has been exceeded, the
response will be committed and the Response object within the queue will
be marked for removal. So in the case no timeout has been defined, the
response will not be added to the queue, and the task may run
indefiniately.

NIO Streams
===========

In Grizzly 1.9, it was possible to write data to the client using
non-blocking I/O, however, when reading post data from within a
GrizzlyAdapter implementation, all I/O in that case was blocking. In
Grizzly 2.3, it's very simple using the InputStream/Reader
from the request and read data in a non-blocking manner. As far as
writing, all I/O will be written in non-blocking mode, but there is some
extra features on the OutputStream vended by the Response instance that
will be covered that expose advanced non-blocking operations.

Non-Blocking Writing
--------------------

The methods getNIOOutputStream() and getNIOWriter() on Response return
instances of NIOOutputStream and NIOWriter. In addition to the methods
defined by java.io.OutputStream and java.io.Writer, these entities both
implement the org.glassfish.grizzly.OutputSink interface.

```java
/**
 * <p>
 * This interface defines methods to allow an {@link java.io.OutputStream} or
 * {@link java.io.Writer} to allow the developer to check with the runtime
 * whether or not it's possible to write a certain amount of data, or if it's
 * not possible, to be notified when it is.
 * </p>
 *
 * @since 2.0
 */
public interface OutputSink {


    /**
     * Instructs the <code>OutputSink</code> to invoke the provided
     * {@link WriteHandler} when it is possible to write more bytes (or characters).
     *
     * Note that once the {@link WriteHandler} has been notified, it will not
     * be considered for notification again at a later point in time.
     *
     * @param handler the {@link WriteHandler} that should be notified
     *  when it's possible to write more data.
     *
     * @throws IllegalStateException if this method is invoked and a handler
     *  from a previous invocation is still present (due to not having yet been
     *  notified).
     *
     * @since 2.3
     */
    void notifyCanWrite(final WriteHandler handler);


    /**
     * @return <code>true</code> if a write to this <code>OutputSink</code>
     *  will succeed, otherwise returns <code>false</code>.
     *
     * @since 2.3
     */
    boolean canWrite();

}
```

The typical flow when using these methods is to call canWrite(). If the
call to this method returns false, the application should call
notifyCanWrite() providing a WriteHandler that will be invoked when I/O
is possible.

For Grizzly 2.3 HTTP applications that deal in primarily
binary data and are using Grizzly Buffers, they can leverage an
additional method available on the NIOOutputStream specified by the
BinaryNIOutputSink interface. This interface allows the direct writing
of Buffer instances. This optimizes away the need to copy bytes to a
Buffer implementation under the covers.

```java
/**
 * Adds the ability for binary based {@link NIOOutputSink}s to write a
 * {@link Buffer} instead of having to convert to those types supported by
 * {@link java.io.OutputStream}.
 *
 * @since 2.0
 */
public interface BinaryNIOOutputSink extends NIOOutputSink {

    /**
     * Writes the contents of the specified {@link org.glassfish.grizzly.Buffer}.
     *
     * @param buffer the {@link org.glassfish.grizzly.Buffer to write}
     */
    void write(final Buffer buffer) throws IOException;

}
```

Non-Blocking Reading
--------------------

On the input, side, Grizzly provides a similar interface for
non-blocking reads called NIOInputSource.

```java
/**
 * <p>
 * This interface defines methods to allow an {@link InputStream} or
 * {@link Reader} to notify the developer <em>when</em> and <em>how much</em>
 * data is ready to be read without blocking.
 * </p>
 *
 * @since 2.0
 */
public interface InputSource {


    /**
     * <p>
     * Notify the specified {@link ReadHandler} when any number of bytes
     * can be read without blocking.
     * </p>
     *
     * <p>
     * Invoking this method is equivalent to calling: notifyAvailable(handler, 1).
     * </p>
     *
     * @param handler the {@link ReadHandler} to notify.
     *
     * @throws IllegalArgumentException if <code>handler</code> is <code>null</code>.
     * @throws IllegalStateException if an attempt is made to register a handler
     *  before an existing registered handler has been invoked or if all request
     *  data has already been read.
     *
     * @see ReadHandler#onDataAvailable()
     * @see ReadHandler#onAllDataRead()
     */
    void notifyAvailable(final ReadHandler handler);


    /**
     * <p>
     * Notify the specified {@link ReadHandler} when the number of bytes that
     * can be read without blocking is greater or equal to the specified
     * <code>size</code>.
     * </p>
     *
     * @param handler the {@link ReadHandler} to notify.
     * @param size the least number of bytes that must be available before
     *  the {@link ReadHandler} is invoked.
     *
     * @throws IllegalArgumentException if <code>handler</code> is <code>null</code>,
     *  or if <code>size</code> is less or equal to zero.
     * @throws IllegalStateException if an attempt is made to register a handler
     *  before an existing registered handler has been invoked or if all request
     *  data has already been read.
     *
     * @see ReadHandler#onDataAvailable()
     * @see ReadHandler#onAllDataRead()
     */
    void notifyAvailable(final ReadHandler handler, final int size);


    /**
     * @return <code>true</code> when all data for this particular request
     *  has been read, otherwise returns <code>false</code>.
     */
    boolean isFinished();


    /**
     * @return the number of bytes (or characters) that may be obtained
     *  without blocking.  Note when dealing with characters, this method
     *  will return an estimate on the number of characters available.
     */
    int readyData();


    /**
     * @return <code>true</code> if data can be obtained without blocking,
     *  otherwise returns <code>false</code>.
     */
    boolean isReady();

}
```

The general idea behind non-blocking writes holds true for non-blocking
reads. The developer can check to see if data is available to be read
without blocking by calling isReady() or checking for a non-zero return
from readyData(). If no data can be read without blocking, use
notifyAvailable(ReadHandler) or notifyAvailable(ReadHandler, int). When
data becomes available, the ReadHandler will be invoked. Note that if no
length is provided to the notifyAvailable() methods, the ReadHandler
will be invoked as soon as any data becomes available. In this case,
it's a good idea to check how much can be read by another call to
readyData().

For optimized reading of binary data, there is the specialized
interface, BinaryNIOInputSource, that allows direct access to the Buffer
used to store the incoming data:

```java
/**
 * Adds the ability for binary based {@link NIOInputSource}s to obtain the
 * incoming {@link org.glassfish.grizzly.Buffer} directly without having to
 * use intermediate objects to copy the data to.
 *
 * @since 2.0
 */
public interface BinaryNIOInputSource extends NIOInputSource {

    /**
     * <p>
     * Returns the underlying {@link org.glassfish.grizzly.Buffer} that backs this
     *  <code>NIOInputSource</code>.
     * </p>
     *
     * @return the underlying {@link org.glassfish.grizzly.Buffer} that backs this
     *  <code>NIOInputSource</code>.
     */
    Buffer getBuffer();

}
```

One final word on InputStream, OutputStream, Reader, Writer and their
NIO\* counterparts returning by Request and Response objects. Since
Grizzly 2.3 there are no modes (NIO or blocking) streams operate in.
There are only two rules (actually one rule):

-   if InputSource.isReady() returned true - next input operation (only
    one) is guaranteed to be non-blocking, otherwise if
    InputSource.isReady() returned false or we didn't check it - next
    input operation may block;

-   if OutputSink.canWrite() returned true - next output operation (only
    one) is guaranteed to be non-blocking, otherwise if
    OutputSink.canWrite() returned false or we didn't check it - next
    output operation may block;

File Cache
==========

The FileCache allows for efficient caching of static content. There are
several configuration options that allow fine tuning of each FileCache
instance:

<table>
<caption>FileCache Configuration Properties</caption>
<tbody>
<tr class="odd">
<td align="left">secondsMaxAge</td>
<td align="left">Specifies how long an resource may exist within the cache. If the value is zero or less, the resource may be cached indefinately. If not specified, the value defaults to -1.</td>
</tr>
<tr class="even">
<td align="left">maxCacheEntries</td>
<td align="left">Specified how many resources may be cached. An attempt to add an entry that causes the max number of entries to be exceeded, the resource will be removed from the cache. The default number of cached entries is 1024.</td>
</tr>
<tr class="odd">
<td align="left">minEntrySize</td>
<td align="left">The maximum size, in bytes, a file must be in order to be cached in the heap cache. This defaults to Long.MIN_VALUE.</td>
</tr>
<tr class="even">
<td align="left">maxEntrySize</td>
<td align="left">The maximum size, in bytes, a resource may be before it can no longer be considered cachable. This defaults to Long.MAX_VALUE.</td>
</tr>
<tr class="odd">
<td align="left">maxLargeFileCacheSize</td>
<td align="left">The maximum size, in bytes, of the memory mapped cache for large files. This defaults to Long.MAX_VALUE.</td>
</tr>
<tr class="even">
<td align="left">maxSmallFileCacheSize</td>
<td align="left">The maximum size, in bytes, a file must be in order to be cached in the heap cachevs the mapped memory cache. This defaults to 1048576.</td>
</tr>
<tr class="odd">
<td align="left">enabled</td>
<td align="left">Whether or not the FileCache is enabled. This defaults to true on new NetworkListener instances.</td>
</tr>
</tbody>
</table>

All properties of the FileCache can be manipulated by obtaining the
FileCache instance by calling NetworkListener.getFileCache().

AddOn
=====

The AddOn abstraction provides a simple and generic way how to extend
existing HttpServer functionality. The interface looks very simple:

```java
/**
 * The {@link HttpServer} addon interface, responsible for adding
 * features like WebSockets, Comet to HttpServer.
 */
public interface AddOn {
    /**
     * The method, which will be invoked by {@link HttpServer} in order to
     * initialize the AddOn on the passed {@link NetworkListener}.
     * Most of the time the AddOn implementation will update the passed
     * {@link NetworkListener}'s {@link FilterChainBuilder} by adding custom
     * {@link Filter}(s), which implement AddOn's logic.
     *
     * @param networkListener the {@link NetworkListener} the addon is being
     *          initialized on.
     * @param builder the {@link FilterChainBuilder},
     *          representing the {@link NetworkListener} logic.
     */
    public void setup(NetworkListener networkListener,
            FilterChainBuilder builder);
}
```

So basically custom AddOn should implement only one method, and most of
the time AddOn just inserts a custom logic Filter into the given
FilterChainBuilder. Here is example of WebSocketAddOn:

```java
/**
 * WebSockets {@link AddOn} for the {@link org.glassfish.grizzly.http.server.HttpServer}.
 */
public class WebSocketAddOn implements AddOn {

    @Override
    public void setup(final NetworkListener networkListener,
            final FilterChainBuilder builder) {

        // Get the index of HttpCodecFilter in the HttpServer filter chain
        final int httpCodecFilterIdx = builder.indexOfType(HttpCodecFilter.class);

        if (httpCodecFilterIdx >= 0) {
            // Insert the WebSocketFilter right after HttpCodecFilter
            builder.add(httpCodecFilterIdx + 1, new WebSocketFilter());
        }
    }
}
```

Embedded prioritization mechanism
=================================

Starting with version 2.3, Grizzly provides embeded support for
prioritizing incoming HTTP requests processing implemented on a
container level

In some cases it may have certain advantages comparing to application
level prioritization mechanism provided by HttpResponse's suspend/resume
methods.

Every time before invoking HttpHandler.service(Request, Response)
method, Grizzly HTTP server asks HttpHandler which thread-pool it wants
to be used:

```java
public class MyHttpHandler extends HttpHandler {
    ...................................

    /**
     * Returns the <tt>HttpHandler</tt> preferred {@link ExecutorService} to process
     * passed {@link Request}. The <tt>null</tt> return value means process in
     * current thread.
     *
     * The default implementation returns <tt>null</tt> if current thread is not
     * {@link Transport} service thread ({@link Threads#isService()}). Otherwise
     * returns worker thread pool of the {@link Transport} this {@link Request}
     * belongs to ({@link org.glassfish.grizzly.Transport#getWorkerThreadPool()}).
     *
     * @param request the {@link Request} to be processed.
     * @return the <tt>HttpHandler</tt> preferred {@link ExecutorService} to process
     * passed {@link Request}. The <tt>null</tt> return value means process in
     * current thread.
     */
    @Override
    protected ExecutorService getThreadPool(final Request request) {
        // return ExecutorService to process this Request
    }
}
```

Unlike HttpResponse's suspend/resume mechanism this approach doesn't
impact Request/Response state and is completely transparent for
application level.

Note: by default Grizzly configures HttpServer to use [SameThread IO
strategy](#iostrategies), but at the same time initializes worker
thread-pool to be used by HttpHandlers by default. In case all
HttpHandlers (applications) use only custom thread pools controlled by
developer's application - it might be a good idea to prevent Grizzly
creating the default worker thread-pool by calling:

    networkListener.getTransport().setWorkerThreadPoolConfig(null);

For more details please see the priorities sample in Grizzly
http-server-framework [samples](#httpserverframework-other-samples).

Samples
=======

The following example is a composite showing both the suspend/resume as
well as the NIO streams. First we'll show the server side. Note that the
following code snippets are part of a single example that makes use
nested classes.

```java
/**
 * This handler using non-blocking streams to read POST data and echo it
 * back to the client.
 */
private static class NonBlockingEchoHandler extends HttpHandler {


    // -------------------------------------------- Methods from HttpHandler


    @Override
    public void service(final Request request,
                        final Response response) throws Exception {

        final char[] buf = new char[128];
        final NIOReader in = request.getNIOReader(); // return the non-blocking InputStream
        final NIOWriter out = response.getNIOWriter();

        response.suspend();

        // If we don't have more data to read - onAllDataRead() will be called
        in.notifyAvailable(new ReadHandler() {

            @Override
            public void onDataAvailable() throws Exception {
                System.out.printf("[onDataAvailable] echoing %d bytes\n", in.readyData());
                echoAvailableData(in, out, buf);
                in.notifyAvailable(this);
            }

            @Override
            public void onError(Throwable t) {
                System.out.println("[onError]" + t);
                response.resume();
            }

            @Override
            public void onAllDataRead() throws Exception {
                System.out.printf("[onAllDataRead] length: %d\n", in.readyData());
                try {
                    echoAvailableData(in, out, buf);
                } finally {
                    try {
                        in.close();
                    } catch (IOException ignored) {
                    }

                    try {
                        out.close();
                    } catch (IOException ignored) {
                    }

                    response.resume();
                }
            }
        });

    }

    private void echoAvailableData(NIOReader in, NIOWriter out, char[] buf)
            throws IOException {

        while(in.isReady()) {
            int len = in.read(buf);
            out.write(buf, 0, len);
        }
    }

} // END NonBlockingEchoHandler
```

As can be gleened from the name of the class, this HttpHandler
implementation simply echoes POST data back to the client.

Let's cover the major points of this part of the example:

-   Getting NIOReader and NIOWriter to be able to leverage NIO features.

-   The Response is suspended; the service() method will exit.
    ReadHandler implementation will be notified as data becomes
    available.

-   The ReadHandler's onDataAvailable callback invoked as data is
    received by the server and echoed back to the client.

-   The ReadHandler's onAllDataRead callback invoked when client has
    finished message. Any remaining data is echoed back to the client
    and the response is resumed.

Now we need to create a server and install this HttpHandler:

```java
public static void main(String[] args) {

    // create a basic server that listens on port 8080.
    final HttpServer server = HttpServer.createSimpleServer();

    final ServerConfiguration config = server.getServerConfiguration();

    // Map the path, /echo, to the NonBlockingEchoHandler
    config.addHttpHandler(new NonBlockingEchoHandler(), "/echo");

    try {
        server.start();
        Client client = new Client();
        client.run();
    } catch (IOException ioe) {
        LOGGER.log(Level.SEVERE, ioe.toString(), ioe);
    } finally {
        server.stop();
    }
}
```

This part of the example is pretty straight forward. Create the server,
install the HttpHandler to service requests made to /echo, and start the
server.

The client code which will follow will be sending data slowly to
exercise the non-blocking HttpHandler. The client code relies on the
http module primitives, so there's a little more code here to get this
part of the example going. Let's start with the client Filter that sends
request.

```java
private static final class ClientFilter extends BaseFilter {

            private static final String[] CONTENT = {
                "contentA-",
                "contentB-",
                "contentC-",
                "contentD"
            };

            private FutureImpl<String> future;

            private StringBuilder sb = new StringBuilder();

            // ---------------------------------------------------- Constructors


            private ClientFilter(FutureImpl<String> future) {
                this.future = future;
            }


            // ----------------------------------------- Methods from BaseFilter


            @SuppressWarnings({"unchecked"})
            @Override
            public NextAction handleConnect(FilterChainContext ctx) throws IOException {
                System.out.println("\nClient connected!\n");

                HttpRequestPacket request = createRequest();
                System.out.println("Writing request:\n");
                System.out.println(request.toString());
                ctx.write(request); // write the request

                // for each of the content parts in CONTENT, wrap in a Buffer,
                // create the HttpContent to wrap the buffer and write the
                // content.
                MemoryManager mm = ctx.getConnection().getTransport().getMemoryManager();
                for (int i = 0, len = CONTENT.length; i < len; i++) {
                    HttpContent.Builder contentBuilder = request.httpContentBuilder();
                    Buffer b = Buffers.wrap(mm, CONTENT[i]);
                    contentBuilder.content(b);
                    HttpContent content = contentBuilder.build();
                    System.out.printf("(Client writing: %s)\n", b.toStringContent());
                    ctx.write(content);
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }

                // since the request created by createRequest() is chunked,
                // we need to write the trailer to signify the end of the
                // POST data
                ctx.write(request.httpTrailerBuilder().build());

                System.out.println("\n");

                return ctx.getStopAction(); // discontinue filter chain execution

            }


            @Override
            public NextAction handleRead(FilterChainContext ctx) throws IOException {

                HttpContent c = (HttpContent) ctx.getMessage();
                Buffer b = c.getContent();
                if (b.hasRemaining()) {
                    sb.append(b.toStringContent());
                }

                // Last content from the server, set the future result so
                // the client can display the result and gracefully exit.
                if (c.isLast()) {
                    future.result(sb.toString());
                }
                return ctx.getStopAction(); // discontinue filter chain execution

            }


            // ------------------------------------------------- Private Methods


            private HttpRequestPacket createRequest() {

                HttpRequestPacket.Builder builder = HttpRequestPacket.builder();
                builder.method("POST");
                builder.protocol("HTTP/1.1");
                builder.uri("/echo");
                builder.chunked(true);
                HttpRequestPacket packet = builder.build();
                packet.addHeader(Header.Host, HOST + ':' + PORT);
                return packet;

            }

        }

    } // END Client
```

High level points about this code:

-   When the connection is established with the server, the
    handleConnect() method of this Filter will be invoked. When this
    happens, we create a HttpRequestPacket, which contains only message
    part of a POST request. wThe request is then written.

-   The message of the body of the message in 2 second intervals.

-   When all data has been written, write the trailer to signify the end
    of the request since the chunked transfer encoding is being used.

-   Read the response from the server. Store the final result in a
    future to be retrieved later.

Lastly we need to define a client that utilizes the Filter that was just
described:

```java
private static final class Client {

    private static final String HOST = "localhost";
    private static final int PORT = 8080;

    public void run() throws IOException {
        final FutureImpl<String> completeFuture = SafeFutureImpl.create();

        // Build HTTP client filter chain
        FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
        // Add transport filter
        clientFilterChainBuilder.add(new TransportFilter());

        // Add HttpClientFilter, which transforms Buffer <-> HttpContent
        clientFilterChainBuilder.add(new HttpClientFilter());
        // Add ClientFilter
        clientFilterChainBuilder.add(new ClientFilter(completeFuture));


        // Initialize Transport
        final TCPNIOTransport transport =
               TCPNIOTransportBuilder.newInstance().build();
        // Set filterchain as a Transport Processor
        transport.setProcessor(clientFilterChainBuilder.build());

        try {
            // start the transport
            transport.start();

            Connection connection = null;

            // Connecting to a remote Web server
            Future<Connection> connectFuture = transport.connect(HOST, PORT);
            try {
                // Wait until the client connect operation will be completed
                // Once connection has been established, the POST will
                // be sent to the server.
                connection = connectFuture.get(10, TimeUnit.SECONDS);

                // Wait no longer than 30 seconds for the response from the
                // server to be complete.
                String result = completeFuture.get(30, TimeUnit.SECONDS);

                // Display the echoed content
                System.out.println("\nEchoed POST Data: " + result + '\n');
            } catch (Exception e) {
                if (connection == null) {
                    LOGGER.log(Level.WARNING, "Connection failed.  Server is not listening.");
                } else {
                    LOGGER.log(Level.WARNING, "Unexpected error communicating with the server.");
                }
            } finally {
                // Close the client connection
                if (connection != null) {
                    connection.close();
                }
            }
        } finally {
            // stop the transport
            transport.stop();
        }
    }
```

The comments within the Client code should be sufficient to explain
what's going on here. When running the complete example the output will
look something like:

```
Sep 22, 2011 3:22:58 PM org.glassfish.grizzly.http.server.NetworkListener start
INFO: Started listener bound to \[0.0.0.0:8080]
Sep 22, 2011 3:22:58 PM org.glassfish.grizzly.http.server.HttpServer start
INFO: \[HttpServer] Started.

Client connected!

Writing request:

HttpRequestPacket (
   method=POST
   url=/echo
   query=null
   protocol=HTTP/1.1
   content-length=-1
   headers=\[
      Host=localhost:8080]
)
(Client writing: contentA-)
\[onDataAvailable] echoing 9 bytes

(delay 2 seconds)

(Client writing: contentB-)
\[onDataAvailable] echoing 9 bytes

(delay 2 seconds)

(Client writing: contentC-)
\[onDataAvailable] echoing 9 bytes

(delay 2 seconds)

(Client writing: contentD)
\[onDataAvailable] echoing 8 bytes


\[onAllDataRead] length: 0

Echoed POST Data: contentA-contentB-contentC-contentD

Sep 22, 2011 3:23:06 PM org.glassfish.grizzly.http.server.NetworkListener stop
INFO: Stopped listener bound to \[0.0.0.0:8080]
```

A quick note about the output above, the (delay 2 seconds) isn't
actually output. It's been added to visualize the artificial delay added
by the Filter used by the client.

This example in its entirety is available within the samples section of
the Grizzly 2.3 repository.

Other samples
=============

The HTTP server framwork samples can be reviewed in one of two ways:

-   Directly from the git repository:

```
git clone git://java.net/grizzly~git
cd grizzly~git
git checkout 2_3
cd samples/http-server-samples
```

-   Download the sample source bundle from:
    <https://maven.java.net/content/repositories/releases/org/glassfish/grizzly/samples/grizzly-http-server-samples/2.3/grizzly-http-server-samples-2.3-sources.jar>


