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

package org.glassfish.grizzly.memcached;

import org.junit.Assert;
import org.junit.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * @author Bongjae Chang
 */
public class ConsistentHashStoreTest {

    @Test
    public void testBasicConsistentHash() {
        final ConsistentHashStore<String> consistentHash = new ConsistentHashStore<String>();
        consistentHash.add("server1");
        consistentHash.add("server2");
        consistentHash.add("server3");

        final String selectedServer = consistentHash.get("key");
        Assert.assertNotNull(selectedServer);
        Assert.assertEquals(selectedServer, consistentHash.get("key"));

        consistentHash.remove(selectedServer);
        Assert.assertTrue(!selectedServer.equals(consistentHash.get("key")));

        consistentHash.add(selectedServer);
        Assert.assertEquals(selectedServer, consistentHash.get("key"));
    }

    @Test
    public void testSeveralServersAndKeys() {
        final int initialServerNum = 50;
        final int initialKeyNum = 200;
        final int newServersNum = 10;

        final ConsistentHashStore<String> consistentHash = new ConsistentHashStore<String>();
        final HashMap<String, Set<String>> map = new HashMap<String, Set<String>>();
        for (int i = 0; i < initialServerNum; i++) {
            final String serverName = "server" + i;
            consistentHash.add(serverName);
            map.put(serverName, new HashSet<String>());
        }

        for (int i = 0; i < initialKeyNum; i++) {
            final String key = "key" + i;
            final String selectedServer = consistentHash.get(key);
            Set<String> keySet = map.get(selectedServer);
            keySet.add(key);
        }

        // server failure
        Random random = new Random();
        int serverIndex = random.nextInt(initialServerNum);
        final String failureServer = "server" + serverIndex;
        consistentHash.remove(failureServer);

        // when a server failed, original keys should not have failure server
        for (String failureKey : map.remove(failureServer)) {
            Assert.assertTrue(!failureServer.equals(consistentHash.get(failureKey)));
        }

        // when a server failed, original keys should have original servers.
        for (Map.Entry<String, Set<String>> entry : map.entrySet()) {
            for (String key : entry.getValue()) {
                Assert.assertEquals(entry.getKey(), consistentHash.get(key));
            }
        }

        // when new servers added, some original keys should be distributed into new servers
        for (int i = initialServerNum; i < initialServerNum + newServersNum; i++) {
            consistentHash.add("server" + i);
        }
        boolean distributed = false;
        for (int i = 0; i < initialKeyNum; i++) {
            final String key = "key" + i;
            for (int j = initialServerNum; j < initialServerNum + newServersNum; j++) {
                if (("server" + j).equals(consistentHash.get(key))) {
                    distributed = true;
                    break;
                }
            }
            if (distributed) {
                break;
            }
        }
        Assert.assertTrue(distributed);
    }
}
