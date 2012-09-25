/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.http.server.util;

import java.io.IOException;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.server.AddOn;
import org.glassfish.grizzly.http.server.HttpServerFilter;
import org.glassfish.grizzly.http.server.NetworkListener;

/**
 * AggregatorAddOn installs {@link AggregatorFilter} into HttpServer FilterChain.
 * {@link AggregatorFilter} is responsible for aggregating input HTTP message
 * payload (either request or response) and pass it to the next filter in chain
 * only when entire HTTP message (including payload) is read.
 * 
 * @author Alexey Stashok
 */
public class AggregatorAddOn implements AddOn {

    /**
     * {@inheritDoc}
     */
    @Override
    public void setup(final NetworkListener networkListener,
            final FilterChainBuilder builder) {
        
        // Get the index of HttpServerFilter in the HttpServer filter chain
        final int httpServerFilterIdx = builder.indexOfType(HttpServerFilter.class);

        if (httpServerFilterIdx >= 0) {
            // Insert the AggregatorFilter right before HttpServerFilter
            builder.add(httpServerFilterIdx, new AggregatorFilter());
        }     
    }
    
    private static class AggregatorFilter extends BaseFilter {

        @Override
        public NextAction handleRead(final FilterChainContext ctx) throws IOException {
            final Object message = ctx.getMessage();
            
            // If the input message is not HttpContent or it's last HttpContent message -
            // pass the message to a next filter
            if (!(message instanceof HttpContent) || ((HttpContent) message).isLast()) {
                return ctx.getInvokeAction();
            }
            
            // if it's HttpContent chunk (not last) - save it and stop filter chain processing.
            return ctx.getStopAction(message);
        }
    }
}
