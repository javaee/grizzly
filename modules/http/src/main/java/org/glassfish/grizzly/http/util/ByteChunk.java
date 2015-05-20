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

import java.io.IOException;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.nio.ByteBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import org.glassfish.grizzly.utils.Charsets;

/*
 * In a server it is very important to be able to operate on
 * the original byte[] without converting everything to chars.
 * Some protocols are ASCII only, and some allow different
 * non-UNICODE encodings. The encoding is not known beforehand,
 * and can even change during the execution of the protocol.
 * ( for example a multipart message may have parts with different
 *  encoding )
 *
 * For HTTP it is not very clear how the encoding of RequestURI
 * and mime values can be determined, but it is a great advantage
 * to be able to parse the request without converting to string.
 */


/**
 * This class is used to represent a chunk of bytes, and
 * utilities to manipulate byte[].
 *
 * The buffer can be modified and used for both input and output.
 *
 * @author dac@sun.com
 * @author James Todd [gonzo@sun.com]
 * @author Costin Manolache
 * @author Remy Maucherat
 */
public final class ByteChunk implements Chunk, Cloneable, Serializable {

    private static final long serialVersionUID = -1L;

    // Input interface, used when the buffer is emptied.
    public static interface ByteInputChannel {
        /**
         * Read new bytes ( usually the internal conversion buffer ).
         * The implementation is allowed to ignore the parameters,
         * and mutate the chunk if it wishes to implement its own buffering.
         */
        public int realReadBytes(byte cbuf[], int off, int len)
            throws IOException;
    }
    // Output interface, used when the buffer is filled.
    public static interface ByteOutputChannel {
        /**
         * Send the bytes ( usually the internal conversion buffer ).
         * Expect 8k output if the buffer is full.
         */
        public void realWriteBytes(byte cbuf[], int off, int len)
            throws IOException;
    }

    // --------------------

    /** Default encoding used to convert to strings. It should be UTF8,
	as most standards seem to converge, but the servlet API requires
	8859_1, and this object is used mostly for servlets.
    */
    private static final Charset DEFAULT_CHARSET = Constants.DEFAULT_HTTP_CHARSET;

    // byte[]
    private byte[] buff;

    private int start=0;
    private int end;

    private Charset charset;

    private boolean isSet=false; // XXX

    // How much can it grow, when data is added
    private int limit=-1;

    private transient ByteInputChannel in = null;
    private transient ByteOutputChannel out = null;

    private boolean optimizedWrite=true;

    private String cachedString;
    private Charset cachedStringCharset;
    
    /**
     * Creates a new, uninitialized ByteChunk object.
     */
    public ByteChunk() {
    }

    public ByteChunk( int initial ) {
        allocate( initial, -1 );
    }

    //--------------------
    public ByteChunk getClone() {
        try {
            return (ByteChunk)this.clone();
        } catch( Exception ex) {
            return null;
        }
    }

    public boolean isNull() {
        return ! isSet; // buff==null;
    }

    /**
     * Resets the message buff to an uninitialized state.
     */
    public void recycle() {
//        buff = null;
        charset=null;
        start=0;
        end=0;
        isSet=false;
    }

    public void recycleAndReset() {
        buff = null;
        charset=null;
        start=0;
        end=0;
        isSet=false;
        resetStringCache();
    }
    
    public void reset() {
        buff=null;
        resetStringCache();
    }

    protected final void resetStringCache() {
        cachedString = null;
        cachedStringCharset = null;
    }
    
    // -------------------- Setup --------------------

    public void allocate( int initial, int limit  ) {
        boolean output = true;
        if( buff==null || buff.length < initial ) {
            buff=new byte[initial];
        }
        this.limit=limit;
        start=0;
        end=0;
        isSet=true;
        resetStringCache();
    }

    /**
     * Sets the message bytes to the specified sub-array of bytes.
     *
     * @param b the ascii bytes
     * @param off the start offset of the bytes
     * @param len the length of the bytes
     */
    public void setBytes(byte[] b, int off, int len) {
        buff = b;
        start = off;
        end = start+ len;
        isSet=true;
        resetStringCache();
    }

    public void setOptimizedWrite(boolean optimizedWrite) {
        this.optimizedWrite = optimizedWrite;
    }

    public Charset getCharset() {
        return ((charset != null ? charset : DEFAULT_CHARSET));
    }

    public void setCharset(Charset charset) {
        this.charset = charset;
        resetStringCache();
    }

    /**
     * Returns the message bytes.
     */
    public byte[] getBytes() {
        return getBuffer();
    }

