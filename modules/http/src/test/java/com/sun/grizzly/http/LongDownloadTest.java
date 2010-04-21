package com.sun.grizzly.http;

import com.sun.grizzly.ControllerStateListenerAdapter;
import com.sun.grizzly.http.utils.SelectorThreadUtils;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import com.sun.grizzly.util.Utils;
import junit.framework.TestCase;
import org.junit.Assert;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;

public class LongDownloadTest extends TestCase {
    public static final int PORT = 18890;
    private File base;
    private File tmp;
    private static final String LINE =
            "Lorem ipsum dolor sit amet, consectetur adipiscing elit. Vivamus quis lectus odio, et" +
                    " dictum purus. Suspendisse id ante ac tortor facilisis porta. Nullam aliquet dapibus dui, ut" +
                    " scelerisque diam luctus sit amet. Donec faucibus aliquet massa, eget iaculis velit ullamcorper" +
                    " eu. Fusce quis condimentum magna. Vivamus eu feugiat mi. Cras varius convallis gravida. Vivamus" +
                    " et elit lectus. Aliquam egestas, erat sed dapibus dictum, sem ligula suscipit mauris, a" +
                    " consectetur massa augue vel est. Nam bibendum varius lobortis. In tincidunt, sapien quis" +
                    " hendrerit vestibulum, lorem turpis faucibus enim, non rhoncus nisi diam non neque. Aliquam eu" +
                    " urna urna, molestie aliquam sapien. Nullam volutpat, erat condimentum interdum viverra, tortor" +
                    " lacus venenatis neque, vitae mattis sem felis pellentesque quam. Nullam sodales vestibulum" +
                    " ligula vitae porta. Aenean ultrices, ligula quis dapibus sodales, nulla risus sagittis sapien," +
                    " id posuere turpis lectus ac sapien. Pellentesque sed ante nisi. Quisque eget posuere sapien.\n";

    public void setUp() throws IOException {
        tmp = new File("/tmp");
        Utils.dumpOut("Generating large file for download");
        if (!tmp.exists()) {
            tmp = new File(System.getProperty("java.io.tmpdir"));
        }
        base = File.createTempFile("largeFile", ".jar", tmp);
        base.deleteOnExit();
        FileOutputStream out = new FileOutputStream(base);
        try {
            for (int x = 0; x < 1000; x++) {
                out.write(( x + ": " + LINE).getBytes());
                out.flush();
            }
        } finally {
            out.close();
        }
    }

    public void testDownload() throws IOException, InstantiationException {
        final SelectorThread st = newThread();
        try {
            st.setAdapter(new StaticResourcesAdapter(tmp.getAbsolutePath()));
            st.listen();
            File file = File.createTempFile("downloaded-largeFile", ".jar", tmp);
            file.deleteOnExit();
            download(String.format("http://localhost:%s/%s", PORT, base.getName()), file);

            Assert.assertEquals("Files should be same size", base.length(), file.length());
        } finally {
            SelectorThreadUtils.stopSelectorThread(st);
        }
    }

    private void download(final String url, File file) throws IOException {
        URL u = new URL(url);
        HttpURLConnection huc = (HttpURLConnection) u.openConnection();
        huc.setRequestMethod("GET");
        huc.setReadTimeout(0);

        final long start = System.currentTimeMillis();
        long end;
        InputStream is = huc.getInputStream();

        int c;
        byte[] ba = new byte[LINE.length() + 6];
        FileOutputStream fos = new FileOutputStream(file);
        try {
            while ((c = is.read(ba)) != -1) {
                fos.write(ba, 0, c);
                fos.flush();
                Thread.sleep(100);
            }
            is.close();
            fos.close();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            end= System.currentTimeMillis();
            fos.flush();
            fos.close();
        }

        Assert.assertTrue("Should take more than 30 seconds", end - start >= 30000);
    }

    private SelectorThread newThread() {
        SelectorThread st = new SelectorThread() {
            @Override
            public void listen() throws IOException, InstantiationException {
                initEndpoint();
                final CountDownLatch latch = new CountDownLatch(1);
                controller.addStateListener(new ControllerStateListenerAdapter() {

                    @Override
                    public void onReady() {
                        enableMonitoring();
                        latch.countDown();
                    }

                    @Override
                    public void onException(Throwable e) {
                        if (latch.getCount() > 0) {
                            logger().log(Level.SEVERE, "Exception during starting the controller", e);
                            latch.countDown();
                        } else {
                            logger().log(Level.SEVERE, "Exception during " +
                                    "controller processing", e);
                        }
                    }
                });

                start();

                try {
                    latch.await();
                } catch (InterruptedException ex) {
                }

                if (!controller.isStarted()) {
                    throw new IllegalStateException("Controller is not started!");
                }
            }
        };
        st.setPort(PORT);
        st.setKeepAliveTimeoutInSeconds(10);

        return st;
    }
}
