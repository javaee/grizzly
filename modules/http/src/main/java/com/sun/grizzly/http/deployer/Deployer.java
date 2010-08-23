/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.http.deployer;

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Deployer abstraction.
 *
 * @author Hubert Iwaniuk
 * @param <V> Type of object deployed by this deployer.
 * @param <T> Type of deployer configuration.
 * @since Sep 17, 2009
 */
public abstract class Deployer<V extends Deployable, T extends DeploymentConfiguration> {

    private static final Logger logger = Logger.getLogger("com.sun.grizzly.http.deployer.Deployer");
    private Map<DeploymentID, Set<GrizzlyAdapter>> deployed = new HashMap<DeploymentID, Set<GrizzlyAdapter>>();

    /**
     * Deploy {@link Deployable} to gws.
     *
     * @param gws           Grizzly to deploy to.
     * @param toDeploy      Deployable to be deployed.
     * @param configuration Configuration of deployment.
     *
     * @return Deployment identification.
     *
     * @throws DeployException Error in deployment.
     */
    public final DeploymentID deploy(GrizzlyWebServer gws, V toDeploy, T configuration)
        throws DeployException {
        Map<GrizzlyAdapter, Set<String>> map = convert(toDeploy, configuration);
        if (map == null || map.isEmpty()) {
            throw new DeployException("No GrizzlyAdapters created for: " + toDeploy);
        }
        for (Map.Entry<GrizzlyAdapter, Set<String>> adapterEntry : map.entrySet()) {
            GrizzlyAdapter adapter = adapterEntry.getKey();
            Set<String> mappings = adapterEntry.getValue();
            if (logger.isLoggable(Level.FINEST)) {
                logger.log(Level.FINEST, String.format("Deploying '%s' to %s", adapter, mappings));
            }
            gws.addGrizzlyAdapter(adapter, mappings.toArray(new String[mappings.size()]));
        }
        
        final DeploymentID deploymentId = new DeploymentID(toDeploy.hashCode());
        deployed.put(deploymentId, map.keySet());
        return deploymentId;
    }

    /**
     * Undeploy previously deployed deployable.
     *
     * @param gws          Grizzly to undeploy from.
     * @param deploymentId Deployment identification.
     */
    public final void undeploy(GrizzlyWebServer gws, DeploymentID deploymentId) {
        final Set<GrizzlyAdapter> adapters = deployed.get(deploymentId);
        for (GrizzlyAdapter adapter : adapters) {
            gws.removeGrizzlyAdapter(adapter); // TODO removal can go wrong
        }
    }

    /**
     * Converts deployable object to {@link Map} of {@link GrizzlyAdapter}s to paths to deploy to.
     *
     * @param toDeploy      Deployable object to be converted.
     * @param configuration Configuration of deployment.
     *
     * @return {@link Map}ping {@link GrizzlyAdapter}s to paths to be deployed under ({@link Set} of
     *         {@link String}s).
     *
     * @throws DeployException Error while creating adapters.
     */
    protected abstract Map<GrizzlyAdapter, Set<String>> convert(V toDeploy, T configuration)
        throws DeployException;
}
