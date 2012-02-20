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

package org.glassfish.grizzly.memcached.zookeeper;

import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * @author Bongjae Chang
 */
public class ZKClientTest {

    private static final Logger logger = LoggerFactory.getLogger(ZKClientTest.class);

    private static final String DEFAULT_ZOOKEEPER_ADDRESS = "localhost:2181";
    private static final String DEFAULT_CHARSET = "UTF-8";
    private static final String ROOT = "/zktest";
    private static final String REGION = "test-region";

    @Test
    public void emptyTest() {
    }

    // zookeeper server should be booted in local
    //@Test
    public void testNoBarrier() {
        // init
        final ZKClient.Builder builder = new ZKClient.Builder("no-barrier-test", DEFAULT_ZOOKEEPER_ADDRESS);
        builder.rootPath(ROOT).connectTimeoutInMillis(3000).sessionTimeoutInMillis(3000).commitDelayTimeInSecs(30);
        final ZKClient zkClient = builder.build();
        try {
            zkClient.connect();
        } catch (IOException ie) {
            logger.error("failed to connection the server", ie);
            Assert.fail();
        } catch (InterruptedException ignore) {
            Assert.fail();
        }

        final String cacheServerList = "localhost:1111, localhost:2222";
        byte[] serverListBytes = null;
        try {
            serverListBytes = cacheServerList.getBytes(DEFAULT_CHARSET);
        } catch (UnsupportedEncodingException uee) {
            logger.error("failed to get bytes", uee);
            Assert.fail();
        }
        final byte[] expected = serverListBytes;
        final String dataPath = zkClient.registerBarrier(REGION,
                new BarrierListener() {
                    @Override
                    public void onInit(String regionName, String path, byte[] remoteBytes) {
                        Assert.assertEquals(REGION, regionName);
                        Assert.assertEquals(ROOT + "/barrier/" + REGION + "/data", path);
                        if (remoteBytes == null) {
                            final byte[] remoteBytes2 = zkClient.getData(path, false, null);
                            Assert.assertArrayEquals(expected, remoteBytes2);
                        } else {
                            try {
                                logger.info("already has data. data=\"{}\"", new String(remoteBytes, DEFAULT_CHARSET));
                            } catch (Exception ignore) {
                            }
                        }
                    }

                    @Override
                    public void onCommit(String regionName, String path, byte[] remoteBytes) {
                        Assert.fail();
                    }

                    @Override
                    public void onDestroy(String regionName) {
                    }
                },
                serverListBytes);
        Assert.assertNotNull(dataPath);
        logger.info("dataPath={}", dataPath);
        Assert.assertNotNull(zkClient.exists(dataPath, false));

        // clean
        zkClient.unregisterBarrier(REGION);
        clearRegionRepository(zkClient, REGION);
        clearBaseRepository(zkClient);
        zkClient.shutdown();
    }

    // zookeeper server should be booted in local
    //@Test
    public void testOneBarrier() {
        // init
        final ZKClient.Builder builder = new ZKClient.Builder("one-barrier-test", DEFAULT_ZOOKEEPER_ADDRESS);
        builder.rootPath(ROOT).connectTimeoutInMillis(3000).sessionTimeoutInMillis(3000).commitDelayTimeInSecs(2);
        final ZKClient zkClient = builder.build();
        try {
            zkClient.connect();
        } catch (IOException ie) {
            logger.error("failed to connection the server", ie);
            Assert.fail();
        } catch (InterruptedException ignore) {
            Assert.fail();
        }

        final String cacheServerList = "localhost:1111, localhost:2222";
        byte[] serverListBytes = null;
        try {
            serverListBytes = cacheServerList.getBytes(DEFAULT_CHARSET);
        } catch (UnsupportedEncodingException uee) {
            logger.error("failed to get bytes", uee);
            Assert.fail();
        }

        final CountDownLatch latch = new CountDownLatch(1);
        final String newData = "localhost:1111, localhost:2222, localhost:3333";
        final byte[] newDataBytes;
        try {
            newDataBytes = newData.getBytes(DEFAULT_CHARSET);
        } catch (UnsupportedEncodingException e) {
            Assert.fail("encoding error");
            return;
        }
        final String dataPath = zkClient.registerBarrier(REGION,
                new BarrierListener() {
                    @Override
                    public void onInit(String regionName, String path, byte[] remoteBytes) {
                    }

                    @Override
                    public void onCommit(String regionName, String path, byte[] remoteBytes) {
                        Assert.assertArrayEquals(newDataBytes, remoteBytes);
                        latch.countDown();
                    }

                    @Override
                    public void onDestroy(String regionName) {
                    }
                },
                serverListBytes);
        Assert.assertNotNull(dataPath);
        Assert.assertNotNull(zkClient.exists(dataPath, false));

        zkClient.setData(dataPath, newDataBytes, -1);
        try {
            if (!latch.await(3, TimeUnit.SECONDS)) {
                Assert.fail("timed out");
            }
        } catch (InterruptedException ignore) {
        }

        // clean
        zkClient.unregisterBarrier(REGION);
        clearRegionRepository(zkClient, REGION);
        clearBaseRepository(zkClient);
        zkClient.shutdown();
    }

