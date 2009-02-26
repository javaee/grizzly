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



/**
 * An interface marker used to execute operations before 
 * a <code>AsycnProcesssorTask</code> in pre/post or interrupted. Usualy, 
 * implementation of this interface is called by an instance of 
 * <code>AsyncExecutor</code>.
 *
 * Implementation of this interface must be thread-safe.
 *
 * @author Jeanfrancois Arcand
 */
public interface AsyncFilter {
    
    /**
     * Execute and return <code>true</code> if the next <code>AsyncFilter</code> 
     * can be invoked. Return <code>false</code> to stop calling the 
     * <code>AsyncFilter</code>.
     */
    public boolean doFilter(AsyncExecutor asyncExecutor);
}
