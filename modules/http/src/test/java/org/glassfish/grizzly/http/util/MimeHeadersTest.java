/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved.
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

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class MimeHeadersTest {

    MimeHeaders mimeHeaders;

    @Before
    public void setUp() {
        mimeHeaders = new MimeHeaders();
        mimeHeaders.addValue("custom-before").setString("one");
        mimeHeaders.addValue("custom-before").setString("two");
        mimeHeaders.addValue("custom-before").setString("three");
        mimeHeaders.setValue("custom-before-2").setString("one");
        mimeHeaders.mark();
        mimeHeaders.addValue("custom-after").setString("one");
        mimeHeaders.addValue("custom-after").setString("two");
        mimeHeaders.addValue("custom-after").setString("three");
        mimeHeaders.setValue("custom-after-2").setString("one");
    }

    @Test
    public void testNames() throws Exception {
        final String[] expectedNames = {
                "custom-before", "custom-before-2", "custom-after", "custom-after-2"
        };
        Iterable<String> result = mimeHeaders.names();
        List<String> list = new ArrayList<>();
        for (String s : result) {
            list.add(s);
        }
        Assert.assertArrayEquals(expectedNames, list.toArray(new String[list.size()]));
    }

    @Test
    public void testTrailerNames() throws Exception {
        final String[] expectedNames = {
                "custom-after", "custom-after-2"
        };
        Iterable<String> result = mimeHeaders.trailerNames();
        List<String> list = new ArrayList<>();
        for (String s : result) {
            list.add(s);
        }
        Assert.assertArrayEquals(expectedNames, list.toArray(new String[list.size()]));
    }

    @Test
    public void testValues() throws Exception {
        final String[] expectedValuesSet1 = {
                "one", "two", "three"
        };
        final String[] expectedValuesSet2 = {
                "one"
        };
        Iterable<String> result = mimeHeaders.values("custom-before");
        List<String> list = new ArrayList<>();
        for (String s : result) {
            list.add(s);
        }
        Assert.assertArrayEquals(expectedValuesSet1, list.toArray(new String[list.size()]));

        result = mimeHeaders.values("custom-before-2");
        list = new ArrayList<>();
        for (String s : result) {
            list.add(s);
        }
        Assert.assertArrayEquals(expectedValuesSet2, list.toArray(new String[list.size()]));

        result = mimeHeaders.values("custom-after");
        list = new ArrayList<>();
        for (String s : result) {
            list.add(s);
        }
        Assert.assertArrayEquals(expectedValuesSet1, list.toArray(new String[list.size()]));

        result = mimeHeaders.values("custom-after-2");
        list = new ArrayList<>();
        for (String s : result) {
            list.add(s);
        }
        Assert.assertArrayEquals(expectedValuesSet2, list.toArray(new String[list.size()]));
    }

    @Test
    public void testTrailerValues() throws Exception {
        final String[] expectedValuesSet1 = {
                "one", "two", "three"
        };
        final String[] expectedValuesSet2 = {
                "one"
        };
        final String[] emptySet = {};

        Iterable<String> result = mimeHeaders.trailerValues("custom-before");
        List<String> list = new ArrayList<>();
        for (String s : result) {
            list.add(s);
        }
        Assert.assertArrayEquals(emptySet, list.toArray(new String[list.size()]));

        result = mimeHeaders.trailerValues("custom-before-2");
        list = new ArrayList<>();
        for (String s : result) {
            list.add(s);
        }
        Assert.assertArrayEquals(emptySet, list.toArray(new String[list.size()]));

        result = mimeHeaders.trailerValues("custom-after");
        list = new ArrayList<>();
        for (String s : result) {
            list.add(s);
        }
        Assert.assertArrayEquals(expectedValuesSet1, list.toArray(new String[list.size()]));

        result = mimeHeaders.trailerValues("custom-after-2");
        list = new ArrayList<>();
        for (String s : result) {
            list.add(s);
        }
        Assert.assertArrayEquals(expectedValuesSet2, list.toArray(new String[list.size()]));
    }

}
