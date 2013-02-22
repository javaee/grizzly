/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;

import org.glassfish.grizzly.Grizzly;

import org.glassfish.grizzly.localization.LogMessages;

/**
 * @author Costin Manolache
 */
public final class Parameters {
    /**
     * Default Logger.
     */
    private final static Logger logger = Grizzly.logger(Parameters.class);
    // Transition: we'll use the same Hashtable( String->String[] )
    // for the beginning. When we are sure all accesses happen through
    // this class - we can switch to MultiMap
    /* START PWC 6057385
    private Hashtable paramHashStringArray=new Hashtable();
    */
    // START PWC 6057385
    private final LinkedHashMap<String, ArrayList<String>> paramHashValues =
        new LinkedHashMap<String, ArrayList<String>>();
    // END PWC 6057385
    private boolean didQueryParameters = false;
    private boolean didMerge = false;
    MimeHeaders headers;
    DataChunk queryDC;
    
    final DataChunk decodedQuery = DataChunk.newInstance();
    
    public static final int INITIAL_SIZE = 4;
    // Garbage-less parameter merging.
    // In a sub-request with parameters, the new parameters
    // will be stored in child. When a getParameter happens,
    // the 2 are merged togheter. The child will be altered
    // to contain the merged values - the parent is allways the
    // original request.
    private Parameters child = null;
    private Parameters parent = null;
    private Parameters currentChild = null;
    Charset encoding = null;
    Charset queryStringEncoding = null;
    
    private int limit = -1;
    private int parameterCount = 0;

    public void setQuery(final DataChunk queryBC) {
        this.queryDC = queryBC;
    }

    public void setHeaders(final MimeHeaders headers) {
        this.headers = headers;
    }

    public void setLimit(int limit) {
        this.limit = limit;
    }

    public void setEncoding(final Charset encoding) {
        this.encoding = encoding;
        if (debug > 0) {
            log("Set encoding to " + encoding);
        }
    }

    public Charset getEncoding() {
        return encoding;
    }
    
    public void setQueryStringEncoding(final Charset queryStringEncoding) {
        this.queryStringEncoding = queryStringEncoding;
        if (debug > 0) {
            log("Set query string encoding to " + queryStringEncoding);
        }
    }

    public Charset getQueryStringEncoding() {
        return queryStringEncoding;
    }

    public void recycle() {

        paramHashValues.clear();
        didQueryParameters = false;
        currentChild = null;
        didMerge = false;
        encoding = null;
        queryStringEncoding = null;
        parameterCount = 0;
        decodedQuery.recycle();

    }
    // -------------------- Sub-request support --------------------

    public Parameters getCurrentSet() {
        if (currentChild == null) {
            return this;
        }
        return currentChild;
    }

    /**
     * Create ( or reuse ) a child that will be used during a sub-request. All future changes ( setting query string,
     * adding parameters ) will affect the child ( the parent request is never changed ). Both setters and getters will
     * return the data from the deepest child, merged with data from parents.
     */
    public void push() {
        // We maintain a linked list, that will grow to the size of the
        // longest include chain.
        // The list has 2 points of interest:
        // - request.parameters() is the original request and head,
        // - request.parameters().currentChild() is the current set.
        // The ->child and parent<- links are preserved ( currentChild is not
        // the last in the list )
        // create a new element in the linked list
        // note that we reuse the child, if any - pop will not
        // set child to null !
        if (currentChild == null) {
            currentChild = new Parameters();
//            currentChild.setURLDecoder(urlDec);
            currentChild.parent = this;
            return;
        }
        if (currentChild.child == null) {
            currentChild.child = new Parameters();
//            currentChild.setURLDecoder(urlDec);
            currentChild.child.parent = currentChild;
        } // it is not null if this object already had a child
        // i.e. a deeper include() ( we keep it )
        // the head will be the new element.
        currentChild = currentChild.child;
        currentChild.setEncoding(encoding);
    }

