/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.spdy.utils;

import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.WriteResult;

/**
 *
 * @author oleksiys
 */
public class ChunkedCompletionHandler implements CompletionHandler<WriteResult> {
    private final CompletionHandler<WriteResult> parentCompletionHandler;

    private boolean isDone;
    protected int chunksCounter = 1;
    private long writtenSize;

    public ChunkedCompletionHandler(
            final CompletionHandler<WriteResult> parentCompletionHandler) {
        this.parentCompletionHandler = parentCompletionHandler;
    }

    public void incChunks() {
        chunksCounter++;
    }
    
    @Override
    public void cancelled() {
        if (done()) {
            if (parentCompletionHandler != null) {
                parentCompletionHandler.cancelled();
            }
        }
    }

    @Override
    public void failed(Throwable throwable) {
        if (done()) {
            if (parentCompletionHandler != null) {
                parentCompletionHandler.failed(throwable);
            }
        }
    }

    @Override
    public void completed(final WriteResult result) {
        if (isDone) {
            return;
        }

        if (--chunksCounter == 0) {
            done();

            final long initialWrittenSize = result.getWrittenSize();
            writtenSize += initialWrittenSize;

            if (parentCompletionHandler != null) {
                try {
                    result.setWrittenSize(writtenSize);
                    parentCompletionHandler.completed(result);
                } finally {
                    result.setWrittenSize(initialWrittenSize);
                }
            }
        } else {
            updated(result);
            writtenSize += result.getWrittenSize();
        }
    }

    @Override
    public void updated(final WriteResult result) {
        if (parentCompletionHandler != null) {
            final long initialWrittenSize = result.getWrittenSize();

            try {
                result.setWrittenSize(writtenSize + initialWrittenSize);
                parentCompletionHandler.updated(result);
            } finally {
                result.setWrittenSize(initialWrittenSize);
            }
        }
    }

    private boolean done() {
        if (isDone) {
            return false;
        }

        isDone = true;
        
        done0();
        return true;
    }
    
    protected void done0() {
    }
}
