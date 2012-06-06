/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.comet;

import java.io.IOException;
import java.util.concurrent.CountDownLatch;

import org.glassfish.grizzly.http.server.Response;

public class CountDownCometHandler extends DefaultTestCometHandler {
    public final CountDownLatch onEvent;
    public final CountDownLatch onInitialize;
    public final CountDownLatch onInterrupt;
    public final CountDownLatch onTerminate;

    public CountDownCometHandler(CometContext<String> cometContext, Response response) {
        super(cometContext, response, false);
        onEvent = new CountDownLatch(1);
        onInitialize = new CountDownLatch(1);
        onInterrupt = new CountDownLatch(1);
        onTerminate = new CountDownLatch(1);
    }

    @Override
    public void onInterrupt(CometEvent event) throws IOException {
        super.onInterrupt(event);
        onInterrupt.countDown();
    }

    @Override
    public void onTerminate(CometEvent event) throws IOException {
        super.onTerminate(event);
        onTerminate.countDown();
    }

    @Override
    public void onEvent(CometEvent event) throws IOException {
        super.onEvent(event);
        onEvent.countDown();
    }

    @Override
    public void onInitialize(CometEvent event) throws IOException {
        super.onInitialize(event);
        onInitialize.countDown();
    }
}
