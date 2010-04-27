package com.sun.grizzly.http;

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.util.DefaultThreadPool;
import com.sun.grizzly.util.buf.ByteChunk;
import junit.framework.TestCase;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

public class GWSLoadTest extends TestCase {
    private static final Logger logger = Logger.getLogger("grizzly");

    public void testLoad() throws Throwable {

        GrizzlyWebServer gws;
        DefaultThreadPool.DEFAULT_IDLE_THREAD_KEEPALIVE_TIMEOUT = 1000 * 60 * 5;

        gws = new GrizzlyWebServer(6666, "", false);

        gws.getSelectorThread().setDisplayConfiguration(true);
        gws.getSelectorThread().setCompression("on");
        gws.addGrizzlyAdapter(new LoadTestAdapter(), new String[]{"/"});
        gws.start();
        final AtomicInteger count = new AtomicInteger(0);
        try {
            for(int index = 0; index < 50; index++) {
                final int finalIndex = index;
                new Thread(new Runnable() {
                    public void run() {
                        try {
                            logger.finer("Starting client " + finalIndex);
                            count.incrementAndGet();
                            final InputStream stream = (InputStream) new URL("http://localhost:6666").getContent();
                            byte[] buf = new byte[1024];
                            while(stream.read(buf) != -1) {
                                Thread.sleep(10);
                            }
                        } catch (IOException e) {
                            logger.info(e.getMessage());
                            e.printStackTrace();
                        } catch (InterruptedException e) {
                            throw new RuntimeException(e.getMessage(), e);
                        } finally {
                            count.decrementAndGet();
                        }
                    }
                }).start();
            }
            while(count.get() > 0) {
                System.out.printf("waiting for clients: %s\n", count);
                Thread.sleep(500);
            }
        } finally {
            gws.stop();
        }
    }

    private static class LoadTestAdapter extends GrizzlyAdapter {
        private StringBuilder text;
        private String msg;

        public LoadTestAdapter() {
            text = new StringBuilder();
            for(int index = 0; index < 50000; index++) {
                text.append("0123456789");
            }
            msg = text.toString();
        }

        @Override
        public void service(GrizzlyRequest grizzlyRequest, GrizzlyResponse grizzlyResponse) {
            try {
                grizzlyResponse.setContentType("text/html");
                grizzlyResponse.setStatus(500);
                doWrite(grizzlyResponse, msg, "UTF-8");
            } catch (Throwable e) {
            }
        }

        private void doWrite(GrizzlyResponse httpResp, String msg, String encode) throws IOException {
            try {
                byte b[] = msg.getBytes(encode);
                ByteChunk chunk = new ByteChunk();

                int len = b.length;
                chunk.setBytes(b, 0, len);
                httpResp.setCharacterEncoding(encode);

                httpResp.setContentLength(len);

                httpResp.getResponse().doWrite(chunk);
            } catch (Throwable e) {
                e.printStackTrace();

            }
        }
    }
}