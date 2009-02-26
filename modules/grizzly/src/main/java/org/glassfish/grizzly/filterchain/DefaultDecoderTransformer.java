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

import org.glassfish.grizzly.TransformationException;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.TransformationResult.Status;
import org.glassfish.grizzly.Transformer;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.filterchain.AbstractFilterChain.Direction;
import org.glassfish.grizzly.util.AttributeStorage;

/**
 *
 * @author Alexey Stashok
 */
public class DefaultDecoderTransformer implements Transformer {
    private Object output;
    private Object input;
    private TransformationResult lastResult;
    
    private DefaultFilterChain filterChain;

    private FilterChainContext context;

    protected DefaultDecoderTransformer(DefaultFilterChain filterChain) {
        this.filterChain = filterChain;
    }

    protected DefaultDecoderTransformer(FilterChainContext context) {
        this.filterChain = (DefaultFilterChain) context.getFilterChain();
        this.context = context;
    }

    public DefaultFilterChain getFilterChain() {
        return filterChain;
    }

    public void setFilterChain(DefaultFilterChain filterChain) {
        this.filterChain = filterChain;
    }

    public FilterChainContext getContext() {
        return context;
    }

    public void setContext(FilterChainContext context) {
        this.context = context;
    }

    public TransformationResult transform(AttributeStorage state) throws TransformationException {
        return transform(state, getInput(state), getOutput(state));
    }

    public TransformationResult transform(final AttributeStorage state,
            Object originalMessage, Object targetMessage)
            throws TransformationException {
        if (originalMessage == null) {
            originalMessage = getInput(state);
        }
        
        Direction direction = filterChain.executionDirection;
        Direction oppositeDirection = direction.opposite();

        int iterationDirection = direction.getDirection();

        int firstFilterIndex = DefaultFilterChain.getStartingFilterIndex(
                filterChain, direction);
        int lastFilterIndex = DefaultFilterChain.getStartingFilterIndex(
                filterChain, oppositeDirection);

        Object currentMessage;
        TransformationResult decodeResult = new TransformationResult(
                Status.COMPLETED, originalMessage);

        currentMessage = null;

        currentMessage = originalMessage;
        for (int i = firstFilterIndex;
                i != lastFilterIndex + iterationDirection;
                i += iterationDirection) {

            Filter filter = filterChain.get(i);
            if (filter instanceof CodecFilter) {
                CodecFilter codecFilter = (CodecFilter) filter;
                Transformer decoder =
                        codecFilter.getDecoder();
                if (decoder != null) {
                    decodeResult = decoder.transform(state,
                            currentMessage, null);
                    if (decodeResult.getStatus() == Status.COMPLETED) {
                        currentMessage = decodeResult.getMessage();
                        decoder.release(state);
                    } else {
                        break;
                    }
                }
            }
        }

        lastResult = decodeResult;
        
        return decodeResult;
    }

    public Object getOutput(AttributeStorage state) {
        return output;
    }

    public void setOutput(AttributeStorage state, Object outputTarget) {
        this.output = outputTarget;
    }

    public TransformationResult getLastResult(AttributeStorage state) {
        return lastResult;
    }

    public AttributeHolder getProperties(AttributeStorage state) {
        return null;
    }

    public Object getInput(AttributeStorage state) {
        return input;
    }

    public void setInput(AttributeStorage state, Object input) {
        this.input = input;
    }

    public void hibernate(AttributeStorage state) {
    }

    public void release(AttributeStorage state) {
        lastResult = null;
        input = null;
        output = null;
        filterChain = null;
    }
}
