WebSockets
==========

What is WebSockets?
===================

Quoting the description from [RFC
6455](http://tools.ietf.org/html/rfc6455):

> The WebSocket Protocol is designed to supersede existing bidirectional
> communication technologies that use HTTP as a transport layer to
> benefit from existing infrastructure (proxies, filtering,
> authentication). Such technologies were implemented as trade-offs
> between efficiency and reliability because HTTP was not initially
> meant to be used for bidirectional communication (see [RFC6202] for
> further discussion). The WebSocket Protocol attempts to address the
> goals of existing bidirectional HTTP technologies in the context of
> the existing HTTP infrastructure; as such, it is designed to work over
> HTTP ports 80 and 443 as well as to support HTTP proxies and
> intermediaries, even if this implies some complexity specific to the
> current environment.

For futher details on the protocol itself, the RFC referenced previously
is your best source of information.

Overview of Grizzly's WebSocket Implementation
==============================================

Grizzly 2 implements the requirements of RFC 6455, however we do include
support for older versions of the protocol. This is particularly useful
for clients that haven't caught up with the final draft of the
specification. That said, please consider the support for the older
versions to be deprecated in favor of pushing support for the final
draft. The protocol versions supported at the time of writing are: 6, 7,
8, and 13 (13 being the protocol version of the final draft). We do not
support 00/-76, so this may cause an issue with clients such as Safari
which are still using this old version of the protocol.

Like other protocols implemented with Grizzly 2.x, WebSocket support is
implemented using a Filter. If you're not familiar with the concepts of
Filters and FilterChains, please review the documentation in this guide.

The Grizzly WebSocket API
=========================

Understanding how to use the WebSocket API is very simple as there are
only a few core entities that developers need to be familiar with. We'll
begin by covering those first. Once the basics are understood, creating
websocket applications should be straight forward.

WebSocket
---------

This interface provides the core functionality needed by developers to
both send data to a remote end-point or handle events triggered by the
remote end-point sending data. This interface is relevant for both
server and client side uses cases.

```java
public interface WebSocket {


    /**
     * <p>
     * Send a text frame to the remote end-point.
     * <p>
     *
     * @return {@link GrizzlyFuture} which could be used to control/check the sending completion state.
     */
    GrizzlyFuture<DataFrame> send(String data);

    /**
     * <p>
     * Send a binary frame to the remote end-point.
     * </p>
     *
     * @return {@link GrizzlyFuture} which could be used to control/check the sending completion state.
     */
    GrizzlyFuture<DataFrame> send(byte[] data);

    /**
     * <p>
     * Broadcasts the data to the remote end-point set.
     * </p>
     *
     * @param recipients
     * @param data
     */
    void broadcast(final Iterable<? extends WebSocket> recipients, String data);

    /**
     * <p>
     * Broadcasts the data to the remote end-point set.
     * </p>
     *
     * @param recipients
     * @param data
     */
    void broadcast(final Iterable<? extends WebSocket> recipients, byte[] data);

    /**
     * <p>
     * Broadcasts the data fragment to the remote end-point set.
     * </p>
     *
     * @param recipients
     * @param data
     */
    void broadcastFragment(final Iterable<? extends WebSocket> recipients,
            String data, boolean last);

    /**
     * <p>
     * Broadcasts the data fragment to the remote end-point set.
     * </p>
     *
     * @param recipients
     * @param data
     */
    void broadcastFragment(final Iterable<? extends WebSocket> recipients,
            byte[] data, boolean last);

    /**
     * Sends a <code>ping</code> frame with the specified payload (if any).
     * </p>
     *
     * @param data optional payload.  Note that payload length is restricted
     *  to 125 bytes or less.
     *
     * @return {@link GrizzlyFuture} which could be used to control/check the sending completion state.
     *
     * @since 2.1.9
     */
    GrizzlyFuture<DataFrame> sendPing(byte[] data);

    /**
     * <p>
     * Sends a <code>ping</code> frame with the specified payload (if any).
     * </p>
     *
     * <p>It may seem odd to send a pong frame, however, RFC-6455 states:</p>
     *
     * <p>
     *     "A Pong frame MAY be sent unsolicited.  This serves as a
     *     unidirectional heartbeat.  A response to an unsolicited Pong frame is
     *     not expected."
     * </p>
     *
     * @param data optional payload.  Note that payload length is restricted
     *  to 125 bytes or less.
     *
     * @return {@link GrizzlyFuture} which could be used to control/check the sending completion state.
     *
     * @since 2.1.9
     */
    GrizzlyFuture<DataFrame> sendPong(byte[] data);

    /**
     * <p>
     * Sends a fragment of a complete message.
     * </p>
     *
     * @param last boolean indicating if this message fragment is the last.
     * @param fragment the textual fragment to send.
     *
     * @return {@link GrizzlyFuture} which could be used to control/check the sending completion state.
     */
    GrizzlyFuture<DataFrame> stream(boolean last, String fragment);

    /**
     * <p>
     * Sends a fragment of a complete message.
     * </p>
     *
     * @param last boolean indicating if this message fragment is the last.
     * @param fragment the binary fragment to send.
     * @param off the offset within the fragment to send.
     * @param len the number of bytes of the fragment to send.
     *
     * @return {@link GrizzlyFuture} which could be used to control/check the sending completion state.
     */
    GrizzlyFuture<DataFrame> stream(boolean last, byte[] fragment, int off, int len);

    /**
     * <p>
     * Closes this {@link WebSocket}.
     * </p>
     */
    void close();

    /**
     * <p>
     * Closes this {@link WebSocket} using the specified status code.
     * </p>
     *
     * @param code the closing status code.
     */
    void close(int code);

    /**
     * <p>
     * Closes this {@link WebSocket} using the specified status code and
     * reason.
     * </p>
     *
     * @param code the closing status code.
     * @param reason the reason, if any.
     */
    void close(int code, String reason);

    /**
     * <p>
     * Convenience method to determine if this {@link WebSocket} is connected.
     * </p>
     *
     * @return <code>true</code> if the {@link WebSocket} is connected, otherwise
     *  <code>false</code>
     */
    boolean isConnected();

    /**
     * <p>
     * This callback will be invoked when the opening handshake between both
     * endpoints has been completed.
     * </p>
     */
    void onConnect();

    /**
     * <p>
     * This callback will be invoked when a text message has been received.
     * </p>
     *
     * @param text the text received from the remote end-point.
     */
    void onMessage(String text);

    /**
     * <p>
     * This callback will be invoked when a binary message has been received.
     * </p>
     *
     * @param data the binary data received from the remote end-point.
     */
    void onMessage(byte[] data);

    /**
     * <p>
     * This callback will be invoked when a fragmented textual message has
     * been received.
     * </p>
     *
     * @param last flag indicating whether or not the payload received is the
     *  final fragment of a message.
     * @param payload the text received from the remote end-point.
     */
    void onFragment(boolean last, String payload);

    /**
     * <p>
     * This callback will be invoked when a fragmented binary message has
     * been received.
     * </p>
     *
     * @param last flag indicating whether or not the payload received is the
     *  final fragment of a message.
     * @param payload the binary data received from the remote end-point.
     */
    void onFragment(boolean last, byte[] payload);

    /**
     * <p>
     * This callback will be invoked when the remote end-point sent a closing
     * frame.
     * </p>
     *
     * @param frame the close frame from the remote end-point.
     *
     * @see DataFrame
     */
    void onClose(DataFrame frame);

    /**
     * <p>
     * This callback will be invoked when the remote end-point has sent a ping
     * frame.
     * </p>
     *
     * @param frame the ping frame from the remote end-point.
     *
     * @see DataFrame
     */
    void onPing(DataFrame frame);

    /**
     * <p>
     * This callback will be invoked when the remote end-point has sent a pong
     * frame.
     * </p>
     *
     * @param frame the pong frame from the remote end-point.
     *
     * @see DataFrame
     */
    void onPong(DataFrame frame);

    /**
     * Adds a {@link WebSocketListener} to be notified of events of interest.
     *
     * @param listener the {@link WebSocketListener} to add.
     *
     * @return <code>true</code> if the listener was added, otherwise
     *  <code>false</code>
     *
     * @see WebSocketListener
     */
    boolean add(WebSocketListener listener);

    /**
     * Removes the specified {@link WebSocketListener} as a target of event
     * notification.
     *
     * @param listener the {@link WebSocketListener} to remote.
     *
     * @return <code>true</code> if the listener was removed, otherwise
     *  <code>false</code>
     *
     * @see WebSocketListener
     */
    boolean remove(WebSocketListener listener);

}
```
Broadcaster
-----------

New in 2.3 is the concept of the *Broadcaster*. In previous versions of
Grizzly's WebSocket implementation, when sending data, each call would
result in a new frame instance being created. This operation is
expensive - particularly when sending such data to thousands of sockets.
An optimized Broadcaster implementation would frame the data once, and
then send the framed result to each client. The WebSocket interface
mirrors the methods defined here in Broadcaster and the defautl
implementation of the WebSocket interface merely delegates the call to
the Broadcaster implementation.

Here's what the Broadcaster API looks like:

```java
/**
 * Broadcasts the provided <tt>text</tt> content to the specified recipients.
 *
 * @param recipients the recipients of the provided <tt>text</tt> content.
 * @param text       textual content.
 */
public void broadcast(final Iterable<? extends WebSocket> recipients,
                      final String text);

/**
 * Broadcasts the provided <tt>binary</tt> content to the specified recipients.
 *
 * @param recipients the recipients of the provided <tt>binary</tt> content.
 * @param binary     binary content.
 */
public void broadcast(final Iterable<? extends WebSocket> recipients,
                      final byte[] binary);

/**
 * Broadcasts the provided fragmented <tt>text</tt> content to the specified recipients.
 *
 * @param recipients the recipients of the provided fragmented <tt>text</tt> content.
 * @param text       fragmented textual content.
 * @param last       <tt>true</tt> if this is the last fragment, otherwise <tt>false</tt>.
 */
public void broadcastFragment(final Iterable<? extends WebSocket> recipients,
                              final String text,
                              final boolean last);

/**
 * Broadcasts the provided fragmented <tt>binary</tt> content to the specified recipients.
 *
 * @param recipients the recipients of the provided fragmented <tt>binary</tt> content.
 * @param binary     fragmented binary content.
 * @param last       <tt>true</tt> if this is the last fragment, otherwise <tt>false</tt>.
 */
public void broadcastFragment(final Iterable<? extends WebSocket> recipients,
                              final byte[] binary,
                              final boolean last);
```

As can be seen from the listing above, it is possible to send complete
or fragmented messages in an optimized fashion. It should be noted that,
by default, Grizzly will use an unoptimized version of the Broadcaster
(to maintain behavioral compatibility with previous releases). In order
to leverage the optimized version, see the following example:

```java
public static class BroadcastApplication extends WebSocketApplication {
    private final Broadcaster broadcaster;

    public BroadcastApplication(Broadcaster broadcaster) {
        this.broadcaster = broadcaster;
    }


    @Override
    public WebSocket createSocket(ProtocolHandler handler,
                                  HttpRequestPacket requestPacket,
                                  WebSocketListener... listeners) {
        final DefaultWebSocket ws =
                (DefaultWebSocket) super.createSocket(handler,
                requestPacket, listeners);

        ws.setBroadcaster(broadcaster);
        return ws;
    }

    @Override
    public void onMessage(WebSocket socket, String data) {
        socket.broadcast(getWebSockets(), data);
    }
}
```

The example is fairly staight forward. A custom Broadcaster
implementation is provided to the constructor of the
WebSocketApplication instance. When a new socket is created, this custom
Broadcaster will provided and used by the various WebSocket broadcast
calls. Given this example, one can simply do:

```java
WebSocketApplication app = new BroadcastApplication(new OptimizedBroadcaster());
```

NOTE: org.glassfish.grizzly.websockets.OptimizedBroadcaster is included
with the distribution.

WebSocketListener
-----------------

Implementations of this interface allow the developers to listen for
events occurring on various WebSocket instances. This interface is
relevant for both server and client side uses cases.

```java
/**
 * Interface to allow notification of events occurring on specific
 * {@link WebSocket} instances.
 */
public interface WebSocketListener {

    /**
     * <p>
     * Invoked when {@link WebSocket#onClose(DataFrame)} has been called on a
     * particular {@link WebSocket} instance.
     * <p>
     *
     * @param socket the {@link WebSocket} being closed.
     * @param frame the closing {@link DataFrame} sent by the remote end-point.
     */
    void onClose(WebSocket socket, DataFrame frame);

    /**
     * <p>
     * Invoked when the opening handshake has been completed for a specific
     * {@link WebSocket} instance.
     * </p>
     *
     * @param socket the newly connected {@link WebSocket}
     */
    void onConnect(WebSocket socket);

    /**
     * <p>
     * Invoked when {@link WebSocket#onMessage(String)} has been called on a
     * particular {@link WebSocket} instance.
     * </p>
     *
     * @param socket the {@link WebSocket} that received a message.
     * @param text the message received.
     */
    void onMessage(WebSocket socket, String text);

    /**
     * <p>
     * Invoked when {@link WebSocket#onMessage(String)} has been called on a
     * particular {@link WebSocket} instance.
     * </p>
     *
     * @param socket the {@link WebSocket} that received a message.
     * @param bytes the message received.
     */
    void onMessage(WebSocket socket, byte[] bytes);

    /**
     * <p>
     * Invoked when {@link WebSocket#onPing(DataFrame)} has been called on a
     * particular {@link WebSocket} instance.
     * </p>
     *
     * @param socket the {@link WebSocket} that received the ping.
     * @param bytes the payload of the ping frame, if any.
     */
    void onPing(WebSocket socket, byte[] bytes);

    /**
     * <p>
     * Invoked when {@link WebSocket#onPong(DataFrame)} has been called on a
     * particular {@link WebSocket} instance.
     * </p>
     *
     * @param socket the {@link WebSocket} that received the pong.
     * @param bytes  the payload of the pong frame, if any.
     */
    void onPong(WebSocket socket, byte[] bytes);

    /**
     * <p>
     * Invoked when {@link WebSocket#onFragment(boolean, String)} has been called
     * on a particular {@link WebSocket} instance.
     * </p>
     *
     * @param socket the {@link WebSocket} received the message fragment.
     * @param fragment the message fragment.
     * @param last flag indicating if this was the last fragment.
     */
    void onFragment(WebSocket socket, String fragment, boolean last);

    /**
     * <p>
     * Invoked when {@link WebSocket#onFragment(boolean, byte[])} has been called
     * on a particular {@link WebSocket} instance.
     * </p>
     *
     * @param socket   the {@link WebSocket} received the message fragment.
     * @param fragment the message fragment.
     * @param last     flag indicating if this was the last fragment.
     */
    void onFragment(WebSocket socket, byte[] fragment, boolean last);

}
```

WebSocketApplication
--------------------

The WebSocketApplication abstract class provides the basics for creating
a server-side WebSocket application.

```java
**
 * Factory method to create new {@link WebSocket} instances.  Developers may
 * wish to override this to return customized {@link WebSocket} implementations.
 *
 * @param handler the {@link ProtocolHandler} to use with the newly created
 *  {@link WebSocket}.
 *
 * @param listeners the {@link WebSocketListener}s to associate with the new
 *   {@link WebSocket}.
 *
 * @return a new {@link WebSocket} instance.
 */
public WebSocket createSocket(ProtocolHandler handler, WebSocketListener... listeners) {
    ...
}

/**
 * When a {@link WebSocket#onClose(DataFrame)} is invoked, the {@link WebSocket}
 * will be unassociated with this application and closed.
 *
 * @param socket the {@link WebSocket} being closed.
 * @param frame the closing frame.
 */
@Override
public void onClose(WebSocket socket, DataFrame frame) {
    ...
}

/**
 * When a new {@link WebSocket} connection is made to this application, the
 * {@link WebSocket} will be associated with this application.
 *
 * @param socket the new {@link WebSocket} connection.
 */
@Override
public void onConnect(WebSocket socket) {
    ...
}

/**
 * Checks protocol specific information can and should be upgraded.
 *
 * The default implementation will check for the precence of the
 * <code>Upgrade</code> header with a value of <code>WebSocket</code>.
 * If present, {@link #isApplicationRequest(org.glassfish.grizzly.http.HttpRequestPacket)}
 * will be invoked to determine if the request is a valid websocket request.
 *
 * @return <code>true</code> if the request should be upgraded to a
 *  WebSocket connection
 */
public final boolean upgrade(HttpRequestPacket request) {
    ...
}

/**
 * Checks application specific criteria to determine if this application can
 * process the request as a WebSocket connection.
 *
 * @param request the incoming HTTP request.
 * @return <code>true</code> if this application can service this request
 *         <p/>
 * @deprecated URI mapping shouldn't be intrinsic to the application.
 *  WebSocketApplications should be registered using {@link WebSocketEngine#register(String, String, WebSocketApplication)}
 *  using standard Servlet url-pattern rules.
 */
public boolean isApplicationRequest(HttpRequestPacket request) {
    return false;
}
```

So to create a simple server-side WebSocket application, the developer,
at a minimum, would extend this class. It's no longer required nor
recommended to override isApplicationRequest(). Applications should be
registered with the WebSocketEngine with a url pattern. Additional functionality
can be added as required for the application - keep in mind that the
WebSocketApplication is, itself, a WebSocketListener which will be added
to each WebSocket registered with it. This allows handling all of the
events exposed by WebSocketListener (as previously discussed). A word of
caution - if overriding WebSocketApplication{onConnect,onClosed} be sure
to make a call to the super() so that the WebSocket is properly
associated/unassociated with the application to prevent leaks.

WebSocketEngine
---------------

The final piece of the API developers should understand is the
WebSocketEngine. The WebSocketEngine is a JVM singleton in which all
WebSocketApplications are registered. The methods of interest here are
register/unregister:

```java
/**
 * Register a WebSocketApplication to a specific context path and url pattern.
 * If you wish to associate this application with the root context, use an
 * empty string for the contextPath argument.
 *
 * <pre>
 * Examples:
 *   // WS application will be invoked:
 *   //    ws://localhost:8080/echo
 *   // WS application will not be invoked:
 *   //    ws://localhost:8080/foo/echo
 *   //    ws://localhost:8080/echo/some/path
 *   register("", "/echo", webSocketApplication);
 *
 *   // WS application will be invoked:
 *   //    ws://localhost:8080/echo
 *   //    ws://localhost:8080/echo/some/path
 *   // WS application will not be invoked:
 *   //    ws://localhost:8080/foo/echo
 *   register("", "/echo/*", webSocketApplication);
 *
 *   // WS application will be invoked:
 *   //    ws://localhost:8080/context/echo
 *
 *   // WS application will not be invoked:
 *   //    ws://localhost:8080/echo
 *   //    ws://localhost:8080/context/some/path
 *   register("/context", "/echo", webSocketApplication);
 * </pre>
 *
 * @param contextPath the context path (per servlet rules)
 * @param urlPattern url pattern (per servlet rules)
 * @param app the WebSocket application.
 */
public synchronized void register(String contextPath, String urlPattern, WebSocketApplication app) {
    ...
}

/**
 *
 * @deprecated Use {@link #register(String, String, WebSocketApplication)}
 */
@Deprecated
public synchronized void register(WebSocketApplication app) {
    ...
}

/**
 * Un-registers the specified {@link WebSocketApplication} with the
 * <code>WebSocketEngine</code>.
 *
 * @param app the {@link WebSocketApplication} to un-register.
 */
public void unregister(WebSocketApplication app) {
    ...
}

/**
 * Un-registers all {@link WebSocketApplication} instances with the
 * {@link WebSocketEngine}.
 */
public void unregisterAll() {
    ...
}
```

Registration may occur at any time. For example, if this application is
created/registered by a Servlet, then registration would happen in the
Servlet.init() method and deregistration may happen within the
Servlet.destroy() method. Regardless, the WebSocketApplication must be
registered with the WebSocketEngine in order to be queried as part of
the connection upgrade process.

Using Grizzly 2 WebSockets on the Server-Side
=============================================

In the previous sections we touched briefly how the WebSocket protocol
is implemented and the basic API. We'll now show the basics for creating
a WebSocket aware server and registering the WebSocketApplication with
the WebSocket runtime.

In this example, we'll be using Grizzly standalone (of course!). So
first, we'll create the HttpServer and enable WebSocket support.

```java
final HttpServer server = HttpServer.createSimpleServer("/var/www", 8080);
final WebSocketAddOn addon = new WebSocketAddOn();
for (NetworkListener listener : server.getListeners()) {
    listener.registerAddOn(addon);
}
```

That's all there is to it! Note, that you don't need to register the
addon for all listeners, only those listeners that you want WebSocket
support.

Next, we instantiate a WebSocketApplication implementation (we don't go
into the details of the implementation itself - that comes later) and
register it with the WebSocket runtime.

```java
final WebSocketApplication chatApplication = new ChatApplication();
WebSocketEngine.getEngine().register(chatApplication, "/chat");
```

Again, simple! Once the server is started, it will be ready to service
WebSocket requests.

For a more concrete (and runnable) example, review this simple
[WebSocket
Chat](http://java.net/projects/grizzly/sources/git/show/samples/websockets/chat?rev=afc47689a5c16663bc6a574e1ee2962d349cda2e)
application.

Grizzly WebSockets Client
=========================

The Grizzly project does have a WebSocket client if such functionality
is needed. Instead of repeating what has already been written, we'll
recommend that you read the following [blog
entry](http://www.notshabby.net/2012/01/async-http-client-1-7-0-released-details-on-the-grizzly-side-of-things/).
