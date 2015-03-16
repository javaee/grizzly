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

import java.io.CharConversionException;
import java.io.IOException;
import java.nio.charset.Charset;
import org.glassfish.grizzly.Buffer;

/**
 * {@link Buffer} chunk representation.
 * Helps HTTP module to avoid redundant String creation.
 * 
 * @author Alexey Stashok
 */
public class DataChunk implements Chunk {

    public static enum Type {None, Bytes, Buffer, Chars, String}
    
    public static DataChunk newInstance() {
        return newInstance(new ByteChunk(), new BufferChunk(), new CharChunk(), null);
    }

    public static DataChunk newInstance(final ByteChunk byteChunk,
            final BufferChunk bufferChunk,
            final CharChunk charChunk, final String stringValue) {
        return new DataChunk(byteChunk, bufferChunk, charChunk, stringValue);
    }
    
    Type type = Type.None;

    final ByteChunk byteChunk;
    final BufferChunk bufferChunk;
    final CharChunk charChunk;
    
    String stringValue;

    protected DataChunk() {
        this(new ByteChunk(), new BufferChunk(), new CharChunk(), null);
    }

    protected DataChunk(final ByteChunk byteChunk,
            final BufferChunk bufferChunk,
            final CharChunk charChunk,
            final String stringValue) {
        this.byteChunk = byteChunk;
        this.bufferChunk = bufferChunk;
        this.charChunk = charChunk;
        this.stringValue = stringValue;
    }

    public DataChunk toImmutable() {
        return new Immutable(this);
    }

    public Type getType() {
        return type;
    }

    public void set(final DataChunk value) {
//        reset();

        switch (value.getType()) {
            case Bytes: {
                final ByteChunk anotherByteChunk = value.byteChunk;
                setBytesInternal(anotherByteChunk.getBytes(),
                        anotherByteChunk.getStart(),
                        anotherByteChunk.getEnd());
                break;
            }
            case Buffer: {
                final BufferChunk anotherBufferChunk = value.bufferChunk;
                setBufferInternal(anotherBufferChunk.getBuffer(),
                        anotherBufferChunk.getStart(),
                        anotherBufferChunk.getEnd());
                break;
            }
            case String: {
                setStringInternal(value.stringValue);
                break;
            }
            case Chars: {
                final CharChunk anotherCharChunk = value.charChunk;
                setCharsInternal(anotherCharChunk.getChars(),
                                 anotherCharChunk.getStart(),
                                 anotherCharChunk.getLimit());
                break;
            }
        }

//        onContentChanged();
    }
    
    public void set(final DataChunk value, final int start, final int end) {
//        reset();

        switch (value.getType()) {
            case Bytes: {
                final ByteChunk anotherByteChunk = value.byteChunk;
                setBytesInternal(anotherByteChunk.getBytes(), start, end);
                break;
            }
            case Buffer: {
                final BufferChunk anotherBufferChunk = value.bufferChunk;
                setBufferInternal(anotherBufferChunk.getBuffer(), start, end);
                break;
            }
            case String: {
                setStringInternal(value.stringValue.substring(start, end));
                break;
            }
            case Chars: {
                final CharChunk anotherCharChunk = value.charChunk;
                setCharsInternal(anotherCharChunk.getChars(), start, end);
                break;
            }
        }

//        onContentChanged();
    }

    /**
     * Notify the Chunk that its content is going to be changed directly
     */
    public void notifyDirectUpdate() {
        switch (type) {
            case Bytes:
                byteChunk.notifyDirectUpdate();
                return;
            case Buffer:
                bufferChunk.notifyDirectUpdate();
                return;
            case Chars:
                charChunk.notifyDirectUpdate();
        }
    }

    public BufferChunk getBufferChunk() {
        return bufferChunk;
    }

    public void setBuffer(final Buffer buffer,
                          final int position,
                          final int limit) {
        setBufferInternal(buffer, position, limit);
    }

    public void setBuffer(final Buffer buffer) {
        setBufferInternal(buffer, buffer.position(), buffer.limit());
    }

    public CharChunk getCharChunk() {
        return charChunk;
    }

    public void setChars(final char[] chars, final int position, final int limit) {
        setCharsInternal(chars, position, limit);
    }

    public ByteChunk getByteChunk() {
        return byteChunk;
    }
    
