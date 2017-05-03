## Http Framework Overview

The Grizzly 2.3 HTTP framework provides codecs for
marshalling/unmarshalling HTTP request/response data for both the server
and clients. In addition to the codecs themselves, the framework
includes base HTTP primitives and utilities to make working with the
protocol simpler.

## Components of the Framework

There are many components that provide the HTTP protocol handling within
Grizzly. This section will cover the major components.

### HttpCodecFilter (Server/Client)

The HttpCodecFilter is the base Filter that provides the ability to
marshall/unmarshall HTTP primitives to and from the network. Note that
this class does a majority of the heavy lifting when dealing with the
HTTP protocol. However, Grizzly does have two subclasses of
HttpCodecFilter to deal with different semantics depending on which side
of the connection being dealt with.

### HTTP Messages

In order to make working with HTTP easier for the developer, Grizzly
abstracts the network representation of an http protocol
request/response into various different message objects.

![HTTP Message Constructions UML
Diagram](images/httpframework/http-packet-classes.png)

As seen from the diagram all messages produced/consumed by the
HttpCodecFilter are simply HttpPackets. From there, there are several
specialized packet representations:

<table>
<caption>HttpPacket Implementations</caption>
<tbody>
<tr class="odd">
<td align="left">HttpHeader</td>
<td align="left">HttpHeaders represents the http message headers.</td>
</tr>
<tr class="even">
<td align="left">HttpContent</td>
<td align="left">Represents the entity body of a http message.</td>
</tr>
<tr class="odd">
<td align="left">HttpTrailer</td>
<td align="left">This is a specialized form of HttpContent. It's only used when the chunked transfer encoding is used and represents the trailer chunk.</td>
</tr>
<tr class="even">
<td align="left">HttpRequestPacket</td>
<td align="left">A form of HttpHeader representing a HTTP request.</td>
</tr>
<tr class="odd">
<td align="left">HttpResponsePacket</td>
<td align="left">A form of HttpHeader representing a HTTP response to a HTTP request.</td>
</tr>
</tbody>
</table>

HttpRequestPacket, HttpResponsePacket, HttpContent, and HttpTrailer (if
using the chunked transfer encoding) all have Builder objects in order
to create new instances. For example, in order to create a response for
a particular request:

```java
final HttpResponsePacket.Builder builder = response.builder(request);
final HttpResponsePacket response = builder.chunked(true).status(200).reasonPhrase("OK").build();
```

Building off the example above, a message body can be added to the
response that was just created:

```java
final HttpContent.Builder contentBuilder = response.httpContentBuilder();
final HttpContent = contentBuilder.setContent(
                         Buffers.wrap(null, "<html><head></head><body>Hello!</body></html>")).setLast(true).build();
```

Finally, since the response body is chunked, a HttpTrailer needs to be
included:

```java
final HttpTrailer.Builder trailerBuilder = response.httpTrailerBuilder();
final HttpTrailer trailer = trailerBuilder.build();
```

Each of the created HttpPacket entities from above can then be written to
client:

```java
// assume we have access to the FilterChainContext via a variable called 'ctx'...
ctx.write(response);
ctx.write(content);
ctx.write(trailer);
```

The examples above are there purely to demonstrate the different builder
objects. However, the example above could have the step with the
HttpContent.Builder eliminated completely since HttpTrailer is a
specialized form of HttpContent:

```java
final HttpResponsePacket.Builder builder = response.builder(request);
final HttpResponsePacket response = builder.chunked(true).status(200).reasonPhrase("OK").build();
final HttpTrailer.Builder trailerBuilder = response.httpTrailerBuilder();
trailerBuilder.setContent(null, "<html><head></head><body>Hello!</body></html>").setLast(true).build();
ctx.write(response);
ctx.write(trailer);
```

### Transfer Encodings

Transfer encodings allow special transformation formats for
communicating http messages between client and server. Grizzly includes
two transfer encoding implementations:

