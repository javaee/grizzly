/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http;

import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.Iterator;
import java.util.Set;
import org.glassfish.grizzly.http.util.Constants;

import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.utils.Charsets;
import org.glassfish.grizzly.http.util.Parameters;
import org.glassfish.grizzly.http.util.UEncoder;

import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.MemoryManager;

import org.junit.Test;
import static org.junit.Assert.*;


public class ParametersTest {

    private static final Parameter SIMPLE =
        new Parameter("foo1", "bar1");
    private static final Parameter SIMPLE_MULTIPLE =
        new Parameter("foo2", "bar1", "bar2");
    private static final Parameter NO_VALUE =
        new Parameter("foo3");
    private static final Parameter EMPTY_VALUE =
        new Parameter("foo4", "");
    private static final Parameter EMPTY =
        new Parameter("");
    private static final Parameter UTF8 =
            new Parameter("\ufb6b\ufb6a\ufb72", "\uffee\uffeb\uffe2");

    @Test
    public void testProcessParametersByteArrayIntInt() {
        doTestProcessParametersByteArrayIntInt(-1, SIMPLE);
        doTestProcessParametersByteArrayIntInt(-1, SIMPLE_MULTIPLE);
        doTestProcessParametersByteArrayIntInt(-1, NO_VALUE);
        doTestProcessParametersByteArrayIntInt(-1, EMPTY_VALUE);
        doTestProcessParametersByteArrayIntInt(-1, EMPTY);
        doTestProcessParametersByteArrayIntInt(-1, UTF8);
        doTestProcessParametersByteArrayIntInt(-1,
                SIMPLE, SIMPLE_MULTIPLE, NO_VALUE, EMPTY_VALUE, EMPTY, UTF8);
        doTestProcessParametersByteArrayIntInt(-1,
                SIMPLE_MULTIPLE, NO_VALUE, EMPTY_VALUE, EMPTY, UTF8, SIMPLE);
        doTestProcessParametersByteArrayIntInt(-1,
                NO_VALUE, EMPTY_VALUE, EMPTY, UTF8, SIMPLE, SIMPLE_MULTIPLE);
        doTestProcessParametersByteArrayIntInt(-1,
                EMPTY_VALUE, EMPTY, UTF8, SIMPLE, SIMPLE_MULTIPLE, NO_VALUE);
        doTestProcessParametersByteArrayIntInt(-1,
                EMPTY, UTF8, SIMPLE, SIMPLE_MULTIPLE, NO_VALUE, EMPTY_VALUE);
        doTestProcessParametersByteArrayIntInt(-1,
                UTF8, SIMPLE, SIMPLE_MULTIPLE, NO_VALUE, EMPTY_VALUE, EMPTY);

        doTestProcessParametersByteArrayIntInt(1,
                SIMPLE, NO_VALUE, EMPTY_VALUE, UTF8);
        doTestProcessParametersByteArrayIntInt(2,
                SIMPLE, NO_VALUE, EMPTY_VALUE, UTF8);
        doTestProcessParametersByteArrayIntInt(3,
                SIMPLE, NO_VALUE, EMPTY_VALUE, UTF8);
        doTestProcessParametersByteArrayIntInt(4,
                SIMPLE, NO_VALUE, EMPTY_VALUE, UTF8);
    }

    // Make sure the inner Parameter class behaves correctly
    @Test
    public void testInternal() {
        assertEquals("foo1=bar1", SIMPLE.toString());
        assertEquals("foo2=bar1&foo2=bar2", SIMPLE_MULTIPLE.toString());
        assertEquals("foo3", NO_VALUE.toString());
        assertEquals("foo4=", EMPTY_VALUE.toString());
    }

    @Test
    public void testISOEncodedQueryParameter() throws UnsupportedEncodingException {
        String paramName = "p\u00e4rameter";
        String paramValue = "\u00e4";
        // parameter=%E4
        testEncodedQueryParameter(paramName, paramValue, Charsets.lookupCharset("ISO-8859-1"));
    }

    @Test
    public void testUTF8EncodedQueryParameter() throws UnsupportedEncodingException {
        String paramName = "p\u00e4rameter";
        String paramValue = "\u00e4";
        // p%C3%A4rameter=%C3%A4
        testEncodedQueryParameter(paramName, paramValue, Charsets.UTF8_CHARSET);
    }
    