    public void setBytes(final byte[] bytes) {
        setBytesInternal(bytes, 0, bytes.length);
    }
    
    public void setBytes(final byte[] bytes, final int position, final int limit) {
        setBytesInternal(bytes, position, limit);
    }
    
    public void setString(String string) {
        setStringInternal(string);
    }

    /**
     *  Copy the src into this DataChunk, allocating more space if needed
     */
    public void duplicate(final DataChunk src) {
        switch (src.getType()) {
            case Bytes: {
                final ByteChunk bc = src.getByteChunk();
                byteChunk.allocate(2 * bc.getLength(), -1);
                try {
                    byteChunk.append(bc);
                } catch (IOException ignored) {
                    // should never occur
                }

                switchToByteChunk();
                break;
            }
            case Buffer: {
                final BufferChunk bc = src.getBufferChunk();
                bufferChunk.allocate(2 * bc.getLength());
                bufferChunk.append(bc);
                switchToBufferChunk();
                break;
            }
            case Chars: {
                final CharChunk cc = src.getCharChunk();
                charChunk.allocate(2 * cc.getLength(), -1);
                try {
                    charChunk.append(cc);
                } catch (IOException ignored) {
                    // should never occur
                }

                switchToCharChunk();
                break;
            }
            case String: {
                setString(src.toString());
                break;
            }
            default: {
                recycle();
            }
        }
    }

    public void toChars(final Charset charset) throws CharConversionException {
        switch (type) {
            case Bytes:
                charChunk.set(byteChunk, charset);
                setChars(charChunk.getChars(), charChunk.getStart(), charChunk.getEnd());
                return;
            case Buffer:
                charChunk.set(bufferChunk, charset);
//                bufferChunk.toChars(charChunk, charset);
                setChars(charChunk.getChars(), charChunk.getStart(), charChunk.getEnd());
                return;
            case String:
                charChunk.recycle();
                try {
                    charChunk.append(stringValue);
                } catch (IOException e) {
                    throw new IllegalStateException("Unexpected exception");
                }
                
                setChars(charChunk.getChars(), charChunk.getStart(), charChunk.getEnd());
                return;
            case Chars:
                return;
            default:
                charChunk.recycle();
        }
    }

    @Override
    public String toString() {
        return toString(null);
    }

    public String toString(Charset charset) {
        switch (type) {
            case Bytes:
                return byteChunk.toString(charset);
            case Buffer:
                return bufferChunk.toString(charset);
            case String:
                return stringValue;
            case Chars:
                return charChunk.toString();
            default:
                return null;
        }
    }

//    protected void onContentChanged() {
//    }

    /**
     * Returns the <tt>DataChunk</tt> length.
     *
     * @return the <tt>DataChunk</tt> length.
     */
    @Override
    public int getLength() {
        switch (type) {
            case Bytes:
                return byteChunk.getLength();
            case Buffer:
                return bufferChunk.getLength();
            case String:
                return stringValue.length();
            case Chars:
                return charChunk.getLength();

            default:
                return 0;
        }
    }

    /**
     * Returns the <tt>DataChunk</tt> start position.
     *
     * @return the <tt>DataChunk</tt> start position.
     */
    @Override
    public int getStart() {
        switch (type) {
            case Bytes:
                return byteChunk.getStart();
            case Buffer:
                return bufferChunk.getStart();
            case Chars:
                return charChunk.getStart();

            default:
                return 0;
        }
    }


    /**
     * Sets the <tt>DataChunk</tt> start position.
     *
     * @param start the <tt>DataChunk</tt> start position.
     */
    @Override
    public void setStart(int start) {
        switch (type) {
            case Bytes:
                byteChunk.setStart(start);
                break;
            case Buffer:
                bufferChunk.setStart(start);
                break;
            case Chars:
                charChunk.setStart(start);
                break;
            default:
                break;
        }
    }

    /**
     * Returns the <tt>DataChunk</tt> end position.
     *
     * @return the <tt>DataChunk</tt> end position.
     */
    @Override
    public int getEnd() {
        switch (type) {
            case Bytes:
                return byteChunk.getEnd();
            case Buffer:
                return bufferChunk.getEnd();
            case Chars:
                return charChunk.getEnd();

            default:
                return stringValue.length();
        }
    }