<table>
<caption>Supported Transfer-Encodings</caption>
<tbody>
<tr class="odd">
<td align="left">Fixed-Length</td>
<td align="left">Implemented by org.glassfish.grizzly.http.FixedLengthTransferEncoding. This transfer-encoing will be used when content-length has been specified, or if chunking has been disabled. In the case where chunking is disabled and no content length has been specified, the implementation will try to determine the content length on its own.</td>
</tr>
<tr class="even">
<td align="left">Chunked</td>
<td align="left">Implemented by org.glassfish.grizzly.ChunkedTransferEncoding. This transfer-encoding will be used when specified on a request or response. For more details on the chunked transfer-encoding, see: <a href="http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6.1">http://www.w3.org/Protocols/rfc2616/rfc2616-sec3.html#sec3.6.1</a>.</td>
</tr>
</tbody>
</table>

Custom transfer-encoding implementations may be provided by implementing
the TransferEncoding interface:

```java
public interface TransferEncoding {
    /**
     * Return <tt>true</tt> if this encoding should be used to parse the
     * content of the passed {@link HttpHeader}, or <tt>false</tt> otherwise.
     *
     * @param httpPacket {@link HttpHeader}.
     * @return <tt>true</tt> if this encoding should be used to parse the
     * content of the passed {@link HttpHeader}, or <tt>false</tt> otherwise.
     */
    public boolean wantDecode(HttpHeader httpPacket);

    /**
     * Return <tt>true</tt> if this encoding should be used to serialize the
     * content of the passed {@link HttpHeader}, or <tt>false</tt> otherwise.
     *
     * @param httpPacket {@link HttpHeader}.
     * @return <tt>true</tt> if this encoding should be used to serialize the
     * content of the passed {@link HttpHeader}, or <tt>false</tt> otherwise.
     */
    public boolean wantEncode(HttpHeader httpPacket);

    /**
     * This method will be called by {@link HttpCodecFilter} to let
     * <tt>TransferEncoding</tt> prepare itself for the content serialization.
     * At this time <tt>TransferEncoding</tt> is able to change, update HTTP
     * packet headers.
     *
     * @param ctx {@link FilterChainContext}
     * @param httpHeader HTTP packet headers.
     * @param content ready HTTP content (might be null).
     */
    public void prepareSerialize(FilterChainContext ctx,
                                 HttpHeader httpHeader,
                                 HttpContent content);

    /**
     * Parse HTTP packet payload, represented by {@link Buffer} using specific
     * transfer encoding.
     *
     * @param ctx {@link FilterChainContext}
     * @param httpPacket {@link HttpHeader} with parsed headers.
     * @param buffer {@link Buffer} HTTP message payload.
     * @return {@link ParsingResult}
     */
    public ParsingResult parsePacket(FilterChainContext ctx,
            HttpHeader httpPacket, Buffer buffer);

    /**
     * Serialize HTTP packet payload, represented by {@link HttpContent}
     * using specific transfer encoding.
     *
     * @param ctx {@link FilterChainContext}
     * @param httpContent {@link HttpContent} with parsed {@link HttpContent#getHttpHeader()}.
     *
     * @return serialized {@link Buffer}
     */
    public Buffer serializePacket(FilterChainContext ctx,
            HttpContent httpContent);
}
```

Custom TransferEncoding implementations may be registered by calling
HttpCodecFilter.addTransferEncoding().

### Content Encodings

Content encodings describe when encodings have been applied to the
message body prior to their being transferred to the recipient. At the
time that this was written, Grizzly 2.3 only supports gzip
and lzma content encodings (implemented by
org.glassfish.grizzly.http.GzipContentEncoding and
org.glassfish.grizzly.http.LzmaContentEncoding).

Custom content-encodings can be provided by implementing the
ContentEncoding interface:

```java
/**
 * Abstraction, which represents HTTP content-encoding.
 * Implementation should take care of HTTP content encoding and decoding.
 *
 * @see GZipContentEncoding
 */
public interface ContentEncoding {

    /**
     * Get the <tt>ContentEncoding</tt> name.
     *
     * @return the <tt>ContentEncoding</tt> name.
     */
    String getName();

    /**
     * Get the <tt>ContentEncoding</tt> aliases.
     *
     * @return the <tt>ContentEncoding</tt> aliases.
     */
    String[] getAliases();

    /**
     * Method should implement the logic, which decides if HTTP packet with
     * the specific {@link HttpHeader} should be decoded using this <tt>ContentEncoding</tt>.
     *
     * @param header HTTP packet header.
     * @return <tt>true</tt>, if this <tt>ContentEncoding</tt> should be used to
     * decode the HTTP packet, or <tt>false</tt> otherwise.
     */
    boolean wantDecode(HttpHeader header);

    /**
     * Method should implement the logic, which decides if HTTP packet with
     * the specific {@link HttpHeader} should be encoded using this <tt>ContentEncoding</tt>.
     *
     * @param header HTTP packet header.
     * @return <tt>true</tt>, if this <tt>ContentEncoding</tt> should be used to
     * encode the HTTP packet, or <tt>false</tt> otherwise.
     */
    boolean wantEncode(HttpHeader header);

    /**
     * Decode HTTP packet content represented by {@link HttpContent}.
     *
     * @param connection {@link Connection}.
     * @param httpContent {@link HttpContent} to decode.
     *
     * @return {@link ParsingResult}, which represents the result of decoding.
     */
    ParsingResult decode(Connection connection, HttpContent httpContent);

    /**
     * Encode HTTP packet content represented by {@link HttpContent}.
     *
     * @param connection {@link Connection}.
     * @param httpContent {@link HttpContent} to encode.
     *
     * @return encoded {@link HttpContent}.
     */
    HttpContent encode(Connection connection, HttpContent httpContent);
}
```

Custom ContentEncoding implementations may be registered by calling
HttpCodecFilter.addContentEncoding().

### HTTP Potpourri

<table>
<caption>Other HTTP Classes of Interest</caption>
<tbody>
<tr class="odd">
<td align="left">Cookie</td>
<td align="left">Represents a HTTP cookie. Supports version 0 and version 1 cookies.</td>
</tr>
<tr class="even">
<td align="left">Cookies</td>
<td align="left">A collection of Cookies.</td>
</tr>
<tr class="odd">
<td align="left">CookiesBuilder</td>
<td align="left">A Builder implementation for created a List of Cookies from the message headers of a HttpHeader packet. This can be used on either the server or client side.</td>
</tr>
<tr class="even">
<td align="left">LazyCookie</td>
<td align="left">A wrapper for Cookie that delays parsing the header value into the values exposed by Cookie until needed.</td>
</tr>
<tr class="odd">
<td align="left">ByteChunk</td>
<td align="left">Represents a portion of a byte array and exposes various operations to operate on said portion.</td>
</tr>
<tr class="even">
<td align="left">BufferChunk</td>
<td align="left">Represents a portion of a buffer and exposes various operations to operate on said portion.</td>
</tr>
<tr class="odd">
<td align="left">CharChunk</td>
<td align="left">A utility class for dealing with character arrays in an efficent manner.</td>
</tr>
<tr class="even">
<td align="left">DataChunk</td>
<td align="left">Represents a chunk of data that may be a ByteChunk, BufferChunk, CharChunk, or String.</td>
</tr>
</tbody>
</table>

## Samples

The following example demonstrates the flexibility of the http module by
using the module for both the client and the server. To begin, we'll
start with the the client:

