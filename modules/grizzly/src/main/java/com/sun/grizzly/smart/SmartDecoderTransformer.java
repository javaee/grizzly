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

package com.sun.grizzly.smart;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import com.sun.grizzly.AbstractTransformer;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.TransformationException;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.TransformationResult.Status;
import com.sun.grizzly.Transformer;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.attributes.AttributeHolder;
import com.sun.grizzly.smart.SequenceUnit.InstantiateUnit;
import com.sun.grizzly.smart.SequenceUnit.StepInUnit;
import com.sun.grizzly.smart.SequenceUnit.TransformUnit;
import com.sun.grizzly.smart.annotations.Codec;
import com.sun.grizzly.smart.transformers.ArrayDecoder;
import com.sun.grizzly.smart.transformers.ByteDecoder;
import com.sun.grizzly.smart.transformers.CharDecoder;
import com.sun.grizzly.smart.transformers.DoubleDecoder;
import com.sun.grizzly.smart.transformers.FloatDecoder;
import com.sun.grizzly.smart.transformers.IntegerDecoder;
import com.sun.grizzly.smart.transformers.LongDecoder;
import com.sun.grizzly.smart.transformers.ShortDecoder;
import com.sun.grizzly.smart.transformers.SmartMemberTransformer;
import com.sun.grizzly.smart.transformers.SmartStringDecoder;
import com.sun.grizzly.attributes.AttributeStorage;

/**
 * Smart decoder, which is able to decode a {@link Buffer} to a custom protocol
 * message.
 * 
 * @author Alexey Stashok
 */
