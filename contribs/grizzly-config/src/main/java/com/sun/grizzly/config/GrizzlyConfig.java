/*
 *   DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *   Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 *
 *   The contents of this file are subject to the terms of either the GNU
 *   General Public License Version 2 only ("GPL") or the Common Development
 *   and Distribution License("CDDL") (collectively, the "License").  You
 *   may not use this file except in compliance with the License. You can obtain
 *   a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 *   or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 *   language governing permissions and limitations under the License.
 *
 *   When distributing the software, include this License Header Notice in each
 *   file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 *   Sun designates this particular file as subject to the "Classpath" exception
 *   as provided by Sun in the GPL Version 2 section of the License file that
 *   accompanied this code.  If applicable, add the following below the License
 *   Header, with the fields enclosed by brackets [] replaced by your own
 *   identifying information: "Portions Copyrighted [year]
 *   [name of copyright owner]"
 *
 *   Contributor(s):
 *
 *   If you wish your version of this file to be governed by only the CDDL or
 *   only the GPL Version 2, indicate your decision by adding "[Contributor]
 *   elects to include this software in this distribution under the [CDDL or GPL
 *   Version 2] license."  If you don't indicate a single choice of license, a
 *   recipient has the option to distribute your version of this file under
 *   either the CDDL, the GPL Version 2 or to extend the choice of license to
 *   its licensees as provided above.  However, if you add GPL Version 2 code
 *   and therefore, elected the GPL Version 2 license, then the option applies
 *   only if the new code is made subject to such option by the copyright
 *   holder.
 *
 */
package com.sun.grizzly.config;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;

import com.sun.grizzly.BaseSelectionKeyHandler;
import com.sun.grizzly.Controller;
import com.sun.grizzly.DefaultProtocolChain;
import com.sun.grizzly.DefaultProtocolChainInstanceHandler;
import com.sun.grizzly.Role;
import com.sun.grizzly.TCPSelectorHandler;
import com.sun.grizzly.util.DefaultThreadPool;
import com.sun.grizzly.util.ExtendedThreadPool;
import com.sun.grizzly.config.dom.NetworkConfig;
import com.sun.grizzly.config.dom.NetworkListener;
import com.sun.grizzly.config.dom.NetworkListeners;
import com.sun.grizzly.config.dom.Protocol;
import com.sun.grizzly.config.dom.ProtocolChain;
import com.sun.grizzly.config.dom.ProtocolChainInstanceHandler;
import com.sun.grizzly.config.dom.SelectionKeyHandler;
import com.sun.grizzly.config.dom.ThreadPool;
import com.sun.grizzly.config.dom.Transport;
import com.sun.grizzly.config.dom.Transports;
import org.jvnet.hk2.component.Habitat;

/**
 * Created Nov 24, 2008
 *
 * @author <a href="mailto:jlee@antwerkz.com">Justin Lee</a>
 */
public class GrizzlyConfig {
    private final Controller controller = new Controller();
    private final NetworkConfig config;
    private final Map<String, TCPSelectorHandler> selectorHandlers = new HashMap<String, TCPSelectorHandler>();
    private final Map<String, SelectionKeyHandler> selectionKeyHandlers = new HashMap<String, SelectionKeyHandler>();
    private final Map<String, ExecutorService> pools = new HashMap<String, ExecutorService>();

    public GrizzlyConfig(final String file) throws Exception {
        final Habitat habitat = Utils.getHabitat(file);
        config = habitat.getComponent(NetworkConfig.class);
    }

    public void setupNetwork() {
        final NetworkListeners listeners = config.getNetworkListeners();
        for (final ThreadPool threadPool : listeners.getThreadPool()) {
            final ExtendedThreadPool pool = (ExtendedThreadPool) createFromClassname(threadPool.getClassname(),
                DefaultThreadPool.class.getName());
            pool.setMaximumPoolSize(Integer.parseInt(threadPool.getMaxThreadPoolSize()));
            pool.setCorePoolSize(Integer.parseInt(threadPool.getMinThreadPoolSize()));
            pool.setName(threadPool.getThreadPoolId());
            pools.put(pool.getName(), (ExecutorService) pool);
        }
        final Transports list = config.getTransports();
        for (final SelectionKeyHandler handler : list.getSelectionKeyHandler()) {
            logSelectionKeyHandler(handler);
        }
        for (final NetworkListener listener : listeners.getNetworkListener()) {
            createListener(listener);
        }
    }

