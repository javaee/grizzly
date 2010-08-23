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

package com.sun.grizzly.aio;

import com.sun.grizzly.Context;
import com.sun.grizzly.ContextTask;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.util.ByteBufferFactory.ByteBufferType;
import com.sun.grizzly.util.DefaultThreadPool;
import com.sun.grizzly.util.WorkerThread;
import java.nio.ByteBuffer;
import java.nio.channels.AsynchronousSocketChannel;
import java.util.concurrent.TimeUnit;

/**
 * {@link ProtocolChain} task, which will be executed by 
 * {@link Context}, when Context.execute({@link ContextTask}) 
 * is called.
 * 
 * @author Jeanfrancois Arcand
 */
public class AIOContextTask extends ContextTask {

    public Object call() throws Exception {
        final WorkerThread workerThread = ((WorkerThread) Thread.currentThread());
        AIOContext ctx = (AIOContext)context;
        AsynchronousSocketChannel channel = ctx.getChannel();
        ByteBuffer buffer = workerThread.getByteBuffer();
        ByteBuffer bb = ctx.getByteBuffer();
        
        if (bb == null && buffer != null){
            bb = buffer;
            workerThread.setByteBuffer(null);
        } else if (bb == null && buffer == null){
            int size = 8192;
            ByteBufferType bbt = ByteBufferType.DIRECT;
            if (ctx.getThreadPool() instanceof DefaultThreadPool){
                size = ((DefaultThreadPool)ctx.getThreadPool() )
                        .getInitialByteBufferSize();
                bbt = ((DefaultThreadPool)ctx.getThreadPool() )
                        .getByteBufferType();
            }
            bb = ByteBuffer.allocateDirect(size);
        }
        ctx.setByteBuffer(bb);

        channel.read(bb, (Long)ctx.getAttribute("timeout"), TimeUnit.SECONDS,
                null, ctx);
       
        return null;
    }


}
