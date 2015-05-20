/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.util;

import java.nio.charset.Charset;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.Buffers;

/**
 * {@link Buffer} chunk representation.
 * Helps HTTP module to avoid redundant String creation.
 *
 * @author Alexey Stashok
 */
public class BufferChunk implements Chunk {
    /** Default encoding used to convert to strings. It should be UTF8,
	as most standards seem to converge, but the servlet API requires
	8859_1, and this object is used mostly for servlets.
    */
    private static final Charset DEFAULT_CHARSET = Constants.DEFAULT_HTTP_CHARSET;

    private Buffer buffer;

    private int start;
    private int end;

    // the last byte available for this BufferChunk
    private int limit;
    
    String cachedString;
    Charset cachedStringCharset;

    public void setBufferChunk(final Buffer buffer,
                               final int start,
                               final int end) {
        setBufferChunk(buffer, start, end, end);
    }

    public void setBufferChunk(final Buffer buffer,
                               final int start,
                               final int end,
                               final int limit) {
        this.buffer = buffer;
        this.start = start;
        this.end = end;
        this.limit = limit;
        resetStringCache();
    }

    public Buffer getBuffer() {
        return buffer;
    }

    public void setBuffer(Buffer buffer) {
        this.buffer = buffer;
        resetStringCache();
    }

    @Override
    public int getStart() {
        return start;
    }

    @Override
    public void setStart(int start) {
        this.start = start;
        resetStringCache();
    }

    @Override
    public int getEnd() {
        return end;
    }

    @Override
    public void setEnd(int end) {
        this.end = end;
        resetStringCache();
    }

    @Override
    public final int getLength() {
        return end - start;
    }

    public final boolean isNull() {
        return buffer == null;
    }

    public void allocate(final int size) {
        if (isNull() || (limit - start) < size) {
            setBufferChunk(Buffers.wrap(null, new byte[size]), 0, 0, size);
        }

        end = start;
    }

    @Override
    public void delete(final int start, final int end) {
        final int absDeleteStart = this.start + start;
        final int absDeleteEnd = this.start + end;

        final int diff = this.end - absDeleteEnd;
        if (diff == 0) {
            this.end = absDeleteStart;
        } else {
            final int oldPos = buffer.position();
            final int oldLim = buffer.limit();

            try {
                Buffers.setPositionLimit(buffer, absDeleteStart, absDeleteStart + diff);
                // we have to duplicate the buffer as, depending on the memory
                // manager, it may be an error to pass a buffer back to itself.
                final Buffer duplicate = buffer.duplicate();
                buffer.put(duplicate, absDeleteEnd, diff);
                this.end = absDeleteStart + diff;
            } finally {
                Buffers.setPositionLimit(buffer, oldPos, oldLim);
            }
        }

        resetStringCache();
    }
    
    public void append(final BufferChunk bc) {
        final int oldPos = buffer.position();
        final int oldLim = buffer.limit();
        final int srcLen = bc.getLength()
                ;
        Buffers.setPositionLimit(buffer, end, end + srcLen);
        buffer.put(bc.getBuffer(), bc.getStart(), srcLen);
        Buffers.setPositionLimit(buffer, oldPos, oldLim);
        end += srcLen;
    }

    @Override
    public final int indexOf(final char c, final int fromIndex) {
        final int idx = indexOf(buffer, start + fromIndex, end, c);
        return idx >= start ? idx - start : -1;
    }


    @Override
    public final int indexOf(final String s, final int fromIndex) {
        final int idx = indexOf(buffer, start + fromIndex, end, s);
        return idx >= start ? idx - start : -1;
    }

    boolean startsWith(String s, int pos) {
        final int len = s.length();
        if (len > getLength() - pos) {
            return false;
        }

        int off = start + pos;
        for (int i = 0; i < len; i++) {
            if (buffer.get(off++) != s.charAt(i)) {
                return false;
            }
        }

        return true;
    }

