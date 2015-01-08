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

import java.util.Iterator;
import org.glassfish.grizzly.Buffer;

/* XXX XXX XXX Need a major rewrite  !!!!
 */
/**
 * This class is used to contain standard Internet message headers,
 * used for SMTP (RFC822) and HTTP (RFC2068) messages as well as for
 * MIME (RFC 2045) applications such as transferring typed data and
 * grouping related items in multipart message bodies.
 *
 * <P> Message headers, as specified in RFC822, include a field name
 * and a field body.  Order has no semantic significance, and several
 * fields with the same name may exist.  However, most fields do not
 * (and should not) exist more than once in a header.
 *
 * <P> Many kinds of field body must conform to a specified syntax,
 * including the standard parenthesized comment syntax.  This class
 * supports only two simple syntaxes, for dates and integers.
 *
 * <P> When processing headers, care must be taken to handle the case of
 * multiple same-name fields correctly.  The values of such fields are
 * only available as strings.  They may be accessed by index (treating
 * the header as an array of fields), or by name (returning an array
 * of string values).
 */

/* Headers are first parsed and stored in the order they are
received. This is based on the fact that most servlets will not
directly access all headers, and most headers are single-valued.
( the alternative - a hash or similar data structure - will add
an overhead that is not needed in most cases )

Apache seems to be using a similar method for storing and manipulating
headers.

Future enhancements:
- hash the headers the first time a header is requested ( i.e. if the
servlet needs direct access to headers).
- scan "common" values ( length, cookies, etc ) during the parse
( addHeader hook )

 */
/**
 *  Memory-efficient repository for Mime Headers. When the object is recycled, it
 *  will keep the allocated headers[] and all the MimeHeaderField - no GC is generated.
 *
 *  For input headers it is possible to use the DataChunk for Fields - so no GC
 *  will be generated.
 *
 *  The only garbage is generated when using the String for header names/values -
 *  this can't be avoided when the servlet calls header methods, but is easy
 *  to avoid inside tomcat. The goal is to use _only_ DataChunk-based Fields,
 *  and reduce to 0 the memory overhead of tomcat.
 *
 *  TODO:
 *  XXX one-buffer parsing - for HTTP ( other protocols don't need that )
 *  XXX remove unused methods
 *  XXX External enumerations, with 0 GC.
 *  XXX use HeaderName ID
 *  
 * 
 * @author dac@eng.sun.com
 * @author James Todd [gonzo@eng.sun.com]
 * @author Costin Manolache
 * @author kevin seguin
 */
public class MimeHeaders {

    public static final int MAX_NUM_HEADERS_UNBOUNDED = -1;

    public static final int MAX_NUM_HEADERS_DEFAULT = 100;

    /** Initial size - should be == average number of headers per request
     *  XXX  make it configurable ( fine-tuning of web-apps )
     */
    public static final int DEFAULT_HEADER_SIZE = 8;
    /**
     * The header fields.
     */
    private MimeHeaderField[] headers = new MimeHeaderField[DEFAULT_HEADER_SIZE];
    /**
     * The current number of header fields.
     */
    private int count;

    private int maxNumHeaders = MAX_NUM_HEADERS_DEFAULT;

    /**
     * The header names {@link Iterable}.
     */
    private final Iterable<String> namesIterable = new Iterable<String>() {

        @Override
        public Iterator<String> iterator() {
            return new NamesIterator(MimeHeaders.this);
        }
    };

    /**
     * Creates a new MimeHeaders object using a default buffer size.
     */
    public MimeHeaders() {
    }

    /**
     * Clears all header fields.
     */
    // [seguin] added for consistency -- most other objects have recycle().
    public void recycle() {
        clear();
    }

    /**
     * Clears all header fields.
     */
    public void clear() {
        for (int i = 0; i < count; i++) {
            headers[i].recycle();
        }
        count = 0;
    }

