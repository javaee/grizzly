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

package org.glassfish.grizzly.http.server.accesslog;

import static java.util.logging.Level.FINE;
import static java.util.logging.Level.WARNING;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.logging.Logger;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.server.HttpServer;

/**
 * An {@link AccessLogAppender appender} enqueueing log entries into a
 * {@link LinkedBlockingQueue} and using a secondary, separate {@link Thread}
 * to forward them to a configured nested {@link AccessLogAppender appender}.
 *
 * @author <a href="mailto:pier@usrz.com">Pier Fumagalli</a>
 * @author <a href="http://www.usrz.com/">USRZ.com</a>
 */
public class QueueingAppender implements AccessLogAppender {

    private static final Logger LOGGER = Grizzly.logger(HttpServer.class);

    /* Our queue */
    private final LinkedBlockingQueue<String> queue = new LinkedBlockingQueue<String>();
    /* Where to forward stuff to */
    private final AccessLogAppender appender;
    /* The thread doing the despooling */
    private final Thread thread;

    /**
     * Create a new {@link QueueingAppender} instance enqueueing log entries
     * into a {@link LinkedBlockingQueue} and dequeueing them using a secondary
     * separate {@link Thread}.
     */
    public QueueingAppender(AccessLogAppender appender) {
        if (appender == null) throw new NullPointerException("Null appender");
        this.appender = appender;

        thread = new Thread(new Dequeuer());
        thread.setName(toString());
        thread.setDaemon(true);
        thread.start();
    }

    @Override
    public void append(String accessLogEntry)
    throws IOException {
        if (thread.isAlive()) try {
            queue.put(accessLogEntry);
        } catch (InterruptedException exception) {
            LOGGER.log(FINE, "Interrupted adding log entry to the queue", exception);
        }
    }

    @Override
    public void close() throws IOException {
        thread.interrupt();
        try {
            thread.join();
        } catch (InterruptedException exception) {
            LOGGER.log(FINE, "Interrupted stopping de-queuer", exception);
        } finally {
            appender.close();
        }
    }

    /* ====================================================================== */
    /* OUR DE-QUEUER                                                          */
    /* ====================================================================== */

    private final class Dequeuer implements Runnable {
        @Override
        public void run() {
            while (true) try {
                final String accessLogEntry = queue.take();
                if (accessLogEntry != null) appender.append(accessLogEntry);
            } catch (InterruptedException exception) {
                LOGGER.log(FINE, "Interrupted waiting for log entry to be queued, exiting!", exception);
                return;
            } catch (Throwable throwable) {
                LOGGER.log(WARNING, "Exception caught appending ququed log entry", throwable);
            }
        }
    }

}
