/*
 * The contents of this file are subject to the terms 
 * of the Common Development and Distribution License 
 * (the License).  You may not use this file except in
 * compliance with the License.
 * 
 * You can obtain a copy of the license at 
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing 
 * permissions and limitations under the License.
 * 
 * When distributing Covered Code, include this CDDL 
 * Header Notice in each file and include the License file 
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.  
 * If applicable, add the following below the CDDL Header, 
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information: 
 * "Portions Copyrighted [year] [name of copyright owner]"
 * 
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */
package com.sun.grizzly.http;


import java.io.IOException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;

/**
 * This interface allow implementation of multi Selector support from a 
 * SelectorThread.
 *
 * @author Jeanfrancois
 */
public interface MultiSelectorThread {
    /**
     * Add a <code>Channel</code> to be processed by this
     * <code>Selector</code>
     */
    void addChannel(SocketChannel channel) throws IOException, ClosedChannelException;

    /**
     * Provides the count of request threads that are currently
     * being processed by the container
     * 
     * @return Count of requests
     */
    int getCurrentBusyProcessorThreads();

    /**
     * Initialize this <code>SelectorThread</code>
     */
    void initEndpoint() throws IOException, InstantiationException;

    /**
     * Start and wait for incoming connection
     */
    void startEndpoint() throws IOException, InstantiationException;

    /**
     * Stop incoming connection
     */
    void stopEndpoint();    
}
