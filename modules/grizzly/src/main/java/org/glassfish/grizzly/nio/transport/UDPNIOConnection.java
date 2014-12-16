/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.nio.transport;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketAddress;
import java.nio.channels.DatagramChannel;
import java.nio.channels.SelectionKey;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.ConnectionProbe;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.WriteHandler;
import org.glassfish.grizzly.asyncqueue.AsyncQueueWriter;
import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.nio.NIOConnection;
import org.glassfish.grizzly.nio.SelectorRunner;
import org.glassfish.grizzly.utils.Exceptions;
import org.glassfish.grizzly.utils.Holder;
import org.glassfish.grizzly.utils.JdkVersion;
import org.glassfish.grizzly.utils.NullaryFunction;

/**
 * {@link org.glassfish.grizzly.Connection} implementation
 * for the {@link UDPNIOTransport}
 *
 * @author Alexey Stashok
 */
@SuppressWarnings("unchecked")
public class UDPNIOConnection extends NIOConnection {

    private static final Logger LOGGER = Grizzly.logger(UDPNIOConnection.class);

    private static final boolean IS_MULTICAST_SUPPORTED;
    private static final Method JOIN_METHOD;
    private static final Method JOIN_WITH_SOURCE_METHOD;
    private static final Method MK_GET_NETWORK_INTERFACE_METHOD;
    private static final Method MK_GET_SOURCE_ADDRESS_METHOD;
    private static final Method MK_DROP_METHOD;
    private static final Method MK_BLOCK_METHOD;
    private static final Method MK_UNBLOCK_METHOD;
    
    static {
        JdkVersion jdkVersion = JdkVersion.getJdkVersion();
        JdkVersion minimumVersion = JdkVersion.parseVersion("1.7.0");
        
            
        boolean isInitialized = false;
        Method join = null, joinWithSource = null,
                mkGetNetworkInterface = null, mkGetSourceAddress = null,
                mkDrop = null, mkBlock = null, mkUnblock = null;
        if (minimumVersion.compareTo(jdkVersion) <= 0) { // If JDK version is >= 1.7
            try {
                join = DatagramChannel.class.getMethod("join",
                        InetAddress.class, NetworkInterface.class);
                joinWithSource = DatagramChannel.class.getMethod("join",
                        InetAddress.class, NetworkInterface.class, InetAddress.class);
                
                final Class membershipKeyClass = loadClass("java.nio.channels.MembershipKey");
                
                mkGetNetworkInterface = membershipKeyClass.getDeclaredMethod("networkInterface");
                mkGetSourceAddress = membershipKeyClass.getDeclaredMethod("sourceAddress");
                mkDrop = membershipKeyClass.getDeclaredMethod("drop");
                
                mkBlock = membershipKeyClass.getDeclaredMethod("block", InetAddress.class);
                mkUnblock = membershipKeyClass.getDeclaredMethod("unblock", InetAddress.class);
                isInitialized = true;
            } catch (Throwable t) {
                LOGGER.log(Level.WARNING, LogMessages.WARNING_GRIZZLY_CONNECTION_UDPMULTICASTING_EXCEPTIONE(), t);
            }
        }
        
        if (isInitialized) {
            IS_MULTICAST_SUPPORTED = true;
            JOIN_METHOD = join;
            JOIN_WITH_SOURCE_METHOD = joinWithSource;
            MK_GET_NETWORK_INTERFACE_METHOD = mkGetNetworkInterface;
            MK_GET_SOURCE_ADDRESS_METHOD = mkGetSourceAddress;
            MK_DROP_METHOD = mkDrop;
            MK_BLOCK_METHOD = mkBlock;
            MK_UNBLOCK_METHOD = mkUnblock;
        } else {
            IS_MULTICAST_SUPPORTED = false;
            JOIN_METHOD = JOIN_WITH_SOURCE_METHOD =
                    MK_GET_NETWORK_INTERFACE_METHOD =
                    MK_GET_SOURCE_ADDRESS_METHOD = MK_DROP_METHOD =
                    MK_BLOCK_METHOD = MK_UNBLOCK_METHOD = null;
        }
    }
    
    private final Object multicastSync;
    private Map<InetAddress, Set<Object>> membershipKeysMap;
    
    Holder<SocketAddress> localSocketAddressHolder;
    Holder<SocketAddress> peerSocketAddressHolder;

