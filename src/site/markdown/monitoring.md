## Monitoring Overview

The ability to provide data of how an application is being used at
runtime is an important feature offered by many frameworks. Grizzly
2.3 is no exception. Grizzly provides the ability to
monitor key components within the framework and allows this monitoring
to be extended by custom authored components. Let's start by looking at
the entities that enable monitoring within Grizzly.

## Core Monitoring Artifacts

At the core of Grizzly monitoring are three simple artifacts in the
*org.glassfish.grizzly.monitoring package*. The first being called
*MonitoringAware* which denotes an entity may be monitored:

```java
/**
 * General interface for the objects, which could be monitored during the lifecycle.
 */
public interface MonitoringAware<E> {
    /**
     * Return the object associated {@link MonitoringConfig}.
     *
     * @return the object associated {@link MonitoringConfig}.
     */
    MonitoringConfig<E> getMonitoringConfig();
}
```

Entities such a *MemoryManager*, *Transport*, etc are all
*MonitoringAware*. As seen by the interface definition, all
*MonitoringAware* entities return a *MonitoringConfig* object with which
you can register monitoring probes.

```java
/**
 * General monitoring configuration interface.
 */
public interface MonitoringConfig<E> {
    /**
     * Add the monitoring probes, which will be notified about object's lifecycle events.
     *
     * @param probes the monitoring probes.
     */
    public void addProbes(E... probes);

    /**
     * Remove the monitoring probes.
     *
     * @param probes the monitoring probes.
     */
    public boolean removeProbes(E... probes);

    /**
     * Get the the monitoring probes, which are registered on the objet.
     * Please note, it's not appropriate to modify the returned array's content.
     * Please use {@link #addProbes(Object[])} and
     * {@link #removeProbes(Object[])} instead.
     *
     * @return the the monitoring probes, which are registered on the object.
     */
    public E[] getProbes();

    /**
     * Removes all the monitoring probes, which are registered on the object.
     */
    public void clearProbes();

    /**
     * Create the JMX {@link Object}, which represents this object.
     * 
     * @return the JMX {@link Object}, which represents this object.
     */
    public Object createManagementObject();
}
```

Since *MonitoringConfig* is really nothing more than a simplified
collection plus the *createManagementObject()* method responsible for returning
JMX representation of the object, Grizzly provides a default implementation called
*org.glassfish.grizzly.monitoring.DefaultMonitoringConfig* with *null* JMX representation. This should be
able to satisfied most developer needs.

Monitoring probes, as seen in the code example above, can be of any
type. Grizzly provides probe interfaces for all MonitoringAware entites
within the framework.

<table>
<caption>Core Module Probes</caption>
<tbody>
<tr class="odd">
<td align="left">org.glassfish.grizzly.TransportProbe</td>
<td align="left">Provides details on events happening within a particular Transport. Such events include when the transport is started, stopped, an error occurring, or if the transport's configuration has been changed.</td>
</tr>
<tr class="even">
<td align="left">org.glassfish.grizzly.ConnectionProbe</td>
<td align="left">Provides details on Connections within the framework. This includes both binding of server-side sockets, or inbound connections from clients.</td>
</tr>
<tr class="odd">
<td align="left">org.glassfish.grizzly.memory.MemoryProbe</td>
<td align="left">Provides buffer allocation (both pooled and non-pooled)/deallocation events.</td>
</tr>
<tr class="even">
<td align="left">org.glassfish.grizzly.threadpool.ThreadPoolProbe</td>
<td align="left">Provides details relating to the lifecycle of the threadpool itself as well as that of the threads it manages as well as delegated task information.</td>
</tr>
</tbody>
</table>

