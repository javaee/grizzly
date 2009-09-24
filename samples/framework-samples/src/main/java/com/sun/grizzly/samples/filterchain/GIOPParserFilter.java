/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
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
 *
 */
package com.sun.grizzly.samples.filterchain;

import com.sun.grizzly.Transformer;
import java.io.IOException;
import com.sun.grizzly.Connection;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.TransformationResult.Status;
import com.sun.grizzly.filterchain.CodecFilter;
import com.sun.grizzly.filterchain.FilterAdapter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.streams.Stream;
import com.sun.grizzly.streams.StreamReader;
import java.util.logging.Filter;

/**
 * Example of parser {@link Filter}.
 *
 * @author Alexey Stashok
 */
public class GIOPParserFilter extends FilterAdapter
        implements CodecFilter<Stream, GIOPMessage> {

    private static final GIOPDecoder decoder = new GIOPDecoder();
    private static final GIOPEncoder encoder = new GIOPEncoder();

    @Override
    public NextAction handleRead(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        final Connection connection = ctx.getConnection();
        final StreamReader input = ctx.getStreamReader();

        TransformationResult<GIOPMessage> result =
                decoder.transform(connection, input, null);
        if (result != null && result.getStatus() == Status.COMPLETED) {
            ctx.setMessage(result.getMessage());
            return nextAction;
        }

        return ctx.getStopAction();
    }

    /**
     * Post read is called to let FilterChain cleanup resources.
     *
     * @param ctx {@link FilterChainContext}
     * @param nextAction default {@link NextAction} next step instruction for the {@link FilterChain}
     * @return {@link NextAction} next step instruction for the {@link FilterChain}
     * @throws IOException
     */
    @Override
    public NextAction postRead(FilterChainContext ctx, NextAction nextAction)
            throws IOException {

        final StreamReader reader = ctx.getStreamReader();
        // Check, if there is some data remaining in the input stream
        if (reader.hasAvailableData()) {
            // if yes - rerun the parser filter to parse next message
            return ctx.getRerunChainAction();
        }

        return nextAction;
    }

    @Override
    public Transformer<Stream, GIOPMessage> getDecoder() {
        return decoder;
    }

    @Override
    public Transformer<GIOPMessage, Stream> getEncoder() {
        return encoder;
    }
}
