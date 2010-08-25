/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.samples.reverse;

import com.sun.grizzly.Connection;
import com.sun.grizzly.filterchain.BaseFilter;
import java.io.IOException;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reverse Echo Filter demonstrates the possibility, how developer may reuse the
 * current thread to continue input data processing, but complete the FilterChain
 * invocation from the custom thread and let next Connection data to be processed.
 *
 * @author Alexey Stashok
 */
public class ReverseEchoFilter extends BaseFilter {
    // Thread pool, where we will finish FilterChain context execution
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    /**
     * Handle just read operation, when some message has come and ready to be
     * processed.
     *
     * @param ctx Context of {@link FilterChainContext} processing
     * @return the next action
     * @throws java.io.IOException
     */
    @Override
    public NextAction handleRead(final FilterChainContext ctx)
            throws IOException {

        final Object message = ctx.getMessage();

        // If message == null - it means we're inside resume phase (1)
        if (message == null) {
            // Stop the processing
            return ctx.getStopAction();
        }

        // Gather the Context information, cause it may become invalid after executing "finishing thread"
        
        // Peer address is used for non-connected UDP Connection :)
        final Object peerAddress = ctx.getAddress();
        // Get Connection
        final Connection connection = ctx.getConnection();

        final NextAction suspendAction = ctx.getSuspendAction();
        
        // Execute "context finishing" asynchronously
        executorService.execute(new Runnable() {

            @Override
            public void run() {
// (1)
                // set the message to null so our filter is able to distinguish the state
                ctx.setMessage(null);
                
                // resume the context (so ReverseEchoFilter.handleRead(...) will be called again,
                // but message will be null this time)..
                ctx.resume();
            }
        });

        // Do echo
        connection.write(peerAddress, message, null);

        return suspendAction;
    }

}