public class SmartDecoderTransformer<E> extends AbstractTransformer<Buffer, E>
        implements SmartTransformer<Buffer, E> {
    public static final String MESSAGE_PROCESSING_TREE_ATTR_NAME = "SmartDecoderTransformer.processingTree";

    private Map<Class, Class<? extends Transformer>> predefinedTransformers;

    private Class<E> messageClass;

    private List<SequenceUnit> decodingSequence;

    protected Attribute<List> messageProcessingTreeAttribute;
    protected Attribute<Integer> currentTransformerIdxAttribute;

    public SmartDecoderTransformer(Class<E> messageClass) {
        this.messageClass = messageClass;
        initializePredefinedTransformers();
        decodingSequence = new ArrayList();
        createTransformerSequence(messageClass, decodingSequence);

        currentTransformerIdxAttribute = attributeBuilder.createAttribute(
                "SmartDecoderTransformer.currentTransformerIdx");
        messageProcessingTreeAttribute = attributeBuilder.createAttribute(
                MESSAGE_PROCESSING_TREE_ATTR_NAME);
    }

    @Override
    public String getName() {
        return SmartDecoderTransformer.class.getName();
    }

    public Class<E> getMessageClass() {
        return messageClass;
    }

    @Override
    public TransformationResult<Buffer, E> transformImpl(AttributeStorage storage,
            Buffer input) throws TransformationException {
        int currentElementIndex = 0;

        List processingTree = messageProcessingTreeAttribute.get(storage);

        if (processingTree == null) {
            E rootMessage = newInstance(messageClass);

            processingTree = new ArrayList();
            processingTree.add(rootMessage);
            messageProcessingTreeAttribute.set(storage, processingTree);
            currentElementIndex = 0;
        } else {
            currentElementIndex = currentTransformerIdxAttribute.get(storage);
        }

        while(currentElementIndex < decodingSequence.size()) {
            SequenceUnit sequenceUnit =
                    decodingSequence.get(currentElementIndex);

            switch(sequenceUnit.getType()) {
                case TRANSFORM:
                    TransformUnit transformerUnit =
                            (TransformUnit) sequenceUnit;
                    
                    Transformer transformer = transformerUnit.transformer;
                    TransformationResult result =
                            transformer.transform(storage, input);

                    Status status = result.getStatus();
                    if (status == Status.COMPLETED) {
                        Object object = processingTree.get(
                                processingTree.size() - 1);
                        try {
                            Object transformedObject = result.getMessage();
                            transformerUnit.field.set(object, transformedObject);
                        } catch (Exception e) {
                            throw new TransformationException(e);
                        } finally {
                            transformer.release(storage);
                        }
                        
                        currentElementIndex++;
                    } else if (status == Status.INCOMPLETED) {
                        return saveState(storage, processingTree,
                                currentElementIndex,
                                TransformationResult.<Buffer, E>createIncompletedResult(
                                input, false));
                    } else {
                        transformer.release(storage);
                        return result;
                    }
                    
                    break;
                case INSTANTIATE:
                    InstantiateUnit instantiateUnit =
                            (InstantiateUnit) sequenceUnit;
                    Field field = instantiateUnit.field;
                    Object instValue = newInstance(field.getType());
                    Object parentObject = processingTree.get(
                            processingTree.size() - 1);
                    try {
                        field.set(parentObject, instValue);
                    } catch (Exception e) {
                        throw new TransformationException(e);
                    }
                    currentElementIndex++;
                    break;
                case STEPIN:
                    StepInUnit stepInUnit = (StepInUnit) sequenceUnit;
                    parentObject = processingTree.get(
                            processingTree.size() - 1);
                    Object stepInObject;
                    try {
                        stepInObject = stepInUnit.field.get(parentObject);
                    } catch (Exception e) {
                        throw new TransformationException(e);
                    }
                    processingTree.add(stepInObject);
                    currentElementIndex++;
                    break;
                case STEPOUT:
                    processingTree.remove(processingTree.size() - 1);
                    currentElementIndex++;
            }
        }

        return saveState(storage, processingTree, currentElementIndex,
        TransformationResult.<Buffer, E>createCompletedResult(
                (E) processingTree.get(0), input, false));
    }

    @Override
    public void release(AttributeStorage storage) {
        AttributeHolder holder = storage.getAttributes();
        if (holder != null) {
            messageProcessingTreeAttribute.remove(holder);
            currentTransformerIdxAttribute.remove(holder);
        }

        super.release(storage);
    }

    @Override
    public boolean hasInputRemaining(Buffer input) {
        return input != null && input.hasRemaining();
    }

    public Map<Class, Class<? extends Transformer>> getPredefinedTransformers() {
        return predefinedTransformers;
    }

    @Override
    public Transformer createTransformer(Class fieldType,
            Class<? extends Transformer> prefTransformerClass) {

        if (prefTransformerClass != null) {
            return newInstance(prefTransformerClass);
        }

        Class<? extends Transformer> transformerClass = getTransformer(fieldType);
        if (transformerClass != null) {
            return newInstance(transformerClass);
        }

        return null;
    }

    private void createTransformerSequence(Class messageClass,
            List<SequenceUnit> sequence) {
        
        for(Field field : messageClass.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (Modifier.isTransient(modifiers)) {
                continue;
            }

            Class<? extends Transformer> prefTransformerType = null;
            Codec codec = field.getAnnotation(Codec.class);

            if (codec != null && codec.decoder() != null) {
                String prefTransformerTypeName = codec.decoder();
                if (prefTransformerTypeName != null &&
                        prefTransformerTypeName.length() > 0) {
                    try {
                        prefTransformerType = (Class<? extends Transformer>)
                                Class.forName(prefTransformerTypeName);
                    } catch (ClassNotFoundException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }

            Class<?> type = field.getType();

            Transformer transformer =
                    createTransformer(type, prefTransformerType);

            field.setAccessible(true);
            if (transformer != null) {
                initializeTransformer(transformer, field);
                sequence.add(new SequenceUnit.TransformUnit(transformer, field));
            } else {
                sequence.add(new SequenceUnit.InstantiateUnit(field));
                sequence.add(new SequenceUnit.StepInUnit(field));
                createTransformerSequence(type, sequence);
                sequence.add(new SequenceUnit.StepOutUnit());
            }
        }
    }

    private void initializePredefinedTransformers() {
        predefinedTransformers = new HashMap<Class, Class<? extends Transformer>>();
        predefinedTransformers.put(Byte.class, ByteDecoder.class);
        predefinedTransformers.put(byte.class, ByteDecoder.class);

        predefinedTransformers.put(Short.class, ShortDecoder.class);
        predefinedTransformers.put(short.class, ShortDecoder.class);

        predefinedTransformers.put(Character.class, CharDecoder.class);
        predefinedTransformers.put(char.class, CharDecoder.class);

        predefinedTransformers.put(Integer.class, IntegerDecoder.class);
        predefinedTransformers.put(int.class, IntegerDecoder.class);

        predefinedTransformers.put(Long.class, LongDecoder.class);
        predefinedTransformers.put(long.class, LongDecoder.class);

        predefinedTransformers.put(Float.class, FloatDecoder.class);
        predefinedTransformers.put(float.class, FloatDecoder.class);

        predefinedTransformers.put(Double.class, DoubleDecoder.class);
        predefinedTransformers.put(double.class, DoubleDecoder.class);

        predefinedTransformers.put(String.class, SmartStringDecoder.class);

        predefinedTransformers.put(Array.class, ArrayDecoder.class);
    }

    private TransformationResult<Buffer, E> saveState(AttributeStorage storage,
            List messageProcessingTree, int index,
            TransformationResult<Buffer, E> lastResult) {
        currentTransformerIdxAttribute.set(storage, index);
        messageProcessingTreeAttribute.set(storage, messageProcessingTree);
        return lastResult;
    }
    
    protected Class<? extends Transformer> getTransformer(Class clazz) {
        if (clazz.isArray()) {
            return predefinedTransformers.get(Array.class);
        }

        return predefinedTransformers.get(clazz);
    }
    
    private void initializeTransformer(Transformer transformer, Field field) {

        if (transformer instanceof SmartMemberTransformer) {
            ((SmartMemberTransformer) transformer).initialize(this, field);
        }
    }

    private static <E> E newInstance(Class<E> clazz) {
        try {
            E instance = clazz.newInstance();
            return instance;
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Can not initialize class: " + clazz.getName(), e);
        }
    }
}
