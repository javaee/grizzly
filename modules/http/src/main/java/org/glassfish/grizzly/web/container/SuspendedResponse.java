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
package org.glassfish.grizzly.web.container;

import java.io.IOException;
import java.util.concurrent.ScheduledFuture;
import java.util.logging.Level;
import org.glassfish.grizzly.web.container.util.LoggerUtils;

/**
 *
 * @author Jean-Francois Arcand
 */
public class SuspendedResponse<A> implements Runnable {

//    public static final Attribute<SuspendedResponse> SuspendedResponseAttr =
//            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(
//            "suspended-response");

    private A attachment;
    private CompletionHandler<? super A> completionHandler;
    private long timeout;
    private Response response;
    protected ScheduledFuture future;

    public SuspendedResponse(long timeout, A attachment,
            CompletionHandler<? super A> completionHandler, Response response) {
        this.timeout = timeout;
        this.attachment = attachment;
        this.completionHandler = completionHandler;
        this.response = response;
    }

    public A getAttachment() {
        return attachment;
    }

    public long getTimeout() {
        return timeout;
    }

    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public CompletionHandler<? super A> getCompletionHandler() {
        return completionHandler;
    }

    public void setCompletionHandler(
            CompletionHandler<? super A> completionHandler) {
        this.completionHandler = completionHandler;
    }

    public void resume() {
        if (future != null) {
            future.cancel(false);
        }
        
        completionHandler.resumed(attachment);
        try {
            response.sendHeaders();
            response.flush();
            response.finish();
        } catch (IOException ex) {
            LoggerUtils.getLogger().log(Level.FINEST, "resume", ex);
        }
    }

    public void timeout() {
        timeout(true);
    }

    public void timeout(boolean forceClose) {
        // If the buffers are empty, commit the response header
        if (future != null && !future.isDone()) {
            future.cancel(false);
        }

        doTimeout(forceClose);
    }

    public void run() {
        doTimeout(true);
    }

    public ScheduledFuture getFuture() {
        return future;
    }

    public void setFuture(ScheduledFuture future) {
        this.future = future;
    }

    protected void doTimeout(boolean forceClose) {
        try {
            completionHandler.cancelled(attachment);
        } finally {
            if (forceClose && !response.isCommitted()) {
                try {
                    response.sendHeaders();
                    response.flush();
                    response.finish();
                } catch (IOException ex) {
                    // Swallow?
                }
            }
        }

    }
}
