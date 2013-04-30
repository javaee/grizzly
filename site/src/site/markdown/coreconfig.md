Core Configuration
==================

The primary points of configuration of the core framework are that of
the Transport instances and their associated thread pools. The ability
to configure both entities is possible via the NIOTransportBuilder.

Transport Configuration
-----------------------

Just as there are concrete NIOTransport implementations for TCP and UDP,
so too are there two concrete NIOTransportBuilder implementations. Each
NIOTransportBuilder implementation exposes configurable features unique
to each transport. The following describes configuration properties
common to all NIOTransports and then describes the properties for the
TCP and UDP NIOTransport implementations.

<table>
<caption>NIOTransportBuilder Properties</caption>
<tbody>
<tr class="odd">
<td align="left">workerThreadPoolConfig</td>
<td align="left">This property exposes a ThreadPoolConfig instance that allows configuration of the worker thread pool used by the transport that will be constructed by this builder. Note: depending on the IOStrategy being used, this value may be null.</td>
</tr>
<tr class="even">
<td align="left">selectorThreadPoolConfig</td>
<td align="left">This propery exposes a ThreadPoolConfig instance that allows configuration of the selector/kernel thread pool used by the transport that will be constructed by this builder.</td>
</tr>
<tr class="odd">
<td align="left">IOStrategy</td>
<td align="left">Sets the IOStrategy that will be used by this transport. Note that changing this value before the transport has been started may have an impact on the return value of the workerThreadPoolConfig property. If no value is explicitly set, the WorkerThreadIOStrategy will be employed. See the section on <a href="iostrategies.xml">IOStrategies</a> for specifics on each concrete IOStrategy included with Grizzly 2.3</td>
</tr>
<tr class="even">
<td align="left">memoryManager</td>
<td align="left">Sets the MemoryManager to be used by this transport. If no value is explicitly set, the MemoryManager used will be the NIOTransportBuilder.DEFAULT_MEMORY_MANAGER. See the section on <a href="memory.xml">Memory Management</a> for specifics on the MemoryManager system.</td>
</tr>
<tr class="odd">
<td align="left">selectorHandler</td>
<td align="left">Sets the SelectorHandler to be used by this transport. If no value is explicitly set, the SelectorHandler used wil be the NIOTransportBuilder.DEFAULT_SELECTOR_HANDLER. See the section on <a href="transports-connections.xml">Transports and Connections</a> for specifics on the SelectorHandler.</td>
</tr>
<tr class="even">
<td align="left">selectionKeyHandler</td>
<td align="left">Sets the SelectionKeyHandler to be used by this transport. If no value is explicitly set, the SelectionKeyHandler used will be the NIOTransportBuilder.DEFAULT_SELECTION_KEY_HANDLER. See the section on <a href="transports-connections.xml">Transports and Connections</a> for specifics on the SelectionKeyHandler.</td>
</tr>
<tr class="odd">
<td align="left">attributeBuilder</td>
<td align="left">Sets the AttributeBuilder to be used by this transport. If no value is explicitly set, the AttributeBuilder used will be the NIOTransportBuilder.DEFAULT_ATTRIBUTE_BUILDER.</td>
</tr>
<tr class="even">
<td align="left">NIOChannelDistributor</td>
<td align="left">Sets the NIOChannelDistributor used by this transport. See the section on <a href="transports-connections.xml?">Transports and Connections</a> for specifics on the NIOChannelDistributor.</td>
</tr>
<tr class="odd">
<td align="left">processor</td>
<td align="left">Sets the Processor used by this transport.</td>
</tr>
<tr class="even">
<td align="left">processorSelector</td>
<td align="left">Sets the ProcessorSelector used by this transport.</td>
</tr>
<tr class="odd">
<td align="left">readBufferSize</td>
<td align="left">Sets the size of the Buffer that will be allocated, per-connection, to read incoming data.</td>
</tr>
<tr class="even">
<td align="left">writeBuffersSize</td>
<td align="left">Sets the size of the Buffer that will be applicated, per-connection, to write outgoing data.</td>
</tr>
</tbody>
</table>

<table>
<caption>TCPNIOTransportBuilder Properties</caption>
<tbody>
<tr class="odd">
<td align="left">clientSocketSoTimeout</td>
<td align="left">Enable/disable SO_TIMEOUT with the specified timeout, in milliseconds (client mode).</td>
</tr>
<tr class="even">
<td align="left">connectionTimeout</td>
<td align="left">Time in milliseconds for how long establishing a connection can take before the operation times out.</td>
</tr>
<tr class="odd">
<td align="left">keepAlive</td>
<td align="left">Enable/disable SO_KEEPALIVE.</td>
</tr>
<tr class="even">
<td align="left">linger</td>
<td align="left">Enable/disable SO_LINGER with the specified linger time in seconds. The maximum timeout value is platform specific. The setting only affects socket close.</td>
</tr>
<tr class="odd">
<td align="left">reuseAddress</td>
<td align="left">Enable/disable the SO_REUSEADDR socket option. When a TCP connection is closed the connection may remain in a timeout state for a period of time after the connection is closed (typically known as the TIME_WAIT state or 2MSL wait state). For applications using a well known socket address or port it may not be possible to bind a socket to the required SocketAddress if there is a connection in the timeout state involving the socket address or port.</td>
</tr>
<tr class="even">
<td align="left">serverConnectionBacklog</td>
<td align="left">Specifies the maximum pending connection queue length.</td>
</tr>
<tr class="odd">
<td align="left">serverSocketSoTimeout</td>
<td align="left">Enable/disable SO_TIMEOUT with the specified timeout, in milliseconds (server mode).</td>
</tr>
<tr class="even">
<td align="left">tcpNoDelay</td>
<td align="left">Enable/disable TCP_NODELAY (disable/enable Nagle's algorithm).</td>
</tr>
<tr class="odd">
<td align="left">temporarySelectorIO</td>
<td align="left">Allows the specification of a TemporarySelectorIO instance to aid in the simulation of blocking IO.</td>
</tr>
<tr class="even">
<td align="left">optimizedForMultiplexing</td>
<td align="left">Controlls the behavior of writing to a connection. If enabled, then all writes regardless if the current thread can write directly to the connection or not, will be passed to the async write queue. When the write actually occurs, the transport will attempt to write as much content from the write queue as possible. This option is disabled by default.</td>
</tr>
<tr class="odd">
<td align="left">maxAsyncWriteQueueSizeInBytes</td>
<td align="left">Specifies the size, in bytes, of the async write queue on a per-connection basis. If not specified, the value will be configured to be four times the size of the system's socket write buffer size. Setting this value to -1 will allow the queue to be unbounded.</td>
</tr>
</tbody>
</table>

<table>
<caption>UDPNIOTransportBuilder Properties</caption>
<tbody>
<tr class="odd">
<td align="left">connectionTimeout</td>
<td align="left">Time in milliseconds for how long establishing a connection can take before the operation times out.</td>
</tr>
<tr class="even">
<td align="left">reuseAddress</td>
<td align="left">Enable/disable the SO_REUSEADDR socket option. When a TCP connection is closed the connection may remain in a timeout state for a period of time after the connection is closed (typically known as the TIME_WAIT state or 2MSL wait state). For applications using a well known socket address or port it may not be possible to bind a socket to the required SocketAddress if there is a connection in the timeout state involving the socket address or port.</td>
</tr>
<tr class="odd">
<td align="left">temporarySelectorIO</td>
<td align="left">Allows the specification of a TemporarySelectorIO instance to aid in the simulation of blocking IO.</td>
</tr>
</tbody>
</table>

Thread Pool Configuration
-------------------------

Grizzly's thread pool configuration is managed by the ThreadPoolConfig
object:

<table>
<caption>ThreadPoolConfig Properties</caption>
<tbody>
<tr class="odd">
<td align="left">queue</td>
<td align="left">The task Queue implementation to be used.</td>
</tr>
<tr class="even">
<td align="left">queueLimit</td>
<td align="left">The maximum number of pending tasks that may be queued.</td>
</tr>
<tr class="odd">
<td align="left">threadFactory</td>
<td align="left">The ThreadFactory that the pool will use to create new Threads.</td>
</tr>
<tr class="even">
<td align="left">poolName</td>
<td align="left">The name of this thread pool.</td>
</tr>
<tr class="odd">
<td align="left">priority</td>
<td align="left">The priority to be assigned to each thread. This will override any priority assigned by the specified ThreadFactory.</td>
</tr>
<tr class="even">
<td align="left">corePoolSize</td>
<td align="left">The initial number of threads that will be present with the thread pool is created.</td>
</tr>
<tr class="odd">
<td align="left">maxPoolSize</td>
<td align="left">The maximum number threads that may be maintained by this thread pool.</td>
</tr>
<tr class="even">
<td align="left">keepAliveTime</td>
<td align="left">The maximum time a thread may stay idle and wait for a new task to execute before it will be released. Custom time units can be used.</td>
</tr>
<tr class="odd">
<td align="left">transactionTimeout</td>
<td align="left">The maximum time a thread may be allowed to run a single task before interrupt signal will be sent. Custom time units can be used.</td>
</tr>
</tbody>
</table>

The thread pool configuration is fairly straight forward. However, it
should be noted that Grizzly, internally, has several thread pool
implementations: SyncThreadPool, FixedThreadPool, and
QueueLimitedThreadPool. Which implementation is chosen is based on the
configuration. The following sections describe each of the thread pool
implementations.

### FixedThreadPool

This pool will be selected when the queueLimit property is less than
zero, and the max and core pool sizes are the same. The FixedThreadPool
has no synchronization when executing tasks, so it offers better
performance.

### QueueLimitedThreadPool

This pool will be selected when the queueLimit property is greater than
zero, and the max and core pool sizes are the same. The
QueueLimitedThreadPool is an extension of the FixedThreadPool, so if
offers the same benefits of the FixedThreadPool without having an
unbounded task queue.

### SyncThreadPool

This pool will be selected when none of the criteria for the other
thread pools apply. This thread pool does have synchronization to have
precise control over the decision of thread creation.

Examples
--------

Here are some examples of using the TCPNIOTransportBuilder to configure
the Transport and/or thread pool.

```java
final TCPNIOTransportBuilder builder = TCPNIOTranportBuilder.newInstance();
final TCPNIOTransport transport = builder.build();
```

Creates a new TCPNIOTransport using all default configuration values.

```java
final TCPNIOTransportBuilder builder = TCPNIOTransportBuilder.newInstance();
final TCPNIOTransport transport = builder.setIOStrategy(SameThreadIOStrategy.getInstance()).setTcpNoDelay(true).build();
```

Creates a new TCPNIOTransport instance using the SameThreadIOStrategy,
and tcp-no-delay set to true. Note that configuration calls can be
chained.

```java
final TCPNIOTransportBuilder builder = TCPNIOTransportBuilder.newInstance();
final ThreadPoolConfig config = builder.getWorkerThreadPoolConfig();
config.setCorePoolSize(5).setMaxPoolSize(5).setQueueLimit(-1);
final TCPNIOTransport transport = builder.build();
```

This example will configure the TCPNIOTransport to use the
FixedThreadPool implementation due to the fact that there is no queue
limit and the core and max pool sizes are the same.
