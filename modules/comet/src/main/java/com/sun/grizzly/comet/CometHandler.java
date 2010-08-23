/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.comet;

import java.io.IOException;

/**
 * This interface represents a suspended connection (or response). Passing an 
 * instance of this class to {@link CometContext#addCometHandler} automatically
 * tells Grizzly Comet to suspend the underlying connection and to avoid commiting the 
 * response. Since the response is not commited, the connection is considered
 * as suspended and can be resumed later when an event happens by invoking 
 * {@link CometContext#resumeCometHandler(CometHandler)},
 * from {@link CometHandler#onEvent}. 
 * 
 * {@link CometContext#resumeCometHandler(CometHandler),
 * resume the connection by commiting the response. As an example, a browser icons
 * will spins when a connection is suspended, as the complete response hasn't been
 * sent back. 
 * 
 * Components that implement this interface will be notified 
 * {@link CometContext#notify(String)} is invoked or when
 * the {@link CometContext#getExpirationDelay()} expires.
 *
 * With {@link Servlet}, it is recommended to attach the {@link HttpServletResponse}
 * and use this object to push back messages to the client.
 *
 * @author Jeanfrancois Arcand
 */
public interface CometHandler<E> {

    /**
     * Attach an intance of E to this class.
     */
    void attach(E attachment);
    
    
    /**
     * Receive {@link CometEvent} notification. This method will be invoked
     * everytime a {@link CometContext#notify} is invoked. The {@link CometEvent}
     * will contains the message that can be pushed back to the remote client,
     * cached or ignored. This method can also be used to resume a connection
     * once a notified by invoking {@link CometContext#resumeCometHandler}.<br>
     * its not optimal to flush outputstream in this method for long polling,
     * flush is performed in each CometContext.resume call.<br>
     * flushing multiple times can fragment the data into several tcp packets,
     * that leads to extra IO and overhead in general due to client ack for each packet etc.
     */
    void onEvent(CometEvent event) throws IOException;
    
    
    /**
     * Receive {@link CometEvent} notification when Grizzly is about to
     * suspend the connection. This method is always invoked during the 
     * processing of {@link CometContext#addCometHandler} operations.
     */
    void onInitialize(CometEvent event) throws IOException;
    
    
    /**
     * Receive {@link CometEvent} notification when the response
     * is resumed by a {@link CometHandler} or by the {@link CometContext}
     */
    void onTerminate(CometEvent event) throws IOException;
    
    
    /**
     * Receive {@link CometEvent} notification when the underlying 
     * tcp communication is resumed by Grizzly. This happens
     * when the {@link CometContext#setExpirationDelay} expires or when
     * the remote client close the connection.
     */
    void onInterrupt(CometEvent event) throws IOException;
    
}