    private void createListener(final NetworkListener listener) {
        try {
            final TCPSelectorHandler handler = getSelectorHandler(listener.getTransport());
            handler.setPort(Integer.parseInt(listener.getPort()));
            handler.setInet(InetAddress.getByName(listener.getAddress()));
            handler.setProtocolChainInstanceHandler(findProtocol(listener.getProtocol()));
        } catch (UnknownHostException e) {
            throw new GrizzlyConfigException(e.getMessage(), e);
        }
    }

    private com.sun.grizzly.ProtocolChainInstanceHandler findProtocol(final String protocolName) {
        for (final Protocol protocol : config.getProtocols().getProtocol()) {
            if (protocolName.equals(protocol.getName())) {
                return configureProtocol(protocol);
            }
        }
        return null;
    }

    private com.sun.grizzly.ProtocolChainInstanceHandler configureProtocol(final Protocol protocol) {
        final ProtocolChainInstanceHandler chainInstanceHandler = protocol.getProtocolChainInstanceHandler();
        final com.sun.grizzly.ProtocolChainInstanceHandler handler
            = (com.sun.grizzly.ProtocolChainInstanceHandler) createFromClassname(
            chainInstanceHandler == null ? null : chainInstanceHandler.getClassname(),
            DefaultProtocolChainInstanceHandler.class.getName());
        final com.sun.grizzly.ProtocolChain chain = getProtocolChain(chainInstanceHandler);
        return handler;
    }

    private com.sun.grizzly.ProtocolChain getProtocolChain(
        final ProtocolChainInstanceHandler chainInstanceHandler) {
        final ProtocolChain protocolChain = chainInstanceHandler == null ? null : chainInstanceHandler.getProtocolChain();
        final com.sun.grizzly.ProtocolChain chain = (com.sun.grizzly.ProtocolChain) createFromClassname(
            protocolChain == null ? null : protocolChain.getClassname(),
            DefaultProtocolChain.class.getName());
//        chain.addFilter(getProtocolFilter())
        return chain;
    }

    private TCPSelectorHandler getSelectorHandler(final String name) {
        if (name == null) {
            throw new GrizzlyConfigException("The transport can not be null");
        }
        for (final Transport transport : config.getTransports().getTransport()) {
            if (name.equals(transport.getName())) {
                return createSelectorHandler(transport);
            }
        }
        return null;//createSelectorHandler(new Transport());
    }

    private void logSelectionKeyHandler(final SelectionKeyHandler handler) {
        selectionKeyHandlers.put(handler.getName(), handler);
    }

    private TCPSelectorHandler createSelectorHandler(final Transport transport) {
        final TCPSelectorHandler handler = (TCPSelectorHandler) createFromClassname(transport.getClassname(),
            TCPSelectorHandler.class.getName());
        handler.setRole(Role.SERVER);
        handler.setSelectionKeyHandler(getKeySelectionHandler(transport, handler));
        controller.addSelectorHandler(handler);
        selectorHandlers.put(transport.getName(), handler);
        return handler;
    }

    private Object createFromClassname(final String classname, final String defaultClassName) {
        try {
            return Class.forName(classname == null ? defaultClassName : classname).newInstance();
        } catch (Exception e) {
            e.printStackTrace();
            throw new GrizzlyConfigException(e.getMessage(), e);
        }
    }

    private com.sun.grizzly.SelectionKeyHandler getKeySelectionHandler(final Transport transport,
        final TCPSelectorHandler selectorHandler) {
        final com.sun.grizzly.SelectionKeyHandler keyHandler;
        final String name = transport.getSelectionKeyHandler();
        if (name == null) {
            keyHandler = new BaseSelectionKeyHandler();
        } else {
            final SelectionKeyHandler handler = selectionKeyHandlers.get(name);
            final String classname = handler.getClassname();
            keyHandler = (com.sun.grizzly.SelectionKeyHandler) createFromClassname(classname,
                BaseSelectionKeyHandler.class.getName());
        }
        keyHandler.setSelectorHandler(selectorHandler);
        selectorHandler.setSelectionKeyHandler(keyHandler);
        return keyHandler;
    }
}