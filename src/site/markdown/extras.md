## Core Framework Extras

These following are extras built off the core Grizzly framework.

### Client-Side Connection Pool

As of Grizzly 2.3.4, a new client-side connection pool API has been introduced.
This API is completely different from one provided by Grizzly 1.9.x, it has more
features and hopefully is nicer and easier to use.  There are 2 main abstractions:
SingleEndpointPool and MultiEndpointPool, which represent a connection pool to a
single and multiple endpoints respectively. Each connection pool abstraction has
a builder, which helps to construct and initialize a connection pool of a
specific configuration.

<!-- The javadocs for the full connection pool package may be found [here][pkgdocs]. -->

[pkgdocs]: https://grizzly.java.net/docs/2.3/apidocs/org/glassfish/grizzly/connectionpool/package-summary.html

## SingleEndpointPool

The SingleEndpointPool represents a connection pool to a single endpoint. For
example if we want to create a connection pool to “grizzly.java.net:443″ and set
the maximum number of connections in pool equal to eight – the code will look like:

[sep]: https://grizzly.java.net/docs/2.3/apidocs/org/glassfish/grizzly/connectionpool/SingleEndpointPool.html

```java
TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
                .processor(myFilterChain)
                .build();
transport.start();

SingleEndpointPool pool = SingleEndpointPool
                .builder(SocketAddress.class)
                .connectorHandler(transport)
                .endpointAddress(new InetSocketAddress("grizzly.java.net", 443))
                .maxPoolSize(8)
                .build();

try {
    // use the connection pool
    ............
} finally {
    pool.close();
    transport.shutdownNow();
}
```

A Connection could be asynchronously obtained from the pool using either
Future or CompletionHandler:

```java
Future<Connection> connectionFuture = pool.take();
```

or

```java
CompletionHandler<Connection> myCompletionHandler = createMyCompletionHandler();
pool.take(myCompletionHandler);
```

Please note, if you try to retrieve a Connection using Future, but suddenly changed
your mind and don’t want to wait for a Connection, you have to use the code like:

```java
if (!connectionFuture.cancel(false)) {
    // means Connection is ready
    pool.release(connectionFuture.get());
}
```

to properly cancel the asynchronous operation and return a Connection (if there
is any) back to the pool. In general it is highly important to return a
Connection back to a pool once you don’t need it to avoid connection pool starvation,
which will make connection pool useless.

```java
pool.release(connection);
```

Another interesting feature of Grizzly connection pool, is the ability to attach/detach
connections to/from a pool. For example if you retrieved a connection, but don’t
plan to use it as part of the pool or don’t plan to return it back to the pool,
you can detach this connection:

```java
Connection connection = pool.take().get(10, TimeUnit.SECONDS);
pool.detach(connection);
```

and the pool will be able to establish a new connection (lazily, if needed) to
reimburse it. On other hand if you have a connection, created outside the pool
(or detached from the pool) and you want to attach this connection to the pool
– you can call:

```java
pool.attach(foreignConnection);
```

When the pool is not needed anymore (usually when you exit application), it is
recommended to close it:

```java
pool.close();
```

During the close() operation execution all the idle connections will be closed.
The busy connections, which were not returned back to the pool yet, will be kept
open and will be closed once you will try to return them back to the pool.

## SingleEndpointPool Configuration