    @Test
    public void testQueryParameterEncodingReset() throws UnsupportedEncodingException {
        String paramName = "\u0430\u0440\u0433\u0443\u043c\u0435\u043d\u0442";
        String paramValue = "\u0437\u043d\u0430\u0447\u0435\u043d\u0438\u0435";
        
        UEncoder encoder = new UEncoder();
        encoder.setEncoding(Charsets.UTF8_CHARSET.name());
        String encodedQueryString = encoder.encodeURL(paramName) + "=" + encoder.encodeURL(paramValue);
        
        Parameters parameters = new Parameters();
        parameters.setQueryStringEncoding(Constants.DEFAULT_HTTP_CHARSET);
        DataChunk queryStringDataChunk = DataChunk.newInstance();
        queryStringDataChunk.setBuffer(
                Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, encodedQueryString));
        parameters.setQuery(queryStringDataChunk);
        parameters.handleQueryParameters();
        
        // the parameter should not be found
        assertNull(parameters.getParameter(paramName));
        
        // reset the query encoding
        parameters.recycle();
        parameters.setQueryStringEncoding(Charsets.UTF8_CHARSET);
        parameters.handleQueryParameters();
        
        assertEquals(paramValue, parameters.getParameter(paramName));
    }
    

    public void testEncodedQueryParameter(String paramName, String paramValue, Charset charset) throws UnsupportedEncodingException {
        String charsetName = charset.name();
        UEncoder encoder = new UEncoder();
        encoder.setEncoding(charsetName);
        String encodedQueryString = encoder.encodeURL(paramName) + "=" + encoder.encodeURL(paramValue);
        Parameters parameters = new Parameters();
        parameters.setQueryStringEncoding(charset);
        DataChunk queryStringDataChunk = DataChunk.newInstance();
        queryStringDataChunk.setString(encodedQueryString);
        parameters.setQuery(queryStringDataChunk);
        parameters.handleQueryParameters();
        String storedValue = parameters.getParameter(paramName);
        assertEquals(paramValue, storedValue);
    }

    private long doTestProcessParametersByteArrayIntInt(int limit,
            Parameter... parameters) {

        // Build the byte array
        StringBuilder input = new StringBuilder();
        boolean first = true;
        for (Parameter parameter : parameters) {
            if (first) {
                first = false;
            } else {
                input.append('&');
            }
            input.append(parameter.toString());
        }

        byte[] data = input.toString().getBytes();

        Parameters p = new Parameters();
        p.setEncoding(Charsets.UTF8_CHARSET);
        p.setLimit(limit);

        long start = System.nanoTime();
        p.processParameters(Buffers.wrap(MemoryManager.DEFAULT_MEMORY_MANAGER, data), 0, data.length);
        long end = System.nanoTime();

        if (limit == -1) {
            validateParameters(parameters, p);
        } else {
            Parameter[] limitParameters = new Parameter[limit];
            System.arraycopy(parameters, 0, limitParameters, 0, limit);
            validateParameters(limitParameters, p);
        }
        return end - start;
    }

    @Test
    public void testNonExistantParameter() {
        Parameters p = new Parameters();

        String value = p.getParameter("foo");
        assertNull(value);

        Set<String> names = p.getParameterNames();
        assertTrue(names.isEmpty());

        String[] values = p.getParameterValues("foo");
        assertNull(values);
    }


    @Test
    public void testAddParameters() {
        Parameters p = new Parameters();

        // Empty at this point
        Iterator<String> names = p.getParameterNames().iterator();
        assertFalse(names.hasNext());
        String[] values = p.getParameterValues("foo");
        assertNull(values);

        // Add a parameter with two values
        p.addParameter("foo", "value1");
        p.addParameter("foo", "value2");

        names = p.getParameterNames().iterator();
        assertTrue(names.hasNext());
        assertEquals("foo", names.next());
        assertFalse(names.hasNext());

        values = p.getParameterValues("foo");
        assertEquals(2, values.length);
        assertEquals("value1", values[0]);
        assertEquals("value2", values[1]);

        // Add two more values
        p.addParameter("foo", "value3");
        p.addParameter("foo", "value4");

        names = p.getParameterNames().iterator();
        assertTrue(names.hasNext());
        assertEquals("foo", names.next());
        assertFalse(names.hasNext());

        values = p.getParameterValues("foo");
        assertEquals(4, values.length);
        assertEquals("value1", values[0]);
        assertEquals("value2", values[1]);
        assertEquals("value3", values[2]);
        assertEquals("value4", values[3]);
    }

    @Test
    public void testAddParametersLimit() {
        Parameters p = new Parameters();

        p.setLimit(2);

        // Empty at this point
        Iterator<String> names = p.getParameterNames().iterator();
        assertFalse(names.hasNext());
        String[] values = p.getParameterValues("foo1");
        assertNull(values);

        // Add a parameter
        p.addParameter("foo1", "value1");

        names = p.getParameterNames().iterator();
        assertTrue(names.hasNext());
        assertEquals("foo1", names.next());
        assertFalse(names.hasNext());

        values = p.getParameterValues("foo1");
        assertEquals(1, values.length);
        assertEquals("value1", values[0]);

        // Add another parameter
        p.addParameter("foo2", "value2");

        names = p.getParameterNames().iterator();
        assertTrue(names.hasNext());
        assertEquals("foo1", names.next());
        assertEquals("foo2", names.next());
        assertFalse(names.hasNext());

        values = p.getParameterValues("foo1");
        assertEquals(1, values.length);
        assertEquals("value1", values[0]);

        values = p.getParameterValues("foo2");
        assertEquals(1, values.length);
        assertEquals("value2", values[0]);

        // Add another parameter
        IllegalStateException e = null;
        try {
            p.addParameter("foo3", "value3");
        } catch (IllegalStateException ise) {
            e = ise;
        }
        assertNotNull(e);

        // Check current parameters remain unaffected
        names = p.getParameterNames().iterator();
        assertTrue(names.hasNext());
        assertEquals("foo1", names.next());
        assertEquals("foo2", names.next());
        assertFalse(names.hasNext());

        values = p.getParameterValues("foo1");
        assertEquals(1, values.length);
        assertEquals("value1", values[0]);

        values = p.getParameterValues("foo2");
        assertEquals(1, values.length);
        assertEquals("value2", values[0]);

    }

    @Test
    public void testProcessParametersChar() {

        Parameters params = new Parameters();
        // first an invalid url encoded request
        String request = "test=%c]";
        params.setQueryStringEncoding(Charset.forName("UTF-8"));

        try {
            params.processParameters(request.toCharArray(), 0,
                                     request.length());
        } catch (IllegalStateException e) {

        }
        // now a valid request, which would have failed before the fix, due to
        // the buffer not being recycled
        request = "test=test";
        params.setQueryStringEncoding(Charset.forName("UTF-8"));
        params.processParameters(request.toCharArray(), 0,
                                 request.length());
    }

    @Test
    public void testProcessParametersString() {

        Parameters params = new Parameters();
        // first an invalid url encoded request
        String request = "test=%c]";
        params.setQueryStringEncoding(Charset.forName("UTF-8"));

        try {
            params.processParameters(request);
        } catch (IllegalStateException e) {

        }
        // now a valid request, which would have failed before the fix, due to
        // the buffer not being recycled
        request = "test=test";
        params.setQueryStringEncoding(Charset.forName("UTF-8"));
        params.processParameters(request);
    }

    private void validateParameters(Parameter[] parameters, Parameters p) {
        Iterator<String> names = p.getParameterNames().iterator();

        int i = 0;
        while (names.hasNext()) {
            while (parameters[i].getName() == null) {
                i++;
            }

            String name = names.next();
            String[] values = p.getParameterValues(name);

            boolean match = false;

            for (Parameter parameter : parameters) {
                if (name.equals(parameter.getName())) {
                    match = true;
                    if (parameter.values.length == 0) {
                        // Special case
                        assertArrayEquals(new String[] {""}, values);
                    } else {
                        assertArrayEquals(parameter.getValues(), values);
                    }
                    break;
                }
            }
            assertTrue(match);
        }
    }

    private static class Parameter {
        private final String name;
        private final String[] values;
        private final UEncoder uencoder = new UEncoder();

        public Parameter(String name, String... values) {
            this.name = name;
            this.values = values;
        }

        public String getName() {
            return name;
        }

        public String[] getValues() {
            return values;
        }

        @Override
        public String toString() {
            StringBuilder result = new StringBuilder();
            boolean first = true;
            if (values.length == 0) {
                return uencoder.encodeURL(name);
            }
            for (String value : values) {
                if (first) {
                    first = false;
                } else {
                    result.append('&');
                }
                if (name != null) {
                    result.append(uencoder.encodeURL(name));
                }
                if (value != null) {
                    result.append('=');
                    result.append(uencoder.encodeURL(value));
                }
            }

            return result.toString();
        }
    }
}