    private int readBufferSize = -1;
    private int writeBufferSize = -1;

    public UDPNIOConnection(UDPNIOTransport transport,
            DatagramChannel channel) {
        super(transport);

        this.channel = channel;

        resetProperties();
        
        multicastSync = IS_MULTICAST_SUPPORTED ? new Object() : null;
    }

    public boolean isConnected() {
        return channel != null && ((DatagramChannel) channel).isConnected();
    }

    /**
     * Joins a multicast group to begin receiving all datagrams sent to the
     * group. If this connection is currently a member of the group on the given
     * interface to receive all datagrams then this method call has no effect.
     * Otherwise this connection joins the requested group and channel's
     * membership in not source-specific.
     *
     * A multicast connection may join several multicast groups, including the
     * same group on more than one interface. An implementation may impose a
     * limit on the number of groups that may be joined at the same time.
     *
     * @param group The multicast address to join
     * @param networkInterface The network interface on which to join the group
     *
     * @throws IOException
     */
    public void join(final InetAddress group,
            final NetworkInterface networkInterface) throws IOException {
        join(group, networkInterface, null);
    }

    /**
     * Joins a multicast group to begin receiving datagrams sent to the group
     * from a given source address. If this connection is currently a member of
     * the group on the given interface to receive datagrams from the given
     * source address then this method call has no effect. Otherwise
     * this connection joins the group and depending on the source parameter
     * value (whether it's not null or null value) the connection's membership
     * is or is not source-specific.
     *
     * Membership is cumulative and this method may be invoked again with the
     * same group and interface to allow receiving datagrams sent by other
     * source addresses to the group.
     *
     * @param group The multicast address to join
     * @param networkInterface The network interface on which to join the group
     * @param source The source address
     */
    public void join(final InetAddress group,
            final NetworkInterface networkInterface, final InetAddress source)
            throws IOException {
        
        if (!IS_MULTICAST_SUPPORTED) {
            throw new UnsupportedOperationException("JDK 1.7+ required");
        }
        
        if (group == null) {
            throw new IllegalArgumentException("group parameter can't be null");
        }

        if (networkInterface == null) {
            throw new IllegalArgumentException("networkInterface parameter can't be null");
        }

        synchronized (multicastSync) {
            Object membershipKey = join0((DatagramChannel) channel,
                    group, networkInterface, source);

            if (membershipKeysMap == null) {
                membershipKeysMap = new HashMap<InetAddress, Set<Object>>();
            }
            
            Set<Object> keySet = membershipKeysMap.get(group);
            if (keySet == null) {
                keySet = new HashSet<Object>();
                membershipKeysMap.put(group, keySet);
            }
            
            keySet.add(membershipKey);
        }
    }

    /**
     * Drops non-source specific membership in a multicast group.
     * If this connection doesn't have non-source specific membership in the group
     * on the given interface to receive datagrams then this method call
     * has no effect. Otherwise this connection drops the group membership.
     * 
     * @param group The multicast address to join
     * @param networkInterface The network interface on which to join the group
     * 
     * @throws IOException 
     */
    public void drop(final InetAddress group,
            final NetworkInterface networkInterface) throws IOException {
        drop(group, networkInterface, null);
    }
    
    /**
     * Drops membership in a multicast group.
     * If the source parameter is null - this method call is equivalent to
     * {@link #drop(java.net.InetAddress, java.net.NetworkInterface)}.
     * 
     * If the source parameter is not null and this connection doesn't have 
     * source specific membership in the group on the given interface to
     * receive datagrams then this method call has no effect.
     * Otherwise this connection drops the source specific group membership.
     * 
     * @param group The multicast address to join
     * @param networkInterface The network interface on which to join the group
     * @param source The source address
     * 
     * @throws IOException 
     */
    public void drop(final InetAddress group,
            final NetworkInterface networkInterface, final InetAddress source)
            throws IOException {
        
        if (!IS_MULTICAST_SUPPORTED) {
            throw new UnsupportedOperationException("JDK 1.7+ required");
        }
        
        if (group == null) {
            throw new IllegalArgumentException("group parameter can't be null");
        }

        if (networkInterface == null) {
            throw new IllegalArgumentException("networkInterface parameter can't be null");
        }

        synchronized (multicastSync) {
            final Set<Object> keys;
            if (membershipKeysMap != null &&
                    (keys = membershipKeysMap.get(group)) != null) {
                for (final Iterator<Object> it = keys.iterator(); it.hasNext(); ) {
                    final Object key = it.next();
                    
                    if (networkInterface.equals(networkInterface0(key))) {
                        if ((source == null && sourceAddress0(key) == null) ||
                                (source != null && source.equals(sourceAddress0(key)))) {
                            drop0(key);
                            it.remove();
                        }
                    }
                    
                    if (keys.isEmpty()) {
                        membershipKeysMap.remove(group);
                    }
                }
            }
        }
    }
    