```java
/**
 * Simple asynchronous HTTP client implementation, which downloads HTTP resource
 * and saves its content in a local file.
 */
public class Client {
    private static final Logger logger = Grizzly.logger(Client.class);

    public static void main(String[] args) throws IOException, URISyntaxException {
        // Check command line parameters
        if (args.length < 1) {
            System.out.println("To download the resource, please run: Client <url>");
            System.exit(0);
        }

        final String url = args[0];

        // Parse passed URL
        final URI uri = new URI(url);
        final String host = uri.getHost();
        final int port = uri.getPort() > 0 ? uri.getPort() : 80;

        final FutureImpl<String> completeFuture = SafeFutureImpl.create();

        // Build HTTP client filter chain
        FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
        // Add transport filter
        clientFilterChainBuilder.add(new TransportFilter());
        // Add HttpClientFilter, which transforms Buffer <-> HttpContent
        clientFilterChainBuilder.add(new HttpClientFilter());
        // Add HTTP client download filter, which is responsible for downloading
        // HTTP resource asynchronously
        clientFilterChainBuilder.add(new ClientDownloadFilter(uri, completeFuture));

        // Initialize Transport
        final TCPNIOTransport transport =
                TCPNIOTransportBuilder.newInstance().build();
        // Set filterchain as a Transport Processor
        transport.setProcessor(clientFilterChainBuilder.build());

        try {
            // start the transport
            transport.start();

            Connection connection = null;

            // Connecting to a remote Web server
            Future<Connection> connectFuture = transport.connect(host, port);
            try {
                // Wait until the client connect operation will be completed
                // Once connection will be established - downloading will
                // start @ ClientDownloadFilter.onConnect(...)
                connection = connectFuture.get(10, TimeUnit.SECONDS);
                // Wait until download will be completed
                String filename = completeFuture.get();
                logger.log(Level.INFO, "File " + filename + " was successfully downloaded");
            } catch (Exception e) {
                if (connection == null) {
                    logger.log(Level.WARNING, "Can not connect to the target resource");
                } else {
                    logger.log(Level.WARNING, "Error downloading the resource");
                }
            } finally {
                // Close the client connection
                if (connection != null) {
                    connection.close();
                }
            }
        } finally {
            logger.info("Stopping transport...");
            // stop the transport
            transport.shutdownNow();

            logger.info("Stopped transport...");
        }
    }
}
```

The documentation within the example above should be sufficient to get
an understanding of what is going on within the client. However, the
ClientDownloadFilter, added at line 37 warrants a look:

