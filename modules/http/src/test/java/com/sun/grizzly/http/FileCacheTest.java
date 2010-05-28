package com.sun.grizzly.http;

import com.sun.grizzly.Controller;
import com.sun.grizzly.http.utils.SelectorThreadUtils;
import com.sun.grizzly.tcp.StaticResourcesAdapter;
import junit.framework.Assert;
import junit.framework.TestCase;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class FileCacheTest extends TestCase {
    private static final int PORT = 50000;
    private List<SelectorThread> threads = new ArrayList<SelectorThread>();
    private Set<InetAddress> addresses = new HashSet<InetAddress>();

    /**
     * Sets up the fixture, for example, open a network connection.
     * This method is called before a test is executed.
     */
    @Override
    protected void setUp() throws Exception {
        InetAddress local = InetAddress.getLocalHost();
        addresses.add(local);
        final Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        while(interfaces.hasMoreElements()) {
            final Enumeration<InetAddress> inetAddresses = interfaces.nextElement().getInetAddresses();
            while(inetAddresses.hasMoreElements()) {
                final InetAddress address = inetAddresses.nextElement();
                if(address instanceof Inet4Address && address.isReachable(2000)) {
                    addresses.add(address);
                }
            }
        }

        System.out.println("**************** FileCacheTest.setUp: addresses = " + addresses);

        if (addresses.size() > 1) {
            for (InetAddress address : addresses) {
                threads.add(setUpThread(address)) ;
            }
        }
    }

    /**
     * Tears down the fixture, for example, close a network connection.
     * This method is called after a test is executed.
     */
    @Override
    protected void tearDown() throws Exception {
        for (SelectorThread thread : threads) {
            thread.stopEndpoint();
        }
    }

    public void testCache() throws IOException {
        int count = 100;
        while (count-- > 0) {
            if (count % 10 == 0) {
                System.out.printf("Running test: %s\n", count);
            }
            for (SelectorThread thread : threads) {
                checkContent(thread.getAddress());
            }
        }
    }

    private void checkContent(final InetAddress host) throws IOException {
        final String address = host.getCanonicalHostName();
        final URLConnection urlConnection = new URL("http://" + address + ":" + PORT).openConnection();
        final String got = getContent(urlConnection);
        final String expected = String.format("<html><body>You've found the %s server</body></html>", address);
        System.out.println("FileCacheTest.checkContent: host = " + host);
        System.out.println("FileCacheTest.checkContent: expected = " + expected);
        System.out.println("FileCacheTest.checkContent: got      = " + got);
        Assert.assertEquals(got, expected);
    }

    protected String getContent(URLConnection connection) {
        try {
            InputStream inputStream = connection.getInputStream();
            InputStreamReader reader = new InputStreamReader(inputStream);
            try {
                StringBuilder builder = new StringBuilder();
                char[] buffer = new char[1024];
                int read;
                while ((read = reader.read(buffer)) != -1) {
                    builder.append(buffer, 0, read);
                }
                return builder.toString();
            } finally {
                if (reader != null) {
                    reader.close();
                }
                if (inputStream != null) {
                    inputStream.close();
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
            Assert.fail(e.getMessage());
        }

        return "";
    }

    private SelectorThread setUpThread(final InetAddress address) throws IOException {
        final SelectorThread thread = new SelectorThread();
        thread.setController(new Controller());
        thread.setDisplayConfiguration(true);
        thread.setAddress(address);
        thread.setPort(PORT);
        thread.setFileCacheIsEnabled(true);
        setRootFolder(thread, address);
        SelectorThreadUtils.startSelectorThread(thread);

        return thread;
    }

    protected void setRootFolder(SelectorThread thread, InetAddress address) {
        final String host = address.getCanonicalHostName();
        final String path = System.getProperty("java.io.tmpdir", "/tmp") + "/filecachetest-" + host;
        File dir = new File(path);
        dir.mkdirs();

        final StaticResourcesAdapter adapter = new StaticResourcesAdapter(path);
        FileWriter writer;
        try {
            writer = new FileWriter(new File(dir, "index.html"));
            try {
                writer.write("<html><body>You've found the " + host + " server</body></html>");
                writer.flush();
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
        } catch (IOException e) {
            Assert.fail(e.getMessage());
        }
        thread.setAdapter(adapter);
    }

}
