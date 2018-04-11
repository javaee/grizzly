## Core Framework Samples

## Parsing an incoming message

When writing network application, very often we need to parse incoming
bytes into application specific message.

The problem is that chunk of data been read from NIO Connection may
contain just a part of application message, or contain several of them
at once. So how parsing Filter should work out these scenarios?

-   If we don't have enough data to build an application message - we
    stop the FilterChain processing and store the incomplete buffer by
    returning

```java
return filterChainContext.getStopAction(buffer);
```

-   If the source Buffer contains more than one message at once - we're
    splitting up the source Buffer into two chunks:

    1.  The Buffer, which belongs to the first complete application
        message;

    2.  The remainder.

    The 1st chunk could be parsed immediately and correspondent
    application message created. After completing the parsing we may
    want to instruct FilterChain to continue execution, by calling the
    next Filter in the chain, and store the remainder to be processed
    later (once processing of this message is complete).

```java
return filterChainContext.getInvokeAction(remainder);
```

Here is an example of parsing Filter, parses the incoming Buffer and
creates a GIOPMessage, which has following structure

```java
public class GIOPMessage {
    private byte G;
    private byte I;
    private byte O;
    private byte P;

    private byte major;
    private byte minor;

    private byte flags;
    private byte value;

    private int bodyLength;

    private byte[] body;
}
```

So the actual parser Filter code is

```java
private static final int HEADER_SIZE = 12;

/**
 * Method is called, when new data was read from the Connection and ready
 * to be processed.
 *
 * We override this method to perform Buffer -> GIOPMessage transformation.
 *
 * @param ctx Context of {@link FilterChainContext} processing
 * @return the next action
 * @throws java.io.IOException
 */
@Override
public NextAction handleRead(final FilterChainContext ctx) throws IOException {
    // Get the source buffer from the context
    final Buffer sourceBuffer = ctx.getMessage();

    final int sourceBufferLength = sourceBuffer.remaining();

    // If source buffer doesn't contain header
    if (sourceBufferLength < HEADER_SIZE) {
        // stop the filterchain processing and store sourceBuffer to be
        // used next time

    }

    // Get the body length
    final int bodyLength = sourceBuffer.getInt(HEADER_SIZE - 4);
    // The complete message length
    final int completeMessageLength = HEADER_SIZE + bodyLength;

    // If the source message doesn't contain entire body
    if (sourceBufferLength < completeMessageLength) {
        // stop the filterchain processing and store sourceBuffer to be
        // used next time

    }

    // Check if the source buffer has more than 1 complete GIOP message
    // If yes - split up the first message and the remainder
    final Buffer remainder = sourceBufferLength > completeMessageLength ?
        sourceBuffer.split(completeMessageLength) : null;

    // Construct a GIOP message
    final GIOPMessage giopMessage = new GIOPMessage();

    // Set GIOP header bytes
    giopMessage.setGIOPHeader(sourceBuffer.get(), sourceBuffer.get(),
            sourceBuffer.get(), sourceBuffer.get());

    // Set major version
    giopMessage.setMajor(sourceBuffer.get());

    // Set minor version
    giopMessage.setMinor(sourceBuffer.get());

    // Set flags
    giopMessage.setFlags(sourceBuffer.get());

    // Set value
    giopMessage.setValue(sourceBuffer.get());

    // Set body length
    giopMessage.setBodyLength(sourceBuffer.getInt());

    // Read body
    final byte[] body = new byte[bodyLength];
    sourceBuffer.get(body);
    // Set body
    giopMessage.setBody(body);

    ctx.setMessage(giopMessage);

    // We can try to dispose the buffer
    sourceBuffer.tryDispose();

    // Instruct FilterChain to store the remainder (if any) and continue execution

}
```

## Asynchronous FilterChain execution

During the FilterChain execution, we might want to continue IOEvent
processing in the different/custom thread.

For example, we've chosen the SameThreadStrategy for our usecase to
avoid redundant thread context switches and it fits great to our
usecase, but at the same time we still have some scenarios, which
either execute long lasting tasks, or use blocking I/O (for ex. database
access), and to not make entire server unresponsive, we'd want to
execute such a task in our custom thread pool and let Grizzly service
other Connections.

This might be easily implemented with Grizzly using code like (not
working sample):

```java
public NextAction handleRead(final FilterChainContext ctx) throws IOException {

        // Get the SuspendAction in advance, cause once we execute LongLastTask in the
        // custom thread - we lose control over the context
        final NextAction suspendAction = ctx.getSuspendAction();

        // suspend the current execution
        ctx.suspend();

        // schedule async work
        scheduler.schedule(new Runnable() {

            @Override
            public void run() {
                doLongLastingTask();

                // Resume the FilterChain IOEvent processing
                ctx.resumeNext();
            }
        }, 5, TimeUnit.SECONDS);

        // return suspend status
        return suspendAction;
}
```

during processing IOEvent. We have to understand that by instructing
Grizzly to suspend the IOEvent execution, we're becoming responsible for
resuming the execution at one point of time (when long lasting task or
blocking I/O operation completes).

```java
public void run() {
    doLongLastingTask();

    // Resume the FilterChain IOEvent processing
    ctx.resumeNext();
}
```

## Other samples

The core framework samples can be reviewed in one of two ways:

-   Directly from the git repository:

```
git clone https://github.com/eclipse-ee4j/grizzly.git
cd grizzly
git checkout intial-contribution
cd samples/framework-samples
```

-   Download the sample source bundle from:
    <https://maven.java.net/content/repositories/releases/org/glassfish/grizzly/samples/grizzly-framework-samples/2.4.0/grizzly-framework-samples-2.4.0-sources.jar>


