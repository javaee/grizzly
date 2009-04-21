/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */
package com.sun.grizzly.config;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;

import com.sun.grizzly.util.buf.Ascii;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.HexUtils;
import com.sun.grizzly.util.buf.MessageBytes;

/**
 * Utility class for parsing ByteBuffer
 *
 * @author Jeanfrancois
 */
public class HttpUtils {
    private static final int MAX_CONTEXT_ROOT_LENGTH = 2048;
    private final static String CSS =
        "H1 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:22px;} " +
            "H2 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:16px;} " +
            "H3 {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;font-size:14px;} " +
            "BODY {font-family:Tahoma,Arial,sans-serif;color:black;background-color:white;} " +
            "B {font-family:Tahoma,Arial,sans-serif;color:white;background-color:#525D76;} " +
            "P {font-family:Tahoma,Arial,sans-serif;background:white;color:black;font-size:12px;}" +
            "A {color : black;}" +
            "HR {color : #525D76;}";
    private static final int START_PARAM_IDX = 0;
    private static final int SEPARATOR_POS_PARAM_IDX = 2;
    private static final int C_PARAM_IDX = 3;
    private static final int PREV_PARAM_IDX = 4;

    public static byte[] readRequestLine(final SelectionKey selectionKey,
        final HttpParserState state, final int timeout) throws IOException {
        int readBytes;
        final ByteBuffer byteBuffer = state.getBuffer();
        do {
            final byte[] contextRoot = findContextRoot(state);
            if (contextRoot == null) {
                if (byteBuffer.position() > MAX_CONTEXT_ROOT_LENGTH ||
                    (readBytes = GrizzlyUtils.readToWorkerThreadBuffers(selectionKey,
                        timeout)) <= 0) {
                    return null;
                }
            } else {
                return contextRoot;
            }

        } while (readBytes > 0);
        return null;
    }

    public static byte[] readHost(final SelectionKey selectionKey,
        final HttpParserState state, final int timeout) throws IOException {
        int readBytes;
        final ByteBuffer byteBuffer = state.getBuffer();
        do {
            final byte[] host = findHost(state);
            if (!state.isCompleted() && host == null) {
                // TODO: We must parse until /r/n/r/n
                if (byteBuffer.position() > MAX_CONTEXT_ROOT_LENGTH ||
                    (readBytes = GrizzlyUtils.readToWorkerThreadBuffers(selectionKey,
                        timeout)) <= 0) {
                    return null;
                }
            } else {
                return host;
            }

        } while (readBytes > 0);
        return null;
    }

