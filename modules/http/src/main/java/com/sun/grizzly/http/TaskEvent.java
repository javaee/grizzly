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
 * Sample event object used by instance of <code>TaskListener</code> to share
 * status information about where they are when processing a request.
 *
 * @author Jean-Francois Arcand
 */
public class TaskEvent<E> {
 
    public final static int START = 0;
    public final static int ERROR = 1; 
    public final static int COMPLETED = 2;
    public final static int CONTINUE = 3;
    public final static int START_ASYNCHRONOUS = 4;
    
    private int status;
    
 
    public int getStatus(){
        return status;
    }
    
    
    public void setStatus(int status){
        this.status = status;
    }

    
    /**
     * The associated <code>TaskContext</code> instance. Can be null.
     */
    protected E ctx;

    
    /**
     * Create an instance and associated the <code>TaskContext</code> object.
     */
    public TaskEvent(E ctx){
        this.ctx = ctx;
    }
    
    
    /**
     * Create an empty instance.
     */
    public TaskEvent(){
    }
  
    
    /**
     * Set <code>TaskContext</code> instance. Can be null.
     */
    public void attach(E ctx){
        this.ctx = ctx;
    }

    
    /**
     * Return the <code>TaskContext</code> instance. Can be null.
     */
    public E attachement(){
        return ctx;
    }  
    
    
    
}


