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

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.WatchedEvent;
import org.apache.zookeeper.Watcher;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;
import org.glassfish.grizzly.Grizzly;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Zookeeper client implementation for barrier and recoverable operation
 * <p/>
 * All operations will be executed on the valid connection because the failed connection will be reconnected automatically.
 * This has Barrier function.
 * {@link BarrierListener} can be registered with a specific region and initial data by the {@link #registerBarrier} method and unregistered by the {@link #unregisterBarrier} method.
 * If the zookeeper server doesn't have the data node, the given initial data will be set in the server when the {@link #registerBarrier} method is called.
 * If the specific data will be changed in remote zookeeper server, all clients which have joined will receive changes simultaneously.
 * If all clients receive changes successfully,
 * {@link BarrierListener#onCommit} will be called simultaneously at the scheduled time(data modification time + {@code commitDelayTimeInSecs}).
 * <p/>
 * This also supports some safe APIs which is similar to original {@link ZooKeeper}'s APIs
 * like create, delete, exists, getChildren, getData and setData.
 * <p/>
 * Examples of barrier's use:
 * {@code
 * // initial
 * <p/>
 * final ZKClient.Builder builder = new ZKClient.Builder("myZookeeperClient", "localhost:2181");
 * builder.rootPath(ROOT).connectTimeoutInMillis(3000).sessionTimeoutInMillis(30000).commitDelayTimeInSecs(60);
 * final ZKClient zkClient = builder.build();
 * zkClient.connect()
 * final String registeredPath = zkClient.registerBarrier( "user", myListener, initData );
 * // ...
 * // cleanup
 * zkClient.unregisterBarrier( "user" );
 * zkClient.shutdown();
 * }
 * <p/>
 * [NOTE]
 * Zookeeper already guides some simple barrier examples:
 * http://zookeeper.apache.org/doc/r3.3.4/zookeeperTutorial.html
 * http://code.google.com/p/good-samples/source/browse/trunk/zookeeper-3.x/src/main/java/com/googlecode/goodsamples/zookeeper/barrier/Barrier.java
 * <p/>
 * But, their examples have a race condision issue:
 * https://issues.apache.org/jira/browse/ZOOKEEPER-1011
 *
 * @author Bongjae Chang
 */
public class ZKClient {

    private static final Logger logger = Grizzly.logger(ZKClient.class);

    private static final String JVM_AND_HOST_UNIQUE_ID = ManagementFactory.getRuntimeMXBean().getName();
    private static final int RETRY_COUNT_UNTIL_CONNECTED = 5;
    /**
     * Path information:
     * /root/barrier/region_name/current/(client1, client2, ...)
     * /root/barrier/region_name/data
     * /root/barrier/region_name/participants/(client1, client2, ...)
     */
    private static final String BASE_PATH = "/barrier";
    private static final String CURRENT_PATH = "/current";
    private static final String DATA_PATH = "/data";
    private static final String PARTICIPANTS_PATH = "/participants";
    private static final byte[] NO_DATA = new byte[0];

    private final Lock lock = new ReentrantLock();
    private final Condition lockCondition = lock.newCondition();
    private final AtomicBoolean reconnectingFlag = new AtomicBoolean(false);
    private boolean connected;
    private Watcher.Event.KeeperState currentState;
    private AtomicBoolean running = new AtomicBoolean(true);
    private final Map<String, BarrierListener> listenerMap = new ConcurrentHashMap<String, BarrierListener>();
    private final ScheduledExecutorService scheduledExecutor;
    private ZooKeeper zooKeeper;

    private final String uniqueId;
    private final String uniqueIdPath;
    private final String basePath;

    private final String name;
    private final String zooKeeperServerList;
    private final long connectTimeoutInMillis;
    private final long sessionTimeoutInMillis;
    private final String rootPath;
    private final long commitDelayTimeInSecs;

    private ZKClient(final Builder builder) {
        this.name = builder.name;
        this.uniqueId = JVM_AND_HOST_UNIQUE_ID + "_" + this.name;
        this.uniqueIdPath = normalizePath(this.uniqueId);
        this.rootPath = normalizePath(builder.rootPath);
        this.basePath = this.rootPath + BASE_PATH;

        this.zooKeeperServerList = builder.zooKeeperServerList;
        this.connectTimeoutInMillis = builder.connectTimeoutInMillis;
        this.sessionTimeoutInMillis = builder.sessionTimeoutInMillis;
        this.commitDelayTimeInSecs = builder.commitDelayTimeInSecs;

        this.scheduledExecutor = Executors.newScheduledThreadPool(5);
    }

    /**
     * Connect this client to the zookeeper server
     * <p/>
     * this method will wait for {@link Watcher.Event.KeeperState#SyncConnected} from the zookeeper server.
     *
     * @throws IOException          the io exception of internal ZooKeeper
     * @throws InterruptedException the interrupted exception of internal ZooKeeper
     */
    public void connect() throws IOException, InterruptedException {
        lock.lock();
        try {
            if (connected) {
                return;
            }
            zooKeeper = new ZooKeeper(zooKeeperServerList,
                    (int) sessionTimeoutInMillis,
                    new InternalWatcher(new Watcher() {

                        @Override
                        public void process(WatchedEvent event) {
                        }
                    }));
            if (!ensureConnected(connectTimeoutInMillis)) {
                zooKeeper.close();
                currentState = Watcher.Event.KeeperState.Disconnected;
                connected = false;
            } else {
                connected = true;
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "connected the zookeeper server successfully");
                }
            }
        } finally {
            lock.unlock();
        }
    }

    private void close() {
        lock.lock();
        try {
            if (!connected) {
                return;
            }
            if (zooKeeper != null) {
                try {
                    zooKeeper.close();
                } catch (InterruptedException ignore) {
                }
            }
            currentState = Watcher.Event.KeeperState.Disconnected;
            connected = false;
        } finally {
            lock.unlock();
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "closed successfully");
        }
    }

    private void reconnect() throws IOException, InterruptedException {
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "trying to reconnect the zookeeper server");
        }
        final boolean localReconnectingFlag = reconnectingFlag.get();
        lock.lock();
        try {
            if (!reconnectingFlag.compareAndSet(localReconnectingFlag, !localReconnectingFlag)) {
                // prevent duplicated trials
                return;
            }
            close();
            connect();
        } finally {
            lock.unlock();
        }
        if (logger.isLoggable(Level.INFO)) {
            logger.log(Level.INFO, "reconnected the zookeeper server successfully");
        }
    }

    /**
     * Close this client
     */
    public void shutdown() {
        if (!running.compareAndSet(true, false)) {
            if (logger.isLoggable(Level.FINE)) {
                logger.log(Level.FINE, "shutting down or already shutted down");
            }
            return;
        }
        listenerMap.clear();
        close();
        if (scheduledExecutor != null) {
            scheduledExecutor.shutdown();
        }
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "shutted down successfully");
        }
    }

    /**
     * Register the specific barrier
     *
     * @param regionName  specific region name
     * @param listener    {@link BarrierListener} implementations
     * @param initialData initial data. if the zookeeper server doesn't have any data, this will be set
     * @return the registered data path of the zookeeper server
     */
    public String registerBarrier(final String regionName, final BarrierListener listener, final byte[] initialData) {
        if (regionName == null) {
            throw new IllegalArgumentException("region name must be not null");
        }
        if (listener == null) {
            throw new IllegalArgumentException("listener must be not null");
        }
        listenerMap.put(regionName, listener);

        // ensure all paths exist
        createWhenThereIsNoNode(rootPath, NO_DATA, CreateMode.PERSISTENT); // ensure root path
        createWhenThereIsNoNode(basePath, NO_DATA, CreateMode.PERSISTENT); // ensure base path
        final String currentRegionPath = basePath + normalizePath(regionName);
        createWhenThereIsNoNode(currentRegionPath, NO_DATA, CreateMode.PERSISTENT); // ensure my region path
        createWhenThereIsNoNode(currentRegionPath + CURRENT_PATH, NO_DATA, CreateMode.PERSISTENT); // ensure nodes path
        createWhenThereIsNoNode(currentRegionPath + CURRENT_PATH + uniqueIdPath, NO_DATA, CreateMode.EPHEMERAL); // register own node path
        createWhenThereIsNoNode(currentRegionPath + PARTICIPANTS_PATH, NO_DATA, CreateMode.PERSISTENT); // ensure participants path

        final String currentDataPath = currentRegionPath + DATA_PATH;
        final boolean dataCreated = createWhenThereIsNoNode(currentDataPath,
                initialData == null ? NO_DATA : initialData,
                CreateMode.PERSISTENT); // ensure data path
        if (!dataCreated) { // if the remote data already exists
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO, "the central data exists in the zookeeper server");
            }
            final byte[] remoteDataBytes = getData(currentDataPath, false, null);
            try {
                listener.onInit(regionName, currentDataPath, remoteDataBytes);
            } catch (Exception e) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "failed to onInit. name=" + name + ", regionName=" + regionName + ", listener=" + listener, e);
                }
            }
        } else {
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO,
                        "initial data was set because there was no remote data in the zookeeper server. initialData={0}",
                        initialData);
            }
            try {
                listener.onInit(regionName, currentDataPath, null);
            } catch (Exception e) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "failed to onInit. name=" + name + ", regionName=" + regionName + ", listener=" + listener, e);
                }
            }
        }

        // register the watcher for detecting the data's changes
        exists(currentDataPath, new RegionWatcher(regionName));
        if (logger.isLoggable(Level.FINE)) {
            logger.log(Level.FINE, "the path \"{0}\" will be watched. name={1}, regionName={2}", new Object[]{name, currentDataPath, regionName});
        }
        return currentDataPath;
    }

    private boolean createWhenThereIsNoNode(final String path, final byte[] data, final CreateMode createMode) {
        if (exists(path, false) != null) {
            return false;
        }
        create(path, data, ZooDefs.Ids.OPEN_ACL_UNSAFE, createMode);
        return true;
    }

    /**
     * Unregister the listener which was registered by {@link #registerBarrier}
     *
     * @param regionName specific region name
     */
    public void unregisterBarrier(final String regionName) {
        if (regionName == null) {
            return;
        }
        final BarrierListener listener = listenerMap.remove(regionName);
        if (listener != null) {
            try {
                listener.onDestroy(regionName);
            } catch (Exception e) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING, "failed to onDestroy. name=" + name + ", regionName=" + regionName + ", listener=" + listener, e);
                }
            }
        }
    }

    public String create(final String path, final byte[] data, final List<ACL> acl, final CreateMode createMode) {
        try {
            return retryUntilConnected(new Callable<String>() {
                @Override
                public String call() throws Exception {
                    return zooKeeper.create(path, data, acl, createMode);
                }
            });
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to do \"create\". path=" + path + ", data=" + Arrays.toString(data), e);
            }
            return null;
        }
    }

    public Stat exists(final String path, final boolean watch) {
        try {
            return retryUntilConnected(new Callable<Stat>() {
                @Override
                public Stat call() throws Exception {
                    return zooKeeper.exists(path, watch);
                }
            });
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to do \"exists\". path=" + path, e);
            }
            return null;
        }
    }

    public Stat exists(final String path, final Watcher watch) {
        try {
            return retryUntilConnected(new Callable<Stat>() {
                @Override
                public Stat call() throws Exception {
                    return zooKeeper.exists(path, new InternalWatcher(watch));
                }
            });
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to do \"exists\". path=" + path, e);
            }
            return null;
        }
    }

    public List<String> getChildren(final String path, final boolean watch) {
        try {
            return retryUntilConnected(new Callable<List<String>>() {
                @Override
                public List<String> call() throws Exception {
                    return zooKeeper.getChildren(path, watch);
                }
            });
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to do \"getChildren\". path=" + path, e);
            }
            return null;
        }
    }

    public List<String> getChildren(final String path, final Watcher watcher) {
        try {
            return retryUntilConnected(new Callable<List<String>>() {
                @Override
                public List<String> call() throws Exception {
                    return zooKeeper.getChildren(path, new InternalWatcher(watcher));
                }
            });
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to do \"getChildren\". path=" + path, e);
            }
            return null;
        }
    }

    public byte[] getData(final String path, final boolean watch, final Stat stat) {
        try {
            return retryUntilConnected(new Callable<byte[]>() {
                @Override
                public byte[] call() throws Exception {
                    return zooKeeper.getData(path, watch, stat);
                }
            });
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to do \"getData\". path=" + path, e);
            }
            return null;
        }
    }

    public byte[] getData(final String path, final Watcher watcher) {
        try {
            return retryUntilConnected(new Callable<byte[]>() {
                @Override
                public byte[] call() throws Exception {
                    return zooKeeper.getData(path, new InternalWatcher(watcher), null);
                }
            });
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to do \"getData\". path=" + path, e);
            }
            return null;
        }
    }

    public Stat setData(final String path, final byte[] data, final int version) {
        try {
            return retryUntilConnected(new Callable<Stat>() {
                @Override
                public Stat call() throws Exception {
                    return zooKeeper.setData(path, data, version);
                }
            });
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to do \"setData\". path=" + path + ", data=" + Arrays.toString(data) + ", version=" + version, e);
            }
            return null;
        }
    }

    public boolean delete(final String path, final int version) {
        try {
            retryUntilConnected(new Callable<Boolean>() {
                @Override
                public Boolean call() throws Exception {
                    zooKeeper.delete(path, version);
                    return true;
                }
            });
        } catch (Exception e) {
            if (logger.isLoggable(Level.SEVERE)) {
                logger.log(Level.SEVERE, "failed to do \"delete\". path=" + path + ", version=" + version, e);
            }
            return false;
        }
        return false;
    }

    private <T> T retryUntilConnected(final Callable<T> callable) throws Exception {
        for (int i = 0; i < RETRY_COUNT_UNTIL_CONNECTED; i++) {
            try {
                return callable.call();
            } catch (KeeperException.ConnectionLossException cle) {
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "the callable will be retried because of ConnectionLossException");
                } else if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "the callable will be retried because of ConnectionLossException", cle);
                }
                reconnect();
            } catch (KeeperException.SessionExpiredException see) {
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO, "the callable will be retried because of SessionExpiredException");
                } else if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE, "the callable will be retried because of SessionExpiredException", see);
                }
                reconnect();
            }
        }
        if (logger.isLoggable(Level.SEVERE)) {
            logger.log(Level.SEVERE, "failed to retry. retryCount={0}", RETRY_COUNT_UNTIL_CONNECTED);
        }
        return null;
    }

    // should be guided by the lock
    private boolean ensureConnected(final long timeoutInMillis) {
        final Date timeoutDate;
        if (timeoutInMillis < 0) {
            timeoutDate = null;
        } else {
            timeoutDate = new Date(System.currentTimeMillis() + timeoutInMillis);
        }
        boolean stillWaiting = true;
        while (currentState != Watcher.Event.KeeperState.SyncConnected) {
            if (!stillWaiting) {
                return false;
            }
            try {
                if (timeoutDate == null) {
                    lockCondition.await();
                } else {
                    stillWaiting = lockCondition.awaitUntil(timeoutDate);
                }
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return true;
    }

    /**
     * Internal watcher wrapper for tracking state and reconnecting
     */
    private class InternalWatcher implements Watcher {

        private final Watcher inner;

        private InternalWatcher(final Watcher inner) {
            this.inner = inner;
        }

        @Override
        public void process(final WatchedEvent event) {
            if (event != null && logger.isLoggable(Level.FINER)) {
                logger.log(Level.FINER,
                        "received event. eventState={0}, eventType={1}, eventPath={2}, watcher={3}",
                        new Object[]{event.getState(), event.getType(), event.getPath(), this});
            }
            if (!running.get()) {
                if (event != null && logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO,
                            "this event will be ignored because this client is shutting down or already has shutted down. name={0}, eventState={1}, eventType={2}, eventPath={3}, watcher={4}",
                            new Object[]{name, event.getState(), event.getType(), event.getPath(), this});
                }
                return;
            }
            if (processStateChanged(event)) {
                return;
            }
            if (inner != null) {
                inner.process(event);
            }
        }

        @Override
        public String toString() {
            return "InternalWatcher{" +
                    "inner=" + inner +
                    '}';
        }
    }

    /**
     * Watcher implementation for a region
     */
    private class RegionWatcher implements Watcher {

        private final String regionName;
        private final List<String> aliveNodesExceptMyself = new ArrayList<String>();
        private final Set<String> toBeCompleted = new HashSet<String>();
        private final Lock regionLock = new ReentrantLock();
        private volatile boolean isSynchronizing = false;

        private RegionWatcher(final String regionName) {
            this.regionName = regionName;
        }

        @Override
        public void process(final WatchedEvent event) {
            if (event == null) {
                return;
            }
            // check if current region is already unregistered
            if (listenerMap.get(regionName) == null) {
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO,
                            "this event will be ignored because this region already has unregistered. name={0}, regionName={1}, eventState={2}, eventType={3}, eventPath={4}, watcher={5}",
                            new Object[]{name, regionName, event.getState(), event.getType(), event.getPath(), this});
                }
                return;
            }
            final Event.KeeperState eventState = event.getState();
            final String eventPath = event.getPath();
            final Watcher.Event.EventType eventType = event.getType();

            final String currentRegionPath = basePath + normalizePath(regionName);
            final String currentNodesPath = currentRegionPath + CURRENT_PATH;
            final String currentParticipantPath = currentRegionPath + PARTICIPANTS_PATH;
            final String currentDataPath = currentRegionPath + DATA_PATH;
            if ((eventType == Event.EventType.NodeDataChanged || eventType == Event.EventType.NodeCreated) &&
                    currentDataPath.equals(eventPath)) { // data changed
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO,
                            "the central data has been changed in the remote zookeeper server. name={0}, regionName={1}",
                            new Object[]{name, regionName});
                }
                regionLock.lock();
                try {
                    isSynchronizing = true;
                    aliveNodesExceptMyself.clear();
                    toBeCompleted.clear();
                    // we should watch nodes' changes(watch1) while syncronizing nodes
                    final List<String> currentNodes = getChildren(currentNodesPath, this);
                    aliveNodesExceptMyself.addAll(currentNodes);
                    // remove own node
                    aliveNodesExceptMyself.remove(uniqueId);
                    for (final String node : currentNodes) {
                        final String participant = currentParticipantPath + "/" + node;
                        // we should watch the creation or deletion event(watch2)
                        if (exists(participant, this) == null) {
                            toBeCompleted.add(participant);
                        } else {
                            toBeCompleted.remove(participant);
                        }
                    }
                } finally {
                    regionLock.unlock();
                }
                // register own to barrier path
                create(currentParticipantPath + uniqueIdPath, NO_DATA, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
                // register the watcher for detecting next data's changes again
                exists(currentDataPath, this);
            } else if (isSynchronizing &&
                    eventType == Event.EventType.NodeDeleted &&
                    currentDataPath.equals(eventPath)) { // data deleted
                regionLock.lock();
                try {
                    if (isSynchronizing) {
                        isSynchronizing = false;
                        if (!aliveNodesExceptMyself.isEmpty() || !toBeCompleted.isEmpty()) {
                            if (logger.isLoggable(Level.WARNING)) {
                                logger.log(Level.WARNING,
                                        "the central data deleted in the remote zookeeper server while preparing to syncronize the data. name={0}, regionName={1}",
                                        new Object[]{name, regionName});
                            }
                            aliveNodesExceptMyself.clear();
                            toBeCompleted.clear();
                        }
                    }
                } finally {
                    regionLock.unlock();
                }
                // register the watcher for detecting next data's changes again
                exists(currentDataPath, this);
            } else if (isSynchronizing &&
                    (eventType == Watcher.Event.EventType.NodeCreated || eventType == Watcher.Event.EventType.NodeDeleted) &&
                    eventPath != null && eventPath.startsWith(currentParticipantPath)) { // a participant joined from (watch2)
                regionLock.lock();
                try {
                    if (isSynchronizing) {
                        toBeCompleted.remove(eventPath);
                        if (toBeCompleted.isEmpty()) {
                            isSynchronizing = false;
                            scheduleCommit(event, currentDataPath, currentParticipantPath);
                        }
                    }
                } finally {
                    regionLock.unlock();
                }
            } else if (isSynchronizing &&
                    eventType == Event.EventType.NodeChildrenChanged && currentNodesPath.equals(eventPath)) { // nodes changed from (watch1)
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING,
                            "some clients are failed or added while preparing to syncronize the data. name={0}, regionName={1}",
                            new Object[]{name, regionName});
                }
                regionLock.lock();
                try {
                    if (isSynchronizing) {
                        // we should watch nodes' changes again(watch1)
                        final List<String> currentNodes = getChildren(currentNodesPath, this);
                        // remove own node
                        currentNodes.remove(uniqueIdPath);
                        final List<String> failureNodes = new ArrayList<String>(aliveNodesExceptMyself);
                        failureNodes.removeAll(currentNodes);
                        for (final String node : failureNodes) {
                            final String participant = currentParticipantPath + "/" + node;
                            toBeCompleted.remove(participant);
                        }
                        if (toBeCompleted.isEmpty()) {
                            isSynchronizing = false;
                            scheduleCommit(event, currentDataPath, currentParticipantPath);
                        }
                    }
                } finally {
                    regionLock.unlock();
                }
            } else {
                if (logger.isLoggable(Level.FINE)) {
                    logger.log(Level.FINE,
                            "not interested. name={0}, regionName={1}, eventState={2}, eventType={3}, eventPath={4}, watcher={5}",
                            new Object[]{name, regionName, eventState, eventType, eventPath, this});
                }
            }
        }

        private void scheduleCommit(final WatchedEvent event, final String currentDataPath, final String currentParticipantPath) {
            if (event == null || currentDataPath == null) {
                return;
            }
            if (logger.isLoggable(Level.INFO)) {
                logger.log(Level.INFO,
                        "all clients are prepared. name={0}, regionName={1}, commitDelayTimeInSecs={2}",
                        new Object[]{name, regionName, commitDelayTimeInSecs});
            }
            // all nodes are prepared
            final Stat stat = new Stat();
            final byte[] remoteDataBytes = getData(currentDataPath, false, stat);
            final Long scheduled = stat.getMtime() + TimeUnit.SECONDS.toMillis(commitDelayTimeInSecs);
            final long remaining = scheduled - System.currentTimeMillis();
            if (remaining < 0) {
                if (logger.isLoggable(Level.WARNING)) {
                    logger.log(Level.WARNING,
                            "commitDelayTimeInSecs may be too small. so we will commit immediately. name={0}, regionName={1}, scheduledTime=before {2}ms",
                            new Object[]{name, regionName, -remaining});
                }
            } else {
                final Date scheduledDate = new Date(scheduled);
                if (logger.isLoggable(Level.INFO)) {
                    logger.log(Level.INFO,
                            "the changes of the central data will be applied. name={0}, regionName={1}, scheduledDate={2}, data={3}",
                            new Object[]{name, regionName, scheduledDate.toString(), remoteDataBytes == null ? "null" : remoteDataBytes});
                }
            }
            scheduledExecutor.schedule(new Runnable() {
                @Override
                public void run() {
                    final BarrierListener listener = listenerMap.get(regionName);
                    if (listener == null) {
                        if (logger.isLoggable(Level.INFO)) {
                            logger.log(Level.INFO,
                                    "this commit will be ignored because this region already has unregistered. eventState={0}, eventType={1}, eventPath={2}, watcher={3}",
                                    new Object[]{event.getState(), event.getType(), event.getPath(), this});
                        }
                        return;
                    }
                    try {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE,
                                    "name={0}, regionName={1}, scheduledTime={2}ms, commit time={3}ms",
                                    new Object[]{name, regionName, scheduled, System.currentTimeMillis()});
                        }
                        listener.onCommit(regionName, currentDataPath, remoteDataBytes);
                        if (logger.isLoggable(Level.INFO)) {
                            logger.log(Level.INFO,
                                    "committed successfully. name={0}, regionName={1}, listener={2}",
                                    new Object[]{name, regionName, listener});
                        }
                    } catch (Exception e) {
                        if (logger.isLoggable(Level.WARNING)) {
                            logger.log(Level.WARNING,
                                    "failed to onCommit. name=" + name + ", regionName=" + regionName + ", listener=" + listener,
                                    e);
                        }
                    }
                    // delete own barrier path
                    final String path = currentParticipantPath + uniqueIdPath;
                    if (!delete(path, -1)) {
                        if (logger.isLoggable(Level.FINE)) {
                            logger.log(Level.FINE,
                                    "there is no the participant path to be deleted because it may already has been closed. name={0}, regionName={1}, path={2}",
                                    new Object[]{name, regionName, path});
                        }
                    }
                }
            }, remaining, TimeUnit.MILLISECONDS);
        }

        @Override
        public String toString() {
            return "RegionWatcher{" +
                    "regionName='" + regionName + '\'' +
                    '}';
        }
    }

    private boolean processStateChanged(final WatchedEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        final Watcher.Event.KeeperState eventState = event.getState();
        final String eventPath = event.getPath();
        final boolean isStateChangedEvent;
        // state changed
        if (eventPath == null) {
            lock.lock();
            try {
                currentState = eventState;
                lockCondition.signalAll();
            } finally {
                lock.unlock();
            }
            isStateChangedEvent = true;
        } else {
            isStateChangedEvent = false;
        }
        if (eventState == Watcher.Event.KeeperState.Expired) {
            try {
                reconnect();
            } catch (Exception e) {
                if (logger.isLoggable(Level.SEVERE)) {
                    logger.log(Level.SEVERE, "failed to reconnect the zookeeper server", e);
                }
            }
        }
        return isStateChangedEvent;
    }

    @Override
    public String toString() {
        return "ZKClient{" +
                "connected=" + connected +
                ", running=" + running +
                ", currentState=" + currentState +
                ", listenerMap=" + listenerMap +
                ", name='" + name + '\'' +
                ", uniqueId='" + uniqueId + '\'' +
                ", uniqueIdPath='" + uniqueIdPath + '\'' +
                ", rootPath='" + rootPath + '\'' +
                ", basePath='" + basePath + '\'' +
                ", zooKeeperServerList='" + zooKeeperServerList + '\'' +
                ", connectTimeoutInMillis=" + connectTimeoutInMillis +
                ", sessionTimeoutInMillis=" + sessionTimeoutInMillis +
                ", commitDelayTimeInSecs=" + commitDelayTimeInSecs +
                '}';
    }

    /**
     * Normalize the given path
     *
     * @param path path for the zookeeper
     * @return normalized path
     */
    private static String normalizePath(final String path) {
        if (path == null) {
            return "/";
        }
        String temp = path.trim();
        while (temp.length() > 1 && temp.endsWith("/")) {
            temp = temp.substring(0, temp.length() - 1);
        }
        final StringBuilder builder = new StringBuilder(64);
        if (!temp.startsWith("/")) {
            builder.append('/');
        }
        builder.append(temp);
        return builder.toString();
    }

    /**
     * Builder for ZKClient
     */
    public static class Builder {
        private static final String DEFAULT_ROOT_PATH = "/";
        private static final long DEFAULT_CONNECT_TIMEOUT_IN_MILLIS = 5000; // 5secs
        private static final long DEFAULT_SESSION_TIMEOUT_IN_MILLIS = 30000; // 30secs
        private static final long DEFAULT_COMMIT_DELAY_TIME_IN_SECS = 60; // 60secs

        private final String name;
        private final String zooKeeperServerList;

        private String rootPath = DEFAULT_ROOT_PATH;
        private long connectTimeoutInMillis = DEFAULT_CONNECT_TIMEOUT_IN_MILLIS;
        private long sessionTimeoutInMillis = DEFAULT_SESSION_TIMEOUT_IN_MILLIS;
        private long commitDelayTimeInSecs = DEFAULT_COMMIT_DELAY_TIME_IN_SECS;

        /**
         * The specific name or Id for ZKClient
         *
         * @param name                name or id
         * @param zooKeeperServerList comma separated host:port pairs, each corresponding to a zookeeper server.
         *                            e.g. "127.0.0.1:3000,127.0.0.1:3001,127.0.0.1:3002"
         */
        public Builder(final String name, final String zooKeeperServerList) {
            this.name = name;
            this.zooKeeperServerList = zooKeeperServerList;
        }

        /**
         * Root path for ZKClient
         *
         * @param rootPath root path of the zookeeper. default is "/".
         * @return this builder
         */
        public Builder rootPath(final String rootPath) {
            this.rootPath = rootPath;
            return this;
        }

        /**
         * Connect timeout in milli-seconds
         *
         * @param connectTimeoutInMillis connect timeout. negative value means "never timed out". default is 5000(5 secs).
         * @return this builder
         */
        public Builder connectTimeoutInMillis(final long connectTimeoutInMillis) {
            this.connectTimeoutInMillis = connectTimeoutInMillis;
            return this;
        }

        /**
         * Session timeout in milli-seconds
         *
         * @param sessionTimeoutInMillis Zookeeper connection's timeout. default is 30000(30 secs).
         * @return this builder
         */
        public Builder sessionTimeoutInMillis(final long sessionTimeoutInMillis) {
            this.sessionTimeoutInMillis = sessionTimeoutInMillis;
            return this;
        }

        /**
         * Delay time in seconds for committing
         *
         * @param commitDelayTimeInSecs delay time before committing. default is 60(60secs).
         * @return this builder
         */
        public Builder commitDelayTimeInSecs(final long commitDelayTimeInSecs) {
            this.commitDelayTimeInSecs = commitDelayTimeInSecs;
            return this;
        }

        /**
         * Build a ZKClient
         *
         * @return an instance of ZKClient
         */
        public ZKClient build() {
            return new ZKClient(this);
        }
    }
}