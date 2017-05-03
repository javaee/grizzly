## JAXWS Overview

Starting with version 2.1.2, Grizzly supports
[JAX-WS](http://jax-ws.java.net/) web services execution via [HTTP
server framework](httpserverframework.html). Using this feature it is
possible to run JAX-WS web services without need to run them inside
web-/app-server, but at the same time use all the Grizzly features in
order to optimize web service execution.

## How it works

The JAX-WS web services support is represented by the JaxwsHandler,
which extends HttpHandler (see HTTP Server [documentation](#httpserverframework.html)
functionality, so each time JaxwsHandler is getting called, it passes
control to JAX-WS core, which in its turn calls correspondent web
service.

A JaxwsHandler could be associated with only one web service, which
could be passed to JaxwsHandler constructor.two ways:

-   Web service instance, so default configuration will be used;

-   [WSEndpoint](http://java.net/projects/jax-ws/sources/sources/content/tags/JAXWS_2_2_5_07282011/jaxws-ri/rt/src/com/sun/xml/ws/api/server/WSEndpoint.java),
    which may contain customized web services configuration, like
    binding, WSDL location etc...

A JaxwsHandler is able to process not just web service's operation
calls, but also meta data requests like requests for web service's WSDL
and XSD. So if you register JaxwsHandler to serve web service on
*http://localhost:8080/addservice*, the WSDL of the service would be
published at *http://localhost:8080/addservice?WSDL*.

## JaxwsHandler Configuration

<table>
<caption>JaxwsHandler constructor parameters</caption>
<tbody>
<tr class="odd">
<td align="left">implementer</td>
<td align="left">The web service instance. When using programmatic approach, it might be just an instance of class, annotated as @WebService. By using &quot;implementor&quot; approach, when constructing JaxwsHandler, we're actually asking JAX-WS core to configure web service basing on implementor's class name and annotations.</td>
</tr>
<tr class="even">
<td align="left">endpoint</td>
<td align="left">The web services, represented by <a href="http://java.net/projects/jax-ws/sources/sources/content/tags/JAXWS_2_2_5_07282011/jaxws-ri/rt/src/com/sun/xml/ws/api/server/WSEndpoint.java">WSEndpoint</a>. Unlike &quot;implementor&quot; approach, when using endpoint, we can customize web service more finely. It's also possible to use external config files in this case.</td>
</tr>
<tr class="odd">
<td align="left">isAsync</td>
<td align="left">If true, the JaxwsHandler will execute WebService in asynchronous mode, otherwise synchronous.</td>
</tr>
</tbody>
</table>

## Sample

It's very easy to publish web service, using Grizzly. Assume we have
"add" web service declared following way:

```java
import javax.jws.WebService;
import javax.jws.WebMethod;
import javax.jws.WebParam;

@WebService
public class AddService {
    @WebMethod
    public int add(@WebParam(name="value1") int value1, @WebParam(name="value2") int value2) {
        return value1 + value2;
    }
}
```

In order to make this web service available on URL:
*http://localhost:8080/add*, we do this:

```java
HttpServer httpServer = new HttpServer();
NetworkListener networkListener = new NetworkListener("jaxws-listener", "0.0.0.0", PORT);

HttpHandler httpHandler = new JaxwsHandler(new AddService());
httpServer.getServerConfiguration().addHttpHandler(httpHandler, "/add");
httpServer.addListener(networkListener);

httpServer.start();
```

It's easy to test this, just type *http://localhost:8080/add?WSDL* in
your browser to check the web service's WSDL.
