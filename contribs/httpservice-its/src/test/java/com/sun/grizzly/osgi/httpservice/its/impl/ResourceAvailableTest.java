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

package com.sun.grizzly.osgi.httpservice.its.impl;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.ops4j.pax.exam.CoreOptions.*;
import org.ops4j.pax.exam.Option;
import static org.ops4j.pax.exam.container.def.PaxRunnerOptions.*;
import org.ops4j.pax.exam.junit.Configuration;
import org.ops4j.pax.exam.junit.JUnit4TestRunner;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

/**
 * Integration testing of Resource registration.
 *
 * @author Hubert Iwaniuk
 * @since Feb 9, 2009
 */
@RunWith(JUnit4TestRunner.class)
public class ResourceAvailableTest {

    @Configuration
    public static Option[] configuration() {
        return options(
                repositories(
                        repository("http://repository.springsource.com/maven/bundles/external"),
                        repository("http://repository.ops4j.org/maven2"),
                        repository("http://repo1.maven.org/maven2/")
                ),
                logProfile(),
                frameworks(
                        felix()
                ),
                systemProperty("org.osgi.service.http.port").value("8989"),
                mavenBundle().groupId("com.sun.grizzly").artifactId("grizzly-http-servlet").version("1.9.11-SNAPSHOT"),
                mavenBundle().groupId("com.sun.grizzly").artifactId("grizzly-http").version("1.9.11-SNAPSHOT"),
                mavenBundle().groupId("com.sun.grizzly").artifactId("grizzly-utils").version("1.9.11-SNAPSHOT"),
                mavenBundle().groupId("com.sun.grizzly").artifactId("grizzly-framework").version("1.9.11-SNAPSHOT"),
                mavenBundle().groupId("com.sun.grizzly").artifactId("grizzly-portunif").version("1.9.11-SNAPSHOT"),
                mavenBundle().groupId("com.sun.grizzly").artifactId("grizzly-rcm").version("1.9.11-SNAPSHOT"),
                mavenBundle().groupId("com.sun.grizzly.osgi").artifactId("grizzly-httpservice").version(
                        "1.9.11-SNAPSHOT"),
                mavenBundle().groupId("com.sun.grizzly.osgi.httpservice.its").artifactId("first-it").version(
                        "1.0-SNAPSHOT")
        );
    }

    @Test
    public void isResourceAvailable() throws IOException {
        URL url = new URL("http://localhost:8989/2/index.html");
        final HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        Assert.assertEquals("Status code should be the same.", 200, conn.getResponseCode());
    }
}