    /**
     * Discard the last child. This happens when we return from a sub-request and the parameters are locally modified.
     */
    public void pop() {
        if (currentChild == null) {
            throw new RuntimeException("Attempt to pop without a push");
        }
        currentChild.recycle();
        currentChild = currentChild.parent;
        // don't remove the top.
    }
    // -------------------- Data access --------------------
    // Access to the current name/values, no side effect ( processing ).
    // You must explicitely call handleQueryParameters and the post methods.
    // This is the original data representation ( hash of String->String[])

    public void addParameterValues(String key, String[] newValues) {
        if (key == null) {
            return;
        }
        ArrayList<String> values;
        if (paramHashValues.containsKey(key)) {
            values = paramHashValues.get(key);
        } else {
            values = new ArrayList<String>(1);
            paramHashValues.put(key, values);
        }
        values.ensureCapacity(values.size() + newValues.length);
        Collections.addAll(values, newValues);
    }

    public String[] getParameterValues(String name) {
        handleQueryParameters();
        ArrayList<String> values = null;
        // sub-request
        if (currentChild != null) {
            currentChild.merge();
            values = currentChild.paramHashValues.get(name);
        } else {
            // no "facade"
            values = paramHashValues.get(name);
        }
        return ((values != null) ? values.toArray(new String[values.size()]) : null);
    }

    public Set<String> getParameterNames() {
        handleQueryParameters();
        // Slow - the original code
        if (currentChild != null) {
            currentChild.merge();
            /* START PWC 6057385
            return currentChild.paramHashStringArray.keys();
            */
            // START PWC 6057385
            currentChild.paramHashValues.keySet();
            // END PWC 6057385
        }
        // merge in child
        /* START PWC 6057385
        return paramHashStringArray.keys();
        */
        // START PWC 6057385
        return paramHashValues.keySet();
        // END PWC 6057385
    }

    /**
     * Combine the parameters from parent with our local ones
     */
    private void merge() {
        // recursive
        if (debug > 0) {
            log("Before merging " + this + ' ' + parent + ' ' + didMerge);
            log(paramsAsString());
        }
        // Local parameters first - they take precedence as in spec.
        handleQueryParameters();
        // we already merged with the parent
        if (didMerge) {
            return;
        }
        // we are the top level
        if (parent == null) {
            return;
        }
        // Add the parent props to the child ( lower precedence )
        parent.merge();
        /* START PWC 6057385
        Hashtable parentProps=parent.paramHashStringArray;
        */
        // START PWC 6057385
        LinkedHashMap<String, ArrayList<String>> parentProps = parent.paramHashValues;
        // END PWC 6057385
        merge2(paramHashValues, parentProps);
        didMerge = true;
        if (debug > 0) {
            log("After " + paramsAsString());
        }
    }

    // Shortcut.
    public String getParameter(final String name) {
        ArrayList<String> values = paramHashValues.get(name);
        if (values != null) {
            if (values.isEmpty()) {
                return "";
            }
            return values.get(0);
        } else {
            return null;
        }
    }
    // -------------------- Processing --------------------

    /**
     * Process the query string into parameters
     */
    public void handleQueryParameters() {
        if (didQueryParameters) {
            return;
        }
        didQueryParameters = true;
        if (queryDC == null || queryDC.isNull()) {
            return;
        }
        if (debug > 0) {
            log("Decoding query " + queryDC + ' ' + queryStringEncoding);
        }
        
        decodedQuery.duplicate(queryDC);
        
        processParameters(decodedQuery, queryStringEncoding);

    }
    // --------------------

