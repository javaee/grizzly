I/O Strategies
==============

When working with NIO, the natural question we ask is how we're going to
process a particullar NIO event, which occurred on an NIO channel.
Usually we have two options: process the NIO event in the current
(Selector) thread or pass it to the worker thread for processing.

1.  **Worker-thread IOStrategy**.

    The most useful IOStrategy, where Selector thread delegates NIO
    events processing to a worker threads.

    ![""](images/coreframework/workerthread-strategy.gif)

    This IOStrategy is very scalable and safe. We can change the size of
    selector and worker thread pool as required and there is no risk
    that some problem, which may occur during the specific NIO event
    processing, will impact other Channels registered on the same
    Selector.

2.  **Same-thread IOStrategy**.

    Potentially the most efficient IOStrategy. Unlike the worker-thread
    IOStrategy, the same-thread IOStrategy processes NIO events in the
    current thread, avoiding expensive [1] thread context switches.

    ![""](images/coreframework/samethread-strategy.gif)

    This IOStrategy is still pretty scalable, because we can tune the
    selector thread pool size, but it does have drawbacks. Care needs to
    be taken that channel NIO event processing won't block or execute
    any long lasting operation, because it may block the processing of
    other NIO events that occur on the same Selector.

3.  **Dynamic IOStrategy**.

    As mentioned previously worker-thread and same-thread strategies
    have distinct advantages and disadvantages. However, what if a
    strategy could try to swap them smartly during runtime depending on
    the current conditions (load, gathered statistics... etc)?

    ![""](images/coreframework/dynamic-strategy.gif)

    Potentially this IOStrategy could bring a lot of benefit and allow
    finer control of the resources. However, it's important to not
    overload the condition evaluation logic, as its complexity will make
    this IOStrategy inefficient comparing to previous two strategies.

4.  **Leader-follower IOStrategy**.

    The last IOStrategy included with Grizzly 2.3, is the
    leader-follower strategy:

    ![""](images/coreframework/leaderfollower-strategy.gif)

    This IOStrategy is similar to worker-thread IOStrategy, but instead
    of passing NIO event processing to a worker thread, it changes
    worker thread to a selector thread by passing it the control over
    Selector and the actual NIO event processing takes place in the
    current thread.

Grizzly 2.3 provides general interface
*org.glassfish.grizzly.IOStrategy:*

```java
public interface IOStrategy extends WorkerThreadPoolConfigProducer {

    /**
     * The {@link org.glassfish.grizzly.nio.SelectorRunner} will invoke this
     * method to allow the strategy implementation to decide how the
     * {@link IOEvent} will be handled.
     *
     * @param connection the {@link Connection} upon which the provided
     *  {@link IOEvent} occurred.
     * @param ioEvent the {@link IOEvent} that triggered execution of this
     *  <code>strategy</code>
     *
     * @return <tt>true</tt>, if this thread should keep processing IOEvents on
     * the current and other Connections, or <tt>false</tt> if this thread
     * should hand-off the farther IOEvent processing on any Connections,
     * which means IOStrategy is becoming responsible for continuing IOEvent
     * processing (possibly starting new thread, which will handle IOEvents).
     *
     * @throws IOException if an error occurs processing the {@link IOEvent}.
     */
    boolean executeIoEvent(final Connection connection, final IOEvent ioEvent)
    throws IOException;

    /**
     * The {@link org.glassfish.grizzly.nio.SelectorRunner} will invoke this
     * method to allow the strategy implementation to decide how the
     * {@link IOEvent} will be handled.
     *
     * @param connection the {@link Connection} upon which the provided
     *  {@link IOEvent} occurred.
     * @param ioEvent the {@link IOEvent} that triggered execution of this
     *  <code>strategy</code>
     * @param isIoEventEnabled <tt>true</tt> if IOEvent is still enabled on the
     *  {@link Connection}, or <tt>false</tt> if IOEvent was preliminary disabled
     *  or IOEvent is being simulated.
     *
     * @return <tt>true</tt>, if this thread should keep processing IOEvents on
     * the current and other Connections, or <tt>false</tt> if this thread
     * should hand-off the farther IOEvent processing on any Connections,
     * which means IOStrategy is becoming responsible for continuing IOEvent
     * processing (possibly starting new thread, which will handle IOEvents).
     *
     * @throws IOException if an error occurs processing the {@link IOEvent}.
     */
    boolean executeIoEvent(final Connection connection, final IOEvent ioEvent,
            final boolean isIoEventEnabled) throws IOException;

}
```

And the IOStrategy implementation may decide what to do with the
specific NIO event processing.

Grizzly 2.3 has four predefined IOStrategy
implementations, as per list above:

1.  *org.glassfish.grizzly.strategies.WorkerThreadIOStrategy*

2.  *org.glassfish.grizzly.strategies.SameThreadIOStrategy*

3.  *org.glassfish.grizzly.strategies.SimpleDynamicThreadStrategy*

4.  *org.glassfish.grizzly.strategies.LeaderFollowerIOStrategy*

The strategies are assigned per Transport, so it's possible to get/set
the IOStrategy using Transport's *get/setIOStrategy* methods. By default
TCP and UDP transports use the worker-thread IOStrategy.

[1] some OSes do a great job optimizing the thread context switches, but
we still suppose it as relatively expensive operation.
