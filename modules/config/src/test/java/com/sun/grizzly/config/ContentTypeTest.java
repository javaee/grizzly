/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.config;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import com.sun.grizzly.tcp.StaticResourcesAdapter;
import org.jvnet.hk2.config.Dom;
import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

@Test
public class ContentTypeTest extends BaseGrizzlyConfigTest {
    private static final String TEST_NAME = "default-content-type";
    private static final String EXPECTED_CONTENT_TYPE = "text/plain;charset=iso-8859-1";
    private GrizzlyConfig grizzlyConfig;

    @BeforeClass
    public void setup() {
        grizzlyConfig = new GrizzlyConfig("grizzly-config-content-type.xml");
        grizzlyConfig.setupNetwork();
        int count = 0;
        for (GrizzlyServiceListener listener : grizzlyConfig.getListeners()) {
            setRootFolder(listener, count++);
        }
    }

    @AfterClass
    public void tearDown() {
        grizzlyConfig.shutdown();
    }

    public void defaultContentType() throws Exception {
        HttpURLConnection conn = (HttpURLConnection) new URL("http://localhost:38082/test.xyz").openConnection();
        conn.connect();
        Assert.assertEquals(conn.getResponseCode(), 200, "Wrong response code");
        Assert.assertEquals(conn.getHeaderField("Content-Type"), EXPECTED_CONTENT_TYPE, "Missing or wrong response Content-Type.");
    }

    @Override
    protected void setRootFolder(final GrizzlyServiceListener listener, final int count) {
        final StaticResourcesAdapter adapter = (StaticResourcesAdapter) listener.getEmbeddedHttp().getAdapter();
        final String name = System.getProperty("java.io.tmpdir", "/tmp") + "/"
            + Dom.convertName(getClass().getSimpleName()) + count;
        File dir = new File(name);
        dir.mkdirs();
        FileWriter writer;
        try {
            writer = new FileWriter(new File(dir, "test.xyz"));
            try {
                writer.write("<test>\nThis is a test\n</test>");
                writer.flush();
            } finally {
                if (writer != null) {
                    writer.close();
                }
            }
        } catch (IOException e) {
            Assert.fail(e.getMessage(), e);
        }
        adapter.addRootFolder(name);

    }
}