    /**
     * EXPENSIVE!!!  only for debugging.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("=== MimeHeaders ===\n");

        for (int i = 0; i < count; i++) {
            sb.append(headers[i].nameB)
                    .append(" = ")
                    .append(headers[i].valueB)
                    .append('\n');
        }
        
        return sb.toString();
    }

    public void copyFrom(final MimeHeaders source) {
        if (source.size() == 0) {
            return;
        }
        this.maxNumHeaders = source.maxNumHeaders;
        this.count = source.count;
        if (headers.length < count) {

            MimeHeaderField tmp[] = new MimeHeaderField[count * 2];
            System.arraycopy(headers, 0, tmp, 0, headers.length);
            headers = tmp;
        }

        for (int i = 0, len = source.count; i < len; i++) {
            MimeHeaderField sourceField = source.headers[i];
            MimeHeaderField f = headers[i];
            if (f == null) {
                f = new MimeHeaderField();
                headers[i] = f;
            }
            if (sourceField.nameB.type == DataChunk.Type.Buffer) {
                copyBufferChunk(sourceField.nameB, f.nameB);
            } else {
                f.nameB.set(sourceField.nameB);
            }
            if (sourceField.valueB.type == DataChunk.Type.Buffer) {
                copyBufferChunk(sourceField.valueB, f.valueB);
            } else {
                f.valueB.set(sourceField.valueB);
            }
        }

    }

    private static void copyBufferChunk(DataChunk source, DataChunk dest) {
        final BufferChunk bc = source.getBufferChunk();
        int l = bc.getLength();
        byte[] bytes = new byte[l];
        final Buffer b = bc.getBuffer();
        int oldPos = b.position();
        try {
            b.position(bc.getStart());
            bc.getBuffer().get(bytes, 0, l);
            dest.setBytes(bytes);
        } finally {
            b.position(oldPos);
        }
    }

    // -------------------- Idx access to headers ----------
    /**
     * Returns the current number of header fields.
     */
    public int size() {
        return count;
    }

    /**
     * Returns the Nth header name, or null if there is no such header.
     * This may be used to iterate through all header fields.
     */
    public DataChunk getName(int n) {
        return n >= 0 && n < count ? headers[n].getName() : null;
    }

    /**
     * Returns the Nth header value, or null if there is no such header.
     * This may be used to iterate through all header fields.
     */
    public DataChunk getValue(int n) {
        return n >= 0 && n < count ? headers[n].getValue() : null;
    }

    /**
     * Get the header's "serialized" flag.
     *
     * @param n the header index
     * @return the header's "serialized" flag value.
     */
    public boolean isSerialized(int n) {
        if (n >= 0 && n < count) {
            final MimeHeaderField field = headers[n];
            return field.isSerialized();
        }

        return false;
    }

    /**
     * Set the header's "serialized" flag.
     *
     * @param n        the header index
     * @param newValue the new value
     * @return the old header "serialized" flag value.
     */
    public boolean setSerialized(int n, boolean newValue) {
        final boolean value;
        if (n >= 0 && n < count) {
            final MimeHeaderField field = headers[n];
            value = field.isSerialized();
            field.setSerialized(newValue);
        } else {
            value = true;
        }

        return value;
    }

