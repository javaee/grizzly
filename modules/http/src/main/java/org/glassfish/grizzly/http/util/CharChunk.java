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
 *
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.glassfish.grizzly.http.util;

import java.io.CharConversionException;
import java.io.IOException;
import java.io.Serializable;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CoderResult;
import java.util.Arrays;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.utils.Charsets;

/**
 * Utilities to manipluate char chunks. While String is
 * the easiest way to manipulate chars ( search, substrings, etc),
 * it is known to not be the most efficient solution - Strings are
 * designed as imutable and secure objects.
 *
 * @author dac@sun.com
 * @author James Todd [gonzo@sun.com]
 * @author Costin Manolache
 * @author Remy Maucherat
 */
public final class CharChunk implements Chunk, Cloneable, Serializable {
    private static final UTF8Decoder UTF8_DECODER = new UTF8Decoder();
    
    /** Default encoding used to convert to strings. It should be UTF8,
	as most standards seem to converge, but the servlet API requires
	8859_1, and this object is used mostly for servlets.
    */
    public static final Charset DEFAULT_HTTP_CHARSET = Constants.DEFAULT_HTTP_CHARSET;

    private static final long serialVersionUID = -1L;

    // Input interface, used when the buffer is emptied.
    public interface CharInputChannel {
        /**
         * Read new bytes ( usually the internal conversion buffer ).
         * The implementation is allowed to ignore the parameters,
         * and mutate the chunk if it wishes to implement its own buffering.
         */
        int realReadChars(char cbuf[], int off, int len) throws IOException;
    }
    /**
     *  When we need more space we'll either
     *  grow the buffer ( up to the limit ) or send it to a channel.
     */
    public interface CharOutputChannel {
        /** Send the bytes ( usually the internal conversion buffer ).
         *  Expect 8k output if the buffer is full.
         */
        void realWriteChars(char cbuf[], int off, int len) throws IOException;
    }

    // --------------------
    // char[]
    private char buff[];

    private int start;
    private int end;

    private boolean isSet=false;  // XXX

    // -1: grow undefinitely
    // maximum amount to be cached
    private int limit=-1;

    private transient CharInputChannel in = null;
    private transient CharOutputChannel out = null;

    private boolean optimizedWrite=true;

    private String cachedString;
    
    /**
     * Creates a new, uninitialized CharChunk object.
     */
    public CharChunk() {
    }

    public CharChunk(int size) {
        allocate( size, -1 );
    }

    // --------------------

    public CharChunk getClone() {
        try {
            return (CharChunk)this.clone();
        } catch( Exception ex) {
            return null;
        }
    }

    public boolean isNull() {
        return end <= 0 && !isSet;
    }

    /**
     * Resets the message bytes to an uninitialized state.
     */
    public void recycle() {
        //	buff=null;
        isSet=false; // XXX
        start=0;
        end=0;
    }

    public void reset() {
        buff=null;
        cachedString = null;
    }

    // -------------------- Setup --------------------

    public void allocate( int initial, int limit  ) {
        boolean output = true;
        if( buff==null || buff.length < initial ) {
            buff=new char[initial];
        }
        this.limit=limit;
        start=0;
        end=0;
        output =true;
        isSet=true;
        resetStringCache();
    }

    public void ensureCapacity(final int size) {
        resetStringCache();

        if( buff==null || buff.length < size ) {
            buff=new char[size];
            limit = -1;
        }

        start=0;
        end=0;
    }

    public void setOptimizedWrite(boolean optimizedWrite) {
        this.optimizedWrite = optimizedWrite;
    }

    public void setChars( char[] c, int off, int len ) {
        buff=c;
        start=off;
        end=start + len;
        isSet=true;
        resetStringCache();
    }

    /** Maximum amount of data in this buffer.
     *
     *  If -1 or not set, the buffer will grow indefinitely.
     *  Can be smaller than the current buffer size ( which will not shrink ).
     *  When the limit is reached, the buffer will be flushed ( if out is set )
     *  or throw exception.
     */
    public void setLimit(int limit) {
        this.limit=limit;
        resetStringCache();
    }

    public int getLimit() {
        return limit;
    }

    /**
     * When the buffer is empty, read the data from the input channel.
     */
    public void setCharInputChannel(CharInputChannel in) {
        this.in = in;
    }

