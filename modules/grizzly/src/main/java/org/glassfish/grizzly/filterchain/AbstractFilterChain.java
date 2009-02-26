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

package org.glassfish.grizzly.filterchain;

import java.util.Collection;
import org.glassfish.grizzly.IOEvent;
import org.glassfish.grizzly.Context;
import org.glassfish.grizzly.util.IOEventMask;
import org.glassfish.grizzly.ProcessorResult;
import org.glassfish.grizzly.util.ArrayIOEventMask;
import org.glassfish.grizzly.util.ConcurrentQueuePool;
import org.glassfish.grizzly.util.ObjectPool;
import java.io.IOException;

/**
 * Abstract {@link FilterChain} implementation,
 * which redirects {@link com.sun.grizzly.Processor#process(com.sun.grizzly.Context)}
 * call to the {@link AbstractFilterChain#execute(com.sun.grizzly.filterchain.FilterChainContext)}
 * 
 * @author Alexey Stashok
 */
public abstract class AbstractFilterChain implements FilterChain {
    public enum Direction {
        FORWARD(1),
        BACK(-1);

        private int direction;
        Direction(int direction) {
            this.direction = direction;
        }

        public int getDirection() {return direction;}

        public Direction opposite() {
            if (direction == FORWARD.direction) {
                return BACK;
            }

            return FORWARD;
        }
    };
    
    protected FilterChainFactory factory;
    
    // By default interested in all client connection related events
    protected IOEventMask interestedIoEventsMask = new ArrayIOEventMask(
            IOEventMask.CLIENT_EVENTS_MASK).xor(new ArrayIOEventMask(IOEvent.WRITE));

    protected Direction executionDirection = Direction.FORWARD;

    protected final ObjectPool<FilterChainContext> filterChainContextPool =
            new ConcurrentQueuePool<FilterChainContext>() {
        @Override
        public FilterChainContext newInstance() {
            return new FilterChainContext(filterChainContextPool);
        }
    };

    public AbstractFilterChain(FilterChainFactory factory) {
        this.factory = factory;
    }
    
    /**
     * Get the {@link Direction}, this {@link FilterChain} will follow when
     * executing the {@link Filter}s.
     *
     * @return the {@link Direction}
     */
    public Direction getDirection() {
        return executionDirection;
    }

    /**
     * Set the {@link Direction}, this {@link FilterChain} will follow when
     * executing the {@link Filter}s.
     *
     * @param executionDirection the {@link Direction}
     */
    public void setDirection(Direction executionDirection) {
        this.executionDirection = executionDirection;
    }

    public boolean isInterested(IOEvent ioEvent) {
        return interestedIoEventsMask.isInterested(ioEvent);
    }

    public void setInterested(IOEvent ioEvent, boolean isInterested) {
        interestedIoEventsMask.setInterested(ioEvent, isInterested);
    }

    public FilterChainFactory getFactory() {
        return factory;
    }

    public void beforeProcess(Context context) throws IOException {
    }

    public ProcessorResult process(Context context)
            throws IOException {
        return execute((FilterChainContext) context);
    }

    public void afterProcess(Context context) throws IOException {
    }

    public FilterChainContext context() {
        return filterChainContextPool.poll();
    }
    
    public abstract ProcessorResult execute(FilterChainContext context)
            throws IOException;
    
    protected static int getStartingFilterIndex(Collection collection,
            Direction direction) {
        
        if (direction == Direction.FORWARD) {
            return 0;
        }

        return collection.size() - 1;
    }
}