<table>
<caption>Http Module Probes</caption>
<tbody>
<tr class="odd">
<td align="left">org.glassfish.grizzly.http.HttpProbe</td>
<td align="left">Provides details related to the HTTP codec processing itself. Details such as the parsing headers, content chunks, as well as the reverse when content is serialized to the wire.</td>
</tr>
<tr class="even">
<td align="left">org.glassfish.grizzly.http.KeepAliveProbe</td>
<td align="left">Provides details pertaining to HTTP keep-alive statistics.</td>
</tr>
</tbody>
</table>

<table>
<caption>Http Server Module Probes</caption>
<tbody>
<tr class="odd">
<td align="left">org.glassfish.grizzly.http.server.HttpServerProbe</td>
<td align="left">Provides details relating to request processing lifecycles, such as request started, completed, suspended, timed-out, or cancelled.</td>
</tr>
<tr class="even">
<td align="left">org.glassfish.grizzly.http.server.filecache.FileCacheProbe</td>
<td align="left">Provides file cache statistics such as a entry being cached, removed from the cache, and cache hits/misses.</td>
</tr>
</tbody>
</table>

JMX
===

No monitoring would be complete without support form JMX. Given that,
Grizzly does provide out-of-the-box support for JMX, however, in order
to make our lives easier, we've decided to use another open source
library called [GMBAL](http://kenai.com/projects/gmbal/pages/Home)
(pronounced "gumball") upon which to build our JMX support. To make our
footprint lighter, we separated out JMX related entities and logic into separate modules:

## The core framework JMX monitoring:
```xml
<dependency>
    <groupId>org.glassfish.grizzly</groupId>
    <artifactId>grizzly-framework-monitoring</artifactId>
    <version>2.3.31</version>
</dependency>
```

## The HTTP protocol JMX monitoring:
```xml
<dependency>
    <groupId>org.glassfish.grizzly</groupId>
    <artifactId>grizzly-http-monitoring</artifactId>
    <version>2.3.31</version>
</dependency>
```

## The HTTP server JMX monitoring:
```xml
<dependency>
    <groupId>org.glassfish.grizzly</groupId>
    <artifactId>grizzly-http-server-monitoring</artifactId>
    <version>2.3.31</version>
</dependency>
```

If you're not using maven, you can download the JARs you need for your
project from maven central.

### JMX Core Monitoring Artifacts

In order to make a *MonitoringAware* object JMX compliant
```java
monitoringAware.getMonitoringConfig().createManagementObject();
```
has to return proper non-null JMX representation.

If you implement your own *MonitoringAware* class and want it to be JMX aware -
you might want your JMX representation implement JmxObject interface.
The JmxObject implementation describes the entity that will be registered with the JMX
runtime. The concrete JmxObject implementation typically wraps the
Grizzly artifact to be managed. Here is a relatively simple example:

```java
/**
 * JMX management object for {@link org.glassfish.grizzly.http.KeepAlive}.
 *
 * @since 2.0
 */
@ManagedObject
@Description("The configuration for HTTP keep-alive connections.")
public class KeepAlive extends JmxObject {
    /**
     * The {@link org.glassfish.grizzly.http.KeepAlive} being managed.
     */
    private final org.glassfish.grizzly.http.KeepAlive keepAlive;

    /**
     * The number of live keep-alive connections.
     */
    private final AtomicInteger keepAliveConnectionsCount = new AtomicInteger();

    /**
     * The number of requests processed on a keep-alive connections.
     */
    private final AtomicInteger keepAliveHitsCount = new AtomicInteger();

    /**
     * The number of times keep-alive mode was refused.
     */
    private final AtomicInteger keepAliveRefusesCount = new AtomicInteger();

    /**
     * The number of times idle keep-alive connections were closed by timeout.
     */
    private final AtomicInteger keepAliveTimeoutsCount = new AtomicInteger();

    /**
     * The {@link JMXKeepAliveProbe} used to track keep-alive statistics.
     */
    private final JMXKeepAliveProbe keepAliveProbe = new JMXKeepAliveProbe();

    // ------------------------------------------------------------ Constructors


    /**
     * Constructs a new JMX managed KeepAlive for the specified
     * {@link org.glassfish.grizzly.http.KeepAlive} instance.
     *
     * @param keepAlive the {@link org.glassfish.grizzly.http.KeepAlive}
     *  to manage.
     */
    public KeepAlive(org.glassfish.grizzly.http.KeepAlive keepAlive) {
        this.keepAlive = keepAlive;
    }

    // -------------------------------------------------- Methods from JmxObject


    /**
     * {@inheritDoc}
     */
    @Override
    public String getJmxName() {
        return "Keep-Alive";
    }

    /**
     * <p>
     * {@inheritDoc}
     * </p>
     *
     * <p>
     * When invoked, this method will add a {@link KeepAliveProbe} to track
     * statistics.
     * </p>
     */
    @Override
    protected void onRegister(GrizzlyJmxManager mom, GmbalMBean bean) {
        keepAlive.getMonitoringConfig().addProbes(keepAliveProbe);
    }

    /**
     * <p>
     * {@inheritDoc}
     * </p>
     *
     * <p>
     * When invoked, this method will remove the {@link KeepAliveProbe} added
     * by the {@link #onRegister(GrizzlyJmxManager, GmbalMBean)}
     * call.
     * </p>
     */
    @Override
    protected void onDeregister(GrizzlyJmxManager mom) {
        keepAlive.getMonitoringConfig().removeProbes(keepAliveProbe);
    }

    // --------------------------------------------------- Keep Alive Properties


    /**
     * @see org.glassfish.grizzly.http.KeepAlive#getIdleTimeoutInSeconds()
     */
    @ManagedAttribute(id="idle-timeout-seconds")
    @Description("The time period keep-alive connection may stay idle")
    public int getIdleTimeoutInSeconds() {
        return keepAlive.getIdleTimeoutInSeconds();
    }

    /**
     * @see org.glassfish.grizzly.http.KeepAlive#getMaxRequestsCount()
     */
    @ManagedAttribute(id="max-requests-count")
    @Description("the max number of HTTP requests allowed to be processed on one keep-alive connection")
    public int getMaxRequestsCount() {
        return keepAlive.getMaxRequestsCount();
    }

    /**
     * @return the number live keep-alive connections.
     */
    @ManagedAttribute(id="live-connections-count")
    @Description("The number of live keep-alive connections")
    public int getConnectionsCount() {
        return keepAliveConnectionsCount.get();
    }

    /**
     * @return the number of requests processed on a keep-alive connections.
     */
    @ManagedAttribute(id="hits-count")
    @Description("The number of requests processed on a keep-alive connections.")
    public int getHitsCount() {
        return keepAliveHitsCount.get();
    }

    /**
     * @return the number of times keep-alive mode was refused.
     */
    @ManagedAttribute(id="refuses-count")
    @Description("The number of times keep-alive mode was refused.")
    public int getRefusesCount() {
        return keepAliveRefusesCount.get();
    }

    /**
     * @return the number of times idle keep-alive connections were closed by timeout.
     */
    @ManagedAttribute(id="timeouts-count")
    @Description("The number of times idle keep-alive connections were closed by timeout.")
    public int getTimeoutsCount() {
        return keepAliveTimeoutsCount.get();
    }

    // ---------------------------------------------------------- Nested Classes


    /**
     * JMX statistic gathering {@link KeepAliveProbe}.
     */
    private final class JMXKeepAliveProbe implements KeepAliveProbe {

        @Override
        public void onConnectionAcceptEvent(Connection connection) {
            keepAliveConnectionsCount.incrementAndGet();
            connection.addCloseListener(new Connection.CloseListener() {

                @Override
                public void onClosed(Closeable closeable) throws IOException {
                    keepAliveConnectionsCount.decrementAndGet();
                }
            });
        }

        @Override
        public void onHitEvent(Connection connection, int requestCounter) {
            keepAliveHitsCount.incrementAndGet();
        }

        @Override
        public void onRefuseEvent(Connection connection) {
            keepAliveRefusesCount.incrementAndGet();
        }

        @Override
        public void onTimeoutEvent(Connection connection) {
            keepAliveTimeoutsCount.incrementAndGet();
        }


        // ----------------------------------------- Methods from KeepAliveProbe


    } // END JMXKeepAliveProbe
}
```

There are several things going on here that warrant explanation.

-   Line 59: Declared as a JMX managed entity via the @ManagedObject
    annotation from GMBAL. This annotation must be present on the
    entities to be managed by JMX in order for GMBAL to do its magic.

-   Line 102: Constructor taking in an actual
    org.glassfish.grizzly.http.KeepAlive instance. This is needed as the
    actual KeepAlive instance has the monitoring config where probes can
    be registered.

-   Line 113: Returns the name to be displayed via JMX.

-   Line 128: This callback will be invoked by the GMBAL runtime when
    this @ManagedObject is registered with JMX. It's at this point in
    time that we register the probe implementation (starting at line
    208) with the KeepAlive instance's *JmxMonitoringConfig*.

-   Line 144: This callback will be invoked by the GMBAL runtime when
    this @ManagedObject is de-registered with JMX. It's at this point in
    time that we remove the probe implementation (starting at line 208)
    with the KeepAlive instance's *JxmMonintoringConfig*.

-   Lines 151-203: These define @ManagedAttributes along with their
    descriptions which will be exposed via JMX.

Using the example JmxObject implementation above, the
org.glassfish.grizzly.http.KeepAlive.createManagementObject() is
simple:

```java
protected Object createManagementObject() {
    return new org.glassfish.grizzly.http.jmx.KeepAlive(this);
}
```

The final piece here is how to actually hook into the JMX runtime. It's
easy!

```java
public static void main(String[] args) {

        final GrizzlyJmxManager manager = GrizzlyJmxManager.instance();
        final TCPNIOTransport transport1 = TCPNIOTransportBuilder.newInstance().build();
        final TCPNIOTransport transport2 = TCPNIOTransportBuilder.newInstance().build();
        try {
            Object jmxTransportObject1 =
                    transport1.getMonitoringConfig().createManagementObject();

            Object jmxTransportObject2 =
                    transport2.getMonitoringConfig().createManagementObject();

            manager.registerAtRoot(jmxTransportObject1, "Transport1");
            manager.registerAtRoot(jmxTransportObject2, "Transport2");
            transport1.start();
            transport1.bind(9999);
            System.out.println("Press any key to stop the example...");
            System.in.read();
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.exit(1);
        } finally {
            try {
                transport1.shutdownNow();
            } catch (IOException ignored) {}
        }
}
```

Once running, you can connect to the process via JConsole and inspect
the results (e.g., see that Transport1 is started while Transport2 is
stopped).

Grizzly HTTP JMX Server Monitoring
==================================

If you're using Grizzly\'s HttpServer, enabling monitoring is very
simple:

```java
public static void main(String[] args) {

        HttpServer gws = new HttpServer();
        HttpServer gws1 = new HttpServer();
        NetworkListener listener1 = new NetworkListener("listener1", "localhost", 19080);
        NetworkListener listener2 = new NetworkListener("listener2", "localhost", 19081);
        gws.addListener(listener1);
        gws1.addListener(listener2);

        try {
            gws.start();
            gws1.start();
            gws.getServerConfiguration().setJmxEnabled(true);
            gws1.getServerConfiguration().setJmxEnabled(true);
            assertTrue(true);
        } catch (IOException ioe) {
            ioe.printStackTrace();
            System.exit(1);
        } finally {
            try {
                gws.shutdownNow();
                gws1.shutdownNow();
            } catch (IOException ignored) {}
        }
}
```

Notice that enabling or disabling JMX support is dynamic - no need to
restart the server instances.
