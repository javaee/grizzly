/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.spdy;

import java.util.LinkedList;

/**
 * The queue of element bundles.
 * Each bundle in the queue may be empty or have some elements.
 */
final class BundleQueue<E> {
    private final LinkedList<Record<E>> internalQueue = new LinkedList<Record<E>>();
    private int lastElementAbsoluteDistance;

    /**
     * Add the element to the specified bundle.
     * The bundle is represented by its order in the queue. It is not possible to
     * add the element to a bundle, which already exists and is not the last
     * bundle in the queue.
     * 
     * @param bundle
     * @param element 
     */
    public void add(final int bundle, final E element) {
        if (lastElementAbsoluteDistance > bundle) {
            throw new IllegalStateException("New element must have greater" + " absolute distance than the last element in the queue");
        }
        internalQueue.addLast(new Record<E>(element, bundle - lastElementAbsoluteDistance));
        lastElementAbsoluteDistance = bundle;
    }

    /**
     * Returns <tt>true</tt> if there is available element in the current bundle.
     */
    public boolean hasNext() {
        return !internalQueue.isEmpty() && internalQueue.getFirst().distance == 0;
    }

    /**
     * Returns next available element in the current bundle.
     */
    public E next() {
        if (!hasNext()) {
            throw new IllegalStateException("There is no next element available");
        }
        return internalQueue.removeFirst().value;
    }

    /**
     * Switches to the next bundle in the queue, all the unread elements from
     * the previously active bundle will be removed.
     * @return <tt>true</tt> if next bundle exists and is not empty
     */
    public boolean nextBundle() {
        if (internalQueue.isEmpty()) {
            return false;
        }
        // skip old records
        while (internalQueue.getFirst().distance == 0) {
            internalQueue.removeFirst();
            if (internalQueue.isEmpty()) {
                return false;
            }
        }
        lastElementAbsoluteDistance--;
        return --internalQueue.getFirst().distance == 0;
    }

    private static final class Record<E> {

        private final E value;
        private int distance;

        public Record(final E value, final int distance) {
            this.value = value;
            this.distance = distance;
        }
    }
    
}
