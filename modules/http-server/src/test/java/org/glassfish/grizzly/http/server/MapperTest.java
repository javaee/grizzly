/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server;

import org.glassfish.grizzly.http.server.util.Mapper;
import org.glassfish.grizzly.http.server.util.MappingData;
import org.glassfish.grizzly.http.util.DataChunk;
import org.junit.Test;
import static org.junit.Assert.*;

/**
 * {@link Mapper} tests
 */
public class MapperTest {
    @Test
    public void testVirtualServer() throws Exception {
        final Object defaultHost = new Object();
        final Object host1 = new Object();
        final Mapper mapper = new Mapper();
        mapper.setDefaultHostName("default");
        mapper.addHost("default", new String[] {"default"}, defaultHost);
        mapper.addHost("host1", new String[] {"host1"}, host1);
        
        final Object context1 = new Object();
        mapper.addContext("default", "/context1", context1, null, null);
        mapper.addContext("host1", "/context1", context1, null, null);
        
        final Object wrapper11 = new Object();
        final Object wrapper12 = new Object();
        mapper.addWrapper("default", "/context1", "/wrapper11", wrapper11);
        mapper.addWrapper("host1", "/context1", "/wrapper11", wrapper11);
        mapper.addWrapper("default", "/context1", "/wrapper12", wrapper12);
        mapper.addWrapper("host1", "/context1", "/wrapper12", wrapper12);
        
        final Object context2 = new Object();
        mapper.addContext("host1", "/context2", context2, null, null);
        
        final Object wrapper21 = new Object();
        mapper.addWrapper("host1", "/context2", "/wrapper21", wrapper21);
        
        
        // Test wrapper1 on default host
        final DataChunk host = DataChunk.newInstance();
        host.setBytes("default".getBytes());
        
        final DataChunk uri = DataChunk.newInstance();
        uri.setBytes("/context1/wrapper11".getBytes());
        
        MappingData md = new MappingData();
        mapper.map(host, uri, md);
        
        assertEquals(defaultHost, md.host);
        assertEquals(context1, md.context);
        assertEquals(wrapper11, md.wrapper);

        
        // Test no wrapper2 on default host
        md.recycle();
        uri.recycle();

        uri.setBytes("/context2/wrapper21".getBytes());
        
        mapper.map(host, uri, md);
        
        assertEquals(defaultHost, md.host);
        assertNull(md.context);
        assertNull(md.wrapper);

        // Test wrapper2 on host1
        md.recycle();
        host.recycle();
        uri.recycle();

        host.setBytes("host1".getBytes());
        uri.setBytes("/context2/wrapper21".getBytes());
        
        mapper.map(host, uri, md);
        
        assertEquals(host1, md.host);
        assertEquals(context2, md.context);
        assertEquals(wrapper21, md.wrapper);
        
    }
}