    /**
     * Drops all active membership in a multicast group.
     * If this connection doesn't have any type of membership in the group
     * on the given interface to receive datagrams then this method call
     * has no effect. Otherwise this connection drops all types of the group membership.
     * 
     * @param group The multicast address to join
     * @param networkInterface The network interface on which to join the group
     * 
     * @throws IOException 
     */
    public void dropAll(final InetAddress group,
            final NetworkInterface networkInterface)
            throws IOException {
        
        if (!IS_MULTICAST_SUPPORTED) {
            throw new UnsupportedOperationException("JDK 1.7+ required");
        }
        
        if (group == null) {
            throw new IllegalArgumentException("group parameter can't be null");
        }

        if (networkInterface == null) {
            throw new IllegalArgumentException("networkInterface parameter can't be null");
        }

        synchronized (multicastSync) {
            final Set<Object> keys;
            if (membershipKeysMap != null &&
                    (keys = membershipKeysMap.get(group)) != null) {
                for (final Iterator<Object> it = keys.iterator(); it.hasNext(); ) {
                    final Object key = it.next();
                    
                    if (networkInterface.equals(networkInterface0(key))) {
                        drop0(key);
                        it.remove();
                    }
                }
                
                if (keys.isEmpty()) {
                    membershipKeysMap.remove(group);
                }
            }
        }
    }
    
    /**
     * Blocks multicast datagrams from the given source address. 
     * 
     * If this connection has non-source specific membership in the group
     * on the given interface then this method blocks multicast datagrams from
     * the given source address.  If the given source address is already blocked
     * then this method has no effect.
     * 
     * @param group The multicast address to join
     * @param networkInterface The network interface on which to join the group
     * @param source The source address to block
     * 
     * @throws IOException 
     */
    public void block(final InetAddress group,
            final NetworkInterface networkInterface, final InetAddress source)
            throws IOException {
        
        if (!IS_MULTICAST_SUPPORTED) {
            throw new UnsupportedOperationException("JDK 1.7+ required");
        }
        
        if (group == null) {
            throw new IllegalArgumentException("group parameter can't be null");
        }

        if (networkInterface == null) {
            throw new IllegalArgumentException("networkInterface parameter can't be null");
        }

        synchronized (multicastSync) {
            final Set<Object> keys;
            if (membershipKeysMap != null &&
                    (keys = membershipKeysMap.get(group)) != null) {
                for (final Iterator<Object> it = keys.iterator(); it.hasNext(); ) {
                    final Object key = it.next();
                    
                    if (networkInterface.equals(networkInterface0(key))
                            && sourceAddress0(key) == null) {
                        block0(key, source);
                    }
                }
            }
        }
    }
    
    /**
     * Unblocks multicast datagrams from the given source address. 
     * 
     * If this connection has non-source specific membership in the group
     * on the given interface and specified source address was previously blocked
     * using {@link #block(java.net.InetAddress, java.net.NetworkInterface, java.net.InetAddress)} method
     * then this method unblocks multicast datagrams from the given source address.
     * If the given source address wasn't blocked then this method has no effect.
     * 
     * @param group The multicast address to join
     * @param networkInterface The network interface on which to join the group
     * @param source The source address to block
     * 
     * @throws IOException 
     */
    public void unblock(final InetAddress group,
            final NetworkInterface networkInterface, final InetAddress source)
            throws IOException {
        
        if (!IS_MULTICAST_SUPPORTED) {
            throw new UnsupportedOperationException("JDK 1.7+ required");
        }
        
        if (group == null) {
            throw new IllegalArgumentException("group parameter can't be null");
        }

        if (networkInterface == null) {
            throw new IllegalArgumentException("networkInterface parameter can't be null");
        }

        synchronized (multicastSync) {
            final Set<Object> keys;
            if (membershipKeysMap != null &&
                    (keys = membershipKeysMap.get(group)) != null) {
                for (final Iterator<Object> it = keys.iterator(); it.hasNext(); ) {
                    final Object key = it.next();
                    
                    if (networkInterface.equals(networkInterface0(key))
                            && sourceAddress0(key) == null) {
                        unblock0(key, source);
                    }
                }
            }
        }
    }
    