```java
/**
 * HTTP client download filter.
 * This Filter is responsible for asynchronous downloading of a HTTP resource and
 * saving its content in a local file.
 */
public class ClientDownloadFilter extends BaseFilter {
    private final static Logger logger = Grizzly.logger(ClientDownloadFilter.class);

    // URI of a remote resource
    private final URI uri;
    // local filename, where content will be saved
    private final String fileName;

    // Download completion future
    private FutureImpl<String> completeFuture;

    // local file channel, where we save resource content
    private volatile FileChannel output;
    // number of bytes downloaded
    private volatile int bytesDownloaded;

    private final String resourcePath;

    /**
     * <tt>ClientDownloadFilter</tt> constructor
     *
     * @param uri {@link URI} of a remote resource to download
     * @param completeFuture download completion handler ({@link FutureImpl})
     */
    public ClientDownloadFilter(URI uri, FutureImpl<String> completeFuture) {
        this.uri = uri;

        // Extracting resource path
        resourcePath =
                uri.getPath().trim().length() > 0 ? uri.getPath().trim() : "/";

        int lastSlashIdx = resourcePath.lastIndexOf('/');
        if (lastSlashIdx != -1 && lastSlashIdx < resourcePath.length() - 1) {
            // if the path contains a filename - take it as local filename
            fileName = resourcePath.substring(lastSlashIdx + 1);
        } else {
            // if the path doesn't contain filename - we will use default filename
            fileName = "download#" + System.currentTimeMillis() + ".txt";
        }

        this.completeFuture = completeFuture;
    }

    /**
     * The method is called, when a client connection gets connected to a web
     * server.
     * When this method gets called by a framework - it means that client connection
     * has been established and we can send HTTP request to the web server.
     *
     * @param ctx Client connect processing context
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    @Override
    public NextAction handleConnect(FilterChainContext ctx) throws IOException {
        // Build the HttpRequestPacket, which will be sent to a server
        // We construct HTTP request version 1.1 and specifying the URL of the
        // resource we want to download
        final HttpRequestPacket httpRequest = HttpRequestPacket.builder().method("GET")
                .uri(resourcePath).protocol(Protocol.HTTP_1_1)
                .header("Host", uri.getHost()).build();
        logger.log(Level.INFO, "Connected... Sending the request: {0}", httpRequest);

        // Write the request asynchronously
        ctx.write(httpRequest);

        // Return the stop action, which means we don't expect next filter to process
        // connect event
        return ctx.getStopAction();
    }

    /**
     * The method is called, when we receive a {@link HttpContent} from a server.
     * Once we receive one - we save the content chunk to a local file.
     *
     * @param ctx Request processing context
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        try {
            // Cast message to a HttpContent
            final HttpContent httpContent = ctx.getMessage();

            logger.log(Level.FINE, "Got HTTP response chunk");
            if (output == null) {
                // If local file wasn't created - create it
                logger.log(Level.INFO, "HTTP response: {0}", httpContent.getHttpHeader());
                logger.log(Level.FINE, "Create a file: {0}", fileName);
                FileOutputStream fos = new FileOutputStream(fileName);
                output = fos.getChannel();
            }

            // Get HttpContent's Buffer
            final Buffer buffer = httpContent.getContent();

            logger.log(Level.FINE, "HTTP content size: {0}", buffer.remaining());
            if (buffer.remaining() > 0) {
                bytesDownloaded += buffer.remaining();

                // save Buffer to a local file, represented by FileChannel
                ByteBuffer byteBuffer = buffer.toByteBuffer();
                do {
                    output.write(byteBuffer);
                } while (byteBuffer.hasRemaining());

                // Dispose a content buffer
                buffer.dispose();
            }

            if (httpContent.isLast()) {
                // it's last HttpContent - we close the local file and
                // notify about download completion
                logger.log(Level.FINE, "Downloaded done: {0} bytes", bytesDownloaded);
                completeFuture.result(fileName);
                close();
            }
        } catch (IOException e) {
            close();
        }

        // Return stop action, which means we don't expect next filter to process
        // read event
        return ctx.getStopAction();
    }

    /**
     * The method is called, when the client connection will get closed.
     * Intercepting this method let's use release resources, like local FileChannel,
     * if it wasn't released before.
     *
     * @param ctx Request processing context
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        close();
        return ctx.getStopAction();
    }

    /**
     * Method closes the local file channel, and if download wasn't completed -
     * notify {@link FutureImpl} about download failure.
     *
     * @throws IOException If failed to close <em>localOutput</em>.
     */
    private void close() throws IOException {
        final FileChannel localOutput = this.output;
        // close the local file channel
        if (localOutput != null) {
            localOutput.close();
        }

        if (!completeFuture.isDone()) {
            //noinspection ThrowableInstanceNeverThrown
            completeFuture.failure(new IOException("Connection was closed"));
        }
    }
}
```

Now for the server side. Like the client, there are two parts. The
server itself and the custom Filter. Let's start with the server:

