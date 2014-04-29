/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013-2014 Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import org.glassfish.grizzly.utils.Charsets;

import static org.glassfish.grizzly.http.util.HttpCodecUtils.*;

/**
 * This class serves as a Content-Type holder, plus it implements useful
 * utility methods to work with content-type.
 * 
 * @author Alexey Stashok
 */
public class ContentType {
    private static final String CHARSET_STRING = ";charset=";
    private static final byte[] CHARSET_BYTES =
            CHARSET_STRING.getBytes(Charsets.ASCII_CHARSET);
    
    /**
     * @return {@link SettableContentType}, the mutable {@link ContentType}
     *         representation.
     */
    public static SettableContentType newSettableContentType() {
        return new SettableContentType();
    }
    
    /**
     * Creates a {@link ContentType} wrapper over a {@link String}
     * content-type representation.
     * 
     * @param contentType {@link String} content-type representation
     * @return a {@link ContentType} wrapper over a {@link String}
     *         content-type representation
     */
    public static ContentType newContentType(final String contentType) {
        return new ContentType(contentType);
    }

    /**
     * Creates a {@link ContentType} wrapper over the passed mime-type and
     * character encoding.
     * 
     * @param mimeType {@link String} mimeType-type representation
     *        (like "text/plain", "text/html", etc), which doesn't contain charset
     *        information
     * @param characterEncoding charset attribute to be used with the mime-type
     * 
     * @return a {@link ContentType} wrapper over the passed mime-type and
     *         character encoding
     */
    public static ContentType newContentType(final String mimeType,
            final String characterEncoding) {
        final ContentType ct = new ContentType();
        ct.setMimeType(mimeType);
        ct.setCharacterEncoding(characterEncoding);
        return ct;
    }
    
    // unparsed content-type string, as it was passed by user
    private String unparsedContentType;
    // true, if unparsedContentType has a content-type, that hasn't been parsed,
    // or false otherwise
    private boolean isParsed = true;
    
    // the content-type string prepared for serialization
    private String compiledContentType;
    // the content-type array prepared for serialization
    private byte[] compiledContentTypeArray;
    
    // mime-type part of the content-type (doesn't contain charset attribute)
    private String mimeType;
    
    // charset encoding without quotes
    private String characterEncoding;
    // charset encoding, might be quoted
    private String quotedCharsetValue;
    // true, if charset is set, or false if charset is not set,
    // or unparsedContentType hasn't been parsed
    private boolean isCharsetSet;

    // array holding actual compiled content-type information.
    private byte[] array = new byte[32];
    // length, within 'array', of the content-type information.
    private int len = -1;

    ContentType() {
    }

    ContentType(final String contentType) {
        set(contentType);
    }
    
    /**
     * Prepare the <tt>ContentType</tt> for the serialization.
     * 
     * This method might be particularly useful if we use the same <tt>ContentType</tt>
     * over and over for different responses, so that the <tt>ContentType</tt>
     * will not have to be parsed and prepared for each response separately.
     * 
     * @return this <tt>ContentType</tt>
     */
    public ContentType prepare() {
        getByteArray();
        return this;
    }
    
    /**
     * @return <tt>true</tt> if either mime-type or character encoding is set, or
     *         <tt>false</tt> otherwise
     */
    public boolean isSet() {
        return isMimeTypeSet() || quotedCharsetValue != null;
    }
    
    /**
     * @return <tt>true</tt> if mime-type is set, or <tt>false</tt> otherwise
     */
    public boolean isMimeTypeSet() {
        return !isParsed || mimeType != null;
    }
    
    /**
     * Returns the mime-type part of the content-type (the part without charset attribute).
     * 
     * @return the mime-type part of the content-type (the part without charset attribute)
     */
    public String getMimeType() {
        if (!isParsed) {
            parse();
        }
        
        return mimeType;
    }

    /**
     * Sets the mime-type part of the content-type (the part without charset attribute).
     * 
     * @param mimeType the mime-type part of the content-type (the part without charset attribute)
     */
    protected void setMimeType(final String mimeType) {
        if (!isParsed) {
            parse();
        }
        
        this.mimeType = mimeType;
        
        compiledContentType = !isCharsetSet ? mimeType : null;
        this.compiledContentTypeArray = null;
    }
    