    /**
     * Sets the <tt>DataChunk</tt> end position.
     *
     * @param end the <tt>DataChunk</tt> end position.
     */
    @Override
    public void setEnd(int end) {
        switch (type) {
            case Bytes:
                byteChunk.setEnd(end);
                break;
            case Buffer:
                bufferChunk.setEnd(end);
                break;
            case Chars:
                charChunk.setEnd(end);
                break;
            default:
                break;
        }
    }

    /**
     * Returns true if the message bytes starts with the specified string.
     * @param c the character
     * @param fromIndex The start position
     */
    @Override
    public final int indexOf(final char c, final int fromIndex) {
        switch (type) {
            case Bytes:
                return byteChunk.indexOf(c, fromIndex);
            case Buffer:
                return bufferChunk.indexOf(c, fromIndex);
            case String:
                return stringValue.indexOf(c, fromIndex);
            case Chars:
                return charChunk.indexOf(c, fromIndex);

            default:
                return -1;
        }
    }

    /**
     * Returns true if the message bytes starts with the specified string.
     * @param s the string
     * @param fromIndex The start position
     */
    @Override
    public final int indexOf(final String s, final int fromIndex) {
        switch (type) {
            case Bytes:
                return byteChunk.indexOf(s, fromIndex);
            case Buffer:
                return bufferChunk.indexOf(s, fromIndex);
            case String:
                return stringValue.indexOf(s, fromIndex);
            case Chars:
                return charChunk.indexOf(s, fromIndex);

            default:
                return -1;
        }
    }

