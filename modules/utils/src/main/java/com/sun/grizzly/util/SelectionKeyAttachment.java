/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2012 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.util;

import java.nio.channels.SelectionKey;

/**
 * Basic class for all SelectionKey attachments.
 * Custom attachments should be inherited from it.
 *
 * @author Alexey Stashok
 */
public abstract class SelectionKeyAttachment {
    public static final long UNLIMITED_TIMEOUT = Long.MIN_VALUE;
    public static final long DEREGISTERED = Long.MIN_VALUE + 1;
    public static final long UNSET_TIMEOUT = Long.MIN_VALUE + 2;
    
    protected volatile long timeout = UNSET_TIMEOUT;
    protected volatile long idleTimeoutDelay = UNSET_TIMEOUT;
    protected volatile TimeOutListener timeoutListener;
    protected volatile KeySelectionListener keySelectionListener;

    public static Object getAttachment(SelectionKey key) {
        Object attachment = key.attachment();
        if (attachment instanceof SelectionKeyAttachmentWrapper) {
            return ((SelectionKeyAttachmentWrapper) attachment).getAttachment();
        }

        return attachment;
    }

    /**
     * Returns the idle timeout delay.
     * Default it returns UNLIMITED_TIMEOUT, meaning no idle timeout delay.
     * @return
     */
    public long getIdleTimeoutDelay(){
        return idleTimeoutDelay;
    }

    /**
     * Set the idle timeout delay.
     * Default it returns UNLIMITED_TIMEOUT, meaning no idle timeout delay.
     * @param idleTimeoutDelay
     */
    public void setIdleTimeoutDelay(long idleTimeoutDelay){
        this.idleTimeoutDelay = idleTimeoutDelay;
    }

    /**
     * Get the channel expiration stamp in millis
     * (last time an opeation on the channel was invoked).
     * @return the channel expiration stamp in millis.
     */
    public long getTimeout() {
        return timeout;
    }
    
    /**
     * Set the channel expiration stamp in millis
     * (last time an opeation on the channel was invoked).
     * @param timeout the channel expiration stamp in millis.
     */
    public void setTimeout(long timeout) {
        this.timeout = timeout;
    }

    public TimeOutListener getTimeoutListener() {
        return timeoutListener;
    }

    public void setTimeoutListener(TimeOutListener timeoutListener) {
        this.timeoutListener = timeoutListener;
    }

    public KeySelectionListener getKeySelectionListener() {
        return keySelectionListener;
    }

    public void setKeySelectionListener(KeySelectionListener keySelectionListener) {
        this.keySelectionListener = keySelectionListener;
    }

    /**
     *  called when idle timeout detected.
     *  return true if key should be canceled.
     * @param Key
     * @return
     */
    public boolean timedOut(SelectionKey Key) {
        TimeOutListener listener = this.timeoutListener;
        if (listener != null) {
            return listener.onTimeOut(Key);
        }

        return true;
    }

    /**
     * Used for completely custom selector.select logic.
     *
     * @param selectionKey
     * @return <tt>true</tt>, if we want to continue the default interest
     * processing, or <tt>false</tt> otherwise.
     */
    public boolean handleSelectedKey(SelectionKey selectionKey) {
        final KeySelectionListener listener = keySelectionListener;
        if (listener != null) {
            listener.onKeySelected(selectionKey);
        }

        return false;
    }

    public void release(SelectionKey selectionKey) {
        idleTimeoutDelay = UNSET_TIMEOUT;
        timeout = UNSET_TIMEOUT;
        timeoutListener = null;
        keySelectionListener = null;
    }

    public interface TimeOutListener {
        boolean onTimeOut(SelectionKey key);
    }

    public interface KeySelectionListener {
        void onKeySelected(SelectionKey selectionKey);
    }
}
