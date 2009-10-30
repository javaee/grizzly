/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
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
 */

package com.sun.enterprise.web.connector.grizzly;

import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * Concurrent {@link Queue} implementation, which is used in Grizzly.
 * This class was introduced in order to easily change the type of {@link Queue},
 * which is used in Grizzly, because of strange memory leak, found with
 * {@link ConcurrentLinkedQueue#Node}s (CR6875041).
 * 
 * @author Alexey Stashok
 */
public final class ConcurrentQueue<E> extends LinkedBlockingQueue<E> {
//    private static final AtomicLong totalSize = new AtomicLong();
//    private static final AtomicLong lastTotalOutput = new AtomicLong();
//
//    private final AtomicLong lastOutput = new AtomicLong();
//    private final AtomicInteger lastAddedToTotal = new AtomicInteger();

    private final String name;

    public ConcurrentQueue(String name) {
        this.name = name;
    }


//    @Override
//    public boolean offer(E e) {
//        return super.offer(e);
//        if (super.offer(e)) {
//            final long currentTime = System.currentTimeMillis();
//
//            final long lastOutputNow = lastOutput.get();
//            if (currentTime - lastOutputNow > 1000 * 60) {
//                if (lastOutput.compareAndSet(lastOutputNow, currentTime + 1000 * 60)) {
//                    final int size = size();
//                    final int lastAddedToTotalNow = lastAddedToTotal.getAndSet(size);
//                    final int diff = size - lastAddedToTotalNow;
//                    totalSize.addAndGet(diff);
//
//                    System.out.println("CLQ STATS: name: " + name + " size: " + size);
//                }
//            }
//
//            final long lastTotalOutputNow = lastTotalOutput.get();
//            if (currentTime - lastTotalOutputNow > 1000 * 60) {
//                if (lastTotalOutput.compareAndSet(lastTotalOutputNow, currentTime + 1000 * 60)) {
//                    System.out.println("CLQ TOTAL STATS: " + totalSize.get());
//                }
//            }
//
//            return true;
//        }
//
//        return false;
//    }

}
