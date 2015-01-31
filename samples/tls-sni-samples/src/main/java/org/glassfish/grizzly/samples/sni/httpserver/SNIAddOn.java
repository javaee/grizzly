/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.samples.sni.httpserver;

import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.http.server.AddOn;
import org.glassfish.grizzly.http.server.NetworkListener;
import org.glassfish.grizzly.sni.SNIFilter;
import org.glassfish.grizzly.sni.SNIServerConfigResolver;
import org.glassfish.grizzly.ssl.SSLBaseFilter;
import org.glassfish.grizzly.ssl.SSLEngineConfigurator;

/**
 * SNI {@link AddOn}, that replaces standard {@link SSLBaseFilter} with
 * an {@link SNIFilter} in order to use different {@link SSLEngineConfigurator}
 * depending on the SNI host information.
 */
public class SNIAddOn implements AddOn {
    private final SNIFilter sniFilter;
    private final SNIServerConfigResolver serverConfigResolver;
    
    public SNIAddOn(final SNIServerConfigResolver serverConfigResolver) {
        if (serverConfigResolver == null) {
            throw new IllegalArgumentException("serverConfigResolver can't be null");
        }
        
        this.serverConfigResolver = serverConfigResolver;
        sniFilter = null;
    }
    
    public SNIAddOn(final SNIFilter sniFilter) {
        if (sniFilter == null) {
            throw new IllegalArgumentException("sniFilter can't be null");
        }
        
        this.sniFilter = sniFilter;
        serverConfigResolver = null;
    }
    
    @Override
    public void setup(final NetworkListener networkListener,
            final FilterChainBuilder builder) {
        final int sslFilterIdx = builder.indexOfType(SSLBaseFilter.class);
        if (sslFilterIdx != -1) {
            // replace SSLBaseFilter with SNIFilter
            final SSLBaseFilter sslFilter =
                    (SSLBaseFilter) builder.get(sslFilterIdx);
            
            SNIFilter sniFilterLocal = sniFilter;
            if (sniFilterLocal == null) {
                sniFilterLocal = new SNIFilter(
                        sslFilter.getServerSSLEngineConfigurator(), // default SSLEngineConfigurator
                        null,
                        sslFilter.isRenegotiateOnClientAuthWant());
                sniFilterLocal.setServerSSLConfigResolver(serverConfigResolver);
            }
            
            builder.set(sslFilterIdx, sniFilterLocal);
        }
    }
}
