/*
 *   DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *   Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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

import java.beans.PropertyVetoException;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;

import org.jvnet.hk2.component.Injectable;
import org.jvnet.hk2.config.Attribute;
import org.jvnet.hk2.config.ConfigBean;
import org.jvnet.hk2.config.ConfigBeanProxy;
import org.jvnet.hk2.config.Configured;
import org.jvnet.hk2.config.Dom;
import org.jvnet.hk2.config.DuckTyped;
import org.jvnet.hk2.config.types.PropertyBag;

@Configured
public interface ThreadPool extends ConfigBeanProxy, Injectable, PropertyBag {

    /**
     * The classname of a thread pool implementation
     */
    @Attribute(defaultValue = "com.sun.grizzly.http.StatsThreadPool")
    String getClassname();

    void setClassname(String value);

    /**
     * Idle threads are removed from pool, after this time (in seconds)
     */
    @Attribute(defaultValue = "900", dataType = Integer.class)
    @Max(Integer.MAX_VALUE)
    String getIdleThreadTimeoutSeconds();

    void setIdleThreadTimeoutSeconds(String value);

    /**
     * The maxim number of tasks, which could be queued on the thread pool.  -1 disables any maximum checks.
     */
    @Attribute(defaultValue = "4096", dataType = Integer.class)
    @Max(Integer.MAX_VALUE)
    String getMaxQueueSize();

    void setMaxQueueSize(String value);

    /**
     * Maximum number of threads in the threadpool servicing
     requests in this queue. This is the upper bound on the no. of
     threads that exist in the threadpool.
     */
    @Attribute(defaultValue = "5", dataType = Integer.class)
    @Min(value=2)
    @Max(Integer.MAX_VALUE)
    String getMaxThreadPoolSize();

    void setMaxThreadPoolSize(String value)  throws PropertyVetoException;

    /**
     * Minimum number of threads in the threadpool servicing
     requests in this queue. These are created up front when this
     threadpool is instantiated
     */
    @Attribute(defaultValue = "2", dataType = Integer.class)
    @Min(2)
    @Max(Integer.MAX_VALUE)
    String getMinThreadPoolSize();

    void setMinThreadPoolSize(String value);

    /**
     * This is an id for the work-queue e.g. "thread-pool-1", "thread-pool-2" etc
     */
    @Attribute(required = true, key=true)
    String getName();

    void setName(String value);

    /**
     * This is an id for the work-queue e.g. "thread-pool-1", "thread-pool-2" etc
     */
    @Attribute
    @Deprecated
    String getThreadPoolId();

    void setThreadPoolId(String value);

    @DuckTyped
    List<NetworkListener> findNetworkListeners();

    class Duck {

        static public List<NetworkListener> findNetworkListeners(ThreadPool threadpool) {
            List<NetworkListener> listeners;
            NetworkListeners parent = threadpool.getParent().getParent(NetworkListeners.class);
            if(!Dom.unwrap(parent).getProxyType().equals(NetworkListeners.class)) {
                final NetworkConfig config = Dom.unwrap(parent).element("network-config").createProxy();
                parent = config.getNetworkListeners();
            }
            listeners = parent.getNetworkListener();
            List<NetworkListener> refs = new ArrayList<NetworkListener>();
            for (NetworkListener listener : listeners) {
                if (listener.getThreadPool().equals(threadpool.getName())) {
                    refs.add(listener);
                }
            }
            return refs;
        }

    }
}
