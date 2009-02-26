

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
package com.sun.grizzly.tcp;


/**
 * Enumerated class containing the adapter event codes.
 *
 * Actions represent callbacks from the servlet container to the coyote
 * connector.
 *
 * Actions are implemented by ProtocolHandler, using the ActionHook interface.
 *
 * @see ProtocolHandler
 * @see ActionHook
 *
 * @author Remy Maucherat
 */
public final class ActionCode {


    // -------------------------------------------------------------- Constants


    public static final ActionCode ACTION_ACK = new ActionCode(1);


    public static final ActionCode ACTION_CLOSE = new ActionCode(2);


    public static final ActionCode ACTION_COMMIT = new ActionCode(3);


    /**
     * A flush() operation originated by the client ( i.e. a flush() on
     * the servlet output stream or writer, called by a servlet ).
     *
     * Argument is the Response.
     */
    public static final ActionCode ACTION_CLIENT_FLUSH = new ActionCode(4);

    
    public static final ActionCode ACTION_CUSTOM = new ActionCode(5);


    public static final ActionCode ACTION_RESET = new ActionCode(6);


    public static final ActionCode ACTION_START = new ActionCode(7);


    public static final ActionCode ACTION_STOP = new ActionCode(8);


    public static final ActionCode ACTION_WEBAPP = new ActionCode(9);

    /** Hook called after request, but before recycling. Can be used
        for logging, to update counters, custom cleanup - the request
        is still visible
    */
    public static final ActionCode ACTION_POST_REQUEST = new ActionCode(10);

    /**
     * Callback for lazy evaluation - extract the remote host name.
     */
    public static final ActionCode ACTION_REQ_HOST_ATTRIBUTE = 
        new ActionCode(11);


    /**
     * Callback for lazy evaluation - extract the SSL-related attributes.
     */
    public static final ActionCode ACTION_REQ_HOST_ADDR_ATTRIBUTE = new ActionCode(12);

    /**
     * Callback for lazy evaluation - extract the SSL-related attributes.
     */
    public static final ActionCode ACTION_REQ_SSL_ATTRIBUTE = new ActionCode(13);


    /** Chain for request creation. Called each time a new request is created
        ( requests are recycled ).
     */
    public static final ActionCode ACTION_NEW_REQUEST = new ActionCode(14);


    /**
     * Callback for lazy evaluation - extract the SSL-certificate 
     * (including forcing a re-handshake if necessary)
     */
    public static final ActionCode ACTION_REQ_SSL_CERTIFICATE = new ActionCode(15);

    /**
     * Callback for lazy evaluation - socket remote port.
     **/
    public static final ActionCode ACTION_REQ_REMOTEPORT_ATTRIBUTE = new ActionCode(16);


    /**
     * Callback for lazy evaluation - socket local port.
     **/
    public static final ActionCode ACTION_REQ_LOCALPORT_ATTRIBUTE = new ActionCode(17);


    /**
     * Callback for lazy evaluation - local address.
     **/
    public static final ActionCode ACTION_REQ_LOCAL_ADDR_ATTRIBUTE = new ActionCode(18);


    /**
     * Callback for lazy evaluation - local address.
     **/
    public static final ActionCode ACTION_REQ_LOCAL_NAME_ATTRIBUTE = new ActionCode(19);

    /**
     * Callback for setting FORM auth body replay
     */
    public static final ActionCode ACTION_REQ_SET_BODY_REPLAY = new ActionCode(20);
 
    
    // ----------------------------------------------------------- Constructors
    int code;

    /**
     * Private constructor.
     */
    private ActionCode(int code) {
        this.code=code;
    }

    /** Action id, useable in switches and table indexes
     */
    public int getCode() {
        return code;
    }


}
