## Quick Start

This chapter will present how to get started with Grizzly
2.3, both client and server side.

First, it is necessary to depend on the correct Grizzly
2.3
[core](https://maven.java.net/content/repositories/releases/org/glassfish/grizzly/grizzly-framework/)
artifact. Maven developers will need to add following dependency to the
pom:

```xml
<dependency>
    <groupId>org.glassfish.grizzly</groupId>
    <artifactId>grizzly-framework</artifactId>
    <version>2.3.31</version>
</dependency>
```

All Grizzly maven artifacts are deployed on the [Maven
Central](http://repo1.maven.org/) repository.

Let's implement simple Echo client-server application. The client will
get user's data from standard input, send data to Echo server and
redirect server's response to the standard output. The responsibility of
the Echo server is to read data from network channel and echo the same
data back to the channel.

## Server

### Create echo filter

First of all let's implement echo filter, which will echo the received
message (despite its type) back to the Grizzly Connection.

```java
import java.io.IOException;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;

/**
 * Implementation of {@link FilterChain} filter, which replies with the request
 * message.
 */
public class EchoFilter extends BaseFilter {

    /**
     * Handle just read operation, when some message has come and ready to be
     * processed.
     *
     * @param ctx Context of {@link FilterChainContext} processing
     * @return the next action
     * @throws java.io.IOException
     */
    @Override
    public NextAction handleRead(FilterChainContext ctx)
            throws IOException {
        // Peer address is used for non-connected UDP Connection :)
        final Object peerAddress = ctx.getAddress();

        final Object message = ctx.getMessage();

        ctx.write(peerAddress, message, null);

        return ctx.getStopAction();
    }
}
```

### Server initialization code

All the server FilterChain bricks are ready - let's initialize and start
the server.

```java
import java.io.IOException;
import java.nio.charset.Charset;
import java.util.logging.Logger;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.StringFilter;

/**
 * Class initializes and starts the echo server, based on Grizzly 2.3
 */
public class EchoServer {
    private static final Logger logger = Logger.getLogger(EchoServer.class.getName());

    public static final String HOST = "localhost";
    public static final int PORT = 7777;

    public static void main(String[] args) throws IOException {
        // Create a FilterChain using FilterChainBuilder
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();

        // Add TransportFilter, which is responsible
        // for reading and writing data to the connection
        filterChainBuilder.add(new TransportFilter());

        // StringFilter is responsible for Buffer <-> String conversion
        filterChainBuilder.add(new StringFilter(Charset.forName("UTF-8")));

        // EchoFilter is responsible for echoing received messages
        filterChainBuilder.add(new EchoFilter());

        // Create TCP transport
        final TCPNIOTransport transport =
                TCPNIOTransportBuilder.newInstance().build();

        transport.setProcessor(filterChainBuilder.build());
        try {
            // binding transport to start listen on certain host and port
            transport.bind(HOST, PORT);

            // start the transport
            transport.start();

            logger.info("Press any key to stop the server...");
            System.in.read();
        } finally {
            logger.info("Stopping transport...");
            // stop the transport
            transport.shutdownNow();

            logger.info("Stopped transport...");
        }
    }
}
```

### Running echo server

As we see in the code above, EchoServer class declares main method, so
it could be easily run from command line like:

```
java -classpath grizzly-framework.jar EchoServer
```

or using your favorite IDE.

## Client

### Create client filter

Client filter is responsible for redirecting server response to the
standard output. Please note, the ClientFilter requires
FilterChainContext messages to be java.lang.String (line 21), so it
relies StringFilter is preceding it in the FilterChain.

```java
import java.io.IOException;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;

/**
 * Client filter is responsible for redirecting server response to the standard output
 */
public class ClientFilter extends BaseFilter {
    /**
     * Handle just read operation, when some message has come and ready to be
     * processed.
     *
     * @param ctx Context of {@link FilterChainContext} processing
     * @return the next action
     * @throws java.io.IOException
     */
    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        // We get String message from the context, because we rely prev. Filter in chain is StringFilter
        final String serverResponse = ctx.getMessage();
        System.out.println("Server echo: " + serverResponse);

        return ctx.getStopAction();
    }
}
```

### Client initialization code

Now we're ready to initialize the client, which includes FilterChain and
Transport initialization

```java
Connection connection = null;

// Create a FilterChain using FilterChainBuilder
FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();

// Add TransportFilter, which is responsible
// for reading and writing data to the connection
filterChainBuilder.add(new TransportFilter());

// StringFilter is responsible for Buffer <-> String conversion
filterChainBuilder.add(new StringFilter(Charset.forName("UTF-8")));

// ClientFilter is responsible for redirecting server responses to the standard output
filterChainBuilder.add(new ClientFilter());

// Create TCP transport
final TCPNIOTransport transport =
        TCPNIOTransportBuilder.newInstance().build();
transport.setProcessor(filterChainBuilder.build());
```

### Adding user interaction and client shutdown code

Let's complete the code above by adding the logic, which reads user data
from the standard input, sends it to the server and client shutdown
being executed on end of input.

```java
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.util.logging.Logger;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.nio.transport.TCPNIOTransportBuilder;
import org.glassfish.grizzly.utils.StringFilter;

/**
 * The simple client, which sends a message to the echo server
 * and waits for response
 */
public class EchoClient {
    private static final Logger logger = Grizzly.logger(EchoClient.class);

    public static void main(String[] args) throws IOException,
            ExecutionException, InterruptedException, TimeoutException {

        Connection connection = null;

        // Create a FilterChain using FilterChainBuilder
        FilterChainBuilder filterChainBuilder = FilterChainBuilder.stateless();
        // Add TransportFilter, which is responsible
        // for reading and writing data to the connection
        filterChainBuilder.add(new TransportFilter());
        // StringFilter is responsible for Buffer <-> String conversion
        filterChainBuilder.add(new StringFilter(Charset.forName("UTF-8")));
        // ClientFilter is responsible for redirecting server responses to the standard output
        filterChainBuilder.add(new ClientFilter());

        // Create TCP transport
        final TCPNIOTransport transport =
                TCPNIOTransportBuilder.newInstance().build();
        transport.setProcessor(filterChainBuilder.build());

        try {
            // start the transport
            transport.start();

            // perform async. connect to the server
            Future<Connection> future = transport.connect(EchoServer.HOST,
                    EchoServer.PORT);
            // wait for connect operation to complete
            connection = future.get(10, TimeUnit.SECONDS);

            assert connection != null;

            System.out.println("Ready... (\"q\" to exit)");
            final BufferedReader inReader = new BufferedReader(new InputStreamReader(System.in));
            do {
                final String userInput = inReader.readLine();
                if (userInput == null || "q".equals(userInput)) {
                    break;
                }

                connection.write(userInput);
            } while (true);
        } finally {
            // close the client connection
            if (connection != null) {
                connection.close();
            }

            // stop the transport
            transport.shutdownNow();
        }
    }
}
```

### Running echo client

EchoClient could be run easily using command line like

```no-highlight
java -classpath grizzly-framework.jar EchoClient
```

or your favorite IDE.

By default, if standard input and output were not changed - you'll see
the following on the console:

```no-highlight
Ready... ("q" to exit)
```

Now the client is ready for your input. Each time you typed a line and
pressed \<ENTER\> - the line will be sent to the server and response
got:

```no-highlight
Ready... ("q" to exit)
Hey there!
Server echo: Hey there!
```
