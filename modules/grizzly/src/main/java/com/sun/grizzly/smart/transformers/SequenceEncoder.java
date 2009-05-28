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
package com.sun.grizzly.smart.transformers;

import java.lang.reflect.Field;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.TransformationException;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.TransformationResult.Status;
import com.sun.grizzly.Transformer;
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.smart.SmartTransformer;
import com.sun.grizzly.smart.annotations.Sequence;
import com.sun.grizzly.attributes.AttributeStorage;

/**
 *
 * @author oleksiys
 */
public abstract class SequenceEncoder<E> extends AbstractSmartMemberEncoder<E> {

    protected Sequence config;
    protected Class componentType;
    protected Transformer componentEncoder;

    /**
     * Gets a current sequence element.
     * @param storage attribute storage.
     * @param sequence sequence object.
     * @return component sequence element.
     */
    protected abstract Object get(AttributeStorage storage, E sequence);

    /**
     * Moves to next sequence element and makes it current.
     * Returns <tt>true</tt>, if there is next elements in sequence,
     * or <tt>false</tt> otherwise.
     * @param storage attribute storage.
     * @param sequence sequence object.
     * @return <tt>true</tt>, if there is next elements in sequence,
     * or <tt>false</tt> otherwise.
     */
    protected abstract boolean next(AttributeStorage storage, E sequence);

    /**
     * Moves to previous sequence element and makes it current.
     * Returns <tt>true</tt>, if there is previous elements in sequence,
     * or <tt>false</tt> otherwise.
     * @param storage attribute storage.
     * @param sequence sequence object.
     * @return <tt>true</tt>, if there is previous elements in sequence,
     * or <tt>false</tt> otherwise.
     */
    protected abstract boolean previous(AttributeStorage storage, E sequence);

    /**
     * Returns the size of the sequence.
     * @param storage attribute storage.
     * @param sequence sequence object.
     * @return the size of the sequence.
     */
    protected abstract int size(AttributeStorage storage, E sequence);
    
    public SequenceEncoder() {
    }

    @Override
    public void initialize(SmartTransformer parentTransformer,
            Field field) {
        super.initialize(parentTransformer, field);

        setComponentType(field.getType().getComponentType());

        Class<? extends Transformer> prefTransformerClass = null;

        Sequence sequence = field.getAnnotation(Sequence.class);
        if (sequence != null) {
            if (sequence.codec() != null &&
                    sequence.codec().encoder() != null) {
                String prefTransformerClassName =
                        sequence.codec().encoder();
                if (prefTransformerClassName != null &&
                        prefTransformerClassName.length() > 0) {
                    try {
                        prefTransformerClass = (Class<? extends Transformer>)
                                Class.forName(prefTransformerClassName);
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
        } else {
            throw new IllegalStateException(
                    "Sequence annotation should be used with array element!");
        }

        Transformer componentTransformer = parentTransformer.createTransformer(
                componentType, prefTransformerClass);

        if (componentTransformer == null) {
            throw new IllegalStateException(
                    "Can not find decoder for type: " + componentType);
        }

        setConfig(sequence);
        setComponentEncoder(componentTransformer);
    }

    public TransformationResult<Buffer> transform(AttributeStorage storage,
            E input, Buffer output) throws TransformationException {
        if (input == null) {
            throw new TransformationException("Input should not be null");
        }

        MemoryManager memoryManager = null;
        boolean isAllocated = false;
        if (output == null) {
            memoryManager = obtainMemoryManager(storage);
            if (memoryManager != null) {
                output = memoryManager.allocate(1024);
                isAllocated = true;
            } else {
                throw new TransformationException("Output Buffer is null and there is no way to allocate one");
            }
        }

        while(next(storage, input)) {
            Object element = get(storage, input);
            TransformationResult<Buffer> result = componentEncoder.transform(
                    storage, element, output);
            Status status = result.getStatus();
            if (status == Status.COMPLETED) {
                componentEncoder.release(storage);
            } else if (status == Status.INCOMPLED) {
                if (isAllocated) {
                    output = memoryManager.reallocate(output,
                            output.capacity() * 2);
                    previous(storage, input);
                } else {
                    saveState(storage, incompletedResult);
                    return incompletedResult;
                }
            } else {
                return result;
            }
        }

        TransformationResult<Buffer> result = new TransformationResult<Buffer>(
                Status.COMPLETED, output.duplicate().flip());
        saveState(storage, result);
        return result;
    }

    public Class getComponentType() {
        return componentType;
    }

    public void setComponentType(Class componentClass) {
        this.componentType = componentClass;
    }

    public Transformer getComponentEncoder() {
        return componentEncoder;
    }

    public void setComponentEncoder(Transformer elementDecoder) {
        this.componentEncoder = elementDecoder;
    }

    public Sequence getConfig() {
        return config;
    }

    public void setConfig(Sequence config) {
        this.config = config;
    }


    /**
     * Save the transformer state.
     * @param storage attribute storage
     */
    protected void saveState(AttributeStorage storage,
            TransformationResult<Buffer> lastResult) {
        lastResultAttribute.set(storage, lastResult);
    }
}
