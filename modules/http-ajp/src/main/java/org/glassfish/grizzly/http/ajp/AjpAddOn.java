/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.ajp;

import java.util.Properties;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.http.HttpCodecFilter;
import org.glassfish.grizzly.http.server.AddOn;
import org.glassfish.grizzly.http.server.HttpServerFilter;
import org.glassfish.grizzly.http.server.NetworkListener;

/**
 * Ajp {@link AddOn} for the {@link org.glassfish.grizzly.http.server.HttpServer}.
 *
 * The addon searches for {@link HttpCodecFilter} occurrence in the passed
 * {@link FilterChainBuilder}, removes it and adds 2 filters:
 * {@link AjpMessageFilter} and {@link AjpHandlerFilter} on its place.
 *
 * @author Alexey Stashok
 */
public class AjpAddOn implements AddOn {

    private boolean isTomcatAuthentication;
    private String secret;

    public AjpAddOn() {
        isTomcatAuthentication = true;
    }

    /**
     * Construct AjpAddOn
     *
     * @param isTomcatAuthentication if true, the authentication will be done in Grizzly.
     * Otherwise, the authenticated principal will be propagated from the
     * native webserver and used for authorization in Grizzly.
     *
     * @param secret if not null, only requests from workers with this
     * secret keyword will be accepted, or null otherwise.
     */
    public void configure(final boolean isTomcatAuthentication,
            final String secret) {
        this.isTomcatAuthentication = isTomcatAuthentication;
        this.secret = secret;
    }

    /**
     * Configure Ajp Filter using properties.
     * We support following properties: request.useSecret, request.secret, tomcatAuthentication.
     *
     * @param properties
     */
    public void configure(final Properties properties) {
        if (Boolean.parseBoolean(properties.getProperty("request.useSecret"))) {
            secret = Double.toString(Math.random());
        }

        secret = properties.getProperty("request.secret", secret);
        isTomcatAuthentication =
                Boolean.parseBoolean(properties.getProperty(
                "tomcatAuthentication", "true"));
    }

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
    public boolean isTomcatAuthentication() {
        return isTomcatAuthentication;
    }

    /**
     * If not null, only requests from workers with this secret keyword will
     * be accepted.
     *
     * @return not null, if only requests from workers with this secret keyword will
     * be accepted, or null otherwise.
     */
    public String getSecret() {
        return secret;
    }

    @Override
    public void setup(final NetworkListener networkListener,
            final FilterChainBuilder builder) {

        final int httpCodecFilterIdx = builder.indexOfType(HttpCodecFilter.class);
        final int httpServerFilterIdx = builder.indexOfType(HttpServerFilter.class);

        int idx;

        if (httpCodecFilterIdx >= 0) {
            builder.remove(httpCodecFilterIdx);
            idx = httpCodecFilterIdx;
        } else {
            idx = httpServerFilterIdx;
        }

        if (idx >= 0) {
            builder.add(idx, createAjpMessageFilter());

            final AjpHandlerFilter ajpHandlerFilter = createAjpHandlerFilter();
            ajpHandlerFilter.setSecret(secret);
            ajpHandlerFilter.setTomcatAuthentication(isTomcatAuthentication);

            builder.add(idx + 1, ajpHandlerFilter);
        }
    }

    protected AjpHandlerFilter createAjpHandlerFilter() {
        return new AjpHandlerFilter();
    }

    protected AjpMessageFilter createAjpMessageFilter() {
        return new AjpMessageFilter();
    }

}
