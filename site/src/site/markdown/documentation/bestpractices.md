Best Practices
==============

When developing a network application, we usually wonder how we can
optimize it. How should the worker thread pool be sized? Which I/O
strategy to employ?

There is no general answer for that question, but we'll try to provide
some tips.

-   **IOStrategy**

    In the [IOStrategy](#iostrategies) section, we introduced different
    Grizzly IOStrategies.

    By default, Grizzly Transports use the worker-thread IOStrategy,
    which is reliable for any possible usecase. However, if the
    application processing logic doesn't involve any blocking I/O
    operations, the same-thread IOStrategy can be used. For these cases,
    the same-thread strategy will be more performant as there are no
    thread context switches.

    For example, if we implement general HTTP Servlet container, we
    can't be sure about nature of specific Servlets developers may have.
    In this case it's safter to use the worker-thread IOStrategy.
    However, if application uses the Grizzly's HttpServer and
    HttpHandler, which leverages NIO streams, then the same-thread
    strategy could be used to optimize processing time and resource
    consumption;

-   **Selector runners count**

    The Grizzly runtime will automatically set the SelectorRunner count
    value equal to
    [Runtime.getRuntime().availableProcessors()](http://download.oracle.com/javase/6/docs/api/java/lang/Runtime.html#availableProcessors()).
    Depending on the usecase, developers may change this value to better
    suit their needs.

    Scott Oaks, from the Glassfish performance team,
    [suggests](http://weblogs.java.net/blog/2007/12/03/glassfish-tuning-primer)
    that there should be one SelectorRunner for every 1-4 cores on your
    machine; no more than that;

-   **Worker thread pool**

    In the [Configuration](#threadpool-config) section, the different
    thread pool implementations, and their pros and cons, were
    discussed.

    All IOStrategies, except the same-thread IOStrategy, use worker
    threads to process IOEvents which occur on Connections. A common
    question is how many worker threads will be needed by an
    application?

    In his
    [blog](http://weblogs.java.net/blog/2007/12/03/glassfish-tuning-primer),
    Scott suggests How many is "just enough"? It depends, of course --
    in a case where HTTP requests don't use any external resource and
    are hence CPU bound, you want only as many HTTP request processing
    threads as you have CPUs on the machine. But if the HTTP request
    makes a database call (even indirectly, like by using a JPA entity),
    the request will block while waiting for the database, and you could
    profitably run another thread. So this takes some trial and error,
    but start with the same number of threads as you have CPU and
    increase them until you no longer see an improvement in throughput.

    Translating this to the general, non HTTP usecase: If IOEvent
    processing includes blocking I/O operation(s), which will make
    thread block doing nothing for some time (i.e, waiting for a result
    from a peer), it's best to have more worker threads to not starve
    other request processing. For simpler application processes, the
    fewer threads, the better.


