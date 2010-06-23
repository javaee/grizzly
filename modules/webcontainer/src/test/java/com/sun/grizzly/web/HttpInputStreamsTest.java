/*
 * This is a test
 */

package com.sun.grizzly.web;

import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.TransportFactory;
import com.sun.grizzly.filterchain.*;
import com.sun.grizzly.http.*;
import com.sun.grizzly.http.server.GrizzlyRequest;
import com.sun.grizzly.http.server.GrizzlyResponse;
import com.sun.grizzly.http.server.adapter.GrizzlyAdapter;
import com.sun.grizzly.http.server.embed.GrizzlyWebServer;
import com.sun.grizzly.impl.FutureImpl;
import com.sun.grizzly.impl.SafeFutureImpl;
import com.sun.grizzly.memory.MemoryUtils;
import com.sun.grizzly.nio.transport.TCPNIOTransport;
import com.sun.grizzly.utils.ChunkingFilter;
import junit.framework.TestCase;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.CharBuffer;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Test cases to validate the behaviors of {@link com.sun.grizzly.http.server.io.RequestInputStream} and
 * {@link com.sun.grizzly.http.server.io.RequestReader}.
 */
public class HttpInputStreamsTest extends TestCase {

    private static final int PORT = 8003;

    /**
     * Use a Future to capture exceptions that happen in threads outside of the
     * JUnit thread.  It's important that all test cases within this class call
     * <code>reportThreadErrors()</code> before returning for accurate results.
     */
//    private final FutureImpl<Throwable> exception = SafeFutureImpl.create();


    // ----------------------------------------------------- Binary Test Methods


    public void testBinaryWithGet() throws Throwable {

        ReadStrategy reader = new ReadStrategy() {
            @Override public boolean doRead(GrizzlyRequest request)
                    throws IOException {

                // test issues a GET, so the InputStream should be inert
                InputStream in = request.getInputStream();
                assertNotNull(in);
                assertEquals(0, in.available());
                assertEquals(-1, in.read());
                assertEquals(-1, in.read(new byte[10]));
                assertEquals(-1, in.read(new byte[10], 0, 10));
                in.close();
                return true;
            }
        };

        doTest(createRequest("GET", null), reader, 1024);

    }


    public void testBinaryResetNoMark() throws Throwable {

        final String expected = "abcdefghijklmnopqrstuvwxyz";
        ReadStrategy reader = new ReadStrategy() {
            @Override public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                InputStream in = request.getInputStream();
                try {
                    in.reset();
                    fail();
                } catch (IOException ioe) {
                    // expected
                }
                return true;
            }
        };

