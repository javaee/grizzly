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

package org.glassfish.grizzly.impl;

import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.Processor;
import org.glassfish.grizzly.ProcessorLock;

/**
 * 
 * @author oleksiys
 */
public class ProcessorLockImpl implements ProcessorLock {
    private Connection connection;
    private IOEvent ioEvent;
    private AtomicReference<Processor> lockedProcessor =
            new AtomicReference<Processor>();
    protected ReadWriteLock internalUpdateLock = new ReentrantReadWriteLock();

    // Flag, which signals, if IOEvent updates came at time, when lock was acquired
    private boolean isEventUpdated;

    public ProcessorLockImpl(Connection connection, IOEvent ioEvent) {
        this.connection = connection;
        this.ioEvent = ioEvent;
    }

    public void lock(Processor processor) {
        assert processor != null;
        
        internalUpdateLock.writeLock().lock();
        try {
            lockedProcessor.set(processor);
        } finally {
            internalUpdateLock.writeLock().unlock();
        }
    }

    public void unlock() {
        internalUpdateLock.writeLock().lock();
        try {
            lockedProcessor.set(null);
        } finally {
            internalUpdateLock.writeLock().unlock();
        }
    }

    public Processor getProcessor() {
        return lockedProcessor.get();
    }

    public boolean isLocked() {
        return lockedProcessor.get() != null;
    }

    public ReadWriteLock getInternalUpdateLock() {
        return internalUpdateLock;
    }

    public boolean isEventUpdated() {
        return isEventUpdated;
    }

    public void setEventUpdated(boolean isEventUpdated) {
        this.isEventUpdated = isEventUpdated;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(64);
        sb.append("ProcessorLock[ connection=").append(connection);
        sb.append(", ioEvent=").append(ioEvent);
        sb.append(", processor=").append(lockedProcessor.get());
        sb.append(']');
        return sb.toString();
    }
}
