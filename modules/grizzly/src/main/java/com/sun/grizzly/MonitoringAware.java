/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2010 Sun Microsystems, Inc. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License. You can obtain
 * a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 * or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 * Sun designates this particular file as subject to the "Classpath" exception
 * as provided by Sun in the GPL Version 2 section of the License file that
 * accompanied this code.  If applicable, add the following below the License
 * Header, with the fields enclosed by brackets [] replaced by your own
 * identifying information: "Portions Copyrighted [year]
 * [name of copyright owner]"
 *
 * Contributor(s):
 *
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

package com.sun.grizzly;

/**
 * General interface for the objects, which could be monitored during the lifecycle.
 * 
 * @author Alexey Stashok
 */
public interface MonitoringAware<E> {
    /**
     * Add the monitoring probes, which will be notified about object's lifecycle events.
     *
     * @param probes the monitoring probes.
     */
    public void addProbes(E... probes);

    /**
     * Remove the monitoring probes.
     *
     * @param probes the monitoring probes.
     */
    public boolean removeProbes(E... probes);

    /**
     * Get the the monitoring probes, which are registered on the objet.
     * Please note, it's not appropriate to modify the returned array's content.
     * Please use {@link #addMonitoringProbe(java.lang.Object)} and
     * {@link #removeMonitoringProbe(java.lang.Object)} instead.
     *
     * @return the the monitoring probes, which are registered on the object.
     */
    public E[] getProbes();

    /**
     * Removes all the monitoring probes, which are registered on the object.
     */
    public void clearProbes();
}