    /**
     * Combine 2 hashtables into a new one. ( two will be added to one ). Used to combine child parameters (
     * RequestDispatcher's query ) with parent parameters ( original query or parent dispatcher )
     */
    /* START PWC 6057385
    private static void merge2(Hashtable one, Hashtable two ) {
        Enumeration e = two.keys();

        while (e.hasMoreElements()) {
            String name = (String) e.nextElement();
    */
    // START PWC 6057385
    private static void merge2(LinkedHashMap<String, ArrayList<String>> one,
        LinkedHashMap<String, ArrayList<String>> two) {

        for (String name : two.keySet()) {
            // END PWC 6057385
            ArrayList<String> oneValue = one.get(name);
            ArrayList<String> twoValue = two.get(name);
            ArrayList<String> combinedValue;

            if (twoValue != null) {
                if (oneValue == null) {
                    combinedValue = new ArrayList<String>(twoValue);
                } else {
                    combinedValue = new ArrayList<String>(oneValue.size() +
                            twoValue.size());
                    combinedValue.addAll(oneValue);
                    combinedValue.addAll(twoValue);
                }
                one.put(name, combinedValue);
            }
        }
    }

    public void addParameter(String key, String value)
            throws IllegalStateException {

        if (key == null) {
            return;
        }

        parameterCount++;
        if (limit > -1 && parameterCount > limit) {
            // Processing this parameter will push us over the limit. ISE is
            // what Request.parseParts() uses for requests that are too big
            throw new IllegalStateException(//sm.getString(
                    //"parameters.maxCountFail", Integer.valueOf(limit)));
                    );
        }

        ArrayList<String> values = paramHashValues.get(key);
        if (values == null) {
            values = new ArrayList<String>(1);
            paramHashValues.put(key, values);
        }
        values.add(value);
    }

    //    public void setURLDecoder(UDecoder u) {
//        urlDec = u;
//    }
    // -------------------- Parameter parsing --------------------
    // This code is not used right now - it's the optimized version
    // of the above.
    // we are called from a single thread - we can do it the hard way
    // if needed
    final BufferChunk tmpName = new BufferChunk();
    final BufferChunk tmpValue = new BufferChunk();
    private BufferChunk origName = new BufferChunk();
    private BufferChunk origValue = new BufferChunk();
    final CharChunk tmpNameC = new CharChunk(1024);
    final CharChunk tmpValueC = new CharChunk(1024);

    public static final String DEFAULT_ENCODING = Constants.DEFAULT_HTTP_CHARACTER_ENCODING;
    public static final Charset DEFAULT_CHARSET = Constants.DEFAULT_HTTP_CHARSET;


    public void processParameters(final Buffer buffer, final int start, final int len) {
        processParameters(buffer, start, len, encoding);
    }