```java
/**
 * Simple HTTP (Web) server, which listens on a specific TCP port and shares
 * static resources (files), located in a passed folder.
 */
public class Server {
    private static final Logger logger = Grizzly.logger(Server.class);

    // TCP Host
    public static final String HOST = "localhost";
    // TCP port
    public static final int PORT = 7777;

    public static void main(String[] args) throws IOException {
        if (args.length < 1) {
            throw new IllegalStateException("Resources directory not specified.");
        }

        // Construct filter chain
        FilterChainBuilder serverFilterChainBuilder = FilterChainBuilder.stateless();
        // Add transport filter
        serverFilterChainBuilder.add(new TransportFilter());
        // Add IdleTimeoutFilter, which will close connections, which stay
        // idle longer than 10 seconds.
        serverFilterChainBuilder.add(
                new IdleTimeoutFilter(
                        new DelayedExecutor(Executors.newCachedThreadPool()),
                                            10,
                                            TimeUnit.SECONDS));
        // Add HttpServerFilter, which transforms Buffer <-> HttpContent
        serverFilterChainBuilder.add(new HttpServerFilter());
        // Simple server implementation, which locates a resource in a local file system
        // and transfers it via HTTP
        serverFilterChainBuilder.add(new WebServerFilter(args[0]));

        // Initialize Transport
        final TCPNIOTransport transport =
                TCPNIOTransportBuilder.newInstance().build();
        // Set filterchain as a Transport Processor
        transport.setProcessor(serverFilterChainBuilder.build());

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

Line 27, a custom Filter, WebServerFilter, is added to serve resources
from the directory in which the server was started. Let's take a look at
that Filter:

```java
/**
 * Simple Web server implementation, which locates requested resources in a
 * local filesystem and transfers it asynchronously to a client.
 *
 * @author Alexey Stashok
 */
public class WebServerFilter extends BaseFilter {
    private static final Logger logger = Grizzly.logger(WebServerFilter.class);
    private final File rootFolderFile;

    /**
     * Construct a WebServer
     * @param rootFolder Root folder in a local filesystem, where server will look
     *                   for resources
     */
    public WebServerFilter(String rootFolder) {
        this.rootFolderFile = new File(rootFolder);

        // check whether the root folder
        if (!rootFolderFile.isDirectory() || !rootFolderFile.canRead()) {
            throw new IllegalStateException("Directory " + rootFolder + " doesn't exist or can't be read");
        }
    }

    /**
     * The method is called once we have received some {@link HttpContent}.
     *
     * Filter gets {@link HttpContent}, which represents a part or complete HTTP
     * request. If it's just a chunk of a complete HTTP request - filter checks
     * whether it's the last chunk, if not - swallows content and returns.
     * If incoming {@link HttpContent} represents complete HTTP request or it is
     * the last HTTP request - it initiates file download and sends the file
     * asynchronously to the client.
     *
     * @param ctx Request processing context
     *
     * @return {@link NextAction}
     * @throws IOException
     */
    @Override
    public NextAction handleRead(FilterChainContext ctx)
            throws IOException {

        // Get the incoming message as HttpContent
        final HttpContent httpContent = ctx.getMessage();
        // Get HTTP request message header
        final HttpRequestPacket request = (HttpRequestPacket) httpContent.getHttpHeader();

        // Check if it's the last HTTP request chunk
        if (!httpContent.isLast()) {
            // if not
            // swallow content
            return ctx.getStopAction();
        }

        // if entire request was parsed
        // extract requested resource URL path
        final String localURL = extractLocalURL(request);

        // Locate corresponding file
        final File file = new File(rootFolderFile, localURL);

        logger.log(Level.INFO, "Request file: {0}", file.getAbsolutePath());

        if (!file.isFile()) {
            // If file doesn't exist - response 404
            final HttpPacket response = create404(request);
            ctx.write(response);

            // return stop action
            return ctx.getStopAction();
        } else {
            // if file exists
            // suspend HttpRequestPacket processing to send the HTTP response
            // asynchronously
            ctx.suspend();
            final NextAction suspendAction = ctx.getSuspendAction();

            // Start asynchronous file download
            downloadFile(ctx, request, file);
            // return suspend action
            return suspendAction;
        }
    }

    /**
     * Start asynchronous file download
     *
     * @param ctx HttpRequestPacket processing context
     * @param request HttpRequestPacket
     * @param file local file
     *
     * @throws IOException
     */
    private void downloadFile(FilterChainContext ctx,
            HttpRequestPacket request, File file) throws IOException {
        // Create DownloadCompletionHandler, responsible for asynchronous
        // file transferring
        final DownloadCompletionHandler downloadHandler =
                new DownloadCompletionHandler(ctx, request, file);
        // Start the download
        downloadHandler.start();
    }

    /**
     * Create a 404 HttpResponsePacket packet
     * @param request original HttpRequestPacket
     *
     * @return 404 HttpContent
     */
    private static HttpPacket create404(HttpRequestPacket request)
            throws CharConversionException {
        // Build 404 HttpResponsePacket message headers
        final HttpResponsePacket responseHeader = HttpResponsePacket.builder(request).
                protocol(request.getProtocol()).status(404).
                reasonPhrase("Not Found").build();

        // Build 404 HttpContent on base of HttpResponsePacket message header
        return responseHeader.httpContentBuilder().
                    content(Buffers.wrap(null,
                                         "Can not find file, corresponding to URI: "
                                                 + request.getRequestURIRef().getDecodedURI())).
                          build();
    }

    /**
     * Extract URL path from the HttpRequestPacket
     *
     * @param request HttpRequestPacket message header
     * @return requested URL path
     */
    private static String extractLocalURL(HttpRequestPacket request)
            throws CharConversionException {
        // Get requested URL
        String url = request.getRequestURIRef().getDecodedURI();

        // Extract path
        final int idx;
        if ((idx = url.indexOf("://")) != -1) {
            final int localPartStart = url.indexOf('/', idx + 3);
            if (localPartStart != -1) {
                url = url.substring(localPartStart + 1);
            } else {
                url = "/";
            }
        }

        return url;
    }