    /**
     * @return the character encoding (the content-type's charset attribute value)
     */
    public String getCharacterEncoding() {
        if (!isParsed) {
            parse();
        }
        
        return characterEncoding;
    }

    /**
     * Sets the the character encoding (the content-type's  charset attribute value).
     * 
     * @param charset the character encoding (the content-type's  charset attribute value)
     */
    protected void setCharacterEncoding(final String charset) {
        if (!isParsed) {
            // parse mime-type
            parse();
        }
        
        // START SJSAS 6316254
        quotedCharsetValue = charset;
        // END SJSAS 6316254
        isCharsetSet = charset != null;
        
        if (isCharsetSet) {
            compiledContentType = null;
            characterEncoding = charset.replace('"', ' ').trim();
        } else {
            compiledContentType = mimeType;
            characterEncoding = null;
        }
        this.compiledContentTypeArray = null;
    }

    /**
     * Used in conjunction with {@link #getByteArray()}.  The array returned by
     * the aforementioned method may be larger than the data contained therein.
     * This method will return the proper data length.
     *
     * @return the data length within the array returned by {@link #getByteArray()}
     */
    public int getArrayLen() {
        return len;
    }

    /**
     * @return the byte array representation of the content-type
     */
    public byte[] getByteArray() {
        // if there's prepared byte array - return it
        if (compiledContentTypeArray != null) {
            //len = compiledContentTypeArray.length;
            return compiledContentTypeArray;
        }
        
        // if there's prepared String - convert it to a byte array
        if (compiledContentType != null) {
            checkArray(compiledContentType.length());
            compiledContentTypeArray = toCheckedByteArray(compiledContentType, array, 0);
            len = compiledContentType.length();
            return compiledContentTypeArray;
        }
        
        if (!isParsed) {
            // if we have unparsed content-type and no charset set independently -
            // convert the unparsedContentType to a byte array
            if (quotedCharsetValue == null) {
                checkArray(unparsedContentType.length());
                compiledContentTypeArray = toCheckedByteArray(unparsedContentType, array, 0);
                return compiledContentTypeArray;
            }
            
            // otherwise parse the unparsed content-type
            parse();
        }
        
        if (mimeType == null) {
            return EMPTY_ARRAY;
        }
        

        final int mtsz = mimeType.length();
        
        if (isCharsetSet) {
            // if isCharsetSet - build a content-type array: mimeType + CHARSET_BYTES + quotedCharsetValue
            final int qcssz = quotedCharsetValue.length();
            final int len = mtsz + qcssz + CHARSET_BYTES.length;
            checkArray(len);

            toCheckedByteArray(mimeType, array, 0);
            System.arraycopy(CHARSET_BYTES, 0, array, mtsz, CHARSET_BYTES.length);

            final int offs = mtsz + CHARSET_BYTES.length;
            toCheckedByteArray(quotedCharsetValue, array, offs);
            this.len = len;
        } else {
            // otherwise build the array based on mimeType only
            checkArray(mtsz);
            toCheckedByteArray(mimeType, array, 0);
            this.len = mtsz;
        }

        compiledContentTypeArray = array;
        
        return compiledContentTypeArray;
    }
    
    /**
     * @return the content type of this HTTP message.
     */
    public String get() {
        if (compiledContentType != null) {
            return compiledContentType;
        }
        
        if (!isParsed) {
            parse();
        }
        
        String ret = mimeType;

        if (ret != null && isCharsetSet) {
            final StringBuilder sb =
                    new StringBuilder(ret.length() + quotedCharsetValue.length() + 9);
            ret = sb.append(ret).append(CHARSET_STRING).append(quotedCharsetValue).toString();
        }

        compiledContentType = ret;
        
        return ret;
    }
    
    /**
     * Sets the content type.
     *
     * This method must preserve any charset that may already have
     * been set via a call to request/response.setContentType(),
     * request/response.setLocale(), or request/response.setCharacterEncoding().
     *
     * @param contentType the content type
     */
    protected void set(final String contentType) {
        if (unparsedContentType != null) {
            // there might be charset assigned, we don't want to lose it
            parse();
        }
        
        unparsedContentType = contentType;
        
        isParsed = (contentType == null);
        mimeType = null;
        
        compiledContentType = !isCharsetSet ? contentType : null;
        compiledContentTypeArray = null;
    }
    
