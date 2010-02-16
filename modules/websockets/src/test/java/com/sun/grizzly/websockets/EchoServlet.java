package com.sun.grizzly.websockets;

import com.sun.grizzly.util.buf.ByteChunk;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
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
    }

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        try {
            ByteChunk chunk = readBytes(request);
            if (chunk.getLength() > 0) {
                final ServletOutputStream outputStream = response.getOutputStream();
                outputStream.write(chunk.getBytes(), 0, chunk.getLength());
                outputStream.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            ByteChunk chunk = readBytes(request);
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
