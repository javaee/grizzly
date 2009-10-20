package com.sun.grizzly.config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URLConnection;

import com.sun.grizzly.tcp.StaticResourcesAdapter;
import org.jvnet.hk2.config.Dom;
import org.testng.Assert;

public class BaseGrizzlyConfigTest {
    protected String getContent(URLConnection connection) {
        try {
            InputStream inputStream;
            inputStream = connection.getInputStream();
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
            Assert.fail(e.getMessage(), e);
        }
        
        return "";
    }

    protected void setRootFolder(GrizzlyServiceListener listener, int count) {
        final StaticResourcesAdapter adapter = (StaticResourcesAdapter) listener.getWebFilterConfig().getAdapter();
        final String name = System.getProperty("java.io.tmpdir", "/tmp") + "/"
            + Dom.convertName(getClass().getSimpleName()) + count;
        File dir = new File(name);
        dir.mkdirs();
        FileWriter writer;
        try {
            writer = new FileWriter(new File(dir, "index.html"));
            try {
                writer.write("<html><body>You've found the server on port " + listener.getPort() + "</body></html>");
                writer.flush();
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
        } catch (IOException e) {
            Assert.fail(e.getMessage(), e);
        }
        adapter.setRootFolder(name);
    }
}
