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
package com.sun.grizzly.http;

import com.sun.grizzly.DefaultSelectionKeyHandler;
import com.sun.grizzly.tcp.Response;
import com.sun.grizzly.tcp.Response.ResponseAttachment;
import com.sun.grizzly.util.Copyable;
import com.sun.grizzly.util.SelectionKeyAttachment;
import java.nio.channels.SelectionKey;
import java.util.Iterator;
import java.util.logging.Level;

/**
 * Default HTTP {@link SelectionKeyHandler} implementation
 *
 * @author Jean-Francois Arcand
 * @author Alexey Stashok
 */
public class SelectorThreadKeyHandler extends DefaultSelectionKeyHandler {

    private SelectorThread selectorThread;

    public SelectorThreadKeyHandler() {
    }

    public SelectorThreadKeyHandler(SelectorThread selectorThread) {
        this.selectorThread = selectorThread;
    }

    @Override
    public void copyTo(Copyable copy) {
        super.copyTo(copy);
        SelectorThreadKeyHandler copyHandler = (SelectorThreadKeyHandler) copy;
        copyHandler.selectorThread = selectorThread;
    }

    @Override
    public void doRegisterKey(SelectionKey key, int ops, long currentTime) {
        Object attachment = key.attachment();
        if (attachment instanceof KeepAliveThreadAttachment) {
            if (!key.isValid()) {
                selectorThread.cancelKey(key);
                return;
            }
            KeepAliveThreadAttachment k = (KeepAliveThreadAttachment) attachment;
            k.setTimeout(currentTime);
        } else {
            addExpirationStamp(key);
        }
        key.interestOps(key.interestOps() | ops);
    }

    @Override
    public void cancel(SelectionKey key) {
        if (key == null) {
            return;
        }
        if (selectorThread.getThreadPool() instanceof StatsThreadPool) {
            if (selectorThread.isMonitoringEnabled() &&
                    ((StatsThreadPool) selectorThread.getThreadPool()).getStatistic().decrementOpenConnectionsCount(key.channel())) {
                selectorThread.getRequestGroupInfo().decreaseCountOpenConnections();
            }
        }

        Object attachment = key.attachment();
        if (attachment instanceof KeepAliveThreadAttachment) {
            KeepAliveThreadAttachment k = (KeepAliveThreadAttachment) attachment;
            k.resetKeepAliveCount();
        }
        super.cancel(key);
    }

    /**
     * Reset the expiration time
     */
    public void resetExpiration() {
        nextKeysExpiration = 0;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void expire(Iterator<SelectionKey> iterator) {
        //must check for timeout, attachments can have such interest
        final long currentTime = System.currentTimeMillis();
        if (currentTime < nextKeysExpiration) {
            return;
        }
        nextKeysExpiration = currentTime + 1000;
        while (iterator.hasNext()) {
            SelectionKey key = iterator.next();
            if (!key.isValid()) {
                continue;
            }
            Object attachment = key.attachment();
            if (attachment != null) {
                long expire = getExpirationStamp(attachment);
                if (expire == SelectionKeyAttachment.UNLIMITED_TIMEOUT) {
                    continue;
                }

                long idleLimit, activeThreadTimeout;
                if (attachment instanceof KeepAliveThreadAttachment) {
                    activeThreadTimeout = ((KeepAliveThreadAttachment) attachment).getActiveThreadTimeout();

                    if (activeThreadTimeout != SelectionKeyAttachment.UNLIMITED_TIMEOUT) {
                        idleLimit = activeThreadTimeout;
                    } else {
                        idleLimit = ((SelectionKeyAttachment) attachment).getIdleTimeoutDelay();
                        if (idleLimit == SelectionKeyAttachment.UNLIMITED_TIMEOUT) {
                            //this is true when attachment class dont have idletimeoutdelay configured.
                            idleLimit = timeout;
                        }
                    }
                } else {
                    idleLimit = timeout;
                }
                if (idleLimit == -1) {
                    continue;
                }

                if (currentTime - expire >= idleLimit) {
                    if (attachment instanceof Response.ResponseAttachment) {
                        ((ResponseAttachment) attachment).timeout();
                        key.attach(null);
                        continue;
                    }

                    if (attachment instanceof KeepAliveThreadAttachment) {
                        KeepAliveThreadAttachment k = (KeepAliveThreadAttachment) attachment;
                        if (k.activeThread() != null) {
                            if (logger.isLoggable(Level.WARNING)) {
                                logger.log(Level.WARNING, "Interrupting idle Thread: " + k.activeThread().getName());
                            }
                            k.activeThread().interrupt();
                        }
                    }
                    cancel(key);
                }
            }
        }
    }

    /**
     * Gets expiration timeout stamp from the {@link SelectionKey}
     * depending on its attachment
     *
     * @param {@link SelectionKey}
     */
    private long getExpirationStamp(Object attachment) {
        if (attachment instanceof Long) {
            return (Long) attachment;
        } else if (attachment instanceof SelectionKeyAttachment) {
            return ((SelectionKeyAttachment) attachment).getTimeout();
        } else if (attachment instanceof Response.ResponseAttachment) {
            return ((Response.ResponseAttachment) attachment).getExpirationTime() - timeout;
        }
        return SelectionKeyAttachment.UNLIMITED_TIMEOUT;
    }
}
