

/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 *
 * You can obtain a copy of the license at
 * glassfish/bootstrap/legal/CDDLv1.0.txt or
 * https://glassfish.dev.java.net/public/CDDLv1.0.html.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * glassfish/bootstrap/legal/CDDLv1.0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your
 * own identifying information: Portions Copyright [yyyy]
 * [name of copyright owner]
 *
 * Copyright 2005 Sun Microsystems, Inc. All rights reserved.
 *
 * Portions Copyright Apache Software Foundation.
 */ 

package com.sun.grizzly.util.net.jsse;

import java.net.Socket;
import java.security.Principal;
import java.security.PrivateKey;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.X509ExtendedKeyManager;
import javax.net.ssl.X509KeyManager;

/**
 * X509KeyManager which allows selection of a specific keypair and certificate
 * chain (identified by their keystore alias name) to be used by the server to
 * authenticate itself to SSL clients.
 *
 * @author Jan Luehe
 */
public final class JSSEKeyManager extends X509ExtendedKeyManager{
    
    private X509KeyManager delegate;
    private String serverKeyAlias;

    
    /**
     * Constructor.
     *
     * @param mgr The X509KeyManager used as a delegate
     * @param serverKeyAlias The alias name of the server's keypair and
     * supporting certificate chain
     */
    public JSSEKeyManager(X509KeyManager mgr, String serverKeyAlias) {
        super();
        this.delegate = mgr;
        this.serverKeyAlias = serverKeyAlias;
    }
    
    
    /**
     * Choose an alias to authenticate the client side of an SSLEngine connection
     * given the public key type and the list of certificate issuer authorities 
     * recognized by the peer (if any).
     *
     * @return The alias name for the desired key, or null if there are no
     * matches
     */
    public String chooseEngineClientAlias(String[] keyType,
                                          Principal[] issuers,
                                          SSLEngine engine){
        return delegate.chooseClientAlias(keyType,issuers,null);
    }
    
    
    /**
     * Choose an alias to authenticate the server side of an SSLEngine connection
     * given the public key type and the list of certificate issuer authorities
     * recognized by the peer (if any).   
     * @return Alias name for the desired key
     **/
    public String chooseEngineServerAlias(String keyType,
                                          Principal[] issuers,
                                          SSLEngine engine){
        return serverKeyAlias;
    }  

    
    /**
     * Choose an alias to authenticate the client side of a secure socket,
     * given the public key type and the list of certificate issuer authorities
     * recognized by the peer (if any).
     *
     * @param keyType The key algorithm type name(s), ordered with the
     * most-preferred key type first
     * @param issuers The list of acceptable CA issuer subject names, or null
     * if it does not matter which issuers are used
     * @param socket The socket to be used for this connection. This parameter
     * can be null, in which case this method will return the most generic
     * alias to use
     *
     * @return The alias name for the desired key, or null if there are no
     * matches
     */
    public String chooseClientAlias(String[] keyType, Principal[] issuers,
                                    Socket socket) {
        return delegate.chooseClientAlias(keyType, issuers, socket);
    }

    /**
     * Returns this key manager's server key alias that was provided in the
     * constructor.
     *
     * @param keyType The key algorithm type name (ignored)
     * @param issuers The list of acceptable CA issuer subject names, or null
     * if it does not matter which issuers are used (ignored)
     * @param socket The socket to be used for this connection. This parameter
     * can be null, in which case this method will return the most generic
     * alias to use (ignored)
     *
     * @return Alias name for the desired key
     */
    public String chooseServerAlias(String keyType, Principal[] issuers,
                                    Socket socket) {
        return serverKeyAlias;
    }

    /**
     * Returns the certificate chain associated with the given alias.
     *
     * @param alias The alias name
     *
     * @return Certificate chain (ordered with the user's certificate first
     * and the root certificate authority last), or null if the alias can't be
     * found
     */
    public X509Certificate[] getCertificateChain(String alias) {
        return delegate.getCertificateChain(alias); 
    }

    /**
     * Get the matching aliases for authenticating the client side of a secure
     * socket, given the public key type and the list of certificate issuer
     * authorities recognized by the peer (if any).
     *
     * @param keyType The key algorithm type name
     * @param issuers The list of acceptable CA issuer subject names, or null
     * if it does not matter which issuers are used
     *
     * @return Array of the matching alias names, or null if there were no
     * matches
     */
    public String[] getClientAliases(String keyType, Principal[] issuers) {
        return delegate.getClientAliases(keyType, issuers);
    }

    /**
     * Get the matching aliases for authenticating the server side of a secure
     * socket, given the public key type and the list of certificate issuer
     * authorities recognized by the peer (if any).
     *
     * @param keyType The key algorithm type name
     * @param issuers The list of acceptable CA issuer subject names, or null
     * if it does not matter which issuers are used
     *
     * @return Array of the matching alias names, or null if there were no
     * matches
     */
    public String[] getServerAliases(String keyType, Principal[] issuers) {
        return delegate.getServerAliases(keyType, issuers);
    }

    /**
     * Returns the key associated with the given alias.
     *
     * @param alias The alias name
     *
     * @return The requested key, or null if the alias can't be found
     */
    public PrivateKey getPrivateKey(String alias) {
        return delegate.getPrivateKey(alias);
    }
}