    // zookeeper server should be booted in local
    //@Test
    public void testBarrier() {
        final int clientCount = 10;
        final ZKClient[] zkClient = new ZKClient[clientCount + 1];
        for (int i = 0; i < clientCount + 1; i++) {
            final ZKClient.Builder builder = new ZKClient.Builder("barrier-test" + i, DEFAULT_ZOOKEEPER_ADDRESS);
            builder.rootPath(ROOT).connectTimeoutInMillis(3000).sessionTimeoutInMillis(3000).commitDelayTimeInSecs(3);
            zkClient[i] = builder.build();
            try {
                zkClient[i].connect();
            } catch (IOException ie) {
                logger.error("failed to connection the server", ie);
                Assert.fail();
            } catch (InterruptedException ignore) {
                Assert.fail();
            }
        }

        final String cacheServerList = "localhost:1111, localhost:2222";
        byte[] serverListBytes = null;
        try {
            serverListBytes = cacheServerList.getBytes(DEFAULT_CHARSET);
        } catch (UnsupportedEncodingException uee) {
            logger.error("failed to get bytes", uee);
            Assert.fail();
        }

        final CountDownLatch latch = new CountDownLatch(clientCount);
        final String newData = "localhost:1111, localhost:2222, localhost:3333";
        final byte[] newDataBytes;
        try {
            newDataBytes = newData.getBytes(DEFAULT_CHARSET);
        } catch (UnsupportedEncodingException e) {
            Assert.fail("encoding error");
            return;
        }
        String dataPath = null;
        for (int i = 1; i < clientCount + 1; i++) {
            dataPath = zkClient[i].registerBarrier(REGION,
                    new BarrierListener() {
                        @Override
                        public void onInit(String regionName, String path, byte[] remoteBytes) {
                        }

                        @Override
                        public void onCommit(String regionName, String path, byte[] remoteBytes) {
                            Assert.assertArrayEquals(newDataBytes, remoteBytes);
                            latch.countDown();
                        }

                        @Override
                        public void onDestroy(String regionName) {
                        }
                    },
                    serverListBytes);
            Assert.assertNotNull(dataPath);
            Assert.assertNotNull(zkClient[i].exists(dataPath, false));
        }

        zkClient[0].setData(dataPath, newDataBytes, -1);
        try {
            if (!latch.await(6, TimeUnit.SECONDS)) {
                Assert.fail("timed out");
            }
        } catch (InterruptedException ignore) {
        }

        // clean
        for (int i = 1; i < clientCount + 1; i++) {
            zkClient[i].unregisterBarrier(REGION);
            zkClient[i].shutdown();
        }

        clearRegionRepository(zkClient[0], REGION);
        clearBaseRepository(zkClient[0]);
        zkClient[0].shutdown();
    }