    /**
     * Parse the raw request line and return the context-root, without any query parameters.
     */
    public static byte[] findContextRoot(final HttpParserState lastState) {
        final ByteBuffer byteBuffer = lastState.getBuffer();
        final int curPosition = byteBuffer.position();
        final int curLimit = byteBuffer.limit();
        if (byteBuffer.position() == 0) {
            return null;
        }
        byteBuffer.flip();
        byteBuffer.position(lastState.getPosition());
        try {
            byte c = (byte) lastState.getStateParameter(C_PARAM_IDX, -1);
            byte prev = (byte) lastState.getStateParameter(PREV_PARAM_IDX, -1);
            byte prevPrev;
            // Rule b - try to determine the context-root
            while (byteBuffer.hasRemaining()) {
                prevPrev = prev;
                prev = c;
                c = byteBuffer.get();
                lastState.setPosition(byteBuffer.position());
                // State Machine
                // SPACE may be a sequence of consecutve ' '
                // 0 - Search for the first SPACE between the method and the
                //     the request URI
                // 1 - Search for the second SPACE between the request URI
                //     and the method
                switch (lastState.getState()) {
                    case 0: // Search for first ' '
                        if (c == 0x20) {
                            lastState.setState(1);
                            lastState.setStateParameter(START_PARAM_IDX,
                                byteBuffer.position());
                        }
                        break;
                    case 1: // Search for next valid '/', and then ' '
                        // whitespace tolerance
                        if (prev == 0x20 && c == 0x20) {
                            lastState.setStateParameter(START_PARAM_IDX,
                                byteBuffer.position());
                        } else if (c == 0x2f) {
                            // check for '://', which we should skip
                            if (prev == 0x2f && prevPrev == 0x3a) {
                                lastState.setStateParameter(START_PARAM_IDX, -1);
                            } else if (lastState.getStateParameter(START_PARAM_IDX) == -1) {
                                lastState.setStateParameter(START_PARAM_IDX,
                                    byteBuffer.position());
                                lastState.setStateParameter(SEPARATOR_POS_PARAM_IDX, -1);
                            } else if (lastState.getStateParameter(SEPARATOR_POS_PARAM_IDX) < 0) {
                                if (byteBuffer.position() != lastState.getStateParameter(START_PARAM_IDX) + 1) {
                                    lastState.setStateParameter(SEPARATOR_POS_PARAM_IDX, byteBuffer.position() - 1);
                                } else {
                                    lastState.setStateParameter(START_PARAM_IDX,
                                        byteBuffer.position());
                                }
                            }
                            // Search for ? , space ' ' or /
                        } else if (c == 0x20 || c == 0x3b || c == 0x3f) {
                            // Grab the first '/'
                            final int start = lastState.getStateParameter(START_PARAM_IDX) - 1;
                            final int end = byteBuffer.position() - 1;
                            byteBuffer.position(start);
                            byteBuffer.limit(end);
                            final byte[] contextRoot = new byte[end - start];
                            byteBuffer.get(contextRoot);
                            return contextRoot;
                        }
                        break;
                    default:
                        return null;
                }
            }
            return null;
        } finally {
            byteBuffer.limit(curLimit);
            byteBuffer.position(curPosition);
        }
    }

    /**
     * Return the host value, or null if not found.
     */
    public static byte[] findHost(final HttpParserState lastState) {
        final ByteBuffer byteBuffer = lastState.getBuffer();
        final int curPosition = byteBuffer.position();
        final int curLimit = byteBuffer.limit();
        // Rule a - If nothing, return to the Selector.
        if (byteBuffer.position() == lastState.getPosition()) {
            return null;
        }
        byteBuffer.position(lastState.getPosition());
        byteBuffer.limit(curPosition);
        try {
            byte c;
            int rnCounter = 0;
            // Rule b - try to determine the host header
            while (byteBuffer.hasRemaining()) {
                c = (byte) Ascii.toLower(byteBuffer.get());
                if (c == '\r' || c == '\n') {
                    if (++rnCounter == 4) {
                        // End of HTTP header reached
                        lastState.setCompleted(true);
                        return null;
                    }
                } else {
                    rnCounter = 0;
                    lastState.setPosition(byteBuffer.position());
                }
                switch (lastState.getState()) {
                    case 0: // Search for first 'h'
                        if (c == 0x68) {
                            lastState.setState(1);
                        } else {
                            lastState.setState(0);
                        }
                        break;
                    case 1: // Search for next 'o'
                        if (c == 0x6f) {
                            lastState.setState(2);
                        } else {
                            lastState.setState(0);
                        }
                        break;
                    case 2: // Search for next 's'
                        if (c == 0x73) {
                            lastState.setState(3);
                        } else {
                            lastState.setState(0);
                        }
                        break;
                    case 3: // Search for next 't'
                        if (c == 0x74) {
                            lastState.setState(4);
                        } else {
                            lastState.setState(0);
                        }
                        break;
                    case 4: // Search for next ':'
                        if (c == 0x3a) {
                            lastState.setState(5);
                            lastState.setStateParameter(START_PARAM_IDX,
                                byteBuffer.position() + 1);
                        } else {
                            lastState.setState(0);
                        }
                        break;
                    case 5: // Get the Host
                        while (c != 0x3a && c != 0x0d && c != 0x0a) {
                            if (byteBuffer.hasRemaining()) {
                                c = byteBuffer.get();
                                lastState.setPosition(byteBuffer.position());
                            } else {
                                // Whole host value is not in ByteBuffer yet
                                return null;
                            }
                        }
                        final int startPos = lastState.getStateParameter(START_PARAM_IDX);
                        final int endPos = byteBuffer.position() - 1;
                        final byte[] host = new byte[endPos - startPos];
                        byteBuffer.position(startPos);
                        byteBuffer.limit(endPos);
                        byteBuffer.get(host);
                        lastState.setCompleted(true);
                        return host;
                    default:
                        throw new IllegalArgumentException("Unexpected state");
                }
            }
            return null;
        } finally {
            byteBuffer.limit(curLimit);
            byteBuffer.position(curPosition);
        }
    }

