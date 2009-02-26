

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
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * This is the main interface to be implemented by a coyote connector.
 * (In contrast, Adapter is the main interface to be implemented by a
 * coyote servlet container.)
 *
 * @see Adapter
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 */
public interface ProtocolHandler {


    /**
     * Pass config info.
     */
    public void setAttribute(String name, Object value);


    public Object getAttribute(String name);


    /**
     * The adapter, used to call the connector.
     */
    public void setAdapter(Adapter adapter);


    public Adapter getAdapter();


    /**
     * Init the protocol.
     */
    public void init()
        throws Exception;


    /**
     * Start the protocol.
     */
    public void start()
        throws Exception;


    public void destroy()
        throws Exception;


}