        doTest(createRequest("POST", expected), reader, 1024);

    }


    public void testBinaryMarkReset001() throws Throwable {

        final String expected = "abcdefghijklmnopqrstuvwxyz";
        ReadStrategy reader = new ReadStrategy() {
            @Override public boolean doRead(GrizzlyRequest request)
            throws IOException {
                StringBuilder sb = new StringBuilder(expected.length());
                InputStream in = request.getInputStream();

                for (int j = 0; j < 5; j++) {
                    sb.append((char) in.read());
                }
                assertEquals(expected.substring(0, 5), sb.toString());
                in.mark(30);

                for (int j = 0; j < 5; j++) {
                    sb.append((char) in.read());
                }
                in.reset();
                assertEquals(expected.substring(0, 10), sb.toString());

                for (int i = in.read(); i != -1; i = in.read()) {
                    sb.append((char) i);
                }

                in.close();
                assertEquals(0, in.available());
                StringBuilder exp = new StringBuilder(expected);
                exp.insert(5, expected.substring(5, 10));
                assertEquals(exp.toString(), sb.toString());
                return true;
            }
        };

        doTest(createRequest("POST", expected), reader, 1024);

    }


    public void testBinaryMarkReset002() throws Throwable {

        final String expected = "abcdefghijklmnopqrstuvwxyz";
        ReadStrategy reader = new ReadStrategy() {
            @Override public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                StringBuilder sb = new StringBuilder(5);
                InputStream in = request.getInputStream();

                for (int j = 0; j < 5; j++) {
                    sb.append((char) in.read());
                }
                assertEquals(expected.substring(0, 5), sb.toString());
                in.mark(2);

                for (int j = 0; j < 5; j++) {
                    sb.append((char) in.read());
                }
                try {
                    in.reset();
                    fail();
                } catch (IOException ioe) {
                    // expected
                }
                in.close();
                assertEquals(0, in.available());
                return true;
            }
        };

        doTest(createRequest("POST", expected), reader, 1024);

    }


    public void testBinaryMarkReset003() throws Throwable {

        final String expected = "abcdefghijklmnopqrstuvwxyz";
        ReadStrategy reader = new ReadStrategy() {
            @Override 
            public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                StringBuilder sb = new StringBuilder(5);
                InputStream in = request.getInputStream();

                for (int j = 0; j < 5; j++) {
                    sb.append((char) in.read());
                }
                assertEquals(expected.substring(0, 5), sb.toString());
                in.mark(2);

                for (int j = 0; j < 2; j++) {
                    sb.append((char) in.read());
                }
                in.reset();
                assertEquals(expected.substring(0, 7), sb.toString());

                for (int j = 0; j < 2; j++) {
                    sb.append((char) in.read());
                }
                in.reset();
                StringBuilder sb2 = new StringBuilder(expected);
                sb2.insert(5, expected.substring(5, 7));
                assertEquals(sb2.toString().substring(0, 9), sb.toString());

                for (int j = 0; j < 3; j++) {
                    sb.append((char) in.read());
                }
                try {
                    in.reset();
                    fail();
                } catch (IOException ioe) {
                    // expected
                }
                in.close();
                assertEquals(0, in.available());
                return true;
            }
        };

        doTest(createRequest("POST", expected), reader, 1024);

    }


    public void testBinarySkip001() throws Throwable {

        final String expected = "abcdefghijklmnopqrstuvwxyz";
        ReadStrategy reader = new ReadStrategy() {
            @Override
            public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                StringBuilder sb = new StringBuilder(16);
                InputStream in = request.getInputStream();
                long skipped = in.skip(0);
                assertEquals(0, skipped);
                skipped = in.skip(-1000);
                assertEquals(0, skipped);
                skipped = in.skip(10);
                assertEquals(10, skipped);
                for (int i = in.read(); i != -1; i = in.read()) {
                    sb.append((char) i);
                }
                assertEquals(0, in.available());
                assertEquals(-1, in.read());
                assertEquals(-1, in.skip(10));
                in.close();
                assertEquals(expected.substring(10), sb.toString());
                return true;
            }
        };

        doTest(createRequest("POST", expected), reader, 1024);

    }


    public void testBinarySkip002() throws Throwable {

        final String expected = "abcdefghijklmnopqrstuvwxyz";
        ReadStrategy reader = new ReadStrategy() {
            @Override
            public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                InputStream in = request.getInputStream();
                long skipped = in.skip(100);
                assertEquals(26, skipped);
                assertEquals(0, in.available());
                assertEquals(-1, in.read());
                assertEquals(-1, in.skip(10));
                in.close();
                return true;
            }
        };

        doTest(createRequest("POST", expected), reader, 1024);

    }


    public void testBinary002() throws Throwable {

        final String expected = "abcdefghijklmnopqrstuvwxyz";
        ReadStrategy reader = new ReadStrategy() {
            @Override
            public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                InputStream in = request.getInputStream();
                byte[] b = new byte[expected.length()];
                assertEquals(26, in.read(b));
                assertEquals(expected, new String(b));
                assertEquals(0, in.available());
                assertEquals(-1, in.read(b));
                in.close();
                return true;
            }
        };

        doTest(createRequest("POST", expected), reader, 1024);

    }

    public void testBinary003() throws Throwable {

        final String content = "abcdefghijklmnopqrstuvwxyz";
        ReadStrategy reader = new ReadStrategy() {
            @Override
            public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                InputStream in = request.getInputStream();
                byte[] b = new byte[14];
                assertEquals(5, in.read(b, 2, 5));
                assertTrue(in.available() > 0);
                in.close();
                assertEquals("abcde", new String(b, 2, 5));
                return true;
            }
        };

        doTest(createRequest("POST", content), reader, 1024);

    }


    public void testBinary004() throws Throwable {

        final String expected = "abcdefghijklmnopqrstuvwxyz";
        ReadStrategy reader = new ReadStrategy() {
            @Override
            public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                InputStream in = request.getInputStream();
                byte[] b = new byte[expected.length() - 2];
                assertEquals(24, in.read(b));
                assertEquals(expected.substring(0, 24), new String(b));
                assertEquals(2, in.read(b));
                assertEquals(expected.substring(24), new String(b, 0, 2));
                assertEquals(-1, in.read(b));
                assertEquals(0, in.available());
                in.close();
                return true;
            }
        };

        doTest(createRequest("POST", expected), reader, 1024);

    }


    public void testBinary005() throws Throwable {

        final StringBuilder b = new StringBuilder(8192);
        for (int i = 0, let = 'a'; i < 8192; i++, let++) {
            b.append((char) let);
            if (let == 'z') {
                let = 'a';
            }
        }


        ReadStrategy reader = new ReadStrategy() {
            @Override public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                InputStream in = request.getInputStream();
                StringBuilder sb = new StringBuilder();
                byte[] buf = new byte[512];
                for (int i = in.read(buf); i != -1; i = in.read(buf)) {
                    sb.append(new String(buf, 0, i));
                }
                assertEquals(b.toString(), sb.toString());
                assertEquals(0, in.available());
                in.close();
                return true;
            }
        };

        doTest(createRequest("POST", b.toString()), reader, 1024);

    }


    public void testBinary006() throws Throwable {

        int len = 1024 * 17;
        final StringBuilder b = new StringBuilder(len);
        for (int i = 0, let = 'a'; i < len; i++, let++) {
            b.append((char) let);
            if (let == 'z') {
                let = 'a';
            }
        }


        ReadStrategy reader = new ReadStrategy() {
            @Override public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                InputStream in = request.getInputStream();
                StringBuilder sb = new StringBuilder();
                byte[] buf = new byte[512];
                for (int i = in.read(buf); i != -1; i = in.read(buf)) {
                    sb.append(new String(buf, 0, i));
                }
                assertEquals(b.toString(), sb.toString());
                assertEquals(0, in.available());
                in.close();
                return true;
            }
        };

        doTest(createRequest("POST", b.toString()), reader, 1024);

    }


    // -------------------------------------------------- Character Test Methods


    public void testCharacter001() throws Throwable {

        final String expected = "abcdefghijklmnopqrstuvwxyz";
        ReadStrategy reader = new ReadStrategy() {
            @Override public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                StringBuilder sb = new StringBuilder(26);
                Reader in = request.getReader();
                for (int i = in.read(); i != -1; i = in.read()) {
                    sb.append((char) i);
                }
                assertEquals(-1, in.read());
                in.close();
                assertEquals(expected.length(), sb.length());
                assertEquals(expected, sb.toString());
                return true;
            }
        };

        doTest(createRequest("POST", expected), reader, 1024);

    }


    public void testCharacter002() throws Throwable {

        final String expected = "abcdefghijklmnopqrstuvwxyz";
        ReadStrategy reader = new ReadStrategy() {
            @Override
            public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                Reader in = request.getReader();
                char[] b = new char[expected.length()];
                assertEquals(26, in.read(b));
                assertEquals(expected, new String(b));
                assertEquals(-1, in.read(b));
                in.close();
                return true;
            }
        };

        doTest(createRequest("POST", expected), reader, 1024);

    }


    public void testCharacter003() throws Throwable {

        final String content = "abcdefghijklmnopqrstuvwxyz";
        ReadStrategy reader = new ReadStrategy() {
            @Override
            public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                Reader in = request.getReader();
                char[] b = new char[14];
                assertEquals(5, in.read(b, 2, 5));
                in.close();
                assertEquals("abcde", new String(b, 2, 5));
                return true;
            }
        };

        doTest(createRequest("POST", content), reader, 1024);

    }


    public void testCharacter004() throws Throwable {

        final String expected = "abcdefghijklmnopqrstuvwxyz";
        ReadStrategy reader = new ReadStrategy() {
            @Override
            public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                Reader in = request.getReader();
                char[] b = new char[expected.length() - 2];
                assertEquals(24, in.read(b));
                assertEquals(expected.substring(0, 24), new String(b));
                assertEquals(2, in.read(b));
                assertEquals(expected.substring(24), new String(b, 0, 2));
                assertEquals(-1, in.read(b));
                in.close();
                return true;
            }
        };

        doTest(createRequest("POST", expected), reader, 1024);

    }


    public void testCharacter005() throws Throwable {

        int len = 1024 * 8;
        final StringBuilder b = new StringBuilder(len);
        for (int i = 0, let = 'a'; i < len; i++, let++) {
            b.append((char) let);
            if (let == 'z') {
                let = 'a' - 1;
            }
        }

        ReadStrategy reader = new ReadStrategy() {
            @Override
            public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                Reader in = request.getReader();
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[512];
                for (int i = in.read(buf); i != -1; i = in.read(buf)) {
                    sb.append(new String(buf, 0, i));
                }
                assertEquals(b.length(), sb.length());
                assertEquals(b.toString(), sb.toString());
                in.close();
                return true;
            }
        };

        doTest(createRequest("POST", b.toString()), reader, 1024);

    }


    public void testCharacter006() throws Throwable {

        int len = 1024 * 17;
        final StringBuilder b = new StringBuilder(len);
        for (int i = 0, let = 'a'; i < len; i++, let++) {
            b.append((char) let);
            if (let == 'z') {
                let = 'a' - 1;
            }
        }


        ReadStrategy reader = new ReadStrategy() {
            @Override
            public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                Reader in = request.getReader();
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[512];
                for (int i = in.read(buf); i != -1; i = in.read(buf)) {
                    sb.append(new String(buf, 0, i));
                }
                assertEquals(sb.toString(), b.toString());
                in.close();
                return true;
            }
        };

        doTest(createRequest("POST", b.toString()), reader, 1024);

    }


    public void testCharacter007() throws Throwable {

        final int len = 1024 * 57;
        final StringBuilder b = new StringBuilder(len);
        for (int i = 0, let = 'a'; i < len; i++, let++) {
            b.append((char) let);
            if (let == 'z') {
                let = 'a' - 1;
            }
        }


        ReadStrategy reader = new ReadStrategy() {
            @Override
            public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                Reader in = request.getReader();
                StringBuilder sb = new StringBuilder();
                char[] buf = new char[1024 * 9];
                for (int i = in.read(buf); i != -1; i = in.read(buf)) {
                    sb.append(new String(buf, 0, i));
                }
                assertEquals(sb.toString(), b.toString());
                in.close();
                return true;
            }
        };

        doTest(createRequest("POST", b.toString()), reader, 1024 * 9);

    }


    public void testCharacter008() throws Throwable {

        final String expected = "abcdefghijklmnopqrstuvwxyz";
        ReadStrategy reader = new ReadStrategy() {
            @Override
            public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                CharBuffer cbuf = CharBuffer.allocate(52);
                Reader in = request.getReader();
                int read = in.read(cbuf);
                assertEquals(expected.length(), read);
                assertEquals(-1, in.read());
                in.close();
                cbuf.flip();
                assertEquals(expected.length(), cbuf.remaining());
                assertEquals(expected, cbuf.toString());
                return true;
            }
        };

        doTest(createRequest("POST", expected), reader, 1024);

    }


    public void testCharacter009() throws Throwable {

        final String expected = "abcdefghijklmnopqrstuvwxyz";
        ReadStrategy reader = new ReadStrategy() {
            @Override
            public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                Reader in = request.getReader();
                CharBuffer cbuf = CharBuffer.allocate(expected.length() / 2);
                StringBuilder sb = new StringBuilder(expected.length());
                for (int i = in.read(cbuf); i != -1; i = in.read(cbuf)) {
                    sb.append(cbuf.flip().toString());
                }
                assertEquals(-1, in.read());
                assertEquals(expected.length(), sb.length());
                assertEquals(expected, sb.toString());
                return true;
            }
        };

        doTest(createRequest("POST", expected), reader, 1024);

    }


    public void testCharacter010() throws Throwable {

        final int len = 1024 * 57;
        final StringBuilder b = new StringBuilder(len);
        for (int i = 0, let = 'a'; i < len; i++, let++) {
            b.append((char) let);
            if (let == 'z') {
                let = 'a' - 1;
            }
        }

        ReadStrategy reader = new ReadStrategy() {
            @Override
            public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                Reader in = request.getReader();
                CharBuffer cbuf = CharBuffer.allocate(1024 / 2);
                StringBuilder sb = new StringBuilder(len);
                for (int i = in.read(cbuf); i != -1; i = in.read(cbuf)) {
                    sb.append(cbuf.flip().toString());
                }
                assertEquals(-1, in.read());
                assertEquals(b.length(), sb.length());
                assertEquals(b.toString(), sb.toString());
                return true;
            }
        };

        doTest(createRequest("POST", b.toString()), reader, 1024);

    }


    public void testCharacterReady001() throws Throwable {

        final String expected = "abcdefghijklmnopqrstuvwxyz";
        ReadStrategy reader = new ReadStrategy() {
            @Override
            public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                StringBuilder sb = new StringBuilder(26);
                Reader in = request.getReader();
                assertTrue(in.ready());
                for (int i = in.read(); i != -1; i = in.read()) {
                    sb.append((char) i);
                }
                assertEquals(-1, in.read());
                assertFalse(in.ready());
                in.close();
                assertEquals(expected.length(), sb.length());
                assertEquals(expected, sb.toString());
                return true;
            }
        };

        doTest(createRequest("POST", expected), reader, 1024);

    }

    /*  TODO REVISIT:  This test relies on the underlying behavior
           of the RequestReader implementation, however, once the
           test was changed to use GrizzlyRequest to read, the
           Reader is now wrapped by a BufferedReader which changes
           the dynamics of the test.

    public void testCharacterReady002() throws Throwable {

        final int len = 1024 * 8;
        final StringBuilder b = new StringBuilder(len);
        for (int i = 0, let = 'a'; i < len; i++, let++) {
            b.append((char) let);
            if (let == 'z') {
                let = 'a' - 1;
            }
        }

        ReadStrategy reader = new ReadStrategy() {
            @Override public boolean doRead(GrizzlyRequest request)
            throws IOException {
                try {
                    Reader in = request.getReader();
                    StringBuilder sb = new StringBuilder();
                    char[] buf = new char[1024];
                    for (int i = in.read(buf), count = 0; i != -1; i = in.read(buf), count++) {
                        if (count < 7) {
                            assertTrue(in.ready());
                        } else {
                            assertFalse(in.ready());
                        }
                        sb.append(new String(buf, 0, i));
                    }
                    assertEquals(sb.toString(), b.toString());
                    assertFalse(in.ready());
                    assertEquals(-1, in.read(buf));
                    in.close();
                    return true;
                } catch (Throwable t) {
                    exception.result(t);
                    return false;
                }
            }
        };

        doTest(createRequest("POST", b.toString()), reader, len);

    }
    */


    public void testCharacterSkip001() throws Throwable {

        final String expected = "abcdefghijklmnopqrstuvwxyz";
        ReadStrategy reader = new ReadStrategy() {

            @Override
            public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                StringBuilder sb = new StringBuilder(16);
                Reader in = request.getReader();
                long skipped = in.skip(0);
                assertEquals(0, skipped);
                try {
                    in.skip(-1000);
                    fail();
                } catch (IllegalArgumentException iae) {
                    // expected
                }
                skipped = in.skip(10);
                assertEquals(10, skipped);
                for (int i = in.read(); i != -1; i = in.read()) {
                    sb.append((char) i);
                }
                assertEquals(-1, in.read());
                assertEquals(0, in.skip(10));
                in.close();
                assertEquals(expected.substring(10), sb.toString());
                return true;
            }
        };

        doTest(createRequest("POST", expected), reader, 1024);

    }


    public void testCharacterSkip002() throws Throwable {

        final String expected = "abcdefghijklmnopqrstuvwxyz";
        ReadStrategy reader = new ReadStrategy() {
            @Override
            public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                Reader in = request.getReader();
                long skipped = in.skip(100);
                assertEquals(26, skipped);
                assertEquals(-1, in.read());
                assertEquals(0, in.skip(10));
                in.close();
                return true;
            }
        };

        doTest(createRequest("POST", expected), reader, 1024);

    }


    public void testCharacterSkip003() throws Throwable {

        final int len = 1024 * 9;
        final StringBuilder b = new StringBuilder(len);
        for (int i = 0, let = 'a'; i < len; i++, let++) {
            b.append((char) let);
            if (let == 'z') {
                let = 'a' - 1;
            }
        }

        ReadStrategy reader = new ReadStrategy() {
            @Override
            public boolean doRead(GrizzlyRequest request)
                    throws IOException {
                int skipLen = 9000;
                Reader in = request.getReader();
                long skipped = in.skip(skipLen);
                while (skipped != skipLen) {
                    skipped += in.skip(skipLen - skipped);
                }
                char[] buf = new char[1024];
                StringBuilder sb = new StringBuilder(1024);
                for (int i = in.read(buf); i != -1; i = in.read(buf)) {
                    sb.append(buf, 0, i);
                }
                assertEquals(b.toString().substring(skipLen), sb.toString());
                assertEquals(-1, in.read());
                assertEquals(0, in.skip(10));
                in.close();
                return true;
            }
        };

        doTest(createRequest("POST", b.toString()), reader, 1024);

    }


    // --------------------------------------------------------- Private Methods

    @SuppressWarnings({"unchecked"})
    private HttpPacket createRequest(final String method, final String content) {
        final Buffer contentBuffer = content != null ?
            MemoryUtils.wrap(TransportFactory.getInstance().getDefaultMemoryManager(), content) :
            null;
        
        HttpRequestPacket.Builder b = HttpRequestPacket.builder();
        b.method(method).protocol(HttpCodecFilter.HTTP_1_1).uri("/path").chunked(false).header("Host", "localhost");
        if (content != null) {
            b.contentLength(contentBuffer.remaining());
        }

        HttpRequestPacket request = b.build();

        if (content != null) {
            HttpContent.Builder cb = request.httpContentBuilder();
            cb.content(contentBuffer);
            return cb.build();
        }

        return request;
    }


    private void doTest(HttpPacket request, ReadStrategy strategy, int chunkSize)
    throws Throwable {

        final FutureImpl<Boolean> testResult = SafeFutureImpl.create();

        GrizzlyWebServer server = new GrizzlyWebServer();
        GrizzlyWebServer.ServerConfiguration sconfig = server.getServerConfiguration();
        sconfig.addGrizzlyAdapter(new SimpleResponseAdapter(strategy, testResult), new String[] { "/*" });
        GrizzlyWebServer.ListenerConfiguration lconfig = server.getListenerConfiguration();
        lconfig.setPort(PORT);

        TCPNIOTransport ctransport = TransportFactory.getInstance().createTCPTransport();
        try {
            server.start();
            FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
            clientFilterChainBuilder.add(new TransportFilter());
            clientFilterChainBuilder.add(new ChunkingFilter(chunkSize));
            clientFilterChainBuilder.add(new HttpClientFilter());
            clientFilterChainBuilder.add(new ClientFilter(request, testResult));
            ctransport.setProcessor(clientFilterChainBuilder.build());

            ctransport.start();

            Future<Connection> connectFuture = ctransport.connect("localhost", PORT);
            Connection connection = null;
            try {
                connection = connectFuture.get(10, TimeUnit.SECONDS);
                testResult.get(1000, TimeUnit.SECONDS);
            } finally {
                // Close the client connection
                if (connection != null) {
                    connection.close();
                }
            }
        } finally {
            server.stop();
            ctransport.stop();
            TransportFactory.getInstance().close();
        }
    }


    // ---------------------------------------------------------- Nested Classes


    private interface ReadStrategy {

        boolean doRead(GrizzlyRequest request) throws IOException;

    }


    private static final class SimpleResponseAdapter extends GrizzlyAdapter {
        private final FutureImpl<Boolean> testResult;
        private final ReadStrategy strategy;


        // -------------------------------------------------------- Constructors


        public SimpleResponseAdapter(ReadStrategy strategy, FutureImpl<Boolean> testResult) {
            this.strategy = strategy;
            this.testResult = testResult;
        }


        // ----------------------------------------- Methods from GrizzlyAdapter


        @Override
        public void service(GrizzlyRequest req, GrizzlyResponse res) throws Exception {
            Throwable t = null;

            res.setStatus(200, "OK");
            res.setContentLength(0);
            try {
                if (strategy.doRead(req)) {
                    res.addHeader("Status", "OK");
                    return;
                }
            } catch (Throwable e) {
                t = e;
            }

            testResult.failure(t != null ? t : new IllegalStateException("Strategy returned false"));
            res.addHeader("Status", "Failed");
        }

    } // END SimpleResponseAdapter



    private class ClientFilter extends BaseFilter {
        private final Logger logger = Grizzly.logger(ClientFilter.class);

        private final HttpPacket request;
        private final FutureImpl<Boolean> testResult;

        // -------------------------------------------------------- Constructors


        public ClientFilter(HttpPacket request, FutureImpl<Boolean> testResult) {

            this.request = request;
            this.testResult = testResult;

        }


        // ------------------------------------------------ Methods from Filters


        @Override
        public NextAction handleConnect(FilterChainContext ctx)
              throws IOException {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                           "Connected... Sending the request: " + request);
            }

            ctx.write(request);

            return ctx.getStopAction();
        }


        @Override
        public NextAction handleRead(FilterChainContext ctx)
              throws IOException {

            final HttpContent httpContent = (HttpContent) ctx.getMessage();

            logger.log(Level.FINE, "Got HTTP response chunk");

            final Buffer buffer = httpContent.getContent();

            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE,
                           "HTTP content size: " + buffer.remaining());
            }

            if (httpContent.isLast()) {
                try {
                    assertEquals("OK",
                                 "OK",
                                 httpContent.getHttpHeader().getHeader("Status"));
                } finally {
                    testResult.result(Boolean.TRUE);
                }
            }

            return ctx.getStopAction();
        }

        @Override
        public NextAction handleClose(FilterChainContext ctx)
              throws IOException {
            return ctx.getStopAction();
        }

    }

}