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

package com.sun.grizzly.comet.handlers;

import com.sun.grizzly.comet.CometEvent;
import com.sun.grizzly.comet.CometHandler;
import java.io.IOException;
import java.io.PrintWriter;

/**
 * Simple {@link CometHandler} that write (using a {@link PrintWriter})
 * all messages ({@link CometEvent#attachment} it receive. This {@link CometHandler}
 * just reflect everything, without filtering, the result of a {@link CometContext#notify).
 * 
 * @author Jeanfrancois Arcand
 */
public class ReflectorCometHandler implements CometHandler<PrintWriter> {

    // Simple PrintWrite
    protected PrintWriter printWriter;

    // Message send when the connection terminate or gets interrupted.
    private String startingMessage = "";
    
    // Message send when the connection terminate or gets interrupted.
    private String endingMessage = "";
        
    // Is http-streamin mode used 
    private boolean useStreaming = true;

    
    /**
     * Create a reflector. 
     */
    public ReflectorCometHandler(){
    }    
    
    /**
     * Create a reflector. 
     * @param useStreaming should the connection be resumed on the first push, 
     *                     or never resumed from the {@link ReflectorCometHandler#onEvent}. Default is true. 
     */
    public ReflectorCometHandler(boolean useStreaming){
        this.useStreaming = useStreaming;
    }
    
    /**
     * Create a reflector. 
     * @param useStreaming should the connection be resumed on the first push, 
     *                     or never resumed from the {@link ReflectorCometHandler#onEvent}. Default is true. 
     * @param endingMessage Message send when the connection is about to be suspended
     *                      ({@link ReflectorCometHandler#onInitialize})
     *                      or gets interrupted ({@link ReflectorCometHandler#onInterrupt}) 
     * @param endingMessage Message send when the connection terminate ({@link ReflectorCometHandler#onTerminate})
     *                      or gets interrupted ({@link ReflectorCometHandler#onInterrupt})
     */
    public ReflectorCometHandler(boolean useStreaming, String startingMessage,String endingMessage){
        this.useStreaming = useStreaming;
        this.startingMessage = startingMessage;
        this.endingMessage = endingMessage;
    }
    
    /**
     * Attach a {@link PrintWriter} that will be used to write the returned value of
     * {@link CometEvent#attachment}
     * 
     * @param printWriter {@link PrintWriter} that will be used to write the returned value of
     * {@link CometEvent#attachment}
     */
    public void attach(PrintWriter printWriter) {
        this.printWriter = printWriter;
    }

    /**
     * Write {@link CometEvent#attachment} and resume the connection if 
     * {@link ReflectorCometHandler#useStreaming} is <tt>false</tt>
     * @param event
     * @throws java.io.IOException
     */
    public void onEvent(CometEvent event) throws IOException {
        try {
            if (event.getType() != CometEvent.READ) {
                printWriter.println(event.attachment());
                printWriter.flush();
                
                if (!useStreaming){
                    event.getCometContext().resumeCometHandler(this);
                }
            }
        } catch (Throwable t) {
            throw new IOException(t.getMessage());
        }
    }

    /**
     * Send the  {@link ReflectorCometHandler#startingMessage} before the
     * connection get suspended.
     * 
     * @param event (@link CometEvent}
     * @throws java.io.IOException
     */
    public void onInitialize(CometEvent event) throws IOException {
        printWriter.println(startingMessage);
        printWriter.flush();
    }

    /**
     * Send the  {@link ReflectorCometHandler#endingMessage} before the
     * connection get terminated.
     * 
     * @param event (@link CometEvent}
     * @throws java.io.IOException
     */    
    public void onTerminate(CometEvent event) throws IOException {
        onInterrupt(event);
    }
    
    /**
     * Send the  {@link ReflectorCometHandler#endingMessage} before the
     * connection get interupted.
     * 
     * @param event (@link CometEvent}
     * @throws java.io.IOException
     */
    public void onInterrupt(CometEvent event) throws IOException {
        printWriter.println(endingMessage);
    }
}
