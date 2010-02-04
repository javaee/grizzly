/*
 *
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 2007-2010 Sun Microsystems, Inc. All rights reserved.
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
package com.sun.grizzly.filterchain;

import java.util.List;
import com.sun.grizzly.AbstractTransformer;
import com.sun.grizzly.Appendable;
import com.sun.grizzly.Appender;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.IOEvent;
import com.sun.grizzly.TransformationException;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.Transformer;
import com.sun.grizzly.attributes.AttributeStorage;
import com.sun.grizzly.filterchain.DefaultFilterChain.FILTER_STATE_TYPE;
import java.util.logging.Logger;

/**
 *
 * @author Alexey Stashok
 */
public final class DefaultDecoderTransformer extends AbstractTransformer {
    private static final Logger logger = Grizzly.logger(DefaultDecoderTransformer.class);
    
    private final DefaultFilterChain filterChain;

    private final int limit;
    
    protected DefaultDecoderTransformer(final DefaultFilterChain filterChain) {
        this(filterChain, filterChain.size());
    }

    protected DefaultDecoderTransformer(final DefaultFilterChain filterChain,
            int limit) {
        this.filterChain = filterChain;
        this.limit = limit;
    }

    @Override
    public String getName() {
        return DefaultDecoderTransformer.class.getName();
    }

    public DefaultFilterChain getFilterChain() {
        return filterChain;
    }

    @Override
    public TransformationResult transform(final AttributeStorage state,
            final Object originalMessage) throws TransformationException {
        Object currentMessage;
        currentMessage = originalMessage;

        final DefaultFilterChain.FiltersState filtersState =
                DefaultFilterChain.FILTERS_STATE_ATTR.get(state);

        if (filtersState != null) {
            int remainingIndex;
            if ((remainingIndex =
                    filtersState.lastIndexOf(IOEvent.READ, FILTER_STATE_TYPE.REMAINDER, limit)) >= 0) {
                final Object storedMessage =
                        filtersState.clearState(IOEvent.READ, remainingIndex).getState();

                TransformationResult result =
                        processFilterChain(state, storedMessage, remainingIndex);
                
                return result;
            }
        }
        
        return processFilterChain(state, currentMessage, 0);
    }

    private TransformationResult processFilterChain(
            final AttributeStorage state,
            Object currentMessage,
            int startIndex)
            throws TransformationException {
        
        boolean hasInternalRemainder = false;

        TransformationResult decodeResult = null;
        
        final List<Filter> filters = getFilters(state);

        DefaultFilterChain.FiltersState filtersState =
                DefaultFilterChain.FILTERS_STATE_ATTR.get(state);
        
        for (int i = startIndex; i < limit; i++) {
            final Filter filter = filters.get(i);
            if (filter instanceof CodecFilter) {
                final CodecFilter codecFilter = (CodecFilter) filter;
                final Transformer decoder = codecFilter.getDecoder();
                if (decoder != null) {

                    final DefaultFilterChain.FilterStateElement filterState;
                    if (filtersState != null &&
                            (filterState = filtersState.clearState(IOEvent.READ, i)) != null) {
                        Object storedMessage = filterState.getState();
                        if (currentMessage != null) {
                            final Appender appender = filterState.getAppender();
                            if (appender != null) {
                                storedMessage = appender.append(storedMessage,
                                        currentMessage);
                            } else {
                                ((Appendable) storedMessage).append(currentMessage);
                            }
                        }
                        currentMessage = storedMessage;
                    }
                    decodeResult = decoder.transform(state, currentMessage);
                    switch (decodeResult.getStatus()) {
                        case COMPLETED:
                        {
                            final Object remainder = decodeResult.getExternalRemainder();

                            if (decoder.hasInputRemaining(remainder)) {
                                if (filtersState == null) {
                                    filtersState = new DefaultFilterChain.FiltersState(filters.size());
                                    DefaultFilterChain.FILTERS_STATE_ATTR.set(state, filtersState);
                                }

                                hasInternalRemainder = true;
                                filtersState.setState(IOEvent.READ, i,
                                        DefaultFilterChain.FilterStateElement.create(
                                        FILTER_STATE_TYPE.REMAINDER, remainder));
                            }
                            currentMessage = decodeResult.getMessage();
                            break;
                        }
                        case INCOMPLETED:
                        {
                            final Object remainder = decodeResult.getExternalRemainder();

                            if (decoder.hasInputRemaining(remainder)) {
                            if (filtersState == null) {
                                filtersState = new DefaultFilterChain.FiltersState(filters.size());
                                DefaultFilterChain.FILTERS_STATE_ATTR.set(state, filtersState);
                            }
                            filtersState.setState(IOEvent.READ, i,
                                    DefaultFilterChain.FilterStateElement.create(
                                    FILTER_STATE_TYPE.INCOMPLETE, remainder));
                            }

                            return saveLastResult(state,
                                    TransformationResult.createIncompletedResult(
                                    null, hasInternalRemainder));
                        }
                        case ERROR:
                        {
                            throw new TransformationException(
                                    filter.getClass().getName() +
                                    " transformation error: (" +
                                    decodeResult.getErrorCode() + ") " +
                                    decodeResult.getErrorDescription());
                        }
                    }
                }
            }
        }
        
        if (decodeResult == null) {
            return saveLastResult(state,
                    TransformationResult.createCompletedResult(currentMessage,
                    null, false));
        }

        return saveLastResult(state,
                TransformationResult.createCompletedResult(
                decodeResult.getMessage(),
                null, hasInternalRemainder));
    }


    @Override
    public boolean hasInputRemaining(Object input) {
        for (int i = 0; i < limit; i++) {
            final Filter filter = filterChain.get(i);
            if (filter instanceof CodecFilter) {
                return ((CodecFilter) filter).getEncoder().hasInputRemaining(input);
            }
        }

        return input != null;
    }

    @Override
    public void release(AttributeStorage state) {
        releaseFilterTransformers(state);
        super.release(state);
    }

    private void releaseFilterTransformers(final AttributeStorage state) {
        final List<Filter> filters = getFilters(state);
        for (int i = 0; i < limit; i++) {
            final Filter filter = filters.get(i);
            if (filter instanceof CodecFilter) {
                final Transformer decoder = ((CodecFilter) filter).getDecoder();
                if (decoder != null) {
                    decoder.release(state);
                }
            }
        }
    }

    private List<Filter> getFilters(final AttributeStorage state) {
        return filterChain;
    }
}