<table style="font-size:75%;">
<tbody>
<tr>
<th style="width:10%;padding:4px;" align="center">Property</th>
<th style="width:60%;padding:4px;" align="center">Description</th>
<th style="width:30%;padding:4px;" align="center">Notes</th>
</tr>
<tr>
<td style="padding:10px;">connectorHandler</td>
<td style="padding:10px;">The ConnectorHandler to be used to establish Connections to the endpoint</td>
<td style="padding:10px;" align="center">mandatory</td>
</tr>
<tr>
<td style="padding:10px;">endpointAddress</td>
<td style="padding:10px;">The remote endpoint address to open Connection to</td>
<td style="padding:10px;" align="center">mandatory</td>
</tr>
<tr>
<td style="padding:10px;">localEndpointAddress</td>
<td style="padding:10px;">The local endpoint address to bind Connection to</td>
<td style="padding:10px;" align="center">optional</td>
</tr>
<tr id="corepoolsize">
<td style="padding:10px;">corePoolSize</td>
<td style="padding:10px;">The number of Connections, kept in the pool, that are immune to keep-alive mechanism</td>
<td style="padding:10px;" align="center">Default value: 0</td>
</tr>
<tr>
<td style="padding:10px;">maxPoolSize</td>
<td style="padding:10px;">The max number of Connections kept by this pool</td>
<td style="padding:10px;" align="center">Default value: 4</td>
</tr>
<tr>
<td style="padding:10px;">connectTimeout</td>
<td style="padding:10px;">The connect timeout, after which, if a connection is not established, it is considered failed</td>
<td style="padding:10px;" align="center">value &lt; 0 disables the timeout. By default disabled</td>
</tr>
<tr>
<td style="padding:10px;">reconnectDelay</td>
<td style="padding:10px;">The delay to be used before the pool will repeat the attempt to connect to the endpoint after previous connect had failed</td>
<td style="padding:10px;" align="center">value &lt; 0 disables reconnect. By default disabled</td>
</tr>
<tr>
<td style="padding:10px;">maxReconnectAttempts</td>
<td style="padding:10px;">The maximum number of attempts to reconnect that will be made before notification of failure occurs</td>
<td style="padding:10px;" align="center">Default value: 5</td>
</tr>
<tr>
<td style="padding:10px;">keepAliveTimeout</td>
<td style="padding:10px;">The maximum amount of time an idle Connection will be kept in the pool. The idle Connections will be closed till the pool size is greater than <a href="#corepoolsize">corePoolSize</a></td>
<td style="padding:10px;" align="center">value &lt; 0 disables keep-alive mechanism. Default value: 30 seconds</td>
</tr>
<tr>
<td style="padding:10px;">keepAliveCheckInterval</td>
<td style="padding:10px;">The interval, which specifies how often the pool will perform idle Connections check</td>
<td style="padding:10px;" align="center">Default value: 5 seconds</td>
</tr>
</tbody>
</table>

## MultiEndpointPool

The MultiEndpointPool represents a connection pool to multiple endpoints. We can
think of MultiEndpointPool as an Endpoint-to-SingleEndpointPool map, where each endpoint
is represented by an EndpointKey. The MultiEndpointPool supports pretty much the same
set of operations as SingleEndpointPool, but some of these operations (especially related to the
Connection allocation) require EndpointKey parameter. Here is an example of MultiEndpointPool,
which is used to allocate connections to 2 different servers:

[mep]: https://grizzly.java.net/docs/2.3/apidocs/org/glassfish/grizzly/connectionpool/MultiEndpointPool.html

```java
// Build a connection pool
MultiEndpointPool pool = MultiEndpointPool
          .builder(SocketAddress.class)
          .connectorHandler(transport)
          .maxConnectionsPerEndpoint(4)
          .maxConnectionsTotal(16)
          .build();

// define endpoints
EndpointKey endpointKey1 =
           new EndpointKey("endpoint1",
                           new InetSocketAddress("grizzly.java.net", 80));
EndpointKey endpointKey2 =
           new EndpointKey("endpoint2",
                           new InetSocketAddress("mytecc.wordpress.com", 80));
Connection c1 = null;
Connection c2 = null;
try {
    c1 = pool.take(endpointKey1).get();
    c2 = pool.take(endpointKey2).get();
..........................
} finally {
    if (c1 != null) {
        pool.release(c1);
    }
    if (c2 != null) {
        pool.release(c2);
    }
}
```

The SingleEndpointPool and the MultiEndpointPool have similar configuration
properties, which could be tuned: max pool size, connect timeout, keep-alive
timeout, reconnect delay etc. Additionally for MultiEndpointPool it is possible
to tune max connections per endpoint property, which lets us limit the maximum
number of connections to a single endpoint.

## MultiEndpointPool Configuration

<table style="font-size:75%;">
<tbody>
<tr>
<th style="width:10%;padding:4px;" align="center">Property</th>
<th style="width:60%;padding:4px;" align="center">Description</th>
<th style="width:30%;padding:4px;" align="center">Notes</th>
</tr>
<tr>
<td style="padding:10px;">defaultConnectorHandler</td>
<td style="padding:10px;">The default ConnectorHandler to be used to establish Connections to an endpoint</td>
<td style="padding:10px;" align="center">mandatory. It is still possible to set a ConnectorHandler per each endpoint separately</td>
</tr>
<tr>
<td style="padding:10px;">maxConnectionsPerEndpoint</td>
<td style="padding:10px;">The maximum number of Connections each SingleEndpointPool sub-pool is allowed to have</td>
<td style="padding:10px;" align="center">Default value: 2</td>
</tr>
<tr>
<td style="padding:10px;">maxConnectionsTotal</td>
<td style="padding:10px;">The total maximum number of Connections to be kept by the pool</td>
<td style="padding:10px;" align="center">Default value: 16</td>
</tr>
<tr>
<td style="padding:10px;">connectTimeout</td>
<td style="padding:10px;">The connect timeout, after which, if a connection is not established, it is considered failed</td>
<td style="padding:10px;" align="center">value &lt; 0 disables the timeout. By default disabled</td>
</tr>
<tr>
<td style="padding:10px;">reconnectDelay</td>
<td style="padding:10px;">The delay to be used before the pool will repeat the attempt to connect to the endpoint after previous connect had failed</td>
<td style="padding:10px;" align="center">value &lt; 0 disables reconnect. By default disabled</td>
</tr>
<tr>
<td style="padding:10px;">maxReconnectAttempts</td>
<td style="padding:10px;">The maximum number of attempts to reconnect that will be made before notification of failure occurs</td>
<td style="padding:10px;" align="center">Default value: 5</td>
</tr>
<tr>
<td style="padding:10px;">keepAliveTimeout</td>
<td style="padding:10px;">The maximum amount of time an idle Connection will be kept in the pool</td>
<td style="padding:10px;" align="center">value &lt; 0 disables keep-alive mechanism. Default value: 30 seconds</td>
</tr>
<tr>
<td style="padding:10px;">keepAliveCheckInterval</td>
<td style="padding:10px;">The interval, which specifies how often the pool will perform idle Connections check</td>
<td style="padding:10px;" align="center">Default value: 5 seconds</td>
</tr>
</tbody>
</table>

## Samples

