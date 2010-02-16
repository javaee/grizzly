package com.sun.grizzly.websockets;

import com.sun.grizzly.util.buf.ByteChunk;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.CharBuffer;

public class EchoServlet extends HttpServlet {
    private String contextPath;

    @Override
    public void init(ServletConfig config) throws ServletException {
        contextPath = config.getServletContext().getContextPath() + "/echo";
        WebSocketContext context = WebSocketEngine.getEngine().register(contextPath);
        context.setExpirationDelay(5 * 60 * 1000);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        doPost(req, resp);
/*
        System.out.println(new java.util.Date() + ":  WebSocketFilterTest.doGet: " + adapterCount++);
        final ServletInputStream inputStream = req.getInputStream();
        ByteArrayOutputStream bais = new ByteArrayOutputStream();
        int read;
        byte[] bytes = new byte[1024];
        while((read = inputStream.read(bytes)) > 0) {
            bais.write(bytes, 0, read);
        }

        final String echo = new String(bais.toByteArray());
        System.out.println(new java.util.Date() + ":  from client: " + echo);
        final PrintWriter writer = resp.getWriter();
        writer.write(echo);
        writer.flush();
        System.out.println(new java.util.Date() + ":  WebSocketFilterTest$EchoServlet.doGet exeunt");
*/
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            ByteChunk chunk = readBytes2(request);
            if (chunk.getLength() > 0) {
                final ServletOutputStream outputStream = response.getOutputStream();
                outputStream.write(chunk.getBytes(), 0, chunk.getLength());
                outputStream.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

    }

    private ByteChunk readBytes(HttpServletRequest request) throws IOException {
        byte[] buffer = new byte[1024];
        ByteChunk chunk = new ByteChunk();
        int read;
        final ServletInputStream inputStream = request.getInputStream();
        while ((read = inputStream.read(buffer)) > -1) {
            chunk.append(buffer, 0, read);
        }
        return chunk;
    }

    private ByteChunk readBytes2(HttpServletRequest request) throws IOException {
        final CharBuffer buffer = CharBuffer.allocate(1024);
        request.getReader().read(buffer);
        ByteChunk chunk = new ByteChunk();
        final char[] chars = buffer.array();
        for (int index = 0; index < buffer.position(); index++) {
            chunk.append(chars[index]);
        }

        return chunk;
    }
}
