/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.asyncqueue;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Cacheable;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.localization.LogMessages;
import org.glassfish.grizzly.utils.DebugPoint;

/**
 * {@link AsyncQueue} element unit
 * 
 * @param <R> the result type
 * 
 * @author Alexey Stashok
 */
public abstract class AsyncQueueRecord<R> implements Cacheable {
    private final static Logger LOGGER = Grizzly.logger(AsyncQueue.class);
    
    protected Connection connection;
    protected Object message;
    protected CompletionHandler completionHandler;

    protected boolean isRecycled = false;
    protected DebugPoint recycleTrack;
    
    protected AsyncQueueRecord() {
    }
    
    public AsyncQueueRecord(final Connection connection,
            final Object message, final CompletionHandler completionHandler) {
        set(connection, message, completionHandler);
    }

    protected final void set(final Connection connection,
            final Object message, final CompletionHandler completionHandler) {

        checkRecycled();
        this.connection = connection;
        this.message = message;
        this.completionHandler = completionHandler;
    }

    public Connection getConnection() {
        return connection;
    }
  
    @SuppressWarnings("unchecked")
    public final <T> T getMessage() {
        checkRecycled();
        return (T) message;
    }

    public final void setMessage(final Object message) {
        checkRecycled();
        this.message = message;
    }

    /**
     * Returns the current record result object.
     * 
     * @return the current record result object
     */
    public abstract R getCurrentResult();

    public void notifyFailure(final Throwable e) {
        if (completionHandler != null) {
            completionHandler.failed(e);
        } else {
            if (LOGGER.isLoggable(Level.FINE)) {
                LOGGER.log(Level.FINE,
                        LogMessages.FINE_GRIZZLY_ASYNCQUEUE_ERROR_NOCALLBACK_ERROR(e));
            }
        }
    }


    @SuppressWarnings("unchecked")
    public final void notifyIncomplete() {
        if (completionHandler != null) {
            completionHandler.updated(getCurrentResult());
        }
    }
    
    protected final void checkRecycled() {
        if (Grizzly.isTrackingThreadCache() && isRecycled) {
            final DebugPoint track = recycleTrack;
            if (track != null) {
                throw new IllegalStateException("AsyncReadQueueRecord has been recycled at: " + track);
            } else {
                throw new IllegalStateException("AsyncReadQueueRecord has been recycled");
            }
        }
    }
}
