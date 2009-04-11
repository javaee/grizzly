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

@Configured
public interface ThreadPool extends ConfigBeanProxy, Injectable {

    /**
     * The classname of a thread pool implementation
     */
    @Attribute
    String getClassname();

    void setClassname(String value);

    /**
     * Idle threads are removed from pool, after this time (in seconds)
     */
    @Attribute(defaultValue = "120")
    String getIdleThreadTimeout();

    void setIdleThreadTimeout(String value);

    /**
     * The maxim number of tasks, which could be queued on the thread pool.  -1 disables any maximum checks.
     */
    @Attribute(defaultValue = "-1")
    String getMaxQueueSize();

    void setMaxQueueSize(String value);

    /**
     * Maximum number of threads in the threadpool servicing
     requests in this queue. This is the upper bound on the no. of
     threads that exist in the threadpool.
     */
    @Attribute(defaultValue = "200")
    String getMaxThreadPoolSize();

    void setMaxThreadPoolSize(String value);

    /**
     * Minimum number of threads in the threadpool servicing
     requests in this queue. These are created up front when this
     threadpool is instantiated
     */
    @Attribute(defaultValue = "0")
    String getMinThreadPoolSize();

    void setMinThreadPoolSize(String value);

    /**
     * This is an id for the work-queue e.g. "thread-pool-1", "thread-pool-2" etc
     */
    @Attribute(required = true)
    String getThreadPoolId();

    void setThreadPoolId(String value);
}