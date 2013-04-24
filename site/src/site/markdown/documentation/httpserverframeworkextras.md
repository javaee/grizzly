The HTTP Server Framework Extras
================================

The extra Grizzly HTTP server framework features, provided by separate
modules

Multipart (multipart/form-data) HTTP Requests Processing
========================================================

File sharing has become a very popular internet service where users are
able to upload files and share them for group of people. Typically users
will upload their files via a regular HTML form like:

```html
<form action="upload" method="post" enctype="multipart/form-data">
  <input type="text" name="description"/>
  <input type="file" name="fileName"/>
  <input type="submit" value="submit"/>
</form>
```

where after you press "submit", a multipart HTTP request is generated.
For the example above, the HTTP request will contain two parts: text
description and chosen file content, like:

```
POST /upload HTTP/1.1
Host: localhost:18080
Accept: text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8
Accept-Language: en-us,en;q=0.7,uk;q=0.3
Content-Type: multipart/form-data; boundary=---------------------------78965753017082023611608934090
Content-Length: 357

-----------------------------78965753017082023611608934090
Content-Disposition: form-data; name="description"

some text file
-----------------------------78965753017082023611608934090
Content-Disposition: form-data; name="fileName"; filename="test.txt"
Content-Type: text/plain

TEST
-----------------------------78965753017082023611608934090--
```

When using the Grizzly http-server module, the content of the message
above will be represented as one single message together with
boundaries. This means the user will be responsible for parsing content
himself or use an existing library (which, most likely, will process
processes multipart requests in a blocking fashion).

The Grizzly http-server-multipart module provides an API which
simplifies multipart HTTP requests processing and does so in a
non-blocking fashion. At the core of the API is the MultipartScanner,
containing two "scan" methods:

-   Top level multipart HTTP request scanner (multipart/form-data)

```java
public static void scan(final Request request,
        final MultipartEntryHandler partHandler,
        final CompletionHandler<Request> completionHandler);
```

-   multipart/mixed entries scanner

```java
public static void scan(final MultipartEntry multipartMixedEntry,
        final MultipartEntryHandler partHandler,
        final CompletionHandler<MultipartEntry> completionHandler);
```

By calling one of the methods above, we initialize asynchronous
multipart request processing which is going to be handled by the
provided MultipartEntryHandler (which is very similar to HttpHandler)
and ultimately passed CompletionHandler will be notified about multipart
HTTP request processing (i.e., success or error).

The MultipartEntryHandler API is very simple and looks very similar to
HttpHandler:

```java
/**
 * General interface for processing {@link MultipartEntry}s, one by one.
 *
 * @since 2.1
 */
public interface MultipartEntryHandler {
    /**
     * The method is called once {@link MultipartEntry} header is ready to be
     * processed by user code.
     *
     * @param part {@link MultipartEntry}
     * @throws Exception
     */
    public void handle(final MultipartEntry multipartEntry) throws Exception;
}
```

