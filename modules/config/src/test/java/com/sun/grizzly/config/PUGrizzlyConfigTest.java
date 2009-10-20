package com.sun.grizzly.config;

import org.testng.annotations.Test;

/**
 * Created Jan 5, 2009
 *
 * @author <a href="mailto:justin.lee@sun.com">Justin Lee</a>
 */
@Test
public class PUGrizzlyConfigTest {

/*
    public void puConfig() throws IOException, InstantiationException {
        GrizzlyConfig grizzlyConfig = null;
        
        try {
            grizzlyConfig = new GrizzlyConfig("grizzly-config-pu.xml");
            grizzlyConfig.setupNetwork();
            int count = 0;
            for (GrizzlyServiceListener listener : grizzlyConfig.getListeners()) {
                setRootFolder(listener, count++);
            }
            final String httpContent = getContent(new URL("http://localhost:38082").openConnection());
            Assert.assertEquals(httpContent, "<html><body>You've found the server on port 38082</body></html>");

            final String xProtocolContent = getXProtocolContent("localhost", 38082);
            Assert.assertEquals(xProtocolContent, "X-Protocol-Response");
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e.getMessage());
        } finally {
            if (grizzlyConfig != null) {
                grizzlyConfig.shutdownNetwork();
            }
        }
    }

    public void wrongPuConfigDoubleHttp() throws IOException, InstantiationException {
        GrizzlyConfig grizzlyConfig = null;

        boolean isIllegalState = false;
        
        try {
            grizzlyConfig = new GrizzlyConfig("grizzly-config-pu-double-http.xml");
            grizzlyConfig.setupNetwork();
        } catch (IllegalStateException e) {
            isIllegalState = true;
        } finally {
            if (grizzlyConfig != null) {
                grizzlyConfig.shutdownNetwork();
            }
        }

        Assert.assertTrue(isIllegalState,
                "Loop in protocol definition should throw IllegalStateException");
    }

    public void wrongPuConfigLoop() throws IOException, InstantiationException {
        GrizzlyConfig grizzlyConfig = null;

        boolean isIllegalState = false;
        
        try {
            grizzlyConfig = new GrizzlyConfig("grizzly-config-pu-loop.xml");
            grizzlyConfig.setupNetwork();
        } catch (IllegalStateException e) {
            isIllegalState = true;
        } finally {
            if (grizzlyConfig != null) {
                grizzlyConfig.shutdownNetwork();
            }
        }

        Assert.assertTrue(isIllegalState,
                "Double http definition should throw IllegalStateException");
    }
        
    private String getContent(URLConnection connection) throws IOException {
        final InputStream inputStream = connection.getInputStream();
        InputStreamReader reader = new InputStreamReader(inputStream);
        StringBuilder builder = new StringBuilder();
        char[] buffer = new char[1024];
        int read;
        while ((read = reader.read(buffer)) != -1) {
            builder.append(buffer, 0, read);
        }

        return builder.toString();
    }

    private void setRootFolder(GrizzlyServiceListener listener, int count) throws IOException {
        final NIOTransport http = listener.getTransport();
        final StaticResourcesAdapter adapter = (StaticResourcesAdapter) http.getConfig().getAdapter();
        final String name = System.getProperty("java.io.tmpdir", "/tmp") + "/grizzly-config-root" + count;
        File dir = new File(name);
        dir.mkdirs();
        final FileWriter writer = new FileWriter(new File(dir, "index.html"));
        writer.write("<html><body>You've found the server on port " + http.getPort() + "</body></html>");
        writer.flush();
        writer.close();
        adapter.setRootFolder(name);
    }

    private String getXProtocolContent(String host, int port) throws IOException {
        Socket s = null;
        OutputStream os = null;
        InputStream is = null;
        ByteArrayOutputStream baos = null;
        
        try {
            s = new Socket(host, port);
            os = s.getOutputStream();
            os.write("X-protocol".getBytes());
            os.flush();


            is = s.getInputStream();
            baos = new ByteArrayOutputStream();
            int b;
            while ((b = is.read()) != -1) {
                baos.write(b);
            }
        } finally {
            close(os);
            close(is);
            close(baos);
            if (s != null) {
                try {
                    s.close();
                } catch (IOException e) {}
            }
        }

        return new String(baos.toByteArray());
    }

    private void close(Closeable c) {
        if (c != null) {
            try {
                c.close();
            } catch (IOException e) {}
        }
    }
*/
}
