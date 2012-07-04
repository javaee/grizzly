/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2012 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.enterprise.web.connector.grizzly.comet.concurrent;

import com.sun.enterprise.web.connector.grizzly.comet.CometEvent;
import com.sun.enterprise.web.connector.grizzly.comet.CometHandler;
import java.io.IOException;

/**
 * We queue events in each CometHandler to lower the probability
 * that slow or massive IO for one CometHandler severely delays events to others.<br>
 * <br>
 * only streaming mode can benefit from buffering messages like this.  <br>
 * only 1 thread at a time is allowed to do IO,
 * other threads put events in the queue and return to the thread pool.<br>
 * <br>
 * a thread initially calls enqueueEvent and stay there until there are no more
 * events in the queue, calling the onEVent method in synchronized context for each Event.<br>
 * <br>
 * on IOE in onEvent we terminate.<br>
 * we have a limit, to keep memory usage under control.<br>
 * <br>
 * if queue limit is reached onQueueFull is called, and then we terminate.<br>
 * <br>
 * <br>
 * whats not optimal is that a worker thread is sticky to the client depending
 * upon available events in the handlers local queue,
 * that can in theory allow a few clients to block all threads for extended time.<br>
 * that effect can make this implementation unusable depending on the scenario,
 * its not a perfect design be any means.
 * <br>
 * The potential improvement is that only 1 worker thread is tied up to a client instead of several
 * being blocked by synchronized io wait for one CometHandler .<br>
 *
 * @author Gustav Trede
 */
@Deprecated
public abstract class DefaultConcurrentCometHandler<E>
        extends com.sun.grizzly.comet.concurrent.DefaultConcurrentCometHandler<E>
        implements CometHandler<E>{

    public DefaultConcurrentCometHandler() {
    }
    
    public DefaultConcurrentCometHandler(int messageQueueLimit) {
        super(messageQueueLimit);
    }

    public final void onEvent(com.sun.grizzly.comet.CometEvent event) throws IOException {
        onEvent(new CometEvent(event));
    }

    public final void onInitialize(com.sun.grizzly.comet.CometEvent event) throws IOException {
        onInitialize(new CometEvent(event));
    }

    @Override
    public final void onInterrupt(com.sun.grizzly.comet.CometEvent event) throws IOException {
        onInterrupt(new CometEvent(event));
    }

    @Override
    public final void onTerminate(com.sun.grizzly.comet.CometEvent event) throws IOException {
        onTerminate(new CometEvent(event));
    }

    public void onInterrupt(CometEvent event) throws IOException {
        terminate();
    }

    public void onTerminate(CometEvent event) throws IOException {
        terminate();
    }
}