    public void processParameters(final Buffer buffer, final int start, final int len,
        final Charset enc) {

        if (debug > 0) {
            log("Bytes: " + buffer.toStringContent(enc, start, start + len));
        }

        int decodeFailCount = 0;

        int end = start + len;
        int pos = start;
        while (pos < end) {
            if (limit > -1 && parameterCount >= limit) {
                logger.warning(LogMessages.WARNING_GRIZZLY_HTTP_SEVERE_GRIZZLY_HTTP_PARAMETERS_MAX_COUNT_FAIL(limit));
                break;
            }
            int nameStart = pos;
            int nameEnd = -1;
            int valueStart = -1;
            int valueEnd = -1;

            boolean parsingName = true;
            boolean decodeName = false;
            boolean decodeValue = false;
            boolean parameterComplete = false;

            do {
                switch (buffer.get(pos)) {
                    case '=':
                        if (parsingName) {
                            // Name finished. Value starts from next character
                            nameEnd = pos;
                            parsingName = false;
                            valueStart = ++pos;
                        } else {
                            // Equals character in value
                            pos++;
                        }
                        break;
                    case '&':
                        if (parsingName) {
                            // Name finished. No value.
                            nameEnd = pos;
                        } else {
                            // Value finished
                            valueEnd = pos;
                        }
                        parameterComplete = true;
                        pos++;
                        break;
                    case '+':
                    case '%':
                        // Decoding required
                        if (parsingName) {
                            decodeName = true;
                        } else {
                            decodeValue = true;
                        }
                        pos++;
                        break;
                    default:
                        pos++;
                        break;
                }
            } while (!parameterComplete && pos < end);

            if (pos == end) {
                if (nameEnd == -1) {
                    nameEnd = pos;
                } else if (valueStart > -1 && valueEnd == -1) {
                    valueEnd = pos;
                }
            }

            if (debug > 0 && valueStart == -1) {
                log(LogMessages.FINE_GRIZZLY_HTTP_PARAMETERS_NOEQUAL(
                        nameStart,
                        nameEnd,
                        buffer.toStringContent(DEFAULT_CHARSET, nameStart, nameEnd)));
            }

            if (nameEnd <= nameStart) {
                if (logger.isLoggable(Level.INFO)) {
                    String extract;
                    if (valueEnd < nameStart) {
                        logger.info(LogMessages.INFO_GRIZZLY_HTTP_PARAMETERS_INVALID_CHUNK(
                                nameStart,
                                nameEnd,
                                null));
                    }
                }
                continue;
                // invalid chunk - it's better to ignore
            }
            tmpName.setBufferChunk(buffer, nameStart, nameEnd);
            tmpValue.setBufferChunk(buffer, valueStart, valueEnd);

            // Take copies as if anything goes wrong originals will be
            // corrupted. This means original values can be logged.
            // For performance - only done for debug
            if (debug > 0) {
                origName.setBufferChunk(buffer, nameStart, nameEnd);
                origValue.setBufferChunk(buffer, valueStart, valueEnd);
            }

            try {
                String name;
                String value;

                if (decodeName) {
                    name = urlDecode(tmpName, enc);
                } else {
                    name = tmpName.toString(enc);
                }

                if (valueStart != -1) {
                    if (decodeValue) {
                        value = urlDecode(tmpValue, enc);
                    } else {
                        value = tmpValue.toString(enc);
                    }
                } else {
                    value = "";
                }
                
                addParameter(name, value);
            } catch (IOException e) {
                decodeFailCount++;
                if (decodeFailCount == 1 || debug > 0) {
                    if (debug > 0) {
                        log(LogMessages.FINE_GRIZZLY_HTTP_PARAMETERS_DECODE_FAIL_DEBUG(
                                origName.toString(), origValue.toString()));
                    } else if (logger.isLoggable(Level.INFO)) {
                        logger.log(Level.INFO,
                                   LogMessages.INFO_GRIZZLY_HTTP_PARAMETERS_DECODE_FAIL_INFO(
                                           tmpName.toString(), tmpValue.toString()),
                                   e);
                    }
                }
            }
            tmpName.recycle();
            tmpValue.recycle();

        }

        if (decodeFailCount > 1 && debug <= 0) {
            logger.info(LogMessages.INFO_GRIZZLY_HTTP_PARAMETERS_MULTIPLE_DECODING_FAIL(decodeFailCount));
        }
    }

    private String urlDecode(final BufferChunk bc, final Charset enc)
        throws IOException {
//        if (urlDec == null) {
//            urlDec = new UDecoder();
//        }
        URLDecoder.decode(bc, true);
        String result;
        if (enc != null) {
            if (bc.getStart() == -1 && bc.getEnd() == -1) {
                return "";
            }
            result = bc.toString(enc);
        } else {
            final CharChunk cc = tmpNameC;
            final int length = bc.getLength();
            cc.allocate(length, -1);
            // Default encoding: fast conversion
            final Buffer bbuf = bc.getBuffer();
            final char[] cbuf = cc.getBuffer();
            final int start = bc.getStart();
            for (int i = 0; i < length; i++) {
                cbuf[i] = (char) (bbuf.get(i + start) & 0xff);
            }

            cc.setChars(cbuf, 0, length);
            result = cc.toString();
            cc.recycle();
        }
        return result;
    }

