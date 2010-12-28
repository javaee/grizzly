/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.http.server;

import java.util.concurrent.TimeUnit;
import org.glassfish.grizzly.CompletionHandler;

/**
 * Interface represents a context of the suspended {@link Response}.
 * 
 * @author Alexey Stashok
 */
public interface SuspendContext {

    /**
     * Get the suspended {@link Response} {@link CompletionHandler}.
     *
     * @return the suspended {@link Response} {@link CompletionHandler}.
     */
    public CompletionHandler<Response> getCompletionHandler();

    /**
     * Get the suspended {@link Response} {@link TimeoutHandler}.
     *
     * @return the suspended {@link Response} {@link TimeoutHandler}.
     */
    public TimeoutHandler getTimeoutHandler();

    /**
     * Get the suspended {@link Response} timeout. If returned value less or equal
     * to zero - timeout is not set.
     *
     * @return the suspended {@link Response} timeout. If returned value less or equal
     * to zero - timeout is not set.
     */
    public long getTimeout(TimeUnit timeunit);

    /**
     * Set the suspended {@link Response} timeout. If timeout value less or equal
     * to zero - suspended {@link Response} won't be never timed out.
     *
     * @param timeout the suspended {@link Response} timeout.
     * @param timeunit timeout units.
     */
    public void setTimeout(long timeout, TimeUnit timeunit);

    /**
     * Returns <tt>true</tt>, if the {@link Response} is suspended, or
     * <tt>false</tt> otherwise.
     *
     * @return <tt>true</tt>, if the {@link Response} is suspended, or
     * <tt>false</tt> otherwise.
     */
    public boolean isSuspended();
}
