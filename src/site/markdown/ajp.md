## AJP Overview

Starting with version 2.1, Grizzly supports
[AJP](http://en.wikipedia.org/wiki/Apache_JServ_Protocol) 1.3 (Apache
JServ Protocol) natively. "Natively" means AJP is implemented using core
Grizzly APIs and naturally fits into entire Grizzly infrastructure:
memory management, threading etc...

## How it works

The AJP protocol implementation is mainly represented by two Filters:
AjpMessageFilter, AjpHandlerFilter. The AjpMessageFilter is responsible
for constructing AJP protocol messages and AjpHandlerFilter contains the
actual processing logic, which works as a codec between AJP and HTTP
messages. All the Filters upstream to AjpHandlerFilter receive HTTP
messages for processing, so they are not even aware of AJP protocol.

Here is a FilterChain, which is being normally constructed, when Grizzly
HttpServer being used:

![""](images/ajp/httpserver-filterchain.png)

Now, what happens, if we want to use AJP? It's easy, we replace HTTP
Filter, which works as a codec for Buffer \<-\> HTTP transformation,
with the two Filters mentioned above: AjpMessageFilter and
AjpHandlerFilter:

![""](images/ajp/httpserver-ajp-filterchain.png)

So the Grizzly HttpServer Filter won't even notice it operates over AJP
rather than plain HTTP.

In order to simplify HttpServer AJP configuration, there is an AjpAddOn
available, which may be registered on the required HttpServer's
NetworkListener like:

```java
HttpServer httpServer = new HttpServer();
NetworkListener listener =
         new NetworkListener("grizzly",
         NetworkListener.DEFAULT_NETWORK_HOST, PORT);

AjpAddOn ajpAddon = new AjpAddOn();
listener.registerAddOn(ajpAddon);

httpServer.addListener(listener);
```

## AJP Configuration

<table>
<caption>AjpHandlerFilter and AjpAddOn Properties</caption>
<tbody>
<tr class="odd">
<td align="left">isTomcatAuthentication</td>
<td align="left">If set to true, the authentication will be done in Grizzly. Otherwise, the authenticated principal will be propagated from the native webserver and used for authorization in Grizzly. The default value is true.</td>
</tr>
<tr class="even">
<td align="left">secret</td>
<td align="left">If not null, only requests from workers with this secret keyword will be accepted, null means no secret check will be done.</td>
</tr>
<tr class="odd">
<td align="left">shutdownHandlers</td>
<td align="left">The set of ShutdownHandlers, which will be invoked, when AJP shutdown request received. The implementation itself doesn't perform any action related to shutdown request.</td>
</tr>
</tbody>
</table>

## Sample

There is a simple AJP "Hello World" sample available, which contains a
single class, single HttpHandler, which is accessible via AJP and HTTP
at the same time. Here is the most interesting part of it responsible
for HttpServer initialization.

```java
final HttpServer server = new HttpServer();

// Create plain HTTP listener, which will handle port 8080
final NetworkListener httpNetworkListener =
        new NetworkListener("http-listener", "0.0.0.0", 8080);

// Create AJP listener, which will handle port 8009
final NetworkListener ajpNetworkListener =
        new NetworkListener("ajp-listener", "0.0.0.0", 8009);
// Register AJP addon on HttpServer's listener
ajpNetworkListener.registerAddOn(new AjpAddOn());

server.addListener(httpNetworkListener);
server.addListener(ajpNetworkListener);

final ServerConfiguration config = server.getServerConfiguration();

// Map the path, /grizzly, to the HelloWorldHandler
config.addHttpHandler(new HelloWorldHandler(), "/grizzly");
```

The
[readme.txt](http://java.net/projects/grizzly/sources/git/content/samples/http-ajp-samples/readme.txt?rev=c8ff8e24974f3c4be9a1833e58adf139b656a730)
contains setup and run instructions.

Complete sources and instructions could be found
[here](http://java.net/projects/grizzly/sources/git/show/samples/http-ajp-samples?rev=c8ff8e24974f3c4be9a1833e58adf139b656a730).