    /**
     * {@link org.glassfish.grizzly.CompletionHandler}, responsible for asynchronous file transferring
     * via HTTP protocol.
     */
    private static class DownloadCompletionHandler
            extends EmptyCompletionHandler<WriteResult>{

        // MemoryManager, used to allocate Buffers
        private final MemoryManager memoryManager;
        // Downloading FileInputStream
        private final InputStream in;
        // Suspended HttpRequestPacket processing context
        private final FilterChainContext ctx;
        // HttpResponsePacket message header
        private final HttpResponsePacket response;

        // Completion flag
        private volatile boolean isDone;

        /**
         * Construct a DownloadCompletionHandler
         *
         * @param ctx Suspended HttpRequestPacket processing context
         * @param request HttpRequestPacket message header
         * @param file local file to be sent
         * @throws FileNotFoundException
         */
        public DownloadCompletionHandler(FilterChainContext ctx,
                HttpRequestPacket request, File file) throws FileNotFoundException {

            // Open file input stream
            in = new FileInputStream(file);
            this.ctx = ctx;
            // Build HttpResponsePacket message header (send file using chunked HTTP messages).
            response = HttpResponsePacket.builder(request).
                protocol(request.getProtocol()).status(200).
                reasonPhrase("OK").chunked(true).build();
            memoryManager = ctx.getConnection().getTransport().getMemoryManager();
        }

        /**
         * Start the file transferring
         *
         * @throws IOException
         */
        public void start() throws IOException {
            sendFileChunk();
        }

        /**
         * Send the next file chunk
         * @throws IOException
         */
        public void sendFileChunk() throws IOException {
            // Allocate a new buffer
            final Buffer buffer = memoryManager.allocate(1024);

            // prepare byte[] for InputStream.read(...)
            final byte[] bufferByteArray = buffer.array();
            final int offset = buffer.arrayOffset();
            final int length = buffer.remaining();

            // Read file chunk from the file input stream
            int bytesRead = in.read(bufferByteArray, offset, length);
            final HttpContent content;

            if (bytesRead == -1) {
                // if the file was completely sent
                // build the last HTTP chunk
                content = response.httpTrailerBuilder().build();
                isDone = true;
            } else {
                // Prepare the Buffer
                buffer.limit(bytesRead);
                // Create HttpContent, based on HttpResponsePacket message header
                content = response.httpContentBuilder().content(buffer).build();
            }

            // Send a file chunk asynchronously.
            // Once the chunk will be sent, the DownloadCompletionHandler.completed(...) method
            // will be called, or DownloadCompletionHandler.failed(...) is error will happen.
            ctx.write(content, this);
        }

        /**
         * Method gets called, when file chunk was successfully sent.
         * @param result the result
         */
        @Override
        public void completed(WriteResult result) {
            try {
                if (!isDone) {
                    // if transfer is not completed - send next file chunk
                    sendFileChunk();
                } else {
                    // if transfer is completed - close the local file input stream.
                    close();
                    // resume(finishing) HttpRequestPacket processing
                    resume();
                }
            } catch (IOException e) {
                failed(e);
            }
        }

        /**
         * The method will be called, when file transferring was canceled
         */
        @Override
        public void cancelled() {
            // Close local file input stream
            close();
            // resume the HttpRequestPacket processing
            resume();
        }

        /**
         * The method will be called, if file transferring was failed.
         * @param throwable the cause
         */
        @Override
        public void failed(Throwable throwable) {
            // Close local file input stream
            close();
            // resume the HttpRequestPacket processing
            resume();
        }

        /**
         * Returns <tt>true</tt>, if file transfer was completed, or
         * <tt>false</tt> otherwise.
         *
         * @return <tt>true</tt>, if file transfer was completed, or
         * <tt>false</tt> otherwise.
         */
        public boolean isDone() {
            return isDone;
        }

        /**
         * Close the local file input stream.
         */
        private void close() {
            try {
                in.close();
            } catch (IOException e) {
                logger.fine("Error closing a downloading file");
            }
        }

        /**
         * Resume the HttpRequestPacket processing
         */
        private void resume() {
            // Resume the request processing
            ctx.resume(ctx.getStopAction());
        }
    }
}
```

Again, we'll let the inline documentation in the examples provide the
details on what's going on here. Let's see the output of the examples in
action. Here's the server start:

```no-highlight
[586][target]$ java -cp grizzly-http-samples-@VERSION@.jar org.glassfish.grizzly.samples.http.download.Server /tmp
Jan 28, 2011 1:11:17 PM org.glassfish.grizzly.samples.http.download.Server main
INFO: Press any key to stop the server...
```

And on the client side:

```no-highlight
[574][ryanlubke.lunasa: target]$ java -cp grizzly-http-samples-@VERSION@.jar org.glassfish.grizzly.samples.http.download.Client http://localhost:7777/test.html
Jan 28, 2011 1:54:49 PM org.glassfish.grizzly.samples.http.download.ClientDownloadFilter handleConnect
INFO: Connected... Sending the request: HttpRequestPacket (
   method=GET
   url=/test.html
   query=null
   protocol=HTTP_1_1
   content-length=-1
   headers=[
      Host=localhost]
)
Jan 28, 2011 1:54:49 PM org.glassfish.grizzly.samples.http.download.ClientDownloadFilter handleRead
INFO: HTTP response: HttpResponsePacket (status=200 reason=OK protocol=HTTP_1_1 content-length=-1 headers==== MimeHeaders ===
date = Fri, 28 Jan 2011 21:54:49 GMT
transfer-encoding = chunked
 committed=false)
Jan 28, 2011 1:54:49 PM org.glassfish.grizzly.samples.http.download.Client main
INFO: File test.html was successfully downloaded
Jan 28, 2011 1:54:49 PM org.glassfish.grizzly.samples.http.download.Client main
INFO: Stopping transport...
Jan 28, 2011 1:54:49 PM org.glassfish.grizzly.samples.http.download.Client main
INFO: Stopped transport...
```

This example within the java.net maven repository:
<https://maven.java.net/content/repositories/releases/org/glassfish/grizzly/samples/grizzly-http-samples/@VERSION@>.
