/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.messagesbus;

import com.sun.grizzly.comet.CometContext;
import com.sun.grizzly.comet.CometEvent;
import com.sun.grizzly.comet.CometHandler;
import com.sun.grizzly.messagesbus.MessagesBus.CometType;
import java.io.IOException;
import javax.servlet.http.HttpServletResponse;

/**
 * Grizzly Comet {@link CometHandler} which push data to all suspended connection
 * managed by {@link MessagesBus} 
 * 
 * @author Jeanfrancois Arcand
 */
public class MessagesBusCometHandler implements CometHandler<HttpServletResponse> {

    /**
     * The {@link HttpServletResponse} used to push data.
     */
    private HttpServletResponse response;
    
    
    /**
     * The {@link CometContext} this {@link CometHandler} is associated.
     */
    private CometContext cometContext;
        
    
    /**
     * The {@link MessagesBus#CometType} this {@link CometHandler} support.
     */
    private CometType cometType = CometType.LONG_POLLING;
    
    
    /**
     * Create a new {@link CometHandler} associated with {@link CometContext}
     * @param cometContext
     */
    protected MessagesBusCometHandler(CometContext cometContext) {
        this.cometContext = cometContext;
    }
    
    
    /**
     * Create a new {@link CometHandler} associated with {@link CometContext},
     * supported the {@link MessagesBus#CometType}.
     * @param cometContext
     */    
    protected MessagesBusCometHandler(CometContext cometContext, CometType cometType) {
        this.cometContext = cometContext;
        this.cometType = cometType;
    }

    
    /**
     * The {@link HttpServletResponse} used to push back data,
     * @param response
     */
    public void attach(HttpServletResponse response) {
        this.response = response;
    }


    /**
     * Invoked when a {@link CometContext#notify} is executing a push.
     * @param event
     * @throws java.io.IOException
     */
    public void onEvent(CometEvent event) throws IOException {
        if (event.getType() == CometEvent.NOTIFY) {
            String output = (String) event.attachment();

            response.getWriter().println(output);
            response.getWriter().flush();
            
            if (cometType == CometType.LONG_POLLING){
                cometContext.resumeCometHandler(this);
            }
        }
    }

    
    /**
     * Not used.
     * @param event
     * @throws java.io.IOException
     */
    public void onInitialize(CometEvent event) throws IOException {
    }

    
    /**
     * Invoked when {@link MessagesBus#expirationDelay} value expire. The
     * connection will be automatically resumed.
     * @param event
     * @throws java.io.IOException
     */
    public void onInterrupt(CometEvent event) throws IOException {
        removeThisFromContext();
    }

    
    /**
     * Never invoked as Grizly Comet {@link CometContext} API
     * aren't exposed.
     */
    public void onTerminate(CometEvent event) throws IOException {
        removeThisFromContext();
    }

    
    /**
     * Resume the connection.
     * @throws java.io.IOException
     */
    private void removeThisFromContext() throws IOException {
        cometContext.removeCometHandler(this);
    }
}
