/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.tcp;

import com.sun.grizzly.tcp.Response.ResponseAttachment;
import com.sun.grizzly.util.SelectionKeyAttachment;
import com.sun.grizzly.util.ThreadAttachment;
import com.sun.grizzly.util.WorkerThread;
import java.nio.channels.SelectionKey;

/**
 * Utility class, which implements general response suspension functionality.
 * 
 * @author Alexey Stashok
 */
public class SuspendResponseUtils {
    private static final ThreadLocal<Boolean> SUSPENDED_IN_CURRENT_THREAD = new ThreadLocal<Boolean>();

    private static final String SUSPENDED_RESPONSE_ATTR = "SuspendedResponse";
    
    public static void attach(SelectionKey selectionKey,
            Response.ResponseAttachment suspendedResponse) {
        Object attachment = selectionKey.attachment();
        if (attachment == null || !(attachment instanceof ThreadAttachment)) {
            attachment = obtainThreadAttachment();
            selectionKey.attach(attachment);
        }

        final ThreadAttachment threadAttachment = (ThreadAttachment) attachment;
        suspendedResponse.setThreadAttachment(threadAttachment);
        suspendedResponse.resetTimeout();

        threadAttachment.setIdleTimeoutDelay(suspendedResponse.getIdleTimeoutDelay());
        threadAttachment.setTimeoutListener(suspendedResponse);
        threadAttachment.setKeySelectionListener(suspendedResponse);
        threadAttachment.setAttribute(SUSPENDED_RESPONSE_ATTR, suspendedResponse);
    }

    public static void updateIdleTimeOutDelay(SelectionKey selectionKey,
                                              Response.ResponseAttachment suspendedResponse) {

        Object attachment = selectionKey.attachment();
        if (attachment != null) {
            suspendedResponse.resetTimeout();
            final ThreadAttachment threadAttachment = (ThreadAttachment) attachment;
            threadAttachment.setIdleTimeoutDelay(suspendedResponse.getIdleTimeoutDelay());
        }

    }

    public static void detach(SelectionKey selectionKey) {
        final Object attachment = selectionKey.attachment();
        if (attachment != null && attachment instanceof ThreadAttachment) {
            final ThreadAttachment threadAttachment = (ThreadAttachment) attachment;
            threadAttachment.removeAttribute(SUSPENDED_RESPONSE_ATTR);
            threadAttachment.setIdleTimeoutDelay(SelectionKeyAttachment.UNSET_TIMEOUT);
            threadAttachment.setTimeoutListener(null);
            threadAttachment.setKeySelectionListener(null);
        }
    }

    public static Response.ResponseAttachment get(SelectionKey selectionKey) {
        final Object attachment = selectionKey.attachment();
        if (attachment != null && attachment instanceof ThreadAttachment) {
            return (ResponseAttachment) ((ThreadAttachment) attachment).getAttribute(SUSPENDED_RESPONSE_ATTR);
        }
        
        return null;
    }

    private static ThreadAttachment obtainThreadAttachment() {
        final Thread currentThread = Thread.currentThread();
        if (currentThread instanceof WorkerThread) {
            return ((WorkerThread) currentThread).getAttachment();
        }

        return new ThreadAttachment();
    }

    public static boolean isSuspendedInCurrentThread() {
        return SUSPENDED_IN_CURRENT_THREAD.get() == Boolean.TRUE; // ThreadLocal can be null
    }

    public static void setSuspendedInCurrentThread() {
        SUSPENDED_IN_CURRENT_THREAD.set(Boolean.TRUE);
    }

    public static boolean removeSuspendedInCurrentThread() {
        final boolean result = SUSPENDED_IN_CURRENT_THREAD.get() == Boolean.TRUE;
        SUSPENDED_IN_CURRENT_THREAD.remove();
        return result;
    }
}