    /**
     * Sets the content type.
     *
     * This method must preserve any charset that may already have
     * been set via a call to request/response.setContentType(),
     * request/response.setLocale(), or request/response.setCharacterEncoding().
     * 
     * This method copies the passed contentType state into this <tt>ContentType</tt>.
     *
     * @param contentType the content type
     */
    protected void set(final ContentType contentType) {
        if (contentType == null) {
            set((String) null);
            return;
        }
        
        this.unparsedContentType = contentType.unparsedContentType;
        this.isParsed = contentType.isParsed;
        this.mimeType = contentType.mimeType;
        
        this.characterEncoding = contentType.characterEncoding;
        this.quotedCharsetValue = contentType.quotedCharsetValue;
        this.isCharsetSet = contentType.isCharsetSet;
        
        this.compiledContentType = contentType.compiledContentType;
        this.compiledContentTypeArray = contentType.compiledContentTypeArray;
        this.array = contentType.array;
        this.len = contentType.len;

    }
    
    /**
     * Parse the unparsedContentType and splitting it into mime-type and character encoding.
     */
    private void parse() {
        isParsed = true;

        final String type = unparsedContentType;
        
        /*
         * Remove the charset param (if any) from the Content-Type, and use it
         * to set the response encoding.
         * The most recent response encoding setting will be appended to the
         * response's Content-Type (as its charset param) by getContentType();
         */
        boolean hasCharset = false;
        int semicolonIndex = -1;
        int index = type.indexOf(';');
        while (index != -1) {
            int len = type.length();
            semicolonIndex = index;
            index++;
            while (index < len && type.charAt(index) == ' ') {
                index++;
            }
            if (index+8 < len
                    && type.charAt(index) == 'c'
                    && type.charAt(index+1) == 'h'
                    && type.charAt(index+2) == 'a'
                    && type.charAt(index+3) == 'r'
                    && type.charAt(index+4) == 's'
                    && type.charAt(index+5) == 'e'
                    && type.charAt(index+6) == 't'
                    && type.charAt(index+7) == '=') {
                hasCharset = true;
                break;
            }
            index = type.indexOf(';', index);
        }

        if (!hasCharset) {
            mimeType = type;
            return;
        }

        mimeType = type.substring(0, semicolonIndex);
        String tail = type.substring(index+8);
        int nextParam = tail.indexOf(';');
        String charsetValue;
        if (nextParam != -1) {
            mimeType += tail.substring(nextParam);
            charsetValue = tail.substring(0, nextParam);
        } else {
            charsetValue = tail;
        }

        // The charset value may be quoted, but must not contain any quotes.
        if (charsetValue != null && charsetValue.length() > 0) {
            isCharsetSet = true;
            // START SJSAS 6316254
            quotedCharsetValue = charsetValue;
            // END SJSAS 6316254
            characterEncoding = charsetValue.replace('"', ' ').trim();
        }
    }
    
    /**
     * Serializes this <tt>ContentType</tt> value into a passed {@link DataChunk}.
     * 
     * @param dc {@link DataChunk}
     */
    public void serializeToDataChunk(final DataChunk dc) {
        if (compiledContentTypeArray != null) {
            dc.setBytes(compiledContentTypeArray, 0, len);
            return;
        } else if (compiledContentType != null) {
            dc.setString(compiledContentType);
            return;
        }
        
        dc.setBytes(getByteArray(), 0, len);
    }
    
    /**
     * Resets the <tt>ContentType</tt> state.
     */
    protected void reset() {
        unparsedContentType = null;
        compiledContentType = null;
        quotedCharsetValue = null;
        characterEncoding = null;
        compiledContentTypeArray = null;
        mimeType = null;
        isCharsetSet = false;
        isParsed = true;
        len = -1;
    }

    @Override
    public String toString() {
        return get();
    }

