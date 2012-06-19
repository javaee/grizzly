/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
package com.sun.grizzly.util;

import com.sun.grizzly.util.http.MimeHeaders;
import junit.framework.TestCase;

import java.lang.reflect.Array;
import java.lang.reflect.Field;

public class MimeHeadersTest extends TestCase {


    // ------------------------------------------------------------ Test Methods


    public void testBoundedDefaultGrowth() throws Exception {

        doTest(new MimeHeaders());

    }

    public void testBoundedSpecificGrowth() throws Exception {

        final MimeHeaders headers = new MimeHeaders();
        headers.setMaxNumHeaders(25);
        doTest(headers);

    }

    public void testUnboundedGrowthWithConstant() throws Exception {

        final MimeHeaders headers = new MimeHeaders();
        headers.setMaxNumHeaders(MimeHeaders.MAX_NUM_HEADERS_UNBOUNDED);
        doTest(headers);

    }

    public void testUnboundedGrowth() throws Exception {

        final MimeHeaders headers = new MimeHeaders();
        headers.setMaxNumHeaders(Integer.MIN_VALUE);
        doTest(headers);

    }


    // --------------------------------------------------------- Private Methods


    private static void doTest(final MimeHeaders headers) throws Exception {
        int maxHeaders = headers.getMaxNumHeaders();
        boolean unbounded = maxHeaders < 0;
        try {
            for (int i = 0, len = ((unbounded) ? 1000 : maxHeaders - 1); i < len; i++) {
                headers.addValue("" + i).setString("" + i);
                assertEquals("" + i, headers.getHeader("" + i));
            }
        } catch (Exception e) {
            fail("Unexpected exception: " + e.toString());
        }

        try {
            headers.addValue("fail").setString("fail");
        } catch (Exception e) {
            if (!unbounded) {
                if (!(e instanceof MimeHeaders.MaxHeaderCountExceededException)) {
                    fail("Unexpected exception type when exceeded max number of headers: " + e.getClass().toString());
                } else {
                    assertEquals("Illegal attempt to exceed the configured maximum headers of " + maxHeaders, e.getMessage());
                }
            } else {
                fail("Headers configured to be unbounded, but exception was thrown: " + e.toString());
            }
        }

        if (!unbounded) {
            final Field f = MimeHeaders.class.getDeclaredField("headers");
            f.setAccessible(true);
            assertTrue(Array.getLength(f.get(headers)) == maxHeaders);
        }
    }
}
