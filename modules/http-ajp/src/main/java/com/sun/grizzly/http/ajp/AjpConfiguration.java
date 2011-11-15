/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011 Oracle and/or its affiliates. All rights reserved.
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
package com.sun.grizzly.http.ajp;

import java.util.Queue;

/**
 * AJP Configuration acceptable by {@link AjpProcessorTaskFactory}.
 * 
 * @author Alexey Stashok
 */
public interface AjpConfiguration {
    /**
     * Get the {@link ShutdownHandler} queue, which will be called, when shutdown
     * request received. The returned {@link Queue} elements can be modified.
     */
    public Queue<ShutdownHandler> getShutdownHandlers();
    
    /**
     * If set to true, the authentication will be done in Grizzly.
     * Otherwise, the authenticated principal will be propagated from the
     * native webserver and used for authorization in Grizzly.
     * The default value is true.
     *
     * @return true, if the authentication will be done in Grizzly.
     * Otherwise, the authenticated principal will be propagated from the
     * native webserver and used for authorization in Grizzly.
     */
    public boolean isTomcatAuthentication();

    /**
    /**
     * If set to true, the authentication will be done in Grizzly.
     * Otherwise, the authenticated principal will be propagated from the
     * native webserver and used for authorization in Grizzly.
     * The default value is true.
     *
     * @param isTomcatAuthentication if true, the authentication will be done in Grizzly.
     * Otherwise, the authenticated principal will be propagated from the
     * native webserver and used for authorization in Grizzly.
     */
    public void setTomcatAuthentication(boolean isTomcatAuthentication);

    /**
     * If not null, only requests from workers with this secret keyword will
     * be accepted.
     *
     * @return not null, if only requests from workers with this secret keyword will
     * be accepted, or null otherwise.
     */
    public String getSecret();

    /**
     * If not null, only requests from workers with this secret keyword will
     * be accepted.
     *
     * @param requiredSecret if not null, only requests from workers with this
     * secret keyword will be accepted.
     */
    public void setSecret(String requiredSecret);
    
    /**
     * If <tt>true</tt> and a secret has been configured,
     * a correctly formatted AJP request (that includes the secret) will
     * shutdown the server instance associated with this connector.
     * This is set to <tt>false</tt> by default.
     * 
     * @return If <tt>true</tt> and a secret has been configured,
     * a correctly formatted AJP request (that includes the secret) will
     * shutdown the server instance associated with this connector.
     * This is set to <tt>false</tt> by default.
     */
    public boolean isShutdownEnabled();
    
    /**
     * If <tt>true</tt> and a secret has been configured,
     * a correctly formatted AJP request (that includes the secret) will
     * shutdown the server instance associated with this connector.
     * This is set to <tt>false</tt> by default.
     * 
     * @param isShutdownEnabled If <tt>true</tt> and a secret has been configured,
     * a correctly formatted AJP request (that includes the secret) will
     * shutdown the server instance associated with this connector.
     * This is set to <tt>false</tt> by default.
     */
    public void setShutdownEnabled(boolean isShutdownEnabled);
}
