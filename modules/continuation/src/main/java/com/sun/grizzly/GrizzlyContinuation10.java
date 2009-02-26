/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 * 
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
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
 */

package com.sun.grizzly;

import com.sun.enterprise.web.connector.grizzly.comet.CometContext;
import com.sun.enterprise.web.connector.grizzly.comet.CometEngine;
import com.sun.enterprise.web.connector.grizzly.comet.CometEvent;
import com.sun.enterprise.web.connector.grizzly.comet.CometHandler;
import java.io.IOException;
import java.util.Random;

/**
 * Simple Continuation interface based on Grizzly 1.0 Comet implementation.
 * 
 * @author Jeanfrancois Arcand
 */
public class GrizzlyContinuation10 implements GrizzlyContinuation{
    
    CometContext ctx = CometEngine.getEngine().register(
            new String("Continuation-" + new Random().nextLong()));
    
    CometHandler continuationHandler = new CometHandler() {
        public void attach(Object o) {
        }
        public void onEvent(CometEvent cometEvent) throws IOException {
        }
        public void onInitialize(CometEvent cometEvent) throws IOException {
        }
        public void onInterrupt(CometEvent cometEvent) throws IOException {
        }
        public void onTerminate(CometEvent cometEvent) throws IOException {
        }
    };
    
    private Object object;
    
    
    /**
     * Suspend the connection for a maximum of sleepTime milliseconds
     * @param sleepTime The maximum time to wait
     */
    public void suspend(long sleepTime){
        ctx.setExpirationDelay(sleepTime);
        ctx.addCometHandler(continuationHandler);
    }
    
    /**
     * Resume an continuation.
     */
    public void resume() throws Exception {
        ctx.removeCometHandler(continuationHandler);
    }
    
    /**
     * Accessor for the object associated with this continuation
     * @return the object associated with this continuation
     */
    public Object getObject(){
        return object;
    }
    
    /**
     * Accessor for the object associated with this continuation
     * @param object the object associated with this continuation
     */
    public void setObject(Object object){
        this.object = object;
    }
}