    @Override
    public final void delete(final int from, final int to) {
        switch (type) {
            case Bytes:
                byteChunk.delete(from, to);
                return;
            case Buffer:
                bufferChunk.delete(from, to);
                return;
            case String:
                stringValue = stringValue.substring(0, from) +
                        stringValue.substring(to, stringValue.length());
                return;
            case Chars:
                charChunk.delete(from, to);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public java.lang.String toString(final int start, final int end) {
        switch (type) {
            case Bytes:
                return byteChunk.toString(start, end);
            case Buffer:
                return bufferChunk.toString(start, end);
            case String:
                return (start == 0 && end == stringValue.length())
                        ? stringValue
                        : stringValue.substring(start, end);
            case Chars:
                return charChunk.toString(start, end);
            default:
                return null;
        }
    }
    
    /**
     * Compares this DataChunk and the passed object.
     * 
     * @param object the Object to compare
     * @return true if the passed object represents another DataChunk and its
     * content is equal to this DataChunk's content.
     */
    @Override
    public boolean equals(final Object object) {
        if (!(object instanceof DataChunk)) {
            return false;
        }
        
        final DataChunk anotherChunk = (DataChunk) object;
        if (isNull() || anotherChunk.isNull()) {
            return isNull() == anotherChunk.isNull();
        }
        
        switch (type) {
            case Bytes:
                return anotherChunk.equals(byteChunk);
            case Buffer:
                return anotherChunk.equals(bufferChunk);
            case String:
                return anotherChunk.equals(stringValue);
            case Chars:
                return anotherChunk.equals(charChunk);

            default:
                return false;
        }
    }
    
    /**
     * Compares the message bytes to the specified String object.
     * @param s the String to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equals(final String s) {
        switch (type) {
            case Bytes:
                return byteChunk.equals(s);
            case Buffer:
                return bufferChunk.equals(s);
            case String:
                return stringValue.equals(s);
            case Chars:
                return charChunk.equals(s);

            default:
                return false;
        }
    }

    /**
     * Compares the message data to the specified ByteChunk.
     * @param byteChunkToCheck the ByteChunk to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equals(final ByteChunk byteChunkToCheck) {
        return equals(byteChunkToCheck.getBuffer(), byteChunkToCheck.getStart(),
                byteChunkToCheck.getLength());
    }

    
    /**
     * Compares the message data to the specified BufferChunk.
     * @param bufferChunkToCheck the BufferChunk to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equals(final BufferChunk bufferChunkToCheck) {
        switch (type) {
            case Bytes:
                return bufferChunkToCheck.equals(byteChunk.getBuffer(),
                        byteChunk.getStart(), byteChunk.getLength());
            case Buffer:
                return bufferChunkToCheck.equals(bufferChunk);
            case String:
                return bufferChunkToCheck.equals(stringValue);
            case Chars:
                return bufferChunkToCheck.equals(charChunk.getBuffer(),
                        charChunk.getStart(), charChunk.getLength());

            default:
                return false;
        }        
    }
    
    /**
     * Compares the message data to the specified CharChunk.
     * @param charChunkToCheck the CharChunk to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equals(final CharChunk charChunkToCheck) {
        switch (type) {
            case Bytes:
                return charChunkToCheck.equals(byteChunk.getBuffer(),
                        byteChunk.getStart(), byteChunk.getLength());
            case Buffer:
                return bufferChunk.equals(charChunkToCheck.getBuffer(),
                        charChunkToCheck.getStart(), charChunkToCheck.getLength());
            case String:
                return charChunkToCheck.equals(stringValue);
            case Chars:
                return charChunk.equals(charChunkToCheck.getBuffer(),
                        charChunkToCheck.getStart(), charChunkToCheck.getLength());

            default:
                return false;
        }        
    }
    
    /**
     * Compares the message data to the specified byte[].
     * @param bytes the byte[] to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equals(final byte[] bytes) {
        return equals(bytes, 0, bytes.length);
    }
    
    /**
     * Compares the message data to the specified byte[].
     * @param bytes the byte[] to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equals(final byte[] bytes, final int start, final int len) {
        switch (type) {
            case Bytes:
                return byteChunk.equals(bytes, start, len);
            case Buffer:
                return bufferChunk.equals(bytes, start, len);
            case String:
                return ByteChunk.equals(bytes, start, len, stringValue);
            case Chars:
                return charChunk.equals(bytes, start, len);

            default:
                return false;
        }
    }
    
    /**
     * Compares this DataChunk and the passed object ignoring case considerations.
     * 
     * @param object the Object to compare
     * @return true if the passed object represents another DataChunk and its
     * content is equal to this DataChunk's content ignoring case considerations.
     */
    public boolean equalsIgnoreCase(final Object object) {
        if (!(object instanceof DataChunk)) {
            return false;
        }
        
        final DataChunk anotherChunk = (DataChunk) object;
        if (isNull() || anotherChunk.isNull()) {
            return isNull() == anotherChunk.isNull();
        }
        
        switch (type) {
            case Bytes:
                return anotherChunk.equalsIgnoreCase(byteChunk);
            case Buffer:
                return anotherChunk.equalsIgnoreCase(bufferChunk);
            case String:
                return anotherChunk.equalsIgnoreCase(stringValue);
            case Chars:
                return anotherChunk.equalsIgnoreCase(charChunk);

            default:
                return false;
        }
    }
    
    /**
     * Compares the message bytes to the specified String object ignoring case considerations.
     * 
     * @param s the String to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equalsIgnoreCase(final String s) {
        switch (type) {
            case Bytes:
                return byteChunk.equalsIgnoreCase(s);
            case Buffer:
                return bufferChunk.equalsIgnoreCase(s);
            case String:
                return stringValue.equalsIgnoreCase(s);
            case Chars:
                return charChunk.equalsIgnoreCase(s);

            default:
                return false;
        }
    }

    /**
     * Compares the message data to the specified ByteChunk ignoring case considerations.
     * @param byteChunkToCheck the ByteChunk to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equalsIgnoreCase(final ByteChunk byteChunkToCheck) {
        return equalsIgnoreCase(byteChunkToCheck.getBuffer(), byteChunkToCheck.getStart(),
                byteChunkToCheck.getLength());
    }

    
    /**
     * Compares the message data to the specified BufferChunk ignoring case considerations.
     * @param bufferChunkToCheck the BufferChunk to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equalsIgnoreCase(final BufferChunk bufferChunkToCheck) {
        switch (type) {
            case Bytes:
                return bufferChunkToCheck.equalsIgnoreCase(byteChunk.getBuffer(),
                        byteChunk.getStart(), byteChunk.getLength());
            case Buffer:
                return bufferChunkToCheck.equalsIgnoreCase(bufferChunk);
            case String:
                return bufferChunkToCheck.equalsIgnoreCase(stringValue);
            case Chars:
                return bufferChunkToCheck.equalsIgnoreCase(charChunk.getBuffer(),
                        charChunk.getStart(), charChunk.getLength());

            default:
                return false;
        }        
    }
    
    /**
     * Compares the message data to the specified CharChunk ignoring case considerations.
     * @param charChunkToCheck the CharChunk to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equalsIgnoreCase(final CharChunk charChunkToCheck) {
        switch (type) {
            case Bytes:
                return charChunkToCheck.equalsIgnoreCase(byteChunk.getBuffer(),
                        byteChunk.getStart(), byteChunk.getLength());
            case Buffer:
                return bufferChunk.equalsIgnoreCase(charChunkToCheck.getBuffer(),
                        charChunkToCheck.getStart(), charChunkToCheck.getLength());
            case String:
                return charChunkToCheck.equalsIgnoreCase(stringValue);
            case Chars:
                return charChunk.equalsIgnoreCase(charChunkToCheck.getBuffer(),
                        charChunkToCheck.getStart(), charChunkToCheck.getLength());

            default:
                return false;
        }        
    }
    
    /**
     * Compares the message data to the specified byte[] ignoring case considerations.
     * @param bytes the byte[] to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equalsIgnoreCase(final byte[] bytes) {
        return equalsIgnoreCase(bytes, 0, bytes.length);
    }
    
    /**
     * Compares the message data to the specified byte[] ignoring case considerations.
     * @param bytes the byte[] to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equalsIgnoreCase(final byte[] bytes, final int start, final int len) {
        switch (type) {
            case Bytes:
                return byteChunk.equalsIgnoreCase(bytes, start, len);
            case Buffer:
                return bufferChunk.equalsIgnoreCase(bytes, start, len);
            case String:
                return ByteChunk.equalsIgnoreCase(bytes, start, len, stringValue);
            case Chars:
                return charChunk.equalsIgnoreCase(bytes, start, len);

            default:
                return false;
        }
    }
    
    /**
     * Returns DataChunk hash code.
     * @return DataChunk hash code.
     */
    @Override
    public int hashCode() {
        switch (type) {
            case Bytes:
                return byteChunk.hash();
            case Buffer:
                return bufferChunk.hash();
            case String:
                return stringValue.hashCode();
            case Chars:
                return charChunk.hash();
            default:
                return 0;
        }
    }

    /**
     * Compares the data chunk to the specified byte array representing
     * lower-case ASCII characters.
     *
     * @param b the <code>byte[]</code> to compare
     *
     * @return true if the comparison succeeded, false otherwise
     *
     * @since 2.1.2
     */
    public final boolean equalsIgnoreCaseLowerCase(final byte[] b) {
        switch (type) {
            case Bytes:
                return byteChunk.equalsIgnoreCaseLowerCase(b);
            case Buffer:
                return bufferChunk.equalsIgnoreCaseLowerCase(b);
            case String:
                return equalsIgnoreCaseLowerCase(stringValue, b);
            case Chars:
                return charChunk.equalsIgnoreCaseLowerCase(b);

            default:
                return false;
        }
    }
    
    /**
     * Returns <code>true</code> if the <code>DataChunk</code> starts with
     * the specified string.
     * @param s the string
     * @param pos The start position
     *
     * @return <code>true</code> if the <code>DataChunk</code> starts with
     *  the specified string.
     */
    public final boolean startsWith(final String s, final int pos) {
        switch (type) {
            case Bytes:
                return byteChunk.startsWith(s, pos);
            case Buffer:
                return bufferChunk.startsWith(s, pos);
            case String:
                if (stringValue.length() < pos + s.length()) {
                    return false;
                }

                for (int i = 0; i < s.length(); i++) {
                    if (s.charAt(i) != stringValue.charAt(pos + i)) {
                        return false;
                    }
                }
                return true;
            case Chars:
                return charChunk.startsWith(s, pos);

            default:
                return false;
        }
    }
    
    /**
     * Returns <code>true</code> if the <code>DataChunk</code> starts with
     * the specified string.
     *  
     * @param s the string
     * @param pos The start position
     *
     * @return <code>true</code> if the </code>DataChunk</code> starts with
     *  the specified string.
     */
    public final boolean startsWithIgnoreCase(final String s, final int pos) {
        switch (type) {
            case Bytes:
                return byteChunk.startsWithIgnoreCase(s, pos);
            case Buffer:
                return bufferChunk.startsWithIgnoreCase(s, pos);
            case String:
                if (stringValue.length() < pos + s.length()) {
                    return false;
                }

                for (int i = 0; i < s.length(); i++) {
                    if (Ascii.toLower(s.charAt(i))
                            != Ascii.toLower(stringValue.charAt(pos + i))) {
                        return false;
                    }
                }
                return true;
            case Chars:
                return charChunk.startsWithIgnoreCase(s, pos);

            default:
                return false;
        }
    }
    
    public final boolean isNull() {
        return type == Type.None ||
                (byteChunk.isNull() && bufferChunk.isNull() &&
                stringValue == null && charChunk.isNull());
    }

    protected void resetBuffer() {
        bufferChunk.recycle();
    }

    protected void resetCharChunk() {
        charChunk.recycle();
    }

    protected void resetByteChunk() {
        byteChunk.recycleAndReset();
    }

    protected void resetString() {
        stringValue = null;
    }
    
    protected void reset() {
        stringValue = null;
        if (type == Type.Bytes) {
            byteChunk.recycleAndReset();  
        } else if (type == Type.Buffer) {
            bufferChunk.recycle();
        } else if (type == Type.Chars) {
            charChunk.recycle();
        }
        
        type = Type.None;
    }

    public void recycle() {
        reset();
    }

    private static boolean equalsIgnoreCase(String s, byte[] b) {
        final int len = b.length;
        if (s.length() != len) {
            return false;
        }

        for (int i = 0; i < len; i++) {
            if (Ascii.toLower(s.charAt(i)) != Ascii.toLower(b[i])) {
                return false;
            }
        }

        return true;
    }

    /**
     * Compares the String to the specified byte array representing
     * lower-case ASCII characters.
     *
     * @param b the <code>byte[]</code> to compare
     *
     * @return true if the comparison succeeded, false otherwise
     *
     * @since 2.1.2
     */
    private static boolean equalsIgnoreCaseLowerCase(final String s, final byte[] b) {
        final int len = b.length;
        if (s.length() != len) {
            return false;
        }

        for (int i = 0; i < len; i++) {
            if (Ascii.toLower(s.charAt(i)) != b[i]) {
                return false;
            }
        }

        return true;
    }

    private void setBytesInternal(final byte[] array,
                                   final int position,
                                   final int limit) {
        byteChunk.setBytes(array, position, limit - position);
        switchToByteChunk();
    }

    private void setBufferInternal(final Buffer buffer,
                                   final int position,
                                   final int limit) {
        bufferChunk.setBufferChunk(buffer, position, limit, limit);
        switchToBufferChunk();
    }

    private void setCharsInternal(final char[] chars,
                                  final int position,
                                  final int limit) {
        charChunk.setChars(chars, position, limit - position);
        switchToCharChunk();
    }

    private void setStringInternal(String string) {
        stringValue = string;
        switchToString();
    }

    private void switchToByteChunk() {
        if (type == Type.Buffer) {
            resetBuffer();
        } else if (type == Type.Chars) {
            resetCharChunk();
        }
        
        resetString();
        type = Type.Bytes;
//        onContentChanged();
    }

    private void switchToBufferChunk() {
        if (type == Type.Bytes) {
            resetByteChunk();
        } else if (type == Type.Chars) {
            resetCharChunk();
        }
        resetString();
        
        type = Type.Buffer;
//        onContentChanged();
    }

    private void switchToCharChunk() {
        if (type == Type.Bytes) {
            resetByteChunk();
        } else if (type == Type.Buffer) {
            resetBuffer();
        }
        
        resetString();
        type = Type.Chars;
//        onContentChanged();
    }

    private void switchToString() {
        if (type == Type.Bytes) {
            resetByteChunk();
        } else if (type == Type.Chars) {
            resetCharChunk();
        } else if (type == Type.Buffer) {
            resetBuffer();
        }

        type = Type.String;
//        onContentChanged();
    }

    final static class Immutable extends DataChunk {
        public Immutable(DataChunk original) {
            super.set(original);
        }

        @Override
        public DataChunk toImmutable() {
            return this;
        }

        @Override
        public void set(DataChunk value) {
        }

        @Override
        public void setBuffer(Buffer buffer, int start, int end) {
        }

        @Override
        public void setString(String string) {
        }

        @Override
        public void setChars(char[] chars, int position, int limit) {
        }

        @Override
        protected final void resetBuffer() {
        }

        @Override
        protected final void resetString() {
        }

        @Override
        protected void resetCharChunk() {
        }

        @Override
        protected void reset() {
        }

        @Override
        public void recycle() {
        }
    }
}
