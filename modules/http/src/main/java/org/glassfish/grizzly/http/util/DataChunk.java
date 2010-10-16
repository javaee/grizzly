/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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
import java.nio.charset.Charset;
import org.glassfish.grizzly.Buffer;

/**
 * {@link Buffer} chunk representation.
 * Helps HTTP module to avoid redundant String creation.
 * 
 * @author Alexey Stashok
 */
public class DataChunk {
    public enum Type {None, Buffer, Chars, String};
    
    public static DataChunk newInstance() {
        return new DataChunk();
    }
    
    Type type = Type.None;

    final BufferChunk bufferChunk = new BufferChunk();
    final CharChunk charChunk = new CharChunk();
    
    String stringValue;

    protected DataChunk() {
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

    public BufferChunk getBufferChunk() {
        return bufferChunk;
    }

    public void setBuffer(final Buffer buffer,
                          final int position,
                          final int limit) {
        setBufferInternal(buffer, position, limit);
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
    
    /**
     * Compares the message bytes to the specified String object.
     * @param s the String to compare
     * @return true if the comparison succeeded, false otherwise
     */
    public boolean equals(String s) {
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
        charChunk.recycle();
        bufferChunk.recycle();
        type = Type.None;
    }

    public void recycle() {
        reset();
    }

    private void setBufferInternal(final Buffer buffer, final int position,
            final int limit) {
        type = Type.Buffer;

        bufferChunk.setBufferChunk(buffer, position, limit);

        resetString();
        resetCharChunk();
        onContentChanged();
    }

    private void setCharsInternal(final char[] chars,
                                  final int position,
                                  final int limit) {
        type = Type.Chars;
        charChunk.setChars(chars, position, limit - position);

        resetString();
        resetBuffer();
        onContentChanged();
    }

    private void setStringInternal(String string) {
        type = Type.String;
        stringValue = string;
        resetBuffer();
        resetCharChunk();
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
            return;
        }

        @Override
        public void setBuffer(Buffer buffer, int start, int end) {
            return;
        }

        @Override
        public void setString(String string) {
            return;
        }

        @Override
        public void setChars(char[] chars, int position, int limit) {
            return;
        }

        @Override
        protected final void resetBuffer() {
            return;
        }

        @Override
        protected final void resetString() {
            return;
        }

        @Override
        protected void resetCharChunk() {
        }

        @Override
        protected void reset() {
            return;
        }

        @Override
        public void recycle() {
            return;
        }
    }
}
