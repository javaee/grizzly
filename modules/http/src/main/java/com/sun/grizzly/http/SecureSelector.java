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
 * Non blocking SSL interface secure instance of SelectorThread must implement.
 *
 * @author Jeanfrancois Arcand
 */
public interface SecureSelector<E> {
    
    public void setSSLImplementation(E sslImplementation);
    
    
    /**
     * Returns the list of cipher suites to be enabled when {@link SSLEngine}
     * is initialized.
     * 
     * @return <tt>null</tt> means 'use {@link SSLEngine}'s default.'
     */
    public String[] getEnabledCipherSuites();

    
    /**
     * Sets the list of cipher suites to be enabled when {@link SSLEngine}
     * is initialized.
     * 
     * @param cipherSuites <tt>null</tt> means 'use {@link SSLEngine}'s default.'
     */
    public void setEnabledCipherSuites(String[] enabledCipherSuites);

   
    /**
     * Returns the list of protocols to be enabled when {@link SSLEngine}
     * is initialized.
     * 
     * @return <tt>null</tt> means 'use {@link SSLEngine}'s default.'
     */  
    public String[] getEnabledProtocols();

    
    /**
     * Sets the list of protocols to be enabled when {@link SSLEngine}
     * is initialized.
     * 
     * @param protocols <tt>null</tt> means 'use {@link SSLEngine}'s default.'
     */    
    public void setEnabledProtocols(String[] enabledProtocols);   
    
    /**
     * Returns <tt>true</tt> if the SSlEngine is set to use client mode
     * when handshaking.
     */
    public boolean isClientMode() ;


    /**
     * Configures the engine to use client (or server) mode when handshaking.
     */    
    public void setClientMode(boolean clientMode) ;

    
    /**
     * Returns <tt>true</tt> if the SSLEngine will <em>require</em> 
     * client authentication.
     */   
    public boolean isNeedClientAuth() ;

    
    /**
     * Configures the engine to <em>require</em> client authentication.
     */    
    public void setNeedClientAuth(boolean needClientAuth) ;

    
    /**
     * Returns <tt>true</tt> if the engine will <em>request</em> client 
     * authentication.
     */   
    public boolean isWantClientAuth();

    
    /**
     * Configures the engine to <em>request</em> client authentication.
     */    
    public void setWantClientAuth(boolean wantClientAuth); 
}
