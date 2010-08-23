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

import java.io.Serializable;

/**
 * Simple event class used to pass information between {@link CometHandler}
 * and the Comet implementation.
 *
 * @author Jeanfrancois Arcand
 */
public class CometEvent<E> implements Serializable{

    
    /**
     * Interrupt the {@link CometHandler}.
     */
    public final static int INTERRUPT = 0;
    
    
    /**
     * Notify the {@link CometHandler}.
     */
    public final static int NOTIFY = 1;
    
    
    /**
     * Initialize the {@link CometHandler}.
     */    
    public final static int INITIALIZE = 2;
    
    
    /**
     * Terminate the {@link CometHandler}.
     */     
    public final static int TERMINATE = 3;    
    
    
    /**
     * Notify the {@link CometHandler} of available bytes.
     */       
    public final static int READ = 4;
    
    
    /**
     * Notify the {@link CometHandler} when the channel is writable.
     */       
    public final static int WRITE = 5;
    
    
    /**
     * This type of event.
     */
    protected int type;

    
    /**
     * Share an <code>E</code> amongst {@link CometHandler}
     */
    protected E attachment;
    
    
    /**
     * The CometContext from where this instance was fired.
     */
    private transient CometContext cometContext;
    private static final long serialVersionUID = 920798330036889926L;


    /**
     * Create a new <code>CometEvent</code>
     */
    public CometEvent() {
        type = NOTIFY;
    }

    public CometEvent(int type) {
        this.type = type;
    }

    public CometEvent(int type, CometContext context) {
        this.type = type;
        cometContext = context;
    }

    public CometEvent(int type, CometContext cometContext, E attachment) {
        this.type = type;
        this.attachment = attachment;
        this.cometContext = cometContext;
    }
    
    /**
     * Return the <code>type</code> of this object.
     * @return int Return the <code>type</code> of this object
     */
    public int getType(){
        return type;
    }
    
    
    /**
     * Set the <code>type</code> of this object.
     * @param type the <code>type</code> of this object
     */    
    protected void setType(int type){
        this.type = type;
    }
    
    
    /**
     * Attach an <E>
     * @param attachment An attachment. 
     */
    public void attach(E attachment){
        this.attachment = attachment;
    }
    
    
    /**
     * Return the attachment <E>
     * @return attachment An attachment. 
     */    
    public E attachment(){
        return attachment;
    }

    
    /**
     * Return the {@link CometContext} that fired this event.
     */
    public CometContext getCometContext() {
        return cometContext;
    }

    
    /**
     * Set the {@link CometContext} that fired this event.
     */
    protected void setCometContext(CometContext cometContext) {
        this.cometContext = cometContext;
    }
    
}