Inside the handle(...) method we're able to process the passed multipart
entry, check its content type, disposition etc., and finally initialize
the asynchronous non-blocking processing of an entry's content using
either the NIOInputStream (binary mode) or the NIOReader (text mode) API
described in the http-server [section](#hsf-nio-streams)

```java
NIOInputStream nioInputStream = multipartEntry.getNIOInputStream();
```

or

```java
NIOReader nioReader = multipartEntry.getNIOReader();
```

Please note, unlike the NIOInputStream and NIOReader got from HTTP
Request, the implementations provided by MultipartEntry expose the
content of a single multipart entry and report end of stream once end of
multipart entry is reached.

Also it's important to understand that multipart message processing is
asynchronous, so we have to suspend HTTP request processing before
starting the scan. Also, don't forget to resume the HTTP request
processing inside the passed CompletionHandler. See http-server
[section](#hsf-suspend-resume) for more information on HTTP request
suspend/resume.

Dependencies
------------

Maven developers require a dependency on the http-server-multipart
module. The following dependency needs to be added to the pom:

```xml
<dependency>
     <groupId>org.glassfish.grizzly</groupId>
     <artifactId>grizzly-http-server-multipart</artifactId>
     <version>2.3</version>
</dependency>
```

Non-maven developers: additional dependencies: [HTTP Server
framework](#http-server-dep), required by:

-   [grizzly-http-server-multipart.jar](https://maven.java.net/content/repositories/releases/org/glassfish/grizzly/grizzly-http-servlet/2.3/grizzly-http-server-multipart-2.3.jar)

Sample
------

Let's see how a file-uploader implementation may be implemented.

First of all let's create HttpHandler, which would be responsible for
multipart HTTP requests processing:

```java
public class UploaderHttpHandler extends HttpHandler {
    private static final String DESCRIPTION_NAME = "description";
    private static final String FILENAME_ENTRY = "fileName";

    // uploads counter (just for debugging/tracking reasons)
    private final AtomicInteger uploadsCounter = new AtomicInteger(1);

    /**
     * Service HTTP request.
     */
    @Override
    public void service(final Request request, final Response response)
            throws Exception {

        // Suspend the HTTP request processing
        // (in other words switch to asynchronous HTTP processing mode).
        response.suspend();

        // assign uploadNumber for this specific upload
        final int uploadNumber = uploadsCounter.getAndIncrement();

        // Initialize MultipartEntryHandler, responsible for handling
        // multipart entries of this request
        final UploaderMultipartHandler uploader =
                new UploaderMultipartHandler(uploadNumber);

        // Start the asynchronous multipart request scanning...
        MultipartScanner.scan(request,
                uploader,
                new EmptyCompletionHandler<Request>() {
            // CompletionHandler is called once HTTP request processing is completed
            // or failed.
            @Override
            public void completed(final Request request) {
                // Upload is complete
                final int bytesUploaded = uploader.getBytesUploaded();

                // Compose a server response.
                try {
                    response.setContentType("text/plain");
                    final Writer writer = response.getWriter();
                    writer.write("Completed. " + bytesUploaded + " bytes uploaded.");
                } catch (IOException ignored) {
                }

                // Resume the asychronous HTTP request processing
                // (in other words finish the asynchronous HTTP request processing).
                response.resume();
            }

            @Override
            public void failed(Throwable throwable) {
                // Complete the asynchronous HTTP request processing.
                response.resume();
            }
        });
    }
}
```

The important part in the code is calls to response.suspend() before
starting the multipart request scanning and response.resume() once
multipart HTTP request is processed.

Now we're ready to take a look at the UploaderMultipartHandler code,
which is responsible for processing single multipart entries:

```java
/**
 * {@link MultipartEntryHandler}, responsible for processing the upload.
 */
private final class UploaderMultipartHandler
        implements MultipartEntryHandler {

    // upload number
    private final int uploadNumber;
    // number of bytes uploaded
    private final AtomicInteger uploadedBytesCounter = new AtomicInteger();

    public UploaderMultipartHandler(final int uploadNumber) {
        this.uploadNumber = uploadNumber;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handle(final MultipartEntry multipartEntry) throws Exception {
        // get the entry's Content-Disposition
        final ContentDisposition contentDisposition =
                multipartEntry.getContentDisposition();
        // get the multipart entry name
        final String name = contentDisposition.getDispositionParamUnquoted("name");

        // if the multipart entry contains a file content
        if (FILENAME_ENTRY.equals(name)) {

            // get the filename for Content-Disposition
            final String filename =
                    contentDisposition.getDispositionParamUnquoted("filename");

            // Get the NIOInputStream to read the multipart entry content
            final NIOInputStream inputStream = multipartEntry.getNIOInputStream();

            // start asynchronous non-blocking content read.
            inputStream.notifyAvailable(
                    new UploadReadHandler(uploadNumber, filename,
                    inputStream, uploadedBytesCounter));

        } else if (DESCRIPTION_NAME.equals(name)) { // if multipart entry contains a description field
            // skip the multipart entry
            multipartEntry.skip();
        } else { // Unexpected entry?
            // skip it
            multipartEntry.skip();
        }
    }

    /**
     * Returns the number of bytes uploaded for this multipart entry.
     *
     * @return the number of bytes uploaded for this multipart entry.
     */
    int getBytesUploaded() {
        return uploadedBytesCounter.get();
    }
}
```

Inside the handle(...) method we check if the multipart entry represents
a file to be uploaded, because as we mentioned in the beginning, the
HTTP multipart request contains two multipart entries: one for
description and the second for actual file content. If the multipart
entry represents a text description, we just skip it by calling
multipartEntry.skip(). However, if multipart entry represents file
content, we start the asynchronous content reading using NIOInputStream.

Let's see how our asynchronous ReadHandler looks like:

```java
/**
 * Simple {@link ReadHandler} implementation, which is reading HTTP request
 * content (uploading file) in non-blocking mode and saves the content into
 * the specific file.
 */
private class UploadReadHandler implements ReadHandler {

    // the upload number
    private final int uploadNumber;
    // Non-blocking multipart entry input stream
    private final NIOInputStream inputStream;

    // the destination file output stream, where we save the data.
    private final FileOutputStream fileOutputStream;

    // temporary buffer
    private final byte[] buf;

    // uploaded bytes counter
    private final AtomicInteger uploadedBytesCounter;

    private UploadReadHandler(final int uploadNumber,
            final String filename,
            final NIOInputStream inputStream,
            final AtomicInteger uploadedBytesCounter)
            throws FileNotFoundException {

        this.uploadNumber = uploadNumber;
        fileOutputStream = new FileOutputStream(filename);
        this.inputStream = inputStream;
        this.uploadedBytesCounter = uploadedBytesCounter;
        buf = new byte[2048];
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onDataAvailable() throws Exception {
        // save available file content
        readAndSaveAvail();

        // register this handler to be notified next time some data
        // becomes available
        inputStream.notifyAvailable(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onAllDataRead() throws Exception {
        // save available file content
        readAndSaveAvail();
        // finish the upload
        finish();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onError(Throwable t) {
        // finish the upload
        finish();
    }

    /**
     * Read available file content data out of HTTP request and save the
     * chunk into local file output stream
     *
     * @throws IOException
     */
    private void readAndSaveAvail() throws IOException {
        while (inputStream.isReady()) {
            // read the available bytes from input stream
            final int readBytes = inputStream.read(buf);
            // update the counter
            uploadedBytesCounter.addAndGet(readBytes);
            // save the file content to the file
            fileOutputStream.write(buf, 0, readBytes);
        }
    }

    /**
     * Finish the file upload
     */
    private void finish() {
        try {
            // close file output stream
            fileOutputStream.close();
        } catch (IOException ignored) {
        }
    }
}
```

As you can see, the logic is pretty simple. Once the ReadHandler is
notified about data being available, we read the data off the
NIOInputStream and store it to a local file. If we expect more data, we
just re-register the ReadHandler to wait for it to be notified. If there
is no more data to be processed, finish the upload by closing the local
file output stream.

The complete working sample could be downloaded
[here](https://maven.java.net/content/repositories/releases/org/glassfish/grizzly/samples/grizzly-http-multipart-samples/2.3/grizzly-http-multipart-samples-2.3-sources.jar)
