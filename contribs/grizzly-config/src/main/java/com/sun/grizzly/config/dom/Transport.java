/*
 *   DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *   Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 *
 *   The contents of this file are subject to the terms of either the GNU
 *   General Public License Version 2 only ("GPL") or the Common Development
 *   and Distribution License("CDDL") (collectively, the "License").  You
 *   may not use this file except in compliance with the License. You can obtain
 *   a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 *   or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 *   language governing permissions and limitations under the License.
 *
 *   When distributing the software, include this License Header Notice in each
 *   file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 *   Sun designates this particular file as subject to the "Classpath" exception
 *   as provided by Sun in the GPL Version 2 section of the License file that
 *   accompanied this code.  If applicable, add the following below the License
 *   Header, with the fields enclosed by brackets [] replaced by your own
 *   identifying information: "Portions Copyrighted [year]
 *   [name of copyright owner]"
 *
 *   Contributor(s):
 *
 *   If you wish your version of this file to be governed by only the CDDL or
 *   only the GPL Version 2, indicate your decision by adding "[Contributor]
 *   elects to include this software in this distribution under the [CDDL or GPL
 *   Version 2] license."  If you don't indicate a single choice of license, a
 *   recipient has the option to distribute your version of this file under
 *   either the CDDL, the GPL Version 2 or to extend the choice of license to
 *   its licensees as provided above.  However, if you add GPL Version 2 code
 *   and therefore, elected the GPL Version 2 license, then the option applies
 *   only if the new code is made subject to such option by the copyright
 *   holder.
 *
 */
package com.sun.grizzly.config.dom;

import org.jvnet.hk2.component.Injectable;
import org.jvnet.hk2.config.Attribute;
import org.jvnet.hk2.config.ConfigBeanProxy;
import org.jvnet.hk2.config.Configured;

/**
 * Defines one specific transport and its properties
 */
@Configured
public interface Transport extends ConfigBeanProxy, Injectable, PropertyBag {
    /**
     * The number of acceptor threads listening for the transport's events
     */
    @Attribute(defaultValue = "1")
    String getAcceptorThreads();

    void setAcceptorThreads(String value);

    @Attribute
    String getBufferSize();

    void setBufferSize(String size);

    /**
     * Type of ByteBuffer, which will be used with transport. Possible values are: HEAP and DIRECT
     */
    @Attribute(defaultValue = "HEAP")
    String getByteBufferType();

    void setByteBufferType(String value);

    /**
     * Name of class, which implements transport logic
     */
    @Attribute
    String getClassname();

    void setClassname(String value);

    /**
     * Flush Grizzly's internal configuration to the server logs (like number of threads created, how many polled
     * objects, etc.)
     */
    @Attribute(defaultValue = "false")
    String getDisplayConfiguration();

    void setDisplayConfiguration(String bool);

    /**
     * Dump the requests/response information in server.log. Useful for debugging purpose, but significantly reduce
     * performance as the request/response bytes are translated to String.
     */
    @Attribute(defaultValue = "false")
    String getEnableSnoop();

    void setEnableSnoop(String bool);

    /**
     * Timeout, after which idle key will be cancelled and channel closed
     */
    @Attribute
    String getIdleKeyTimeout();

    void setIdleKeyTimeout(String value);

    /**
     * The max number of connections the transport should handle at the same time
     */
    @Attribute(defaultValue = "4096")
    String getMaxConnectionsCount();

    void setMaxConnectionsCount(String value);

    /**
     * Transport's name, which could be used as reference
     */
    @Attribute(required = true)
    String getName();

    void setName(String value);

    /**
     * Read operation timeout
     */
    @Attribute
    String getReadTimeout();

    void setReadTimeout(String value);

    /**
     * Use public SelectionKey handler, which was defined earlier in the document.
     */
    @Attribute
    String getSelectionKeyHandler();

    void setSelectionKeyHandler(String value);

    /**
     * The time, in milliseconds, a NIO Selector will block waiting for events (users requests).
     */
    @Attribute
    String getSelectorPollTimeout();

    void setSelectorPollTimeout(String timeout);

    @Attribute
    String getUseNioDirectByteBuffer();

    void setUseNioDirectByteBuffer(String useDirectByteBuffer);

    /**
     * Write operation timeout
     */
    @Attribute
    String getWriteTimeout();

    void setWriteTimeout(String value);

    @Attribute
    String getTcpNoDelay();
    void setTcpNoDelay(String noDelay);
}