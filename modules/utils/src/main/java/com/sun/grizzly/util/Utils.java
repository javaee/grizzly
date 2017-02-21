/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2011 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */

package com.sun.grizzly.util;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class contains set of useful operations commonly used in the framework
 *
 * @author Alexey Stashok
 * @author Jean-Francois Arcand
 */
public class Utils {
    public static boolean VERBOSE_TESTS = false;
    
    private static final int MAX_SPIN_RECOVER_CYCLES = 10;

    /**
     * Lookup a {@link Charset} by name.
     * Fixes Charset concurrency issue (http://paul.vox.com/library/post/the-mysteries-of-java-character-set-performance.html)
     *
     * @param charsetName
     * @return {@link Charset}
     * 
     * @deprecated use {@link Charsets#lookupCharset(java.lang.String)}
     */
    public static Charset lookupCharset(String charsetName) {
        return Charsets.lookupCharset(charsetName);
    }

    /**
     * Character translation tables.
     */
    private static final byte[] toLower = new byte[256];

    /**
     * Initialize character translation and type tables.
     */
    static {
        for (int i = 0; i < 256; i++) {
            toLower[i] = (byte) i;
        }

        for (int lc = 'a'; lc <= 'z'; lc++) {
            int uc = lc + 'A' - 'a';
            toLower[uc] = (byte) lc;
        }
    }

    public static Selector openSelector() throws IOException {
        try {
            return Selector.open();
        } catch (NullPointerException e) {
            // This is a JDK issue http://bugs.sun.com/view_bug.do?bug_id=6427854
            // Try 5 times and abort
            for (int i = 0; i < 5; i++) {
                try {
                    return Selector.open();
                } catch (NullPointerException e2) {
                }
            }

            String msg = e.getMessage();
            throw new IOException("Can not open Selector" + (msg == null ? "" : msg));
        }
    }

    /**
     * Method reads data from {@link SelectableChannel} to
     * {@link ByteBuffer}. If data is not immediately available - channel
     * will be reregistered on temporary {@link Selector} and wait maximum
     * readTimeout milliseconds for data.
     *
     * @param channel     {@link SelectableChannel} to read data from
     * @param byteBuffer  {@link ByteBuffer} to store read data to
     * @param readTimeout maximum time in millis operation will wait for
     *                    incoming data
     * @return number of bytes were read
     * @throws <code>IOException</code> if any error was occured during read
     */
    public static Result readWithTemporarySelector(SelectableChannel channel,
            ByteBuffer byteBuffer, long readTimeout) throws IOException {
        int count = 1;
        int byteRead = 0;
        int preReadInputBBPos = byteBuffer.position();
        Selector readSelector = null;
        SelectionKey tmpKey = null;

        Result r = new Result(0, false);
        try {
            ReadableByteChannel readableChannel = (ReadableByteChannel) channel;
            while (count > 0) {
                count = readableChannel.read(byteBuffer);
                if (count > -1) {
                    byteRead += count;
                } else if (count == -1 && byteRead == 0) {
                    byteRead = -1;
                }
            }

            if (byteRead >= 0 && byteBuffer.position() == preReadInputBBPos) {
                int recoveringAfterSelectorSpinCounter = 0;
                boolean recoveringAfterSelectorSpin;
                
                do {
                    recoveringAfterSelectorSpin = false;
                    
                    readSelector = SelectorFactory.getSelector();

                    if (readSelector == null) {
                        return r;

                    }
                    count = 1;

                    tmpKey = channel.register(readSelector, SelectionKey.OP_READ);
                    tmpKey.interestOps(tmpKey.interestOps() | SelectionKey.OP_READ);
                    long t1 = System.currentTimeMillis();
                    int code = readSelector.select(readTimeout);
                    long diff = System.currentTimeMillis() - t1;

                    tmpKey.interestOps(
                            tmpKey.interestOps() & ~SelectionKey.OP_READ);

                    if (code == 0) {
                        return r;
                    }

                    while (count > 0) {
                        count = readableChannel.read(byteBuffer);
                        if (count == 0 && byteRead == 0) {
                            recoveringAfterSelectorSpin = true;
                            recoveringAfterSelectorSpinCounter++;
                            
                            LoggerUtils.getLogger().log(Level.WARNING,
                                    "Selector spin encountered. Even though selector reported that an NIO channel is ready for read - the read operation returned 0 bytes. "
                                    + "selector:{0}, select-waited:{1}millis, select-returned:{2}, keys:{3}, selectedKeys:{4}, key:{5}, isValid:{6}."
                                    + "Now recovering, attempt #{7}...",
                                    new Object[] {readSelector, diff, code, readSelector.keys(), readSelector.selectedKeys(), tmpKey, tmpKey.isValid(), recoveringAfterSelectorSpinCounter});

                            tmpKey.cancel();
                            try {
                                readSelector.close();
                            } catch (IOException e) {
                                // ignoring
                            }
                        }
                        if (count > -1) {
                            byteRead += count;
                        } else if (count == -1 && byteRead == 0) {
                            byteRead = -1;
                        }
                    }
                } while (recoveringAfterSelectorSpin && recoveringAfterSelectorSpinCounter < MAX_SPIN_RECOVER_CYCLES);
                
            } else if (byteRead == 0 && byteBuffer.position() != preReadInputBBPos) {
                byteRead += byteBuffer.position() - preReadInputBBPos;
            }
        } finally {
            if (tmpKey != null) {
                tmpKey.cancel();
            }

            if (readSelector != null) {
                // Bug 6403933
                SelectorFactory.selectNowAndReturnSelector(readSelector);
            }
        }

        if (count == -1 || byteRead == -1) {
            r.isClosed = true;
        }
        r.bytesRead = byteRead;
        return r;
    }