    public boolean startsWithIgnoreCase(String s, int pos) {
        final int len = s.length();
        if (len > getLength() - pos) {
            return false;
        }

        int off = start + pos;
        for (int i = 0; i < len; i++) {
            if (Ascii.toLower(buffer.get(off++)) != Ascii.toLower(s.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns the starting index of the specified byte sequence within this
     * <code>Buffer</code>.
     *
     * @param b byte sequence to search for.
     *
     * @return the starting index of the specified byte sequence within this
     *  <code>Buffer</code>
     */
    public int findBytesAscii(byte[] b) {

        final byte first = b[0];
        final int from = getStart();
        final int to = getEnd();

        // Look for first char
        int srcEnd = b.length;

        for (int i = from; i <= to - srcEnd; i++) {
            if (Ascii.toLower(buffer.get(i)) != first) continue;
            // found first char, now look for a match
            int myPos = i + 1;
            for (int srcPos = 1; srcPos < srcEnd;) {
                if (Ascii.toLower(buffer.get(myPos++)) != b[srcPos++]) {
                    break;
                }
                if (srcPos == srcEnd) {
                    return i - from; // found it
                }
            }
        }
        
        return -1;
    }

    @Override
    public int hashCode() {
        return hash();
    }
    
    public int hash() {
        int code=0;
        for (int i = start; i < end; i++) {
            code = code * 31 + buffer.get(i);
        }
        return code;
    }

    @Override
    public boolean equals(final Object o) {
        if (!(o instanceof BufferChunk)) {
            return false;
        }
        
        final BufferChunk anotherBC = (BufferChunk) o;
        
        final int len = getLength();
        
        if (len != anotherBC.getLength()) {
            return false;
        }

        int offs1 = start;
        int offs2 = anotherBC.start;
        
        for (int i = 0; i < len; i++) {
            if (buffer.get(offs1++) != anotherBC.buffer.get(offs2++)) {
                return false;
            }
        }

        return true;
    }
    
    public boolean equals(CharSequence s) {
        if (getLength() != s.length()) {
            return false;
        }

        for (int i = start; i < end; i++) {
            if (buffer.get(i) != s.charAt(i - start)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Compares the message Buffer to the specified byte array.

     * @param b the <code>byte[]</code> to compare
     *
     * @return true if the comparison succeeded, false otherwise
     *
     * @since 2.3
     */
    public boolean equals(final byte[] b) {
        return equals(b, 0, b.length);
    }
    
    /**
     * Compares the message Buffer to the specified byte array.

     * @param b the <code>byte[]</code> to compare
     * @param offset the offset in the array
     * @param len number of bytes to check
     * 
     * @return true if the comparison succeeded, false otherwise
     *
     * @since 2.3
     */
    public boolean equals(final byte[] b, int offset, final int len) {
        if (getLength() != len) {
            return false;
        }

        for (int i = start; i < end; i++) {
            if (buffer.get(i) != b[offset++]) {
                return false;
            }
        }

        return true;
    }

    public static boolean equals(final byte[] c, final int cOff, final int cLen,
                                 final Buffer t, final int tOff, final int tLen) {
        // XXX ENCODING - this only works if encoding is UTF8-compat
        // ( ok for tomcat, where we compare ascii - header names, etc )!!!

        if (cLen != tLen) {
            return false;
        }

        if (c == null || t == null) {
            return false;
        }

        for (int i = 0; i < cLen; i++) {
            if (c[i + cOff] != t.get(i + tOff)) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Compares the message Buffer to the specified char array.

     * @param b the <code>char[]</code> to compare
     * @param offset the offset in the array
     * @param len number of chars to check
     * 
     * @return true if the comparison succeeded, false otherwise
     *
     * @since 2.3
     */
    public boolean equals(final char[] b, int offset, final int len) {
        if (getLength() != len) {
            return false;
        }

        for (int i = start; i < end; i++) {
            if (buffer.get(i) != b[offset++]) {
                return false;
            }
        }

        return true;
    }
    
    public boolean equalsIgnoreCase(final Object o) {
        if (!(o instanceof BufferChunk)) {
            return false;
        }
        
        final BufferChunk anotherBC = (BufferChunk) o;
        
        final int len = getLength();
        
        if (len != anotherBC.getLength()) {
            return false;
        }

        int offs1 = start;
        int offs2 = anotherBC.start;
        
        for (int i = 0; i < len; i++) {
            if (Ascii.toLower(buffer.get(offs1++)) != Ascii.toLower(anotherBC.buffer.get(offs2++))) {
                return false;
            }
        }

        return true;
    }
    
    public boolean equalsIgnoreCase(CharSequence s) {
        if (getLength() != s.length()) {
            return false;
        }

        for (int i = start; i < end; i++) {
            if (Ascii.toLower(buffer.get(i)) != Ascii.toLower(s.charAt(i - start))) {
                return false;
            }
        }

        return true;
    }

    public boolean equalsIgnoreCase(final byte[] b) {
        return equalsIgnoreCase(b, 0, b.length);
    }
    
    public boolean equalsIgnoreCase(final byte[] b, final int offset, final int len) {
        if (getLength() != len) {
            return false;
        }

        int offs1 = start;
        int offs2 = offset;
        
        for (int i = 0; i < len; i++) {
            if (Ascii.toLower(buffer.get(offs1++)) != Ascii.toLower(b[offs2++])) {
                return false;
            }
        }

        return true;
    }
    
    /**
     * Compares the message Buffer to the specified char array ignoring case considerations.

     * @param b the <code>char[]</code> to compare
     * @param offset the offset in the array
     * @param len number of chars to check
     * 
     * @return true if the comparison succeeded, false otherwise
     *
     * @since 2.3
     */
    public boolean equalsIgnoreCase(final char[] b, int offset, final int len) {
        if (getLength() != len) {
            return false;
        }

        for (int i = start; i < end; i++) {
            if (Ascii.toLower(buffer.get(i)) != Ascii.toLower(b[offset++])) {
                return false;
            }
        }

        return true;
    }
    
    /**
     * Compares the buffer chunk to the specified byte array representing
     * lower-case ASCII characters.
     *
     * @param b the <code>byte[]</code> to compare
     *
     * @return true if the comparison succeeded, false otherwise
     *
     * @since 2.1.2
     */
    public boolean equalsIgnoreCaseLowerCase(final byte[] b) {
        return equalsIgnoreCaseLowerCase(buffer, start, end, b);
    }

    @Override
    public String toString() {
        return toString(null);
    }

    public String toString(Charset charset) {
        if (charset == null) charset = DEFAULT_CHARSET;

        if (cachedString != null && charset.equals(cachedStringCharset)) {
            return cachedString;
        }

        cachedString = buffer.toStringContent(charset, start, end);

        cachedStringCharset = charset;

        return cachedString;
    }

    @Override
    public String toString(final int start, final int end) {
        return buffer.toStringContent(DEFAULT_CHARSET, this.start + start,
                this.start + end);
    }

    protected final void resetStringCache() {
        cachedString = null;
        cachedStringCharset = null;
    }
    
    protected final void reset() {
        buffer = null;        
        start = -1;
        end = -1;
        limit = -1;
        resetStringCache();
    }
    
    public final void recycle() {
        reset();
    }

    /**
     * Notify the Chunk that its content is going to be changed directly
     */
    protected void notifyDirectUpdate() {
    }

    public static int indexOf(final Buffer buffer, int off, final int end, final char qq) {
        // Works only for UTF
        while (off < end) {
            final byte b = buffer.get(off);
            if (b == qq) {
                return off;
            }
            off++;
        }
        
        return -1;
    }

    public static int indexOf(final Buffer buffer, int off, final int end, final CharSequence s) {
        // Works only for UTF
        final int strLen = s.length();
        if (strLen == 0) {
            return off;
        }

        if (strLen > (end - off)) return -1;

        int strOffs = 0;
        final int lastOffs = end - strLen;

        while (off <= lastOffs + strOffs) {
            final byte b = buffer.get(off);
            if (b == s.charAt(strOffs)) {
                strOffs++;
                if (strOffs == strLen) {
                    return off - strLen + 1;
                }
            } else {
                strOffs = 0;
            }

            off++;
        }
        return -1;
    }

    /**
     * @return -1, 0 or +1 if inferior, equal, or superior to the String.
     */
    public int compareIgnoreCase(int start, int end, String compareTo) {
        int result = 0;

        int len = compareTo.length();
        if ((end - start) < len) {
            len = end - start;
        }
        for (int i = 0; (i < len) && (result == 0); i++) {
            if (Ascii.toLower(buffer.get(i + start)) > Ascii.toLower(compareTo.charAt(i))) {
                result = 1;
            } else if (Ascii.toLower(buffer.get(i + start)) < Ascii.toLower(compareTo.charAt(i))) {
                result = -1;
            }
        }
        if (result == 0) {
            if (compareTo.length() > (end - start)) {
                result = -1;
            } else if (compareTo.length() < (end - start)) {
                result = 1;
            }
        }
        return result;
    }


    /**
     * @return -1, 0 or +1 if inferior, equal, or superior to the String.
     */
    public int compare(int start, int end, String compareTo) {
        int result = 0;
        int len = compareTo.length();
        if ((end - start) < len) {
            len = end - start;
        }
        for (int i = 0; (i < len) && (result == 0); i++) {
            if (buffer.get(i + start) > compareTo.charAt(i)) {
                result = 1;
            } else if (buffer.get(i + start) < compareTo.charAt(i)) {
                result = -1;
            }
        }
        if (result == 0) {
            if (compareTo.length() > (end - start)) {
                result = -1;
            } else if (compareTo.length() < (end - start)) {
                result = 1;
            }
        }
        return result;
    }
    
    /**
     * Compares the buffer chunk to the specified byte array representing
     * lower-case ASCII characters.
     *
     * @param buffer the <code>byte[]</code> to compare
     * @param start buffer start
     * @param end buffer end
     * @param cmpTo byte[] to compare against
     *
     * @return true if the comparison succeeded, false otherwise
     *
     * @since 2.1.2
     */
    public static boolean equalsIgnoreCaseLowerCase(final Buffer buffer,
            final int start, final int end, final byte[] cmpTo) {
        final int len = end - start;
        if (len != cmpTo.length) {
            return false;
        }

        for (int i = 0; i < len; i++) {
            if (Ascii.toLower(buffer.get(i + start)) != cmpTo[i]) {
                return false;
            }
        }

        return true;        
    }
    
    public static boolean startsWith(final Buffer buffer, final int start,
            final int end, final byte[] cmpTo) {
        final int len = end - start;
        
        if (len < cmpTo.length) {
            return false;
        }

        for (int i = 0; i < cmpTo.length; i++) {
            if (buffer.get(start + i) != cmpTo[i]) {
                return false;
            }
        }

        return true;
    }    
}