    /** When the buffer is full, write the data to the output channel.
     * 	Also used when large amount of data is appended.
     *
     *  If not set, the buffer will grow to the limit.
     */
    public void setCharOutputChannel(CharOutputChannel out) {
        this.out=out;
    }

    // compat
    public char[] getChars() {
        return getBuffer();
    }

    public char[] getBuffer() {
        return buff;
    }

    /**
     * Returns the start offset of the bytes.
     * For output this is the end of the buffer.
     */
    @Override
    public int getStart() {
        return start;
    }

    /**
     * Returns the start offset of the bytes.
     */
    @Override
    public void setStart(int start) {
        this.start = start;
        resetStringCache();
    }

    /**
     * Returns the length of the bytes.
     */
    @Override
    public int getLength() {
        return end-start;
    }


    @Override
    public int getEnd() {
        return end;
    }

    @Override
    public void setEnd( int i ) {
        end=i;
        resetStringCache();
    }

    // -------------------- Adding data --------------------

    public void append( char b ) throws IOException {
        makeSpace( 1 );

        // couldn't make space
        if( limit >0 && end >= limit ) {
            flushBuffer();
        }
        buff[end++]=b;
        resetStringCache();
    }

    public void append( CharChunk src ) throws IOException {
        append( src.getBuffer(), src.getStart(), src.getLength());
    }

    /** Add data to the buffer
     */
    public void append( char src[], int off, int len ) throws IOException {
        // will grow, up to limit
        resetStringCache();
        makeSpace( len );

        // if we don't have limit: makeSpace can grow as it wants
        if( limit < 0 ) {
            // assert: makeSpace made enough space
            System.arraycopy( src, off, buff, end, len );
            end+=len;
            return;
        }

        // Optimize on a common case.
        // If the source is going to fill up all the space in buffer, may
        // as well write it directly to the output, and avoid an extra copy
        if ( optimizedWrite && len == limit && end == start) {
            out.realWriteChars( src, off, len );
            return;
        }

        // if we have limit and we're below
        if( len <= limit - end ) {
            // makeSpace will grow the buffer to the limit,
            // so we have space
            System.arraycopy( src, off, buff, end, len );

            end+=len;
            return;
        }

        // need more space than we can afford, need to flush
        // buffer

        // the buffer is already at ( or bigger than ) limit

        // Optimization:
        // If len-avail < length ( i.e. after we fill the buffer with
        // what we can, the remaining will fit in the buffer ) we'll just
        // copy the first part, flush, then copy the second part - 1 write
        // and still have some space for more. We'll still have 2 writes, but
        // we write more on the first.

        if( len + end < 2 * limit ) {
            /* If the request length exceeds the size of the output buffer,
               flush the output buffer and then write the data directly.
               We can't avoid 2 writes, but we can write more on the second
            */
            int avail=limit-end;
            System.arraycopy(src, off, buff, end, avail);
            end += avail;

            flushBuffer();

            System.arraycopy(src, off+avail, buff, end, len - avail);
            end+= len - avail;

        } else {	// len > buf.length + avail
            // long write - flush the buffer and write the rest
            // directly from source
            flushBuffer();

            out.realWriteChars( src, off, len );
        }        
    }


    /** Add data to the buffer
     */
    public void append( StringBuffer sb ) throws IOException {
        resetStringCache();
        int len=sb.length();

        // will grow, up to limit
        makeSpace( len );

        // if we don't have limit: makeSpace can grow as it wants
        if( limit < 0 ) {
            // assert: makeSpace made enough space
            sb.getChars(0, len, buff, end );
            end+=len;
            return;
        }

        int off=0;
        int sbOff = off;
        int sbEnd = off + len;
        while (sbOff < sbEnd) {
            int d = min(limit - end, sbEnd - sbOff);
            sb.getChars( sbOff, sbOff+d, buff, end);
            sbOff += d;
            end += d;
            if (end >= limit)
            flushBuffer();
        }
    }

    /** Append a string to the buffer
     */
    public void append(String s) throws IOException {
        if (s != null) {
            append(s, 0, s.length());
        }
    }