    /**
     * Find the index of a header with the given name.
     */
    public int indexOf(String name, int fromIndex) {
        // We can use a hash - but it's not clear how much
        // benefit you can get - there is an  overhead
        // and the number of headers is small (4-5 ?)
        // Another problem is that we'll pay the overhead
        // of constructing the hashtable

        // A custom search tree may be better
        for (int i = fromIndex; i < count; i++) {
            if (headers[i].getName().equalsIgnoreCase(name)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * Find the index of a header with the given name.
     */
    public int indexOf(final Header header, final int fromIndex) {
        // We can use a hash - but it's not clear how much
        // benefit you can get - there is an  overhead
        // and the number of headers is small (4-5 ?)
        // Another problem is that we'll pay the overhead
        // of constructing the hashtable

        // A custom search tree may be better
        final byte[] bytes = header.getLowerCaseBytes();
        for (int i = fromIndex; i < count; i++) {
            if (headers[i].getName().equalsIgnoreCaseLowerCase(bytes)) {
                return i;
            }
        }
        return -1;
    }


    public boolean contains(final Header header) {
        return (indexOf(header, 0) >= 0);
    }

    public boolean contains(final String header) {
        return (indexOf(header, 0) >= 0);
    }

    // -------------------- --------------------
    /**
     * Returns an enumeration of strings representing the header field names.
     * Field names may appear multiple times in this enumeration, indicating
     * that multiple fields with that name exist in this header.
     */
    public Iterable<String> names() {
        return namesIterable;
    }

    public Iterable<String> values(final String name) {
        return new Iterable<String>() {

            @Override
            public Iterator<String> iterator() {
                return new ValuesIterator(MimeHeaders.this, name);
            }
        };
    }

    public Iterable<String> values(final Header name) {
        return new Iterable<String>() {

            @Override
            public Iterator<String> iterator() {
                return new ValuesIterator(MimeHeaders.this, name.toString());
            }
        };
    }

    // -------------------- Adding headers --------------------
    /**
     * Adds a partially constructed field to the header.  This
     * field has not had its name or value initialized.
     */
    private MimeHeaderField createHeader() {
        if (maxNumHeaders >= 0 && count == maxNumHeaders) {
            throw new MaxHeaderCountExceededException();
        }
        MimeHeaderField mh;
        int len = headers.length;

        if (count >= len) {
            // expand header list array
            int newCount = count * 2;
            if (maxNumHeaders >= 0 && newCount > maxNumHeaders) {
                newCount = maxNumHeaders;
            }
            MimeHeaderField tmp[] = new MimeHeaderField[newCount];
            System.arraycopy(headers, 0, tmp, 0, len);
            headers = tmp;
        }
        if ((mh = headers[count]) == null) {
            headers[count] = mh = new MimeHeaderField();
        }
        count++;
        return mh;
    }

    /** Create a new named header , return the MessageBytes
    container for the new value
     */
    public DataChunk addValue(String name) {
        MimeHeaderField mh = createHeader();
        mh.getName().setString(name);
        return mh.getValue();
    }

    /** Create a new named header , return the MessageBytes
    container for the new value
     */
    public DataChunk addValue(final Header header) {
        MimeHeaderField mh = createHeader();
        mh.getName().setBytes(header.toByteArray());
        return mh.getValue();
    }

    /** Create a new named header using un-translated byte[].
    The conversion to chars can be delayed until
    encoding is known.
     */
    public DataChunk addValue(final byte[] buffer, final int startN,
            final int len) {
        MimeHeaderField mhf = createHeader();
        mhf.getName().setBytes(buffer, startN, startN + len);
        return mhf.getValue();
    }

    /** Create a new named header using un-translated Buffer.
    The conversion to chars can be delayed until
    encoding is known.
     */
    public DataChunk addValue(final Buffer buffer, final int startN,
            final int len) {
        MimeHeaderField mhf = createHeader();
        mhf.getName().setBuffer(buffer, startN, startN + len);
        return mhf.getValue();
    }

    /** 
     * Allow "set" operations - 
     * return a DataChunk container for the
     * header value ( existing header or new
     * if this .
     */
    public DataChunk setValue(final String name) {
        for (int i = 0; i < count; i++) {
            if (headers[i].getName().equalsIgnoreCase(name)) {
                for (int j = i + 1; j < count; j++) {
                    if (headers[j].getName().equalsIgnoreCase(name)) {
                        removeHeader(j--);
                    }
                }
                return headers[i].getValue();
            }
        }
        MimeHeaderField mh = createHeader();
        mh.getName().setString(name);
        return mh.getValue();
    }

    /**
     * Allow "set" operations -
     * return a DataChunk container for the
     * header value ( existing header or new
     * if this .
     */
    public DataChunk setValue(final Header header) {
        final byte[] bytes = header.getLowerCaseBytes();
        for (int i = 0; i < count; i++) {
            if (headers[i].getName().equalsIgnoreCaseLowerCase(bytes)) {
                for (int j = i + 1; j < count; j++) {
                    if (headers[j].getName().equalsIgnoreCaseLowerCase(bytes)) {
                        removeHeader(j--);
                    }
                }
                return headers[i].getValue();
            }
        }
        MimeHeaderField mh = createHeader();
        mh.getName().setBytes(header.toByteArray());
        
        return mh.getValue();
    }

    //-------------------- Getting headers --------------------
    /**
     * Finds and returns a header field with the given name.  If no such
     * field exists, null is returned.  If more than one such field is
     * in the header, an arbitrary one is returned.
     */
    public DataChunk getValue(String name) {
        for (int i = 0; i < count; i++) {
            if (headers[i].getName().equalsIgnoreCase(name)) {
                return headers[i].getValue();
            }
        }
        return null;
    }

    /**
     * Finds and returns a header field with the given name.  If no such
     * field exists, null is returned.  If more than one such field is
     * in the header, an arbitrary one is returned.
     */
    public DataChunk getValue(final Header header) {
        final byte[] bytes = header.getLowerCaseBytes();
        for (int i = 0; i < count; i++) {
            if (headers[i].getName().equalsIgnoreCaseLowerCase(bytes)) {
                return headers[i].getValue();
            }
        }
        return null;
    }

    // bad shortcut - it'll convert to string ( too early probably,
    // encoding is guessed very late )
    public String getHeader(String name) {
        DataChunk mh = getValue(name);
        return mh != null ? mh.toString() : null;
    }

    public String getHeader(final Header header) {
        DataChunk mh = getValue(header);
        return mh != null ? mh.toString() : null;
    }

    // -------------------- Removing --------------------
    /**
     * Removes a header field with the specified name.  Does nothing
     * if such a field could not be found.
     * @param name the name of the header field to be removed
     */
    public void removeHeader(String name) {
        // XXX
        // warning: rather sticky code; heavily tuned

        for (int i = 0; i < count; i++) {
            if (headers[i].getName().equalsIgnoreCase(name)) {
                removeHeader(i--);
            }
        }
    }

    public void removeHeader(final Header header) {

        for (int i = 0; i < count; i++) {
            if (headers[i].getName().equalsIgnoreCase(header.getBytes())) {
                removeHeader(i--);
            }
        }

    }


    /**
     * Removes the headers with the given name whose values contain the
     * given string.
     *
     * @param name The name of the headers to be removed
     * @param str The string to check the header values against
     */
    @SuppressWarnings("UnusedDeclaration")
    public void removeHeader(final String name, final String str) {
        for (int i = 0; i < count; i++) {
            if (headers[i].getName().equalsIgnoreCase(name)
                    && getValue(i) != null
                    && getValue(i).toString() != null
                    && getValue(i).toString().contains(str)) {
                removeHeader(i--);
            }
        }
    }

    /**
     * Removes the headers with the given name whose values contain the
     * given string.
     *
     * @param name The name of the headers to be removed
     * @param regex The regex string to check the header values against
     */
    @SuppressWarnings("UnusedDeclaration")
    public void removeHeaderMatches(final String name, final String regex) {
        for (int i = 0; i < count; i++) {
            if (headers[i].getName().equalsIgnoreCase(name)
                    && getValue(i) != null
                    && getValue(i).toString() != null
                    && getValue(i).toString().matches(regex)) {
                removeHeader(i--);
            }
        }
    }

    /**
     * Removes the headers with the given name whose values contain the
     * given string.
     *
     * @param header The name of the {@link Header}s to be removed
     * @param regex The regex string to check the header values against
     */
    public void removeHeaderMatches(final Header header, final String regex) {
        for (int i = 0; i < count; i++) {
            if (headers[i].getName().equalsIgnoreCaseLowerCase(
                    header.getLowerCaseBytes())
                    && getValue(i) != null
                    && getValue(i).toString() != null
                    && getValue(i).toString().matches(regex)) {
                removeHeader(i--);
            }
        }
    }

    /**
     * reset and swap with last header
     * @param idx the index of the header to remove.
     */
    void removeHeader(int idx) {
        MimeHeaderField mh = headers[idx];

        mh.recycle();
        headers[idx] = headers[count - 1];
        headers[count - 1] = mh;
        count--;
    }


    // ----------------------------------------------------- Max Header Handling


    public void setMaxNumHeaders(int maxNumHeaders) {
        this.maxNumHeaders = maxNumHeaders;
    }

    public int getMaxNumHeaders() {
        return maxNumHeaders;
    }

    public class MaxHeaderCountExceededException extends IllegalStateException {

        public MaxHeaderCountExceededException() {
            super("Illegal attempt to exceed the configured maximum number of headers: " + maxNumHeaders);
        }

    } // END MaxHeaderCountExceededException

}

/**
 * Enumerate the distinct header names.
 * Each nextElement() is O(n) ( a comparation is
 * done with all previous elements ).
 * This is less frequesnt than add() - we want to keep add O(1).
 */
class NamesIterator implements Iterator<String> {

    int pos;
    int size;
    int currentPos;
    String next;
    final MimeHeaders headers;

    NamesIterator(MimeHeaders headers) {
        this.headers = headers;
        pos = 0;
        size = headers.size();
        findNext();
    }

    private void findNext() {
        next = null;
        for (; pos < size; pos++) {
            next = headers.getName(pos).toString();
            for (int j = 0; j < pos; j++) {
                if (headers.getName(j).equalsIgnoreCase(next)) {
                    // duplicate.
                    next = null;
                    break;
                }
            }
            if (next != null) {
                // it's not a duplicate
                break;
            }
        }
        // next time findNext is called it will try the
        // next element
        pos++;
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public String next() {
        currentPos = pos - 1;
        final String current = next;
        findNext();
        return current;
    }

    @Override
    public void remove() {
        if (currentPos < 0) throw new IllegalStateException("No current element");
        headers.removeHeader(currentPos);
        pos = currentPos;
        currentPos = -1;
        size--;
        findNext();
    }
}

/** Enumerate the values for a (possibly ) multiple
value element.
 */
class ValuesIterator implements Iterator<String> {

    int pos;
    int size;
    int currentPos;
    DataChunk next;
    final MimeHeaders headers;
    final String name;

    ValuesIterator(MimeHeaders headers, String name) {
        this.name = name;
        this.headers = headers;
        pos = 0;
        size = headers.size();
        findNext();
    }

    private void findNext() {
        next = null;
        for (; pos < size; pos++) {
            final DataChunk n1 = headers.getName(pos);
            if (n1.equalsIgnoreCase(name)) {
                next = headers.getValue(pos);
                break;
            }
        }
        pos++;
    }

    @Override
    public boolean hasNext() {
        return next != null;
    }

    @Override
    public String next() {
        currentPos = pos - 1;
        final String current = next.toString();
        findNext();
        return current;
    }

    @Override
    public void remove() {
        if (currentPos < 0) throw new IllegalStateException("No current element");
        headers.removeHeader(currentPos);
        pos = currentPos;
        currentPos = -1;
        size--;
        findNext();
    }
}

class MimeHeaderField {

    protected final DataChunk nameB = DataChunk.newInstance();
    protected final DataChunk valueB = DataChunk.newInstance();

    private boolean isSerialized;
    /**
     * Creates a new, uninitialized header field.
     */
    public MimeHeaderField() {
    }

    public void recycle() {
        isSerialized = false;
        nameB.recycle();
        valueB.recycle();
    }

    public DataChunk getName() {
        return nameB;
    }

    public DataChunk getValue() {
        return valueB;
    }

    public boolean isSerialized() {
        return isSerialized;
    }

    public void setSerialized(boolean isSerialized) {
        this.isSerialized = isSerialized;
    }
}