    @Override
    protected void setSelectionKey(SelectionKey selectionKey) {
        super.setSelectionKey(selectionKey);
    }

    @Override
    protected void setSelectorRunner(SelectorRunner selectorRunner) {
        super.setSelectorRunner(selectorRunner);
    }

    protected boolean notifyReady() {
        return connectCloseSemaphor.compareAndSet(null, NOTIFICATION_INITIALIZED);
    }

    /**
     * Returns the address of the endpoint this <tt>Connection</tt> is
     * connected to, or <tt>null</tt> if it is unconnected.
     * @return the address of the endpoint this <tt>Connection</tt> is
     *         connected to, or <tt>null</tt> if it is unconnected.
     */
    @Override
    public SocketAddress getPeerAddress() {
        return peerSocketAddressHolder.get();
    }

    /**
     * Returns the local address of this <tt>Connection</tt>,
     * or <tt>null</tt> if it is unconnected.
     * @return the local address of this <tt>Connection</tt>,
     *      or <tt>null</tt> if it is unconnected.
     */
    @Override
    public SocketAddress getLocalAddress() {
        return localSocketAddressHolder.get();
    }

    protected final void resetProperties() {
        if (channel != null) {
            setReadBufferSize(transport.getReadBufferSize());
            setWriteBufferSize(transport.getWriteBufferSize());

            final int transportMaxAsyncWriteQueueSize =
                    ((UDPNIOTransport) transport).getAsyncQueueIO()
                    .getWriter().getMaxPendingBytesPerConnection();
            
            setMaxAsyncWriteQueueSize(
                    transportMaxAsyncWriteQueueSize == AsyncQueueWriter.AUTO_SIZE ?
                    getWriteBufferSize() * 4 :
                    transportMaxAsyncWriteQueueSize);
            
            localSocketAddressHolder = Holder.lazyHolder(
                    new NullaryFunction<SocketAddress>() {
                        @Override
                        public SocketAddress evaluate() {
                            return ((DatagramChannel) channel).socket().getLocalSocketAddress();
                        }
                    });

            peerSocketAddressHolder = Holder.lazyHolder(
                    new NullaryFunction<SocketAddress>() {
                        @Override
                        public SocketAddress evaluate() {
                            return ((DatagramChannel) channel).socket().getRemoteSocketAddress();
                        }
                    });
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getReadBufferSize() {
        if (readBufferSize >= 0) {
            return readBufferSize;
        }

        try {
            readBufferSize = ((DatagramChannel) channel).socket().getReceiveBufferSize();
        } catch (IOException e) {
            LOGGER.log(Level.FINE,
                    LogMessages.WARNING_GRIZZLY_CONNECTION_GET_READBUFFER_SIZE_EXCEPTION(),
                    e);
            readBufferSize = 0;
        }

        return readBufferSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setReadBufferSize(final int readBufferSize) {
        if (readBufferSize > 0) {
            try {
                final int currentReadBufferSize = ((DatagramChannel) channel).socket().getReceiveBufferSize();
                if (readBufferSize > currentReadBufferSize) {
                    ((DatagramChannel) channel).socket().setReceiveBufferSize(readBufferSize);
                }
                
                this.readBufferSize = readBufferSize;
            } catch (IOException e) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_CONNECTION_SET_READBUFFER_SIZE_EXCEPTION(),
                        e);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getWriteBufferSize() {
        if (writeBufferSize >= 0) {
            return writeBufferSize;
        }

        try {
            writeBufferSize = ((DatagramChannel) channel).socket().getSendBufferSize();
        } catch (IOException e) {
            LOGGER.log(Level.FINE,
                    LogMessages.WARNING_GRIZZLY_CONNECTION_GET_WRITEBUFFER_SIZE_EXCEPTION(),
                    e);
            writeBufferSize = 0;
        }

        return writeBufferSize;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setWriteBufferSize(int writeBufferSize) {
        if (writeBufferSize > 0) {
            try {
                final int currentSendBufferSize = ((DatagramChannel) channel).socket().getSendBufferSize();
                if (writeBufferSize > currentSendBufferSize) {
                    ((DatagramChannel) channel).socket().setSendBufferSize(writeBufferSize);
                }
                this.writeBufferSize = writeBufferSize;
            } catch (IOException e) {
                LOGGER.log(Level.WARNING,
                        LogMessages.WARNING_GRIZZLY_CONNECTION_SET_WRITEBUFFER_SIZE_EXCEPTION(),
                        e);
            }
        }
    }

    @Override
    protected void enableInitialOpRead() throws IOException {
        super.enableInitialOpRead();
    }
    
    /**
     * Method will be called, when the connection gets connected.
     * @throws IOException
     */
    protected final void onConnect() throws IOException {
        notifyProbesConnect(this);
    }

    /**
     * Method will be called, when some data was read on the connection
     */
    protected final void onRead(Buffer data, int size) {
        if (size > 0) {
            notifyProbesRead(this, data, size);
        }
        checkEmptyRead(size);
    }

    /**
     * Method will be called, when some data was written on the connection
     */
    protected final void onWrite(Buffer data, int size) {
        notifyProbesWrite(this, data, size);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canWrite() {
        return transport.getWriter(this).canWrite(this);
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    @Override
    public boolean canWrite(int length) {
        return transport.getWriter(this).canWrite(this);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void notifyCanWrite(final WriteHandler writeHandler) {
        transport.getWriter(this).notifyWritePossible(this, writeHandler);
    }

    /**
     * {@inheritDoc}
     */
    @Deprecated
    @Override
    public void notifyCanWrite(WriteHandler handler, int length) {
        transport.getWriter(this).notifyWritePossible(this, handler);
    }

    /**
     * Set the monitoringProbes array directly.
     * @param monitoringProbes
     */
    void setMonitoringProbes(final ConnectionProbe[] monitoringProbes) {
        this.monitoringConfig.addProbes(monitoringProbes);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("UDPNIOConnection");
        sb.append("{localSocketAddress=").append(localSocketAddressHolder);
        sb.append(", peerSocketAddress=").append(peerSocketAddressHolder);
        sb.append('}');
        return sb.toString();
    }

    private static Object join0(final DatagramChannel channel,
            final InetAddress group, final NetworkInterface networkInterface,
            final InetAddress source) throws IOException {
        
        return source == null
                ? invoke(channel, JOIN_METHOD, group, networkInterface)
                : invoke(channel, JOIN_WITH_SOURCE_METHOD, group, networkInterface, source);
    }

    private static NetworkInterface networkInterface0(final Object membershipKey)
            throws IOException {
        
        return  (NetworkInterface) invoke(membershipKey, MK_GET_NETWORK_INTERFACE_METHOD);
    }
    
    private static InetAddress sourceAddress0(final Object membershipKey)
            throws IOException {
        
        return  (InetAddress) invoke(membershipKey, MK_GET_SOURCE_ADDRESS_METHOD);
    }

    private static void drop0(final Object membershipKey)
            throws IOException {
        
        invoke(membershipKey, MK_DROP_METHOD);
    }

    private static void block0(final Object membershipKey,
            final InetAddress sourceAddress) throws IOException {
        
        invoke(membershipKey, MK_BLOCK_METHOD, sourceAddress);
    }
    
    private static void unblock0(final Object membershipKey,
            final InetAddress sourceAddress) throws IOException {
        
        invoke(membershipKey, MK_UNBLOCK_METHOD, sourceAddress);
    }

    private static Object invoke(final Object object, final Method method,
            final Object... params) throws IOException {
        try {
            return method.invoke(object, params);
        } catch (InvocationTargetException e) {
            final Throwable t = e.getCause();
            if (t instanceof RuntimeException) {
                throw (RuntimeException) t;
            } else {
                throw Exceptions.makeIOException(t);
            }
        } catch (Throwable t) {
            throw Exceptions.makeIOException(t);
        }
    }
    
    private static Class<?> loadClass(final String cname) throws Throwable {
        return ClassLoader.getSystemClassLoader().loadClass(cname);
    }
}
