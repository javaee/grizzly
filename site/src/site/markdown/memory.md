Memory Management
=================

Overview
--------

Grizzly 2.0 introduces a new subsystem to improve memory management
within the runtime. This subsystem is comprised of three main artifacts:

-   Buffers

-   Thread local memory pools

-   MemoryManager as a factory of sorts using the buffers and thread
    local pools

whose primary purpose is to speed up memory allocation and, when
possible, provide for memory re-use.

The following sections will describe these concepts in detail.

MemoryManager
-------------

The *MemoryManager* is the main interface for allocating/deallocating
*Buffer* instances:

```java
public interface MemoryManager<E extends Buffer>
        extends JmxMonitoringAware<MemoryProbe> {

    /**
     * Allocated {@link Buffer} of the required size.
     *
     * @param size {@link Buffer} size to be allocated.
     * @return allocated {@link Buffer}.
     */
    public E allocate(int size);

    /**
     * Allocated {@link Buffer} at least of the provided size.
     * This could be useful for usecases like Socket.read(...), where
     * we're not sure how many bytes are available, but want to read as
     * much as possible.
     *
     * @param size the min {@link Buffer} size to be allocated.
     * @return allocated {@link Buffer}.
     */
    public E allocateAtLeast(int size);

    /**
     * Reallocate {@link Buffer} to a required size.
     * Implementation may choose the way, how reallocation could be done, either
     * by allocating new {@link Buffer} of required size and copying old
     * {@link Buffer} content there, or perform more complex logic related to
     * memory pooling etc.
     *
     * @param oldBuffer old {@link Buffer} to be reallocated.
     * @param newSize new {@link Buffer} required size.
     * @return reallocated {@link Buffer}.
     */
    public E reallocate(E oldBuffer, int newSize);

    /**
     * Release {@link Buffer}.
     * Implementation may ignore releasing and let JVM Garbage collector to take
     * care about the {@link Buffer}, or return {@link Buffer} to pool, in case
     * of more complex <tt>MemoryManager</tt> implementation.
     *
     * @param buffer {@link Buffer} to be released.
     */
    public void release(E buffer);

    /**
     * Return <tt>true</tt> if next {@link #allocate(int)} or {@link #allocateAtLeast(int)} call,
     * made in the current thread for the given memory size, going to return a {@link Buffer} based
     * on direct {@link java.nio.ByteBuffer}, or <tt>false</tt> otherwise.
     *
     * @param size
     * @return
     */
    public boolean willAllocateDirect(int size);
}
```

There is typically a single *MemoryManager* servicing all transports
defined within the Grizzly runtime. This *MemoryManager* can be obtained
by referencing the static member of the MemoryManager interface:

```java
MemoryManager.DEFAULT_MEMORY_MANAGER
```

Conversely, custom *MemoryManager* implementations may be made as the
default MemoryManager by defining the system property
*org.glassfish.grizzly.DEFAULT\_MEMORY\_MANAGER* that references the
fully qualified class name of the MemoryManager implementation to use.
Note that this implementation must have a public no-arg constructor in
order for the runtime to properly set the new default.

Grizzly 2.3 includes two *MemoryManager* implementations:
*HeapMemoryManager* and *ByteBufferManager*. By default, the Grizzly
runtime will use the *HeapMemoryManager*, however if a Grizzly
application requires direct ByteBuffer access, then the
*ByteBufferManager* can be used.

### ByteBufferManager

The *ByteBufferManager* implementation vends Grizzly Buffer instances
that wrap JDK ByteBuffer instances. This is the MemoryManager to use if
the Grizzly application requires direct ByteBuffer usage.

It should be noted that this MemoryManager, during our benchmarking,
showed to have a little more overhead when using typical heap buffers.
As such, if direct memory isn't needed, we recommend that the default
HeapMemoryManager be used.

### HeapMemoryManager

The HeapMemoryManager is the default MemoryManager. Instead of wrapping
ByteBuffer instances, this MemoryManager will allocate Buffer instances
that wrap byte arrays directly. This MemoryManager offers better
performance characteristics for operations such as trimming or
splitting.

### ThreadLocal Memory Pools

ThreadLocal memory pools provide the ability to allocate memory without
any synchronization costs. Both the *ByteBufferManager* and
*HeapMemoryManager* use such pools. Note that it's not required that a
custom *MemoryManager* use such pools, however, if said *MemoryManager*
implements the *ThreadLocalPoolProvider* interface, then a
*ThreadLocalPool* implementation must be provided. The *ThreadLocalPool*
implementation will be created and passed to each thread being
maintained by Grizzly's managed threads.

### Memory Manager and ThreadLocal Memory Pools Working Together

The following provides a flow diagram of how an allocation request to a
*MemoryManager* with a *ThreadLocalPool* would typically work:

![MemoryManager Allocation Request
Flow](images/coreframework/mmflow.png)

Buffers
-------

Grizzly 2.3 provides several buffers for developers to
leverage when creating applications. These *Buffer* implementations
offer features not available when using the JDK's ByteBuffer.

### Buffer

The *Buffer* is essentially the analogue to the JDK's ByteBuffer. It
offers the same set of methods for:

-   Pushing/pulling data to/from the *Buffer*.

-   Methods for accessing or manipulating the *Buffer's* position,
    limit, and capacity.

In addition to offering familar semantics to ByteBuffer, the following
features are available:

-   Splitting, trimming, and shrinking.

-   Prepending another *Buffer's* content to the current Buffer.

-   Converting the *Buffer* to a ByteBuffer or ByteBuffer[].

-   Converting *Buffer* content to a String.

Please see the javadocs for further details on *Buffer*.

### CompositeBuffer

The *CompositeBuffer* is another *Buffer* implementation which allows
appending of *Buffer* instances. The CompositeBuffer maintains a virtual
position, limit, and capacity based on the *Buffers* that have been
appended and can be treated as a simple *Buffer* instance.

Please see the javadocs for further details on *CompositeBuffer*.