    /**
     * Returns the message bytes.
     */
    public byte[] getBuffer() {
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

    public int getOffset() {
        return getStart();
    }
    
    @Override
    public void setStart(int start) {
        if (end < start) {
            end = start;
        }
        this.start = start;
        resetStringCache();
    }
    
    public void setOffset(int off) {
        setStart(off);
    }    

    /**
     * Returns the length of the bytes.
     * XXX need to clean this up
     */
    @Override
    public int getLength() {
        return end-start;
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
    public void setByteInputChannel(ByteInputChannel in) {
        this.in = in;
    }

    /** When the buffer is full, write the data to the output channel.
     * 	Also used when large amount of data is appended.
     *
     *  If not set, the buffer will grow to the limit.
     */
    public void setByteOutputChannel(ByteOutputChannel out) {
        this.out=out;
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

    /**
     * Notify the Chunk that its content is going to be changed directly
     */
    protected void notifyDirectUpdate() {
    }

    @Override
    public int indexOf(final String s, int fromIdx) {
        // Works only for UTF
        final int strLen = s.length();
        if (strLen == 0) {
            return fromIdx;
        }

        int absFromIdx = fromIdx + start;
        
        if (strLen > (end - absFromIdx)) return -1;

        int strOffs = 0;
        final int lastOffs = end - strLen;

        while (absFromIdx <= lastOffs + strOffs) {
            final byte b = buff[absFromIdx];
            if (b == s.charAt(strOffs)) {
                strOffs++;
                if (strOffs == strLen) {
                    return absFromIdx - strLen -start + 1;
                }
            } else {
                strOffs = 0;
            }

            absFromIdx++;
        }
        return -1;
    }

    @Override
    public void delete(final int start, final int end) {
        resetStringCache();
        
        final int absDeleteStart = this.start + start;
        final int absDeleteEnd = this.start + end;
        
        final int diff = this.end - absDeleteEnd;
        if (diff == 0) {
            this.end = absDeleteStart;
        } else {
            System.arraycopy(buff, absDeleteEnd, buff, absDeleteStart, diff);
            this.end = absDeleteStart + diff;
        }
    }

    
    // -------------------- Adding data to the buffer --------------------
    public void append( char c ) throws IOException {
        append( (byte)c);
    }

    public void append( byte b ) throws IOException {
        resetStringCache();

        makeSpace( 1 );

        // couldn't make space
        if( limit >0 && end >= limit ) {
            flushBuffer();
        }
        buff[end++]=b;
    }

    public void append( ByteChunk src ) throws IOException {
        append( src.getBytes(), src.getStart(), src.getLength());
    }

    /** Add data to the buffer
     */
    public void append( byte src[], int off, int len ) throws IOException {
        resetStringCache();

        // will grow, up to limit
        makeSpace( len );

        // if we don't have limit: makeSpace can grow as it wants
        if( limit < 0 ) {
            // assert: makeSpace made enough space
            System.arraycopy( src, off, buff, end, len );
            end+=len;
            return;
        }

            // Optimize on a common case.
            // If the buffer is empty and the source is going to fill up all the
            // space in buffer, may as well write it directly to the output,
            // and avoid an extra copy
            if ( optimizedWrite && len == limit && end == start) {
                out.realWriteBytes( src, off, len );
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

        // We chunk the data into slices fitting in the buffer limit, although
        // if the data is written directly if it doesn't fit

        int avail=limit-end;
        System.arraycopy(src, off, buff, end, avail);
        end += avail;

        flushBuffer();

        int remain = len - avail;

        while (remain > (limit - end)) {
            out.realWriteBytes( src, (off + len) - remain, limit - end );
            remain = remain - (limit - end);
        }

        System.arraycopy(src, (off + len) - remain, buff, end, remain);
        end += remain;

    }


    // -------------------- Removing data from the buffer --------------------

    public int substract()
        throws IOException {

        resetStringCache();

        if ((end - start) == 0) {
            if (in == null)
                return -1;
            int n = in.realReadBytes( buff, 0, buff.length );
            if (n < 0)
                return -1;
        }

        return (buff[start++] & 0xFF);

    }

    public int substract(ByteChunk src)
        throws IOException {

        resetStringCache();

        if ((end - start) == 0) {
            if (in == null)
                return -1;
            int n = in.realReadBytes( buff, 0, buff.length );
            if (n < 0)
                return -1;
        }

        int len = getLength();
        src.append(buff, start, len);
        start = end;
        return len;

    }

    public int substract( byte src[], int off, int len )
        throws IOException {

        resetStringCache();

        if ((end - start) == 0) {
            if (in == null)
                return -1;
            int n = in.realReadBytes( buff, 0, buff.length );
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
        out.realWriteBytes( buff, start, end-start );
        end=start;
    }

    // See if we can add more space without flushing the buffer
    boolean canGrow() {
	if (buff.length == limit)
	    return false;
	// This seems like a potential place for huge memory use, but it's
	// the same algorithm as makeSpace() has always effectively used.
	int desiredSize = buff.length * 2;
	if (limit > 0 && desiredSize > limit && limit > (end-start)){
	    desiredSize = limit;
        }
	byte[] tmp=new byte[desiredSize];
	System.arraycopy(buff, start, tmp, 0, end-start);
	buff = tmp;
	tmp = null;
	end = end - start;
	start = 0;
	return true;
    }

    /** Make space for len chars. If len is small, allocate
     *	a reserve space too. Never grow bigger than limit.
     */
    private void makeSpace(int count) {
        byte[] tmp;

        int newSize;
        int desiredSize=end + count;

        // Can't grow above the limit
        if( limit > 0 &&
            desiredSize > limit) {
            desiredSize=limit;
        }

        if( buff==null ) {
            if( desiredSize < 256 ) desiredSize=256; // take a minimum
            buff=new byte[desiredSize];
        }

        // limit < buf.length ( the buffer is already big )
        // or we already have space XXX
        if( desiredSize <= buff.length ) {
            return;
        }
        // grow in larger chunks
        if( desiredSize < 2 * buff.length ) {
            newSize= buff.length * 2;
            if( limit >0 &&
            newSize > limit ) newSize=limit;
            tmp=new byte[newSize];
        } else {
            newSize= buff.length * 2 + count ;
            if( limit > 0 &&
            newSize > limit ) newSize=limit;
            tmp=new byte[newSize];
        }

        System.arraycopy(buff, start, tmp, 0, end-start);
        buff = tmp;
        tmp = null;
        end=end-start;
        start=0;
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

        if (charset == null) {
            charset = DEFAULT_CHARSET;
        }

        try {
            return new String(buff, this.start + start, end - start, charset.name());
        } catch (UnsupportedEncodingException e) {
            throw new IllegalStateException("Unexpected error", e);
        }
    }

    public String toString(Charset charset) {
        if (charset == null) {
            charset = this.charset != null ? this.charset : DEFAULT_CHARSET;
        }

        if (cachedString != null && charset.equals(cachedStringCharset)) {
            return cachedString;
        }

        cachedString = charset.decode(ByteBuffer.wrap(buff, start, end - start)).toString();
        cachedStringCharset = charset;
        
        return cachedString;
    }
    
    public String toStringInternal() {
        if (charset == null) {
            charset = DEFAULT_CHARSET;
        }

        return toString(charset);
    }

    public int getInt() {
        return Ascii.parseInt(buff, start,end-start);
    }

    public long getLong() {
        return Ascii.parseLong(buff, start,end-start);
    }


    // -------------------- equals --------------------


    @Override
    public int hashCode() {
        int result = Arrays.hashCode(buff);
        result = 31 * result + start;
        result = 31 * result + end;
        result = 31 * result + charset.hashCode();
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

        ByteChunk byteChunk = (ByteChunk) o;

        if (end != byteChunk.end) return false;
        if (isSet != byteChunk.isSet) return false;
        if (limit != byteChunk.limit) return false;
        if (optimizedWrite != byteChunk.optimizedWrite) return false;
        if (start != byteChunk.start) return false;
        if (!Arrays.equals(buff, byteChunk.buff)) return false;
        if (charset != null ? !charset.equals(byteChunk.charset) : byteChunk.charset != null)
            return false;
        if (in != null ? !in.equals(byteChunk.in) : byteChunk.in != null)
            return false;
        if (out != null ? !out.equals(byteChunk.out) : byteChunk.out != null)
            return false;

        return true;
    }

    /**
     * Compares the message bytes to the specified String object.
     * @param s the String to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equals(final String s) {
        return equals(buff, start, end - start, s);
    }

    /**
     * Compares the message bytes to the specified byte array.

     * @param bytes the <code>byte[]</code> to compare
     *
     * @return true if the comparison succeeded, false otherwise
     *
     * @since 2.3
     */
    public final boolean equals(final byte[] bytes) {
        return equals(buff, start, end - start, bytes, 0, bytes.length);
    }
    
    /**
     * Compares the message bytes to the specified String object.
     * @param s the String to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equalsIgnoreCase(final String s) {
        return equalsIgnoreCase(buff, start, getLength(), s);
    }

    public boolean equalsIgnoreCase(final byte[] b) {
        return equalsIgnoreCase(b, 0, b.length);
    }
    
    public boolean equalsIgnoreCase(final byte[] b, final int offset, final int len) {
        return equalsIgnoreCase(buff, start, getLength(), b, offset, len);
    }
    
    public boolean equalsIgnoreCaseLowerCase(byte[] cmpTo) {
        return equalsIgnoreCaseLowerCase(buff, start, end, cmpTo);
    }
    
    public boolean equals( ByteChunk bb ) {
        return equals( bb.getBytes(), bb.getStart(), bb.getLength());
    }

    public boolean equals( byte b2[], int off2, int len2) {
        byte b1[]=buff;
        if( b1==null && b2==null ) return true;

        int len=end-start;
        if ( len2 != len || b1==null || b2==null )
            return false;

        int off1 = start;

        while ( len-- > 0) {
            if (b1[off1++] != b2[off2++]) {
            return false;
            }
        }
        return true;
    }

    public boolean equals( CharChunk cc ) {
        return equals( cc.getChars(), cc.getStart(), cc.getLength());
    }

    public boolean equals( char c2[], int off2, int len2) {
        // XXX works only for enc compatible with ASCII/UTF !!!
        byte b1[]=buff;
        if( c2==null && b1==null ) return true;

        if (b1== null || c2==null || end-start != len2 ) {
            return false;
        }
        int off1 = start;
        int len=end-start;

        while ( len-- > 0) {
            if ( (char)b1[off1++] != c2[off2++]) {
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

    /**
     * Returns true if the message bytes starts with the specified string.
     * @param s the string
     * @param offset The position
     */
    public boolean startsWith(String s, int offset) {
        byte[] b = buff;
        int len = s.length();
        if (b == null || len+offset > end-start) {
            return false;
        }
        int off = start+offset;
        for (int i = 0; i < len; i++) {
            if (b[off++] != s.charAt(i)) {
            return false;
            }
        }
        return true;
    }
    
    /* Returns true if the message bytes start with the specified byte array */
    public boolean startsWith(byte[] b2) {
        byte[] b1 = buff;
        if (b1 == null && b2 == null) {
            return true;
        }

        int len = end - start;
        if (b1 == null || b2 == null || b2.length > len) {
            return false;
        }
        for (int i = start, j = 0; i < end && j < b2.length; ) {
            if (b1[i++] != b2[j++])
                return false;
        }
        return true;
    }
    
    /**
     * Returns true if the message bytes starts with the specified string.
     * @param s the string
     * @param pos The position
     */
    public boolean startsWithIgnoreCase(String s, int pos) {
        byte[] b = buff;
        int len = s.length();
        if (b == null || len+pos > end-start) {
            return false;
        }
        int off = start+pos;
        for (int i = 0; i < len; i++) {
            if (Ascii.toLower( b[off++] ) != Ascii.toLower( s.charAt(i))) {
            return false;
            }
        }
        return true;
    }

    public int indexOf( String src, int srcOff, int srcLen, int myOff ) {
        char first=src.charAt( srcOff );

        // Look for first char
        int srcEnd = srcOff + srcLen;

        for( int i=myOff+start; i <= (end - srcLen); i++ ) {
            if( buff[i] != first ) continue;
            // found first char, now look for a match
                int myPos=i+1;
            for( int srcPos=srcOff + 1; srcPos< srcEnd; ) {
                    if( buff[myPos++] != src.charAt( srcPos++ ))
                break;
                    if( srcPos==srcEnd ) return i-start; // found it
            }
        }
        return -1;
    }

    // -------------------- Hash code  --------------------

    // normal hash.
    public int hash() {
        return hashBytes( buff, start, end-start);
    }

    // hash ignoring case
    public int hashIgnoreCase() {
        return hashBytesIC( buff, start, end-start );
    }

    public static boolean equals(final byte[] b1, final int b1Offs, final int b1Len,
            final byte[] b2, final int b2Offs, final int b2Len) {
        // XXX ENCODING - this only works if encoding is UTF8-compat
        // ( ok for tomcat, where we compare ascii - header names, etc )!!!

        if (b1Len != b2Len) {
            return false;
        }
        
        if (b1 == b2) {
            return true;
        }
        
        if (b1 == null || b2 == null) {
            return false;
        }

        for (int i = 0; i < b1Len; i++) {
            if (b1[i + b1Offs] != b2[i + b2Offs]) {
                return false;
            }
        }
        return true;
    }
    
    public static boolean equals(final byte[] b, int offs, final int len,
            final String s) {
        // XXX ENCODING - this only works if encoding is UTF8-compat
        // ( ok for tomcat, where we compare ascii - header names, etc )!!!

        if (b == null || len != s.length()) {
            return false;
        }
        
        for (int i = 0; i < len; i++) {
            if (b[offs++] != s.charAt(i)) {
                return false;
            }
        }
        return true;
        
    }
    
    public static boolean equalsIgnoreCase(final byte[] b1, final int b1Offs, final int b1Len,
            final byte[] b2, final int b2Offs, final int b2Len) {
        // XXX ENCODING - this only works if encoding is UTF8-compat
        // ( ok for tomcat, where we compare ascii - header names, etc )!!!

        if (b1Len != b2Len) {
            return false;
        }
        
        if (b1 == b2) {
            return true;
        }
        
        if (b1 == null || b2 == null) {
            return false;
        }

        for (int i = 0; i < b1Len; i++) {
            if (Ascii.toLower(b1[i + b1Offs]) != Ascii.toLower(b2[i + b2Offs])) {
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
    public static boolean equalsIgnoreCase(final byte[] b, final int offset, final int len,
            final String s) {
        if (len != s.length()) {
            return false;
        }
        int boff = offset;
        for (int i = 0; i < len; i++) {
            if (Ascii.toLower(b[boff++]) != Ascii.toLower(s.charAt(i))) {
            return false;
            }
        }
        return true;
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
     * @since 2.3
     */
    public static boolean equalsIgnoreCaseLowerCase(final byte[] buffer,
            final int start, final int end, final byte[] cmpTo) {
        final int len = end - start;
        if (len != cmpTo.length) {
            return false;
        }

        for (int i = 0; i < len; i++) {
            if (Ascii.toLower(buffer[i + start]) != cmpTo[i]) {
                return false;
            }
        }

        return true;        
    }
    
    public static boolean startsWith(final byte[] buffer, final int start,
            final int end, final byte[] cmpTo) {
        final int len = end - start;
        
        if (len < cmpTo.length) {
            return false;
        }

        for (int i = 0; i < cmpTo.length; i++) {
            if (buffer[start + i] != cmpTo[i]) {
                return false;
            }
        }

        return true;
    }
    
    private static int hashBytes(byte[] buff, int start, int bytesLen ) {
        int max=start+bytesLen;
        int code=0;
        for (int i = start; i < max ; i++) {
            code = code * 31 + buff[i];
        }
        return code;
    }

    private static int hashBytesIC(byte[] bytes, int start, int bytesLen ) {
        int max=start+bytesLen;
        int code=0;
        for (int i = start; i < max ; i++) {
            code = code * 31 + Ascii.toLower(bytes[i]);
        }
        return code;
    }

    /**
     * Returns true if the message bytes starts with the specified string.
     * @param c the character
     * @param starting The start position
     */
    public int indexOf(char c, int starting) {
        int ret = indexOf( buff, start+starting, end, c);
        return (ret >= start) ? ret - start : -1;
    }

    public static int  indexOf( byte bytes[], int off, int end, char qq ) {
        // Works only for UTF
        while (off < end) {
            byte b = bytes[off];
            if (b == qq) {
                return off;
            }
            
            off++;
        }
        return -1;
    }

    /** Find a character, no side effects.
     *  @return index of char if found, -1 if not
     */
    public static int findChar( byte buf[], int start, int end, char c ) {
        byte b=(byte)c;
        int offset = start;
        while (offset < end) {
            if (buf[offset] == b) {
            return offset;
            }
            offset++;
        }
        return -1;
    }

    /** Find a character, no side effects.
     *  @return index of char if found, -1 if not
     */
    public static int findChars( byte buf[], int start, int end, byte c[] ) {
        int clen=c.length;
        int offset = start;
        while (offset < end) {
            for( int i=0; i<clen; i++ )
            if (buf[offset] == c[i]) {
                return offset;
            }
            offset++;
        }
        return -1;
    }

    /** Find the first character != c
     *  @return index of char if found, -1 if not
     */
    public static int findNotChars( byte buf[], int start, int end, byte c[] ) {
        int clen=c.length;
        int offset = start;
        boolean found;

        while (offset < end) {
            found=true;
            for( int i=0; i<clen; i++ ) {
                if (buf[offset] == c[i]) {
                    found=false;
                    break;
                }
            }
            if( found ) { // buf[offset] != c[0..len]
                return offset;
            }
            offset++;
        }
        return -1;
    }


    /**
     * Convert specified String to a byte array.
     *
     * @param value to convert to byte array
     * @return the byte array value
     */
    public static byte[] convertToBytes(String value) {
        byte[] result = new byte[value.length()];
        for (int i = 0; i < value.length(); i++) {
            result[i] = (byte) value.charAt(i);
        }
        return result;
    }


}
