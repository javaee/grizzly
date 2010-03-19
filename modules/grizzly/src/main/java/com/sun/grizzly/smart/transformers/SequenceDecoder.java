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
package com.sun.grizzly.smart.transformers;

import java.lang.reflect.Field;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.TransformationException;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.TransformationResult.Status;
import com.sun.grizzly.Transformer;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.smart.Reference;
import com.sun.grizzly.smart.SmartTransformer;
import com.sun.grizzly.smart.Value;
import com.sun.grizzly.smart.annotations.Sequence;
import com.sun.grizzly.attributes.AttributeStorage;

/**
 *
 * @author oleksiys
 */
public abstract class SequenceDecoder<E> extends AbstractSmartMemberDecoder<E> {

    protected Sequence config;
    protected Class componentType;
    protected Transformer componentDecoder;

    protected Attribute<E> sequenceAttribute;

    private Value<Number> size;
    private Value<Number> limit;

    /**
     * Creates a sequence object with certain size.
     * @param storage attribute storage
     * @param size the sequence size
     */
    protected abstract E createSequence(AttributeStorage storage, int size);

    /**
     * Stores an element into the sequence.
     * @param storage attribute storage.
     * @param sequence sequence object.
     * @param component sequence element.
     */
    protected abstract void set(AttributeStorage storage, E sequence,
            Object component);

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
     * Returns the size of the sequence.
     * @param storage attribute storage.
     * @param sequence sequence object.
     * @return the size of the sequence.
     */
    protected abstract int size(AttributeStorage storage, E sequence);
    
    public SequenceDecoder() {
        String prefix = getClass().getName();
        sequenceAttribute = attributeBuilder.createAttribute(prefix + ".sequence");
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
                    sequence.codec().decoder() != null) {
                String prefTransformerClassName =
                        sequence.codec().decoder();
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
        setComponentDecoder(componentTransformer);
    }

    @Override
    protected TransformationResult<Buffer, E> transformImpl(
            AttributeStorage storage, Buffer input)
            throws TransformationException {
        
        if (input == null) {
            throw new TransformationException("Input should not be null");
        }

        E sequence = getSequence(storage);
        if (sequence == null) {
            int annotatedSize = checkSize(storage);
            sequence = createSequence(storage, annotatedSize);
        }

        while(next(storage, sequence)) {
            TransformationResult<Buffer, E> result = componentDecoder.transform(
                    storage, input);
            Status status = result.getStatus();
            if (status == Status.COMPLETED) {
                componentDecoder.release(storage);
                set(storage, sequence, result.getMessage());
            } else if (status == Status.INCOMPLETED) {
                return saveState(storage, sequence,
                        TransformationResult.<Buffer,E>createIncompletedResult(
                        input));
            } else {
                return result;
            }
        }

        return saveState(storage, sequence,
                TransformationResult.<Buffer, E>createCompletedResult(
                sequence, input));
    }

    @Override
    public void release(AttributeStorage storage) {
        sequenceAttribute.remove(storage);
        super.release(storage);
    }

    @Override
    public boolean hasInputRemaining(AttributeStorage storage, Buffer input) {
        return input != null && input.hasRemaining();
    }

    public Class getComponentType() {
        return componentType;
    }

    public void setComponentType(Class componentClass) {
        this.componentType = componentClass;
    }

    public Transformer getComponentDecoder() {
        return componentDecoder;
    }

    public void setComponentDecoder(Transformer elementDecoder) {
        this.componentDecoder = elementDecoder;
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
    protected TransformationResult<Buffer, E> saveState(
            AttributeStorage storage,
            E sequence, TransformationResult<Buffer, E> lastResult) {
        sequenceAttribute.set(storage, sequence);
        return lastResult;
    }

    /**
     * Returns the processing sequence object
     * @param storage attribute storage
     * @return the processing sequence object
     */
    protected E getSequence(AttributeStorage storage) {
        return sequenceAttribute.get(storage);
    }

    protected int checkSize(AttributeStorage storage) {
        int annotatedSize = getAnnotatedSize(storage);
        if (annotatedSize >= 0) {
            int annotatedLimit = getAnnotatedLimit(storage);
            if (annotatedLimit > -1) {
                if (annotatedSize > annotatedLimit) {
                    throw new TransformationException("Size " + annotatedSize +
                            " is bigger than limit " + annotatedLimit);
                }
            }
        } else {
            throw new TransformationException("The array size is not defined");
        }

        return annotatedSize;
    }

    protected int getAnnotatedSize(AttributeStorage storage) {
        Object message = null;
        if (size == null) {
            String configSize = config.size();
            if (configSize == null) {
                return -1;
            }

            try {
                int value = Integer.parseInt(configSize);
                size = new Value<Number>(value);
            } catch (NumberFormatException e) {
                message = getCurrentMessageProcessingObject(storage);
                size = new Value(new Reference(message.getClass(), configSize));
            }
        }

        Integer directValue = (Integer) size.get();
        if (directValue != null) {
            return directValue;
        } else {
            try {
                if (message == null) {
                    message = getCurrentMessageProcessingObject(storage);
                }

                return size.getByReference(message).intValue();
            } catch (Exception e) {
                return -1;
            }
        }

    }

    protected int getAnnotatedLimit(AttributeStorage storage) {
        Object message = null;
        if (limit == null) {
            String configLimit = config.limit();
            if (configLimit == null) {
                return -1;
            }

            try {
                int value = Integer.parseInt(configLimit);
                limit = new Value<Number>(value);
            } catch (NumberFormatException e) {
                message = getCurrentMessageProcessingObject(storage);
                limit = new Value(new Reference(message.getClass(), configLimit));
            }
        }

        Integer directValue = (Integer) limit.get();
        if (directValue != null) {
            return directValue;
        } else {
            try {
                if (message == null) {
                    message = getCurrentMessageProcessingObject(storage);
                }
                
                return limit.getByReference(message).intValue();
            } catch (Exception e) {
                return -1;
            }
        }
    }
}
