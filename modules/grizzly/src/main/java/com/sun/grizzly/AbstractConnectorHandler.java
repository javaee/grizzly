/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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
 *
 */

package com.sun.grizzly;

import com.sun.grizzly.Controller.Protocol;
import java.nio.channels.SelectableChannel;

/**
 * Abstract {@link ConnectorHandler} definition, which preimplements common
 * getter/setter methods.
 * 
 * @author Alexey Stashok
 */
public abstract class AbstractConnectorHandler<E extends SelectorHandler,
        K extends CallbackHandler> implements ConnectorHandler<E, K> {
    /**
     * The <tt>ConnectorHandler</tt> {@link Protocol}.
     */
    protected Protocol protocol;

    /**
     * The internal Controller used (in case not specified).
     */
    protected Controller controller;

    /**
     * The underlying SelectorHandler used to mange SelectionKeys.
     */
    protected E selectorHandler;
    

    /**
     * A {@link CallbackHandler} handler invoked by the SelectorHandler
     * when a non blocking operation is ready to be processed.
     */
    protected K callbackHandler;


    /**
     * The connection's SelectableChannel.
     */
    protected SelectableChannel underlyingChannel;

    /**
     * Is the connection established.
     */
    protected volatile boolean isConnected;
    
    /**
     * Get the <tt>ConnectorHandler</tt> {@link Protocol}.
     * @return the <tt>ConnectorHandler</tt> {@link Protocol}.
     */
    public Protocol protocol() {
        return protocol;
    }

    /**
     * Set the <tt>ConnectorHandler</tt> {@link Protocol}.
     * @param protocol the <tt>ConnectorHandler</tt> {@link Protocol}.
     */
    public void protocol(Protocol protocol) {
        this.protocol = protocol;
    }

    /**
     * Set the {@link Controller} to use with this instance.
     * @param controller the {@link Controller} to use with this instance.
     */
    public void setController(Controller controller) {
        this.controller = controller;
    }

    /**
     * Return the  {@link Controller}
     * @return the  {@link Controller}
     */
    public Controller getController() {
        return controller;
    }

    /**
     * Return the associated {@link SelectorHandler}.
     * @return the associated {@link SelectorHandler}.
     */
    public E getSelectorHandler() {
        return selectorHandler;
    }

    /**
     * Set the associated {@link SelectorHandler}
     * @param selectorHandler the associated {@link SelectorHandler}.
     */
    public void setSelectorHandler(E selectorHandler) {
        this.selectorHandler = selectorHandler;
    }
    
    /**
     * Return the current {@link SelectableChannel} used.
     * @return the current {@link SelectableChannel} used.
     */
    public SelectableChannel getUnderlyingChannel() {
        return underlyingChannel;
    }

    /**
     * Set the {@link SelectableChannel}.
     * @param the {@link SelectableChannel} to use.
     */
    public void setUnderlyingChannel(SelectableChannel underlyingChannel) {
        this.underlyingChannel = underlyingChannel;
    }


    /**
     * Return the {@link CallbackHandler}.
     * @return the {@link CallbackHandler}.
     */
    public K getCallbackHandler() {
        return callbackHandler;
    }

    /**
     * Set the {@link CallbackHandler}.
     * @param callbackHandler the {@link CallbackHandler}.
     */
    public void setCallbackHandler(K callbackHandler) {
        this.callbackHandler = callbackHandler;
    }

    /**
     * Is the underlying channel connected.
     * @return <tt>true</tt> if connected, otherwise <tt>false</tt>
     */
    public boolean isConnected(){
        return isConnected && underlyingChannel != null && underlyingChannel.isOpen();
    }
}
