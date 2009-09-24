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
package com.sun.grizzly.filterchain;

import java.util.List;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.TransformationException;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.TransformationResult.Status;
import com.sun.grizzly.Transformer;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.attributes.AttributeBuilder;
import com.sun.grizzly.attributes.AttributeHolder;
import com.sun.grizzly.attributes.AttributeStorage;

/**
 *
 * @author Alexey Stashok
 */
public class DefaultDecoderTransformer implements Transformer {
    private final DefaultFilterChain filterChain;

    private final Attribute<FilterChainContext> contextAttribute;
    private final Attribute inputAttribute;
    private final Attribute outputAttribute;
    private final Attribute<TransformationResult> lastResultAttribute;

    protected DefaultDecoderTransformer(final DefaultFilterChain filterChain) {
        this(filterChain, Grizzly.DEFAULT_ATTRIBUTE_BUILDER);
    }

    protected DefaultDecoderTransformer(final DefaultFilterChain filterChain,
            final AttributeBuilder attributeBuilder) {
        this.filterChain = filterChain;

        contextAttribute = attributeBuilder.createAttribute("ddt_context");
        inputAttribute = attributeBuilder.createAttribute("ddt_input");
        outputAttribute = attributeBuilder.createAttribute("ddt_output");
        lastResultAttribute = attributeBuilder.createAttribute("ddt_last_result");
    }

    public DefaultFilterChain getFilterChain() {
        return filterChain;
    }

    public FilterChainContext getContext(AttributeStorage state) {
        return contextAttribute.get(state);
    }

    public void setContext(AttributeStorage state, FilterChainContext context) {
        contextAttribute.set(state, context);
    }

    @Override
    public TransformationResult transform(final AttributeStorage state)
            throws TransformationException {
        return transform(state, getInput(state), getOutput(state));
    }

    @Override
    public TransformationResult transform(final AttributeStorage state,
            Object originalMessage, Object targetMessage)
            throws TransformationException {
        if (originalMessage == null) {
            originalMessage = getInput(state);
        }

        final List<Filter> filters = getFilters(state);

        Object currentMessage;
        TransformationResult decodeResult = new TransformationResult(
                Status.COMPLETED, originalMessage);

        currentMessage = null;

        currentMessage = originalMessage;
        for (Filter filter : filters) {
            if (filter instanceof CodecFilter) {
                final CodecFilter codecFilter = (CodecFilter) filter;
                final Transformer decoder = codecFilter.getDecoder();
                if (decoder != null) {
                    decodeResult = decoder.transform(state,
                            currentMessage, null);
                    if (decodeResult.getStatus() == Status.COMPLETED) {
                        currentMessage = decodeResult.getMessage();
                    } else {
                        break;
                    }
                }
            }
        }

        setLastResult(state, decodeResult);

        return decodeResult;
    }

    @Override
    public Object getInput(AttributeStorage state) {
        return inputAttribute.get(state);
    }

    @Override
    public void setInput(AttributeStorage state, Object input) {
        inputAttribute.set(state, input);
    }

    @Override
    public Object getOutput(AttributeStorage state) {
        return outputAttribute.get(state);
    }

    @Override
    public void setOutput(AttributeStorage state, Object outputTarget) {
        outputAttribute.set(state, outputTarget);
    }

    @Override
    public TransformationResult getLastResult(AttributeStorage state) {
        return lastResultAttribute.get(state);
    }

    private void setLastResult(AttributeStorage state,
            TransformationResult lastResult) {
        lastResultAttribute.set(state, lastResult);
    }

    @Override
    public AttributeHolder getProperties(AttributeStorage state) {
        return state.getAttributes();
    }

    @Override
    public void hibernate(AttributeStorage state) {
    }

    @Override
    public void release(AttributeStorage state) {
        releaseFilterTransformers(state);
        contextAttribute.remove(state);
        lastResultAttribute.remove(state);
        inputAttribute.remove(state);
        outputAttribute.remove(state);
    }

    private void releaseFilterTransformers(final AttributeStorage state) {
        final List<Filter> filters = getFilters(state);

        for(Filter filter : filters) {
            if (filter instanceof CodecFilter) {
                final Transformer decoder = ((CodecFilter) filter).getDecoder();
                if (decoder != null) {
                    decoder.release(state);
                }
            }
        }
    }

    private List<Filter> getFilters(final AttributeStorage state) {
        final FilterChainContext context = getContext(state);

        if (context != null) {
            return context.getExecutedFilters();
        } else {
            return filterChain;
        }
    }
}