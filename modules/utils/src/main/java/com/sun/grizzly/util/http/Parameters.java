/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.util.http;

import com.sun.grizzly.util.Charsets;
import com.sun.grizzly.util.LoggerUtils;
import com.sun.grizzly.util.buf.ByteChunk;
import com.sun.grizzly.util.buf.CharChunk;
import com.sun.grizzly.util.buf.MessageBytes;
import com.sun.grizzly.util.buf.UDecoder;
import com.sun.grizzly.util.res.StringManager;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * @author Costin Manolache
 */
public final class Parameters {

    /**
     * Default Logger.
     */
    private final static Logger logger = LoggerUtils.getLogger();

    // Transition: we'll use the same Hashtable( String->String[] )
    // for the beginning. When we are sure all accesses happen through
    // this class - we can switch to MultiMap
    /* START PWC 6057385
    private Hashtable paramHashStringArray=new Hashtable();
    */

    protected static final StringManager sm =
        StringManager.getManager("com.sun.grizzly.util.http.res",
                                 Parameters.class.getClassLoader());

    // START PWC 6057385
    private LinkedHashMap<String, ArrayList<String>> paramHashValues =
            new LinkedHashMap<String, ArrayList<String>>();
    // END PWC 6057385
    private boolean didQueryParameters = false;
    private boolean didMerge = false;

    MessageBytes queryMB;
    MimeHeaders headers;

    UDecoder urlDec;
    MessageBytes decodedQuery = MessageBytes.newInstance();

    // Garbage-less parameter merging.
    // In a sub-request with parameters, the new parameters
    // will be stored in child. When a getParameter happens,
    // the 2 are merged togheter. The child will be altered
    // to contain the merged values - the parent is allways the
    // original request.
    private Parameters child = null;
    private Parameters parent = null;
    private Parameters currentChild = null;

    String encoding = null;
    String queryStringEncoding = null;

    private int limit = -1;
    private int parameterCount = 0;

    /**
     *
     */
    public Parameters() {
    }

    public void setQuery(MessageBytes queryMB) {
        this.queryMB = queryMB;
    }

    @SuppressWarnings("UnusedDeclaration")
    public void setLimit(int limit) {
        this.limit = limit;
    }

    public void setHeaders(MimeHeaders headers) {
        this.headers = headers;
    }

    public void setEncoding(String s) {
        encoding = s;
        if (debug > 0) {
            log("Set encoding to " + s);
        }
    }

    public void setQueryStringEncoding(String s) {
        queryStringEncoding = s;
        if (debug > 0) {
            log("Set query string encoding to " + s);
        }
    }

    public void recycle() {
        parameterCount = 0;
        paramHashValues.clear();
        didQueryParameters = false;
        currentChild = null;
        didMerge = false;
        encoding = null;
        decodedQuery.recycle();
    }

    // -------------------- Sub-request support --------------------

    @SuppressWarnings("UnusedDeclaration")
    public Parameters getCurrentSet() {
        if (currentChild == null) {
            return this;
        }
        return currentChild;
    }