    /** Append a string to the buffer
     */
    public void append(String s, int off, int len) throws IOException {
        if (s==null) return;

        resetStringCache();
        // will grow, up to limit
        makeSpace( len );

        // if we don't have limit: makeSpace can grow as it wants
        if( limit < 0 ) {
            // assert: makeSpace made enough space
            s.getChars(off, off+len, buff, end );
            end+=len;
            return;
        }

        int sOff = off;
        int sEnd = off + len;
        while (sOff < sEnd) {
            int d = min(limit - end, sEnd - sOff);
            s.getChars( sOff, sOff+d, buff, end);
            sOff += d;
            end += d;
            if (end >= limit)
            flushBuffer();
        }
    }

    // -------------------- Removing data from the buffer --------------------
    @Override
    public void delete(final int start, final int end) {
        resetStringCache();
        final int diff = this.end - end;
        if (diff == 0) {
            this.end = start;
        } else {
            System.arraycopy(buff, end, buff, start, diff);
            this.end = start + diff;
        }
    }

    public int substract()
        throws IOException {

        resetStringCache();
        if ((end - start) == 0) {
            if (in == null)
                return -1;
            int n = in.realReadChars(buff, end, buff.length - end);
            if (n < 0)
                return -1;
        }

        return (buff[start++]);

    }

    public int substract(CharChunk src)
        throws IOException {

        resetStringCache();
        if ((end - start) == 0) {
            if (in == null)
                return -1;
            int n = in.realReadChars( buff, end, buff.length - end);
            if (n < 0)
                return -1;
        }

        int len = getLength();
        src.append(buff, start, len);
        start = end;
        return len;

    }

    public int substract( char src[], int off, int len )
        throws IOException {

        resetStringCache();
        if ((end - start) == 0) {
            if (in == null)
                return -1;
            int n = in.realReadChars( buff, end, buff.length - end);
            if (n < 0)
                return -1;
        }

        int n = len;
        if (len > getLength()) {
            n = getLength();
        }
        System.arraycopy(buff, start, src, off, n);
        start += n;
        return n;

    }


    public void flushBuffer() throws IOException {
        //assert out!=null
        if( out==null ) {
            throw new IOException( "Buffer overflow, no sink " + limit + ' ' +
                       buff.length  );
        }
        out.realWriteChars( buff, start, end - start );
        end=start;
        resetStringCache();
    }

    /** Make space for len chars. If len is small, allocate
     *	a reserve space too. Never grow bigger than limit.
     */
    void makeSpace(int count) {
        char[] tmp;

        int newSize;
        int desiredSize=end + count;

        // Can't grow above the limit
        if( limit > 0 &&
            desiredSize > limit) {
            desiredSize=limit;
        }

        if( buff==null ) {
            if( desiredSize < 256 ) desiredSize=256; // take a minimum
            buff=new char[desiredSize];
        }

        // limit < buf.length ( the buffer is already big )
        // or we already have space XXX
        if( desiredSize <= buff.length) {
            return;
        }
        // grow in larger chunks
        if( desiredSize < 2 * buff.length ) {
            newSize= buff.length * 2;
            if( limit >0 &&
            newSize > limit ) newSize=limit;
            tmp=new char[newSize];
        } else {
            newSize= buff.length * 2 + count ;
            if( limit > 0 &&
            newSize > limit ) newSize=limit;
            tmp=new char[newSize];
        }

        System.arraycopy(buff, start, tmp, start, end-start);
        buff = tmp;
        tmp = null;
    }

    /**
     * Notify the Chunk that its content is going to be changed directly
     */
    protected void notifyDirectUpdate() {
    }

    protected final void resetStringCache() {
        cachedString = null;
    }
    
    // -------------------- Conversion and getters --------------------

    @Override
    public String toString() {
        if (null == buff || end - start == 0) {
            return "";
        } else if (cachedString != null) {
            return cachedString;
        }
//        return StringCache.toString(this);
        cachedString = toStringInternal();
        return cachedString;
    }

    @Override
    public String toString(int start, int end) {
        if (start == this.start && end == this.end) {
            return toString();
        } else if (null == buff) {
            return null;
        } else if (end - start == 0) {
            return "";
        }

        return new String(buff, this.start + start, end - start);
    }

    public String toStringInternal() {
        return new String(buff, start, end - start);
    }

    public int getInt() {
	return Ascii.parseInt(buff, start, end-start);
    }

    /**
     * Set {@link ByteChunk} content to CharChunk using given {@link Charset}.
     * @throws CharConversionException
     */
    public void set(final ByteChunk byteChunk, final Charset encoding)
            throws CharConversionException {

        final int bufferStart = byteChunk.getStart();
        final int bufferLength = byteChunk.getLength();
        allocate(bufferLength, -1);

        final byte[] buffer = byteChunk.getBuffer();
        
        if (Charsets.UTF8_CHARSET.equals(encoding)) {
            try {
//                final char[] ccBuf = getChars();
//                final int ccStart = getStart();

                end = UTF8_DECODER.convert(buffer,
                        bufferStart, buff, end,
                        bufferLength);
//                cc.setEnd(ccEnd);
            } catch (IOException e) {
                if (!(e instanceof CharConversionException)) {
                    throw new CharConversionException();
                }

                throw (CharConversionException) e;
            }
//            uri.setChars(cc.getChars(), cc.getStart(), cc.getEnd());
            return;
        } else if (!DEFAULT_HTTP_CHARSET.equals(encoding)) {
            final ByteBuffer bb = ByteBuffer.wrap(buffer,
                    bufferStart, bufferLength);
//            final char[] ccBuf = cc.getChars();
//            final int ccStart = cc.getStart();
            final CharBuffer cb = CharBuffer.wrap(buff, start, buff.length - start);

            final CharsetDecoder decoder = Charsets.getCharsetDecoder(encoding);
            final CoderResult cr = decoder.decode(bb, cb, true);

            if (cr != CoderResult.UNDERFLOW) {
                throw new CharConversionException("Decoding error");
            }

            end = start + cb.position();
//            uri.setChars(cc.getChars(), cc.getStart(), cc.getEnd());

            return;
        }

        // Default encoding: fast conversion
        for (int i = 0; i < bufferLength; i++) {
            buff[i] = (char) (buffer[i + bufferStart] & 0xff);
        }
        end = bufferLength;
        
//        return cc;
//        uri.setChars(cbuf, 0, bc.getLength());
    }
    
    /**
     * Set {@link BufferChunk} content to CharChunk using given {@link Charset}.
     * @throws CharConversionException
     */
    public void set(final BufferChunk bufferChunk, final Charset encoding)
            throws CharConversionException {

        final int bufferStart = bufferChunk.getStart();
        final int bufferLength = bufferChunk.getLength();
        allocate(bufferLength, -1);

        final Buffer buffer = bufferChunk.getBuffer();
        
        if (Charsets.UTF8_CHARSET.equals(encoding)) {
            try {
//                final char[] ccBuf = getChars();
//                final int ccStart = getStart();

                end = UTF8_DECODER.convert(buffer,
                        bufferStart, buff, end,
                        bufferLength);
//                cc.setEnd(ccEnd);
            } catch (IOException e) {
                if (!(e instanceof CharConversionException)) {
                    throw new CharConversionException();
                }

                throw (CharConversionException) e;
            }
//            uri.setChars(cc.getChars(), cc.getStart(), cc.getEnd());
            return;
        } else if (!DEFAULT_HTTP_CHARSET.equals(encoding)) {
            final ByteBuffer bb = buffer.toByteBuffer(
                    bufferStart, bufferStart + bufferLength);
//            final char[] ccBuf = cc.getChars();
//            final int ccStart = cc.getStart();
            final CharBuffer cb = CharBuffer.wrap(buff, start, buff.length - start);

            final CharsetDecoder decoder = Charsets.getCharsetDecoder(encoding);
            final CoderResult cr = decoder.decode(bb, cb, true);

            if (cr != CoderResult.UNDERFLOW) {
                throw new CharConversionException("Decoding error");
            }

            end = start + cb.position();
//            uri.setChars(cc.getChars(), cc.getStart(), cc.getEnd());

            return;
        }

        // Default encoding: fast conversion
        for (int i = 0; i < bufferLength; i++) {
            buff[i] = (char) (buffer.get(i + bufferStart) & 0xff);
        }
        end = bufferLength;
        
//        return cc;
//        uri.setChars(cbuf, 0, bc.getLength());
    }
    
    // -------------------- equals --------------------