    /**
     * Specialized utility method: find a sequence of lower case bytes inside a {@link ByteBuffer}.
     */
    public static int findBytes(final ByteBuffer byteBuffer, final byte[] b) {
        final int curPosition = byteBuffer.position();
        final int curLimit = byteBuffer.limit();
        if (byteBuffer.position() == 0) {
            throw new IllegalStateException("Invalid state");
        }
        byteBuffer.position(0);
        byteBuffer.limit(curPosition);
        try {
            final byte first = b[0];
            final int start = 0;
            final int end = curPosition;
            // Look for first char
            final int srcEnd = b.length;
            for (int i = start; i <= (end - srcEnd); i++) {
                if (Ascii.toLower(byteBuffer.get(i)) != first) {
                    continue;
                }
                // found first char, now look for a match
                int myPos = i + 1;
                for (int srcPos = 1; srcPos < srcEnd;) {
                    if (Ascii.toLower(byteBuffer.get(myPos++)) != b[srcPos++]) {
                        break;
                    }
                    if (srcPos == srcEnd) {
                        return i - start; // found it
                    }
                }
            }
            return -1;
        } finally {
            byteBuffer.limit(curLimit);
            byteBuffer.position(curPosition);
        }
    }

    /**
     * Parse host.
     */
    public static void parseHost(final MessageBytes hostMB, final Socket socket) throws IOException {
        if (hostMB == null || hostMB.isNull()) {
            // HTTP/1.0
            // Default is what the socket tells us. Overriden if a host is
            // found/parsed
            final InetAddress localAddress = socket.getLocalAddress();
            final byte[] host = localAddress.getHostName().getBytes();
            hostMB.setBytes(host, 0, host.length);
            return;
        }
        final ByteChunk valueBC = hostMB.getByteChunk();
        final byte[] valueB = valueBC.getBytes();
        final int valueL = valueBC.getLength();
        final int valueS = valueBC.getStart();
        int colonPos = -1;
        // TODO: Cache instance?
        char[] hostNameC = new char[0];
        if (hostNameC.length < valueL) {
            hostNameC = new char[valueL];
        }
        final boolean ipv6 = valueB[valueS] == '[';
        boolean bracketClosed = false;
        for (int i = 0; i < valueL; i++) {
            final char b = (char) valueB[i + valueS];
            hostNameC[i] = b;
            if (b == ']') {
                bracketClosed = true;
            } else if (b == ':') {
                if (!ipv6 || bracketClosed) {
                    colonPos = i;
                    break;
                }
            }
        }
        if (colonPos < 0) {
            hostMB.setChars(hostNameC, 0, valueL);
        } else {
            hostMB.setChars(hostNameC, 0, colonPos);
            int port = 0;
            int mult = 1;
            for (int i = valueL - 1; i > colonPos; i--) {
                final int charValue = HexUtils.DEC[(int) valueB[i + valueS]];
                if (charValue == -1) {
                    throw new IOException("Invalid Host");
                }
                port += charValue * mult;
                mult *= 10;
            }
        }
    }

    public static byte[] getErrorPage(final String serverName, final String message) {
        final StringBuilder sb = new StringBuilder();
        sb.append("<html><head><title>");
        sb.append(serverName);
        sb.append("</title>");
        sb.append("<style><!--");
        sb.append(CSS);
        sb.append("--></style> ");
        sb.append("</head><body>");
        sb.append("<h1>");
        sb.append(message);
        sb.append("</h1>");
        sb.append("</h3> type Status report<br>message<br>description Not Found</h3>");
        sb.append("<HR size=\"1\" noshade>");
        sb.append("<h3>").append(serverName).append("</h3>");
        sb.append("</body></html>");
        return sb.toString().getBytes();
    }

}