A complete example using the MultiEndpointPool may be found [here](https://github.com/GrizzlyNIO/grizzly-mirror/tree/2.3.x/samples/connection-pool-samples/src/main/java/org/glassfish/grizzly/samples/connectionpool).

### Server Name Indication (SNI) TLS extension support

Grizzly 2.3.12 and later includes SNI TLS extension support for both server and client side.
The client implementation is based on JDK 7 client-side SNI support, so it will work on JDK 7+ only,
the server implementation doesn't rely on JDK SNI support and could be used on any JDK supported by the core Grizzly framework.
The core SNI functionality is implemented as part of `SNIFilter`, which is an extension of `SSLFilter`.
The first step to start using the SNI extension is to insert the `SNIFilter` into the FilterChain (replacing the `SSLFilter`, if the one is used)
and pass default `SSLEngineConfigurator` settings, which will be used for non SNI-aware Connections.

```java
SSLEngineConfigurator serverEngineConfig = createDefaultServerSideConfig();
SSLEngineConfigurator clientEngineConfig = createDefaultClientSideConfig();

SNIFilter sniFilter = new SNIFilter(serverEngineConfig, clientEngineConfig);
```

The instructions for the server and the client side support for the SNI extension are
different, so they will be covered separately.

## SNI server-side support

The Grizzly server-side SNI implementation allows users to chose an SSL configuration, represented
by SSLEngineConfigurator, for each accepted connection, based on a SNI host name
information passed by a client. In order to achieve this, the user has to create
and register an `SNIServerConfigResolver`:

```java
// Register SNIServerConfigResolver to handle new SNI-aware Connections
// and chose proper SSL configuration based on the SNI host passed by
// clients
sniFilter.setServerSSLConfigResolver(new SNIServerConfigResolver() {

    @Override
    public SNIConfig resolve(Connection connection, String hostname) {
        SSLEngineConfigurator sslEngineConfig = host2SSLConfigMap.get(hostname);

        // if sslEngineConfig is null - default server-side configuration,
        // which was passed in the SNIFilter constructor, will be used.

        return SNIConfig.newServerConfig(sslEngineConfig);
    }
});
```

the main responsibility of `SNIServerConfigResolver` is to provide custom SSL configuration for
an accepted connection based on the SNI host name provided by a client.

## SNI client-side support

By default, there's no additional configuration to SNI-enable the client outside of adding the `SNIFilter`
to a FilterChain. Every time a client-side connection is established, Grizzly will
pick up the remote host address specified by a user and send it to the server as SNI host.
Please note that according to the SNI specification, IP addresses are not considered a valid SNI host.
If an IP address is used to establish a client-side connection, no SNI host information will
be sent to a server.  See the following example:

```java
SSLEngineConfigurator serverEngineConfig = createDefaultServerSideConfig();
SSLEngineConfigurator clientEngineConfig = createDefaultClientSideConfig();

SNIFilter sniFilter = new SNIFilter(serverEngineConfig, clientEngineConfig);

FilterChain chain = FilterChainBuilder.stateless()
        .add(new TransportFilter())
        .add(sniFilter)
        ......
        .build();

TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
        .setProcessor(chain)
        .build();

transport.start();

Connection c1 = transport.connect("grizzly.java.net", 443).get();
Connection c2 = transport.connect("10.20.30.40", 443).get();
```

Connection c1 will be established and "grizzly.java.net" will be sent to a server as
SNI host information, however connection C2 will not pass any SNI host information
to the server, because an IP address was used.

If SNI host customization is required on the client, the user may register an `SNIClientConfigResolver`:

```java
// Register SNIClientConfigResolver to handle new SNI-aware Connections
// and chose proper host name and SSL configuration to be used for
// a client-side Connection
sniFilter.setClientSSLConfigResolver(new SNIClientConfigResolver() {

    @Override
    public SNIConfig resolve(Connection connection) {
        return SNIConfig.newClientConfig("my.hostname");
    }
});
```

So if we use `SNIClientConfigResolver` with our first example:

```java
SSLEngineConfigurator serverEngineConfig = createDefaultServerSideConfig();
SSLEngineConfigurator clientEngineConfig = createDefaultClientSideConfig();

SNIFilter sniFilter = new SNIFilter(serverEngineConfig, clientEngineConfig);

// Register SNIClientConfigResolver to handle new SNI-aware Connections
// and chose proper host name and SSL configuration to be used for
// a client-side Connection
sniFilter.setClientSSLConfigResolver(new SNIClientConfigResolver() {

    @Override
    public SNIConfig resolve(Connection connection) {
        return SNIConfig.newClientConfig("my.hostname");
    }
});

FilterChain chain = FilterChainBuilder.stateless()
        .add(new TransportFilter())
        .add(sniFilter)
        ......
        .build();

TCPNIOTransport transport = TCPNIOTransportBuilder.newInstance()
        .setProcessor(chain)
        .build();

transport.start();

Connection c2 = transport.connect("10.20.30.40", 443).get();
```

Connection c1 will be established and "my.hostname" will be sent to a server as
SNI host information.