    // zookeeper server should be booted in local
    //@Test
    public void testComplexBarrier() {
        final int clientCount = 5;
        final int regionCount = 5;
        final ZKClient[] zkClient = new ZKClient[clientCount + 1];
        for (int i = 0; i < clientCount + 1; i++) {
            final ZKClient.Builder builder = new ZKClient.Builder("barrier-test" + i, DEFAULT_ZOOKEEPER_ADDRESS);
            builder.rootPath(ROOT).connectTimeoutInMillis(3000).sessionTimeoutInMillis(3000).commitDelayTimeInSecs(4);
            zkClient[i] = builder.build();
            try {
                zkClient[i].connect();
            } catch (IOException ie) {
                logger.error("failed to connection the server", ie);
                Assert.fail();
            } catch (InterruptedException ignore) {
                Assert.fail();
            }
        }

        final String cacheServerList = "localhost:1111, localhost:2222";
        byte[] serverListBytes = null;
        try {
            serverListBytes = cacheServerList.getBytes(DEFAULT_CHARSET);
        } catch (UnsupportedEncodingException uee) {
            logger.error("failed to get bytes", uee);
            Assert.fail();
        }

        final CountDownLatch latch = new CountDownLatch(clientCount * regionCount);
        final String newData = "localhost:1111, localhost:2222, localhost:3333";
        final byte[] newDataBytes;
        try {
            newDataBytes = newData.getBytes(DEFAULT_CHARSET);
        } catch (UnsupportedEncodingException e) {
            Assert.fail("encoding error");
            return;
        }
        final String[] dataPathArray = new String[regionCount];
        for (int i = 1; i < clientCount + 1; i++) {
            for (int j = 0; j < regionCount; j++) {
                dataPathArray[j] = zkClient[i].registerBarrier(REGION + j,
                        new BarrierListener() {
                            @Override
                            public void onInit(String regionName, String path, byte[] remoteBytes) {
                            }

                            @Override
                            public void onCommit(String regionName, String path, byte[] remoteBytes) {
                                Assert.assertArrayEquals(newDataBytes, remoteBytes);
                                latch.countDown();
                            }

                            @Override
                            public void onDestroy(String regionName) {
                            }
                        },
                        serverListBytes);
                Assert.assertNotNull(dataPathArray[j]);
                Assert.assertNotNull(zkClient[i].exists(dataPathArray[j], false));
            }
        }
        for (int i = 0; i < regionCount; i++) {
            zkClient[0].setData(dataPathArray[i], newDataBytes, -1);
        }
        try {
            if (!latch.await(8, TimeUnit.SECONDS)) {
                Assert.fail("timed out");
            }
        } catch (InterruptedException ignore) {
        }

        // clean
        for (int i = 1; i < clientCount + 1; i++) {
            for (int j = 0; j < regionCount; j++) {
                zkClient[i].unregisterBarrier(REGION + j);
            }
            zkClient[i].shutdown();
        }
        for (int i = 0; i < regionCount; i++) {
            clearRegionRepository(zkClient[0], REGION + i);
        }
        clearBaseRepository(zkClient[0]);
        zkClient[0].shutdown();
    }

    private static void clearRegionRepository(final ZKClient zkClient, final String regionName) {
        if (zkClient == null) {
            return;
        }
        final String regionPath = ROOT + "/barrier/" + regionName;
        final String dataPath = regionPath + "/data";
        final String currentPath = regionPath + "/current";
        final String participantsPath = regionPath + "/participants";

        if (zkClient.exists(dataPath, false) != null) {
            zkClient.delete(dataPath, -1);
        }
        if (zkClient.exists(currentPath, false) != null) {
            final List<String> currentNodes = zkClient.getChildren(currentPath, false);
            if (currentNodes == null || currentNodes.isEmpty()) {
                zkClient.delete(currentPath, -1);
            } else {
                for (String node : currentNodes) {
                    zkClient.delete(currentPath + "/" + node, -1);
                }
                zkClient.delete(currentPath, -1);
            }
        }
        if (zkClient.exists(participantsPath, false) != null) {
            final List<String> paticipants = zkClient.getChildren(participantsPath, false);
            if (paticipants == null || paticipants.isEmpty()) {
                zkClient.delete(participantsPath, -1);
            } else {
                for (String node : paticipants) {
                    zkClient.delete(participantsPath + "/" + node, -1);
                }
                zkClient.delete(participantsPath, -1);
            }
        }
        zkClient.delete(regionPath, -1);
    }

    private static void clearBaseRepository(final ZKClient zkClient) {
        zkClient.delete(ROOT + "/barrier", -1);
        zkClient.delete(ROOT, -1);
    }
}