    /**
     * Parse the character encoding from the specified content type header.
     * If the content type is null, or there is no explicit character encoding,
     * <code>null</code> is returned.
     *
     * @param contentType a content type header
     * @return the contentType's charset attribute value
     */
    public static String getCharsetFromContentType(String contentType) {

        if (contentType == null) {
            return null;
        }
        
        int start = contentType.indexOf("charset=");
        if (start < 0) {
            return null;
        }
        String encoding = contentType.substring(start + 8);
        int end = encoding.indexOf(';');
        if (end >= 0) {
            encoding = encoding.substring(0, end);
        }
        encoding = encoding.trim();
        if ((encoding.length() > 2) && (encoding.startsWith("\""))
                && (encoding.endsWith("\""))) {
            encoding = encoding.substring(1, encoding.length() - 1);
        }
        return (encoding.trim());
    }
    
    /**
     * Removes the charset attribute from the content-type represented by an array.
     * The returned array will be completely independent of the source one.
     * 
     * @param contentType the content-type represented by an array
     * @return a new array, which represents the same content-type as a given one,
     *         but without charset attribute
     */
    public static byte[] removeCharset(final byte[] contentType) {
        final int ctLen = contentType.length;
        
        boolean hasCharset = false;
        int semicolon1Index = -1;
        int semicolon2Index = -1;
        
        int index = ByteChunk.indexOf(contentType, 0, ctLen, ';');
        
        while (index != -1) {
            semicolon1Index = index;
            index++;
            while (index < ctLen && contentType[index] == ' ') {
                index++;
            }
            
            if (index + 8 < ctLen
                    && contentType[index] == 'c'
                    && contentType[index+1] == 'h'
                    && contentType[index+2] == 'a'
                    && contentType[index+3] == 'r'
                    && contentType[index+4] == 's'
                    && contentType[index+5] == 'e'
                    && contentType[index+6] == 't'
                    && contentType[index+7] == '=') {
                
                semicolon2Index = ByteChunk.indexOf(contentType, index + 8, ctLen, ';');
                hasCharset = true;
                break;
            }
            index = ByteChunk.indexOf(contentType, index, ctLen, ';');
        }

        if (!hasCharset) {
            return contentType;
        }

        final byte[] array;
        if (semicolon2Index == -1) {
            array = Arrays.copyOf(contentType, semicolon1Index);
        } else {
            array = new byte[ctLen - (semicolon2Index - semicolon1Index)];
            System.arraycopy(contentType, 0, array, 0, semicolon1Index);
            System.arraycopy(contentType, semicolon2Index, array, semicolon1Index, ctLen - semicolon2Index);
        }
        
        return array;
    }
    
    /**
     * Composes a content-type array, based on the mime-type represented by a byte array
     * and a charset attribute value, represented by a {@link String}.
     * 
     * @param mimeType a mime-type part of the content-type (doesn't contain charset attribute)
     * @param charset charset attribute value
     * 
     * @return a content-type array, composed of the mime-type represented by a byte array
     *         and a charset attribute value, represented by a {@link String}
     */
    public static byte[] compose(final byte[] mimeType, final String charset) {
        final int csLen = charset.length();
        final int additionalLen = CHARSET_BYTES.length + csLen;
        
        final byte[] contentType = Arrays.copyOf(mimeType,
                mimeType.length + additionalLen);
        
        System.arraycopy(CHARSET_BYTES, 0,
                contentType, mimeType.length, CHARSET_BYTES.length);
        
        return toCheckedByteArray(charset, contentType,
                mimeType.length + CHARSET_BYTES.length);
    }

    private void checkArray(final int len) {
        if (len > array.length) {
            array = new byte[len << 1];
        }
    }
    
    /**
     * Mutable {@link ContentType} object.
     */
    public final static class SettableContentType extends ContentType {

        SettableContentType() {
        }

        @Override
        public void reset() {
            super.reset();
        }

        @Override
        public void set(final ContentType contentType) {
            super.set(contentType);
        }

        @Override
        public void set(final String type) {
            super.set(type);
        }

        @Override
        public void setCharacterEncoding(final String charset) {
            super.setCharacterEncoding(charset);
        }

        @Override
        public void setMimeType(final String mimeType) {
            super.setMimeType(mimeType);
        }
        
    }
}
