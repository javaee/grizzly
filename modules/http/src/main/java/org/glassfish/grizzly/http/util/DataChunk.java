/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2011 Oracle and/or its affiliates. All rights reserved.
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
public class DataChunk {

    public enum Type {None, Buffer, Chars, String}
    
    public static DataChunk newInstance() {
        return newInstance(new BufferChunk(), new CharChunk(), null);
    }

    public static DataChunk newInstance(final BufferChunk bufferChunk,
            final CharChunk charChunk, final String stringValue) {
        return new DataChunk(bufferChunk, charChunk, stringValue);
    }
    
    Type type = Type.None;

    final BufferChunk bufferChunk;
    final CharChunk charChunk;
    
    String stringValue;

    protected DataChunk() {
        this(new BufferChunk(), new CharChunk(), null);
    }

    protected DataChunk(final BufferChunk bufferChunk,
            final CharChunk charChunk, final String stringValue) {
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

    public void set(DataChunk value) {
//        reset();

        switch(value.getType()) {
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

        onContentChanged();
    }

    public void notifyDirectUpdate() {
        switch (type) {
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

    public void setBuffer(final Buffer buffer, final boolean disposeOnRecycle) {
        setBufferInternal(buffer,
                          buffer.position(),
                          buffer.limit(),
                          disposeOnRecycle);
    }


    public CharChunk getCharChunk() {
        return charChunk;
    }

    public void setChars(final char[] chars, final int position, final int limit) {
        setCharsInternal(chars, position, limit);
    }

    public void setString(String string) {
        setStringInternal(string);
    }

    /**
     *  Copy the src into this DataChunk, allocating more space if needed
     */
    public void duplicate(final DataChunk src) {
        switch (src.getType()) {
            case Buffer:
                final BufferChunk bc = src.getBufferChunk();
                bufferChunk.allocate(2 * bc.getLength());
                bufferChunk.append(bc);
                switchToBufferChunk();
                break;
            case Chars:
                final CharChunk cc = src.getCharChunk();
                charChunk.allocate(2 * cc.getLength(), -1);
                try {
                    charChunk.append(cc);
                } catch (IOException ignored) {
                    // should never occur
                }

                switchToCharChunk();
                break;
            case String:
                setString(src.toString());
                break;
            default:
                recycle();
        }
    }

    public void toChars(final Charset charset) throws CharConversionException {
        switch (type) {
            case Buffer:
                bufferChunk.toChars(charChunk, charset);
                setChars(charChunk.getChars(), charChunk.getStart(), charChunk.getEnd());
                return;
            case String:
                setChars(stringValue.toCharArray(), 0, stringValue.length());
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

    protected void onContentChanged() {
    }

    /**
     * Returns the <tt>DataChunk</tt> length.
     *
     * @return the <tt>DataChunk</tt> length.
     */
    public int getLength() {
        switch (type) {
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
    public int getStart() {
        switch (type) {
            case Buffer:
                return bufferChunk.getStart();
            case Chars:
                return charChunk.getStart();

            default:
                return 0;
        }
    }

    /**
     * Returns the <tt>DataChunk</tt> end position.
     *
     * @return the <tt>DataChunk</tt> end position.
     */
    public int getEnd() {
        switch (type) {
            case Buffer:
                return bufferChunk.getEnd();
            case Chars:
                return charChunk.getEnd();

            default:
                return stringValue.length();
        }
    }

    /**
     * Returns true if the message bytes starts with the specified string.
     * @param c the character
     * @param fromIndex The start position
     */
    public int indexOf(char c, int fromIndex) {
        switch (type) {
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
    public int indexOf(String s, int fromIndex) {
        switch (type) {
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

    public void delete(final int from, final int to) {
        switch (type) {
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
     * Compares the message bytes to the specified String object.
     * @param s the String to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equals(final String s) {
        switch (type) {
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
     * Returns DataChunk hash code.
     * @return DataChunk hash code.
     */
    @Override
    public int hashCode() {
        switch (type) {
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
     * Compares the message bytes to the specified String object.
     * @param s the String to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equalsIgnoreCase(String s) {
        switch (type) {
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
     * Compares the message bytes to the specified String object.
     *
     * @param b the <code>byte[]</code> to compare
     *
     * @return true if the comparison succeeded, false otherwise
     *
     * @since 2.1.2
     */
    public boolean equalsIgnoreCase(byte[] b) {
        switch (type) {
            case Buffer:
                return bufferChunk.equalsIgnoreCase(b);
            case String:
                return equalsIgnoreCase(stringValue, b);
            case Chars:
                return charChunk.equalsIgnoreCase(b);

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
    public boolean startsWith(String s, int pos) {
        switch (type) {
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
    public boolean startsWithIgnoreCase(String s, int pos) {
        switch (type) {
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
                (bufferChunk.isNull() && stringValue == null && charChunk.isNull());
    }

    protected void resetBuffer() {
        bufferChunk.recycle();
    }

    protected void resetCharChunk() {
        charChunk.recycle();
    }

    protected void resetString() {
        stringValue = null;
    }
    
    protected void reset() {
        stringValue = null;
        if (type == Type.Chars) {
            charChunk.recycle();
        } else if (type == Type.Buffer) {
            bufferChunk.recycle();
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

    private void setBufferInternal(final Buffer buffer,
                                   final int position,
                                   final int limit) {
        setBufferInternal(buffer, position, limit, false);
    }

    private void setBufferInternal(final Buffer buffer,
                                   final int position,
                                   final int limit,
                                   final boolean disposeOnRecycle) {
        bufferChunk.setBufferChunk(buffer, position, limit, disposeOnRecycle);
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

    private void switchToBufferChunk() {
        if (type == Type.Chars) {
            resetCharChunk();
        }
        resetString();
        
        type = Type.Buffer;
        onContentChanged();
    }

    private void switchToCharChunk() {
        if (type == Type.Buffer) {
            resetBuffer();
        }
        
        resetString();
        type = Type.Chars;
        onContentChanged();
    }

    private void switchToString() {
        if (type == Type.Chars) {
            resetCharChunk();
        } else if (type == Type.Buffer) {
            resetBuffer();
        }

        type = Type.String;
        onContentChanged();
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