    @Override
    public int hashCode() {
        int result = Arrays.hashCode(buff);
        result = 31 * result + start;
        result = 31 * result + end;
        result = 31 * result + (isSet ? 1 : 0);
        result = 31 * result + limit;
        result = 31 * result + in.hashCode();
        result = 31 * result + out.hashCode();
        result = 31 * result + (optimizedWrite ? 1 : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        CharChunk charChunk = (CharChunk) o;

        if (end != charChunk.end) return false;
        if (isSet != charChunk.isSet) return false;
        if (limit != charChunk.limit) return false;
        if (optimizedWrite != charChunk.optimizedWrite) return false;
        if (start != charChunk.start) return false;
        if (!Arrays.equals(buff, charChunk.buff)) return false;
        if (in != null ? !in.equals(charChunk.in) : charChunk.in != null)
            return false;
        if (out != null ? !out.equals(charChunk.out) : charChunk.out != null)
            return false;

        return true;
    }

    /**
     * Compares the message bytes to the specified String object.
     * @param s the String to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equals(CharSequence s) {
        char[] c = buff;
        int len = end-start;
        if (c == null || len != s.length()) {
            return false;
        }
        int off = start;
        for (int i = 0; i < len; i++) {
            if (c[off++] != s.charAt(i)) {
            return false;
            }
        }
        return true;
    }

    /**
     * Compares the message bytes to the specified byte array representing
     * ASCII characters.

     * @param b the <code>byte[]</code> to compare
     *
     * @return true if the comparison succeeded, false otherwise
     *
     * @since 2.3
     */
    public boolean equals(byte[] b) {
        char[] c = buff;
        int len = end-start;
        if (c == null || len != b.length) {
            return false;
        }
        int off = start;
        for (int i = 0; i < len; i++) {
            if (c[off++] != b[i]) {
                return false;
            }
        }
        return true;
    }
    
    /**
     * Compares the message bytes to the specified String object.
     * @param s the String to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equalsIgnoreCase(CharSequence s) {
        char[] c = buff;
        int len = end-start;
        if (c == null || len != s.length()) {
            return false;
        }
        int off = start;
        for (int i = 0; i < len; i++) {
            if (Ascii.toLower( c[off++] ) != Ascii.toLower( s.charAt(i))) {
            return false;
            }
        }
        return true;
    }

    /**
     * Compares the message bytes to the specified byte array representing
     * ASCII characters.

     * @param b the <code>byte[]</code> to compare
     *
     * @return true if the comparison succeeded, false otherwise
     *
     * @since 2.1.2
     */
    public boolean equalsIgnoreCase(final byte[] b) {
        return equalsIgnoreCase(b, 0, b.length);
    }

    /**
     * Compares the message bytes to the specified byte array representing
     * ASCII characters.

     * @param b the <code>byte[]</code> to compare
     *
     * @return true if the comparison succeeded, false otherwise
     *
     * @since 2.3
     */
    public boolean equalsIgnoreCase(final byte[] b, final int offset, final int len) {
        char[] c = buff;
        if (c == null || getLength() != len) {
            return false;
        }
        int offs1 = start;
        int offs2 = offset;
        for (int i = 0; i < len; i++) {
            if (Ascii.toLower(c[offs1++]) != Ascii.toLower(b[offs2++])) {
            return false;
            }
        }
        return true;
    }
    
    /**
     * Compares the message bytes to the specified char array representing
     * ASCII characters.

     * @param b the <code>char[]</code> to compare
     *
     * @return true if the comparison succeeded, false otherwise
     *
     * @since 2.3
     */
    public boolean equalsIgnoreCase(final char[] b, final int offset, final int len) {
        char[] c = buff;
        if (c == null || getLength() != len) {
            return false;
        }
        int offs1 = start;
        int offs2 = offset;
        for (int i = 0; i < len; i++) {
            if (Ascii.toLower(c[offs1++]) != Ascii.toLower(b[offs2++])) {
            return false;
            }
        }
        return true;
    }
    
    /**
     * Compares the char chunk to the specified byte array representing
     * lower-case ASCII characters.
     *
     * @param b the <code>byte[]</code> to compare
     *
     * @return true if the comparison succeeded, false otherwise
     *
     * @since 2.1.2
     */
    public boolean equalsIgnoreCaseLowerCase(final byte[] b) {
        char[] c = buff;
        int len = end-start;
        if (c == null || len != b.length) {
            return false;
        }
        int off = start;
        for (int i = 0; i < len; i++) {
            if (Ascii.toLower(c[off++]) != b[i]) {
            return false;
            }
        }
        return true;
    }

    public boolean equals(CharChunk cc) {
        return equals( cc.getChars(), cc.getStart(), cc.getLength());
    }

    public boolean equals(char b2[], int off2, int len2) {
        char b1[]=buff;
        if( b1==null && b2==null ) return true;

        if (b1== null || b2==null || end-start != len2) {
            return false;
        }
        int off1 = start;
        int len=end-start;
        while ( len-- > 0) {
            if (b1[off1++] != b2[off2++]) {
            return false;
            }
        }
        return true;
    }

    public boolean equals(byte b2[], int off2, int len2) {
        char b1[]=buff;
        if( b2==null && b1==null ) return true;

        if (b1== null || b2==null || end-start != len2) {
            return false;
        }
        int off1 = start;
        int len=end-start;

        while ( len-- > 0) {
            if ( b1[off1++] != (char)b2[off2++]) {
            return false;
            }
        }
        return true;
    }

    /**
     * Returns true if the message bytes starts with the specified string.
     * @param s the string
     */
    public boolean startsWith(String s) {
        return startsWith(s, 0);
    }

    boolean startsWith(final String s, final int pos) {
        char[] c = buff;
        int len = s.length();
//        if (c == null || len + pos > end) {
        if (c == null || len + pos > end - start) {
            return false;
        }
        int off = start + pos;
        for (int i = 0; i < len; i++) {
            if (c[off++] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Returns true if the message bytes starts with the specified string.
     * @param s the string
     */
    public boolean startsWithIgnoreCase(final String s, final int pos) {
        char[] c = buff;
        int len = s.length();
        if (c == null || len + pos > end - start) {
            return false;
        }
        int off = start + pos;
        for (int i = 0; i < len; i++) {
            if (Ascii.toLower(c[off++]) != Ascii.toLower(s.charAt(i))) {
                return false;
            }
        }
        return true;
    }

    public boolean endsWith(String s) {
        char[] c = buff;
        int len = s.length();
        if (c == null || len > end - start) {
            return false;
        }
        int off = end - len;
        for (int i = 0; i < len; i++) {
            if (c[off++] != s.charAt(i)) {
                return false;
            }
        }
        return true;
    }
    
    // -------------------- Hash code  --------------------
    
    // normal hash.
    public int hash() {
        int code=0;
        for (int i = start; i < end; i++) {
            code = code * 31 + buff[i];
        }
        return code;
    }

    // hash ignoring case
    public int hashIgnoreCase() {
        int code=0;
        for (int i = start; i < end; i++) {
            code = code * 31 + Ascii.toLower(buff[i]);
        }
        return code;
    }

    public int indexOf(char c) {
        return indexOf( c, start);
    }

    /**
     * Returns true if the message bytes starts with the specified string.
     * @param c the character
     */
    @Override
    public int indexOf(char c, int starting) {
        int ret = indexOf( buff, start+starting, end, c );
        return (ret >= start) ? ret - start : -1;
    }

    public static int indexOf( char chars[], int off, int cend, char qq ) {
        while( off < cend ) {
            if (chars[off] == qq) {
                return off;
            }
            off++;
        }
        return -1;
    }


    @Override
    public final int indexOf(String s, int fromIndex) {
        return indexOf(s, 0, s.length(), fromIndex);
    }

    public final int indexOf(String src, int srcOff, int srcLen, int myOff) {
        char first = src.charAt(srcOff);

        // Look for first char
        int srcEnd = srcOff + srcLen;

        for (int i = myOff + start; i <= (end - srcLen); i++) {
            if (buff[i] != first) {
                continue;
            }
            // found first char, now look for a match
            int myPos = i + 1;
            for (int srcPos = srcOff + 1; srcPos < srcEnd;) {
                if (buff[myPos++] != src.charAt(srcPos++)) {
                    break;
                }
                if (srcPos == srcEnd) {
                    return i - start; // found it
                }
            }
        }
        
        return -1;
    }

    // -------------------- utils
    private int min(int a, int b) {
        if (a < b) return a;
        return b;
    }

}