    public void processParameters(char chars[], int start, int len) {
        int end = start + len;
        int pos = start;
        if (debug > 0) {
            log("Chars: " + new String(chars, start, len));
        }
        do {
            boolean noEq = false;
            int nameStart = pos;
            int valStart = -1;
            int valEnd = -1;
            int nameEnd = CharChunk.indexOf(chars, nameStart, end, '=');
            int nameEnd2 = CharChunk.indexOf(chars, nameStart, end, '&');
            if ((nameEnd2 != -1) &&
                (nameEnd == -1 || nameEnd > nameEnd2)) {
                nameEnd = nameEnd2;
                noEq = true;
                valStart = nameEnd;
                valEnd = nameEnd;
                if (debug > 0) {
                    log("no equal " + nameStart + ' ' + nameEnd + ' ' + new String(chars, nameStart,
                        nameEnd - nameStart));
                }
            }
            if (nameEnd == -1) {
                nameEnd = end;
            }
            if (!noEq) {
                valStart = (nameEnd < end) ? nameEnd + 1 : end;
                valEnd = CharChunk.indexOf(chars, valStart, end, '&');
                if (valEnd == -1) {
                    valEnd = (valStart < end) ? end : valStart;
                }
            }
            pos = valEnd + 1;
            if (nameEnd <= nameStart) {
                continue;
                // invalid chunk - no name, it's better to ignore
                // XXX log it ?
            }
            try {
                tmpNameC.append(chars, nameStart, nameEnd - nameStart);
                tmpValueC.append(chars, valStart, valEnd - valStart);
                if (debug > 0) {
                    log(tmpNameC + "= " + tmpValueC);
                }
//                if (urlDec == null) {
//                    urlDec = new UDecoder();
//                }
                URLDecoder.decode(tmpNameC, tmpNameC, true, queryStringEncoding.name());
                URLDecoder.decode(tmpValueC, tmpValueC, true, queryStringEncoding.name());
                if (debug > 0) {
                    log(tmpNameC + "= " + tmpValueC);
                }
                addParameter(tmpNameC.toString(), tmpValueC.toString());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            tmpNameC.recycle();
            tmpValueC.recycle();

        } while (pos < end);
    }

    public void processParameters(final DataChunk data) {
        processParameters(data, encoding);
    }

    public void processParameters(final DataChunk data, final Charset encoding) {
        if (data == null || data.isNull() || data.getLength() <= 0) {
            return;
        }

        try {
            if (data.getType() == DataChunk.Type.Buffer) {
                final BufferChunk bc = data.getBufferChunk();
                processParameters(bc.getBuffer(), bc.getStart(),
                        bc.getLength(), encoding);
            } else {
                if (data.getType() != DataChunk.Type.Chars) {
                    data.toChars(encoding);
                }

                final CharChunk cc = data.getCharChunk();
                processParameters(cc.getChars(), cc.getStart(),
                        cc.getLength());
            }
        } catch (CharConversionException e) {
            throw new IllegalStateException(e);
        }
    }

    /**
     * Debug purpose
     */
    public String paramsAsString() {
        StringBuilder sb = new StringBuilder();
        for (final String s : paramHashValues.keySet()) {
            // END PWC 6057385
            sb.append(s).append('=');
            ArrayList<String> v = paramHashValues.get(s);
            for (int i = 0, len = v.size(); i < len; i++) {
                sb.append(v.get(i)).append(',');
            }
            sb.append('\n');
        }
        return sb.toString();
    }

    private static final int debug = 0;

    private void log(String s) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Parameters: {0}", s);
        }
    }
    // -------------------- Old code, needs rewrite --------------------

    /**
     * Used by RequestDispatcher
     */
    public void processSingleParameters(final String str) {
        int end = str.length();
        int pos = 0;
        if (debug > 0) {
            log("String: " + str);
        }
        do {
            boolean noEq = false;
            int valStart = -1;
            int valEnd = -1;
            int nameStart = pos;
            int nameEnd = str.indexOf('=', nameStart);
            int nameEnd2 = str.indexOf('&', nameStart);
            if (nameEnd2 == -1) {
                nameEnd2 = end;
            }
            if ((nameEnd2 != -1) &&
                (nameEnd == -1 || nameEnd > nameEnd2)) {
                nameEnd = nameEnd2;
                noEq = true;
                valStart = nameEnd;
                valEnd = nameEnd;
                if (debug > 0) {
                    log("no equal " + nameStart + ' ' + nameEnd + ' ' + str.substring(nameStart, nameEnd));
                }
            }
            if (nameEnd == -1) {
                nameEnd = end;
            }
            if (!noEq) {
                valStart = nameEnd + 1;
                valEnd = str.indexOf('&', valStart);
                if (valEnd == -1) {
                    valEnd = (valStart < end) ? end : valStart;
                }
            }
            pos = valEnd + 1;
            if (nameEnd <= nameStart) {
                continue;
            }
            if (debug > 0) {
                log("XXX " + nameStart + ' ' + nameEnd + ' '
                    + valStart + ' ' + valEnd);
            }
            try {
                tmpNameC.append(str, nameStart, nameEnd - nameStart);
                tmpValueC.append(str, valStart, valEnd - valStart);
                if (debug > 0) {
                    log(tmpNameC + "= " + tmpValueC);
                }
//                if (urlDec == null) {
//                    urlDec = new UDecoder();
//                }
                URLDecoder.decode(tmpNameC, true);
                URLDecoder.decode(tmpValueC, true);
                if (debug > 0) {
                    log(tmpNameC + "= " + tmpValueC);
                }
                if (str.compareTo(tmpNameC.toString()) == 0) {
                    addParameter(tmpNameC.toString(), tmpValueC.toString());
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            tmpNameC.recycle();
            tmpValueC.recycle();

        } while (pos < end);
    }

    public void processParameters(String str) {
        int end = str.length();
        int pos = 0;
        if (debug > 0) {
            log("String: " + str);
        }
        do {
            boolean noEq = false;
            int valStart = -1;
            int valEnd = -1;
            int nameStart = pos;
            int nameEnd = str.indexOf('=', nameStart);
            int nameEnd2 = str.indexOf('&', nameStart);
            if (nameEnd2 == -1) {
                nameEnd2 = end;
            }
            if ((nameEnd2 != -1) &&
                (nameEnd == -1 || nameEnd > nameEnd2)) {
                nameEnd = nameEnd2;
                noEq = true;
                valStart = nameEnd;
                valEnd = nameEnd;
                if (debug > 0) {
                    log("no equal " + nameStart + ' ' + nameEnd + ' ' + str.substring(nameStart, nameEnd));
                }
            }
            if (nameEnd == -1) {
                nameEnd = end;
            }
            if (!noEq) {
                valStart = nameEnd + 1;
                valEnd = str.indexOf('&', valStart);
                if (valEnd == -1) {
                    valEnd = (valStart < end) ? end : valStart;
                }
            }
            pos = valEnd + 1;
            if (nameEnd <= nameStart) {
                continue;
            }
            if (debug > 0) {
                log("XXX " + nameStart + ' ' + nameEnd + ' '
                    + valStart + ' ' + valEnd);
            }
            try {
                tmpNameC.append(str, nameStart, nameEnd - nameStart);
                tmpValueC.append(str, valStart, valEnd - valStart);
                if (debug > 0) {
                    log(tmpNameC + "= " + tmpValueC);
                }
//                if (urlDec == null) {
//                    urlDec = new UDecoder();
//                }
                URLDecoder.decode(tmpNameC, true);
                URLDecoder.decode(tmpValueC, true);
                if (debug > 0) {
                    log(tmpNameC + "= " + tmpValueC);
                }
                addParameter(tmpNameC.toString(), tmpValueC.toString());
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            tmpNameC.recycle();
            tmpValueC.recycle();

        } while (pos < end);
    }

}
