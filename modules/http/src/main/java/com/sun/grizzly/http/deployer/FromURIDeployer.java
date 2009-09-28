/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2007 Sun Microsystems, Inc. All rights reserved.
 */
package com.sun.grizzly.http.deployer;

import com.sun.grizzly.http.embed.GrizzlyWebServer;

import java.net.URI;

/**
 * Deployer abstraction supporting deployment from {@link URI} .
 *
 * @author Hubert Iwaniuk
 * @param <V> Type of object deployed by this deployer.
 * @param <T> Type of deployer configuration.
 * @since Sep 18, 2009
 */
public abstract class FromURIDeployer<V extends Deployable, T extends DeploymentConfiguration> extends Deployer<V, T> {

    /**
     * Deploy deployable to gws.
     *
     * @param gws           Grizzly to deploy to.
     * @param deployFrom    URI ofr deployable to be deployed.
     * @param configuration Configuration of deployment.
     * @return Deployment identification.
     * @throws DeployException Error in deployment.
     */
    public final DeploymentID deploy(GrizzlyWebServer gws, URI deployFrom, T configuration)
            throws DeployException {
        return super.deploy(gws, fromURI(deployFrom, configuration), configuration);
    }

    /**
     * Create object to deploy from uri.
     *
     * @param uri           of deployable object.
     * @param configuration Configuration of deployment.
     * @return Deployable object.
     * @throws DeployException If loading Deployable from uri failed.
     */
    protected abstract V fromURI(URI uri, T configuration) throws DeployException;
}