    /**
     * Return the bytes contained between the startByte and the endByte. The ByteBuffer
     * will be left in the state it was before invoking that method, meaning
     * its position and limit will be the same.
     *
     * @param byteBuffer The bytes.
     * @param startByte  the first byte to look for
     * @param endByte    the second byte to look for
     * @return The byte[] contained between startByte and endByte
     */
    public static byte[] extractBytes(ByteBuffer byteBuffer,
            byte startByte, byte endByte) {

        int curPosition = byteBuffer.position();
        int curLimit = byteBuffer.limit();

        if (byteBuffer.position() == 0) {
            throw new IllegalStateException("Invalid state");
        }

        byteBuffer.position(0);
        byteBuffer.limit(curPosition);
        int state = 0;
        int start = 0;
        int end;
        try {
            byte c;

            // Rule b - try to determine the context-root
            while (byteBuffer.hasRemaining()) {
                c = byteBuffer.get();
                switch (state) {
                    case 0: // Search for first ' '
                        if (c == startByte) {
                            state = 1;
                            start = byteBuffer.position();
                        }
                        break;
                    case 1:
                        if (c == endByte) {
                            end = byteBuffer.position();
                            byte[] bytes = new byte[end - start];
                            byteBuffer.position(start);
                            byteBuffer.limit(end);
                            byteBuffer.get(bytes);
                            return bytes;
                        }
                        break;
                    default:
                        throw new IllegalArgumentException("Unexpected state");
                }
            }
            throw new IllegalStateException("Unexpected state");
        } finally {
            byteBuffer.limit(curLimit);
            byteBuffer.position(curPosition);
        }
    }


    /**
     * Specialized utility method: find a sequence of lower case bytes inside
     * a ByteBuffer.
     */
    public static int findBytes(ByteBuffer byteBuffer, byte[] b) {
        int curPosition = byteBuffer.position();
        int curLimit = byteBuffer.limit();

        if (byteBuffer.position() == 0) {
            throw new IllegalStateException("Invalid state");
        }

        byteBuffer.position(0);
        byteBuffer.limit(curPosition);
        try {
            byte first = b[0];
            int start = 0;
            int end = curPosition;

            // Look for first char 
            int srcEnd = b.length;

            for (int i = start; i <= end - srcEnd; i++) {
                if ((toLower[byteBuffer.get(i) & 0xff] & 0xff) != first) {
                    continue;
                }
                // found first char, now look for a match
                int myPos = i + 1;
                for (int srcPos = 1; srcPos < srcEnd; ) {
                    if ((toLower[byteBuffer.get(myPos++) & 0xff] & 0xff) != b[srcPos++]) {
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


    public static int findBytes(final ByteBuffer bb, Pattern pattern) {
        final int curPosition = bb.position();
        int curLimit = bb.limit();
        bb.position(0);
        bb.limit(curPosition);
        try {
            Matcher matcher = pattern.matcher(new MyCharSequence(curPosition, bb));
            if (matcher.find()) {
                return matcher.start();
            }
            return -1;
        } finally {
            bb.limit(curLimit);
            bb.position(curPosition);
        }
    }

    public static boolean isDebugVM() {
        boolean debugMode = false;
        List<String> l = ManagementFactory.getRuntimeMXBean().getInputArguments();
        for (String s : l) {
            if (s.trim().startsWith("-Xrunjdwp:") || s.contains("jdwp")) {
                debugMode = true;
                break;
            }
        }
        return debugMode;
    }

    public static void dumpOut(final Object text) {
        if (VERBOSE_TESTS) {
            System.out.println(text);
        }
    }

    public static void dumpErr(final Object text) {
        if (VERBOSE_TESTS) {
            System.err.println(text);
        }
    }

    public static byte[] copy(final byte[] src) {
        byte[] copy = null;
        if (src != null) {
            copy = new byte[src.length];
            System.arraycopy(src, 0, copy, 0, src.length);
        }
        return copy;
    }

    public static List<String> toHexStrings(byte[] bytes) {
        return toHexStrings(bytes, 0, bytes.length);
    }

    public static List<String> toHexStrings(byte[] bytes, int start, int end) {
        List<String> list = new ArrayList<String>();
        for (int i = start; i < end; i++) {
            list.add(Integer.toHexString(bytes[i] & 0xFF).toUpperCase());
        }
        return list;
    }

    private static class MyCharSequence implements CharSequence {
        private final int curPosition;
        private final ByteBuffer bb;

        public MyCharSequence(int curPosition, ByteBuffer bb) {
            this.curPosition = curPosition;
            this.bb = bb;
        }

        public int length() {
            return curPosition;
        }

        public char charAt(int index) {
            return (char) bb.get(index);
        }

        public CharSequence subSequence(int start, int end) {
            throw new UnsupportedOperationException();
        }
    }


    public static class Result {
        public int bytesRead = 0;
        public boolean isClosed = false;

        public Result(int i, boolean b) {
            bytesRead = i;
            isClosed = b;
        }
    }
}