    /**
     * Create ( or reuse ) a child that will be used during a sub-request.
     * All future changes ( setting query string, adding parameters )
     * will affect the child ( the parent request is never changed ).
     * Both setters and getters will return the data from the deepest
     * child, merged with data from parents.
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
            currentChild.setURLDecoder(urlDec);
            currentChild.parent = this;
            return;
        }
        if (currentChild.child == null) {
            currentChild.child = new Parameters();
            currentChild.setURLDecoder(urlDec);
            currentChild.child.parent = currentChild;
        } // it is not null if this object already had a child
        // i.e. a deeper include() ( we keep it )

        // the head will be the new element.
        currentChild = currentChild.child;
        currentChild.setEncoding(encoding);
    }

    /**
     * Discard the last child. This happens when we return from a
     * sub-request and the parameters are locally modified.
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
        ArrayList<String> values;
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

    public Enumeration<String> getParameterNames() {
        handleQueryParameters();
        // Slow - the original code
        if (currentChild != null) {
            currentChild.merge();
            /* START PWC 6057385
            return currentChild.paramHashStringArray.keys();
            */
            // START PWC 6057385
            return Collections.enumeration(
                    currentChild.paramHashValues.keySet());
            // END PWC 6057385
        }

        // merge in child
        /* START PWC 6057385
        return paramHashStringArray.keys();
        */
        // START PWC 6057385
        return Collections.enumeration(paramHashValues.keySet());
        // END PWC 6057385
    }

    /**
     * Combine the parameters from parent with our local ones
     */
    private void merge() {
        // recursive
        if (debug > 0) {
            log("Before merging " + this + " " + parent + " " + didMerge);
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

    public String getParameter(String name) {
        ArrayList<String> values = paramHashValues.get(name);
        if (values != null) {
            if (values.size() == 0) {
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

        if (queryMB == null || queryMB.isNull()) {
            return;
        }

        if (debug > 0) {
            log("Decoding query " + decodedQuery + " " + queryStringEncoding);
        }

        try {
            decodedQuery.duplicate(queryMB);
        } catch (IOException e) {
            // Can't happen, as decodedQuery can't overflow
            e.printStackTrace();
        }
        processParameters(decodedQuery, queryStringEncoding);
    }

    // --------------------

    /**
     * Combine 2 hashtables into a new one.
     * ( two will be added to one ).
     * Used to combine child parameters ( RequestDispatcher's query )
     * with parent parameters ( original query or parent dispatcher )
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

    // incredibly inefficient data representation for parameters,
    // until we test the new one

    public void addParameter(String key, String value)
            throws IllegalStateException {

        if (key == null) {
            return;
        }

        parameterCount++;
        if (limit > -1 && parameterCount > limit) {
            // Processing this parameter will push us over the limit. ISE is
            // what Request.parseParts() uses for requests that are too big
            throw new IllegalStateException(sm.getString(
                    "parameters.maxCountFail", limit));
        }

        ArrayList<String> values = paramHashValues.get(key);
        if (values == null) {
            values = new ArrayList<String>(1);
            paramHashValues.put(key, values);
        }
        values.add(value);
    }

    public void setURLDecoder(UDecoder u) {
        urlDec = u;
    }

    // -------------------- Parameter parsing --------------------

    // This code is not used right now - it's the optimized version
    // of the above.

    // we are called from a single thread - we can do it the hard way
    // if needed
    ByteChunk tmpName = new ByteChunk();
    ByteChunk tmpValue = new ByteChunk();
    private ByteChunk origName=new ByteChunk();
    private ByteChunk origValue=new ByteChunk();
    CharChunk tmpNameC = new CharChunk(1024);
    CharChunk tmpValueC = new CharChunk(1024);
    public static final String DEFAULT_ENCODING = "ISO-8859-1";
    public static final Charset DEFAULT_CHARSET = Charset.forName(DEFAULT_ENCODING);

    public void processParameters(byte bytes[], int start, int len) {
        processParameters(bytes, start, len, getCharset(encoding));
    }

    public void processParameters(byte bytes[], int start, int len,
            Charset charset) {

        if (debug > 0) {
            try {
                log(sm.getString("parameters.bytes",
                        // JDK 1.5 compliant
                        new String(bytes, start, len, DEFAULT_ENCODING)));
            } catch(UnsupportedEncodingException e) {
                // Should never happen...
                logger.log(Level.SEVERE, sm.getString("parameters.convertBytesFail"), e);
            }
        }

        int decodeFailCount = 0;

        int end = start + len;
        int pos = start;

        while (pos < end) {

            int nameStart = pos;
            int nameEnd = -1;
            int valueStart = -1;
            int valueEnd = -1;

            boolean parsingName = true;
            boolean decodeName = false;
            boolean decodeValue = false;
            boolean parameterComplete = false;

            do {
                switch(bytes[pos]) {
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
                            valueEnd  = pos;
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
                        pos ++;
                        break;
                    default:
                        pos ++;
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
                try {
                    log(sm.getString("parameters.noequal",
                            nameStart, nameEnd,
                            // JDK 1.5 compliant
                            new String(bytes, nameStart, nameEnd-nameStart,
                                    DEFAULT_ENCODING)));
                } catch(UnsupportedEncodingException e) {
                    // Should never happen...
                    logger.log(Level.SEVERE, sm.getString("parameters.convertBytesFail"), e);
                }
            }

            if (nameEnd <= nameStart) {
                if (logger.isLoggable(Level.INFO)) {
                    if (valueEnd >= nameStart) {
                        try {
                            // JDK 1.5 compliant
                            new String(bytes, nameStart,
                                    valueEnd - nameStart, DEFAULT_ENCODING);
                        } catch(UnsupportedEncodingException e) {
                            // Should never happen...
                            logger.log(Level.SEVERE, sm.getString("parameters.convertBytesFail"), e);
                        }
                    } else {
                        logger.fine(sm.getString("parameters.invalidChunk",
                                nameStart,
                                nameEnd,
                                null));
                    }
                }
                continue;
                // invalid chunk - it's better to ignore
            }

            tmpName.setCharset(charset);
            tmpValue.setCharset(charset);
            tmpName.setBytes(bytes, nameStart, nameEnd - nameStart);
            tmpValue.setBytes(bytes, valueStart, valueEnd - valueStart);

            // Take copies as if anything goes wrong originals will be
            // corrupted. This means original values can be logged.
            // For performance - only done for debug
            if (debug > 0) {
                try {
                    origName.append(bytes, nameStart, nameEnd - nameStart);
                    origValue.append(bytes, valueStart, valueEnd - valueStart);
                } catch (IOException ioe) {
                    // Should never happen...
                    logger.log(Level.SEVERE, sm.getString("parameters.copyFail"), ioe);
                }
            }

            try {
                String name;
                String value;

                if (decodeName) {
                    name = urlDecode(tmpName);
                } else {
                    name = tmpName.toString();
                }

                if (decodeValue) {
                    value = urlDecode(tmpValue);
                } else {
                    value = tmpValue.toString();
                }

                try {
                    addParameter(name, value);
                } catch (IllegalStateException ise) {
                    // Hitting limit stops processing further params but does
                    // not cause request to fail.
                    logger.warning(ise.getMessage());
                    break;
                }
            } catch (IOException e) {
                decodeFailCount++;
                if (decodeFailCount == 1 || debug > 0) {
                    if (debug > 0) {
                        log(sm.getString("parameters.decodeFail.debug",
                                origName.toString(), origValue.toString()), e);
                    } else if (logger.isLoggable(Level.INFO)) {
                        logger.log(Level.INFO, sm.getString("parameters.decodeFail.info",
                                tmpName.toString(), tmpValue.toString()), e);
                    }
                }
            }

            tmpName.recycle();
            tmpValue.recycle();
            // Only recycle copies if we used them
            if (debug > 0) {
                origName.recycle();
                origValue.recycle();
            }
        }

        if (decodeFailCount > 1 && debug <= 0) {
            logger.info(sm.getString("parameters.multipleDecodingFail",
                    decodeFailCount));
        }
    }

    private String urlDecode(ByteChunk bc)
            throws IOException {
        if (urlDec == null) {
            urlDec = new UDecoder();
        }
        urlDec.convert(bc);
        return bc.toString();
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
                    log("no equal " + nameStart + " " + nameEnd + " " + new String(chars, nameStart,
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

                if (urlDec == null) {
                    urlDec = new UDecoder();
                }

                urlDec.convert(tmpNameC);
                urlDec.convert(tmpValueC);

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

    public void processParameters(MessageBytes data) {
        processParameters(data, encoding);
    }

    public void processParameters(MessageBytes data, String encoding) {
        if (data == null || data.isNull() || data.getLength() <= 0) {
            return;
        }

        if (data.getType() == MessageBytes.T_BYTES) {
            ByteChunk bc = data.getByteChunk();
            processParameters(bc.getBytes(), bc.getOffset(),
                    bc.getLength(), getCharset(encoding));
        } else {
            if (data.getType() != MessageBytes.T_CHARS) {
                data.toChars();
            }
            CharChunk cc = data.getCharChunk();
            processParameters(cc.getChars(), cc.getOffset(),
                    cc.getLength());
        }
    }

    /**
     * Debug purpose
     */
    public String paramsAsString() {
        StringBuilder sb = new StringBuilder();
        /* START PWC 6057385
        Enumeration en= paramHashStringArray.keys();
        while( en.hasMoreElements() ) {
            String k=(String)en.nextElement();
        */
        // START PWC 6057385
        for (String k : paramHashValues.keySet()) {
            // END PWC 6057385
            sb.append(k).append("=");
            ArrayList<String> values = paramHashValues.get(k);
            if (values != null) {
                for (String value : values) {
                    sb.append(value).append(",");
                }
            }
            sb.append("\n");
        }
        return sb.toString();
    }

    private static int debug = 0;

    private void log(String s) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Parameters: " + s);
        }
    }

    private void log(String s, Throwable t) {
        if (logger.isLoggable(Level.FINEST)) {
            logger.log(Level.FINEST, "Parameters: " + s, t);
        }
    }

    // -------------------- Old code, needs rewrite --------------------

    /**
     * Used by RequestDispatcher
     */
    @SuppressWarnings("UnusedDeclaration")
    public void processSingleParameters(String str) {
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
                    log("no equal " + nameStart + " " + nameEnd + " " + str.substring(nameStart, nameEnd));
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
                log("XXX " + nameStart + " " + nameEnd + " "
                        + valStart + " " + valEnd);
            }

            try {
                tmpNameC.append(str, nameStart, nameEnd - nameStart);
                tmpValueC.append(str, valStart, valEnd - valStart);

                if (debug > 0) {
                    log(tmpNameC + "= " + tmpValueC);
                }

                if (urlDec == null) {
                    urlDec = new UDecoder();
                }

                urlDec.convert(tmpNameC);
                urlDec.convert(tmpValueC);

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


    @SuppressWarnings("UnusedDeclaration")
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
                    log("no equal " + nameStart + " " + nameEnd + " " + str.substring(nameStart, nameEnd));
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
                log("XXX " + nameStart + " " + nameEnd + " "
                        + valStart + " " + valEnd);
            }

            try {
                tmpNameC.append(str, nameStart, nameEnd - nameStart);
                tmpValueC.append(str, valStart, valEnd - valStart);

                if (debug > 0) {
                    log(tmpNameC + "= " + tmpValueC);
                }

                if (urlDec == null) {
                    urlDec = new UDecoder();
                }

                urlDec.convert(tmpNameC);
                urlDec.convert(tmpValueC);

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

    private Charset getCharset(String encoding) {
        if (encoding == null) {
            return DEFAULT_CHARSET;
        }
        try {
            return Charsets.lookupCharset(encoding);
        } catch(IllegalArgumentException e) {
            return DEFAULT_CHARSET;
        }
    }
}
