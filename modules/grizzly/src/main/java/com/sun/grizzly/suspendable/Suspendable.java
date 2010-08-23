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

package com.sun.grizzly.suspendable;

import java.nio.channels.SelectionKey;

/**
 * An Object representing the result of an suspendable operation like {@link SuspendableFilter@#suspend}
 * An instance of that class can be used to resume or cancel a suspended connection.
 * See {@link SuspendableFilter} for more information.
 * 
 * @author Jeanfrancois Arcand
 */
public class Suspendable {

    private final SuspendableFilter suspendableFilter;
    private SelectionKey key;
    private boolean isResumed = false;

    protected Suspendable(SuspendableFilter suspendableFilter) {
        this.suspendableFilter = suspendableFilter;
    }

    
    /**
     * Resume a suspended connection. If the connection has been resumed, calling
     * resume with produce an {@link IllegalStateException}.
     * @return true is resumed.
     */
    public boolean resume() {
        if (getKey() == null){
            isResumed = true;
            return isResumed;
        }
        
        isResumed = suspendableFilter.resume(getKey());
        return isResumed;
    }

    
    /**
     * Return true if the connection associated with this instance has been {@link #resumed}
     * 
     * @return true if resumed.
     */
    public boolean isResumed() {
        return isResumed;
    }

    
    /**
     * Cancel a suspended connection. If the connection has been  {@link #resumed}, calling
     * resume with produce an {@link IllegalStateException}.
     * @return true is resumed.
     */
    public void cancel(){
        if (isResumed) {
            throw new IllegalStateException("Already cancelled");
        }
        
        if (getKey() == null){
            isResumed = true;
            return;
        }
                
        suspendableFilter.cancel(getKey());
    }

    
    /**
     * Return the underlying SelectionKey representing the connection.
     * @return the underlying SelectionKey representing the connection.
     */
    protected SelectionKey getKey() {
        return key;
    }

    
    /**
     * Set the underlying SelectionKey.
     * @param key  the underlying SelectionKey.
     */
    protected void setKey(SelectionKey key) {
        this.key = key;
    }
}
