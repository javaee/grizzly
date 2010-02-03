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
import com.sun.grizzly.memory.MemoryManager;
import com.sun.grizzly.smart.SequenceUnit.StepInUnit;
import com.sun.grizzly.smart.SequenceUnit.TransformUnit;
import com.sun.grizzly.smart.annotations.Codec;
import com.sun.grizzly.smart.transformers.ArrayEncoder;
import com.sun.grizzly.smart.transformers.ByteEncoder;
import com.sun.grizzly.smart.transformers.CharEncoder;
import com.sun.grizzly.smart.transformers.DoubleEncoder;
import com.sun.grizzly.smart.transformers.FloatEncoder;
import com.sun.grizzly.smart.transformers.IntegerEncoder;
import com.sun.grizzly.smart.transformers.LongEncoder;
import com.sun.grizzly.smart.transformers.ShortEncoder;
import com.sun.grizzly.smart.transformers.SmartMemberTransformer;
import com.sun.grizzly.smart.transformers.SmartStringEncoder;
import com.sun.grizzly.attributes.AttributeStorage;

/**
 * Smart encoder, which is able to encoder a custom protocol message to a
 * {@link Buffer}.
 *
 * @author Alexey Stashok
 */
public class SmartEncoderTransformer<E> extends AbstractTransformer<E, Buffer>
        implements SmartTransformer<E, Buffer> {

    public static final String MESSAGE_PROCESSING_TREE_ATTR_NAME = "SmartEncoderTransformer.processingTree";
    private Map<Class, Class<? extends Transformer>> predefinedTransformers;
    private Class<E> messageClass;
    private List<SequenceUnit> encodingSequence;
    protected Attribute<List> messageProcessingTreeAttribute;
    protected Attribute<Integer> currentTransformerIdxAttribute;

    public SmartEncoderTransformer(Class<E> messageClass) {
        this.messageClass = messageClass;
        initializePredefinedTransformers();
        encodingSequence = new ArrayList();
        createTransformerSequence(messageClass, encodingSequence);

        currentTransformerIdxAttribute = attributeBuilder.createAttribute(
                "SmartDecoderTransformer.currentTransformerIdx");
        messageProcessingTreeAttribute = attributeBuilder.createAttribute(
                MESSAGE_PROCESSING_TREE_ATTR_NAME);
    }

    @Override
    public String getName() {
        return SmartEncoderTransformer.class.getName();
    }

    public Class<E> getMessageClass() {
        return messageClass;
    }

    @Override
    public TransformationResult<E, Buffer> transform(AttributeStorage storage,
            E input) throws TransformationException {


        final MemoryManager memoryManager = obtainMemoryManager(storage);
        Buffer output = memoryManager.allocate(1024);

        int currentElementIndex = 0;

        List processingTree = messageProcessingTreeAttribute.get(storage);

        if (processingTree == null) {
            processingTree = new ArrayList();
            processingTree.add(input);
            messageProcessingTreeAttribute.set(storage, processingTree);
            currentElementIndex = 0;
        } else {
            currentElementIndex = currentTransformerIdxAttribute.get(storage);
        }

        while(currentElementIndex < encodingSequence.size()) {
            SequenceUnit sequenceUnit =
                    encodingSequence.get(currentElementIndex);

            switch(sequenceUnit.getType()) {
                case TRANSFORM:
                    final TransformUnit transformerUnit =
                            (TransformUnit) sequenceUnit;

                    final Transformer transformer = transformerUnit.transformer;

                    Object processingObject =
                            processingTree.get(processingTree.size() - 1);
                    Object fieldValue;
                    try {
                        fieldValue = transformerUnit.field.get(processingObject);
                    } catch (Exception e) {
                        throw new TransformationException(e);
                    }

                    final TransformationResult result =
                            transformer.transform(storage, fieldValue);

                    Status status = result.getStatus();
                    if (status == Status.COMPLETED) {
                        transformer.release(storage);
                        currentElementIndex++;
                    } else if (status == Status.INCOMPLETED) {
                        output = memoryManager.reallocate(output,
                                output.capacity() * 2);
                        continue;
                    } else {
                        transformer.release(storage);
                        return result;
                    }

                    break;
                case STEPIN:
                    StepInUnit stepInUnit = (StepInUnit) sequenceUnit;
                    Object parentObject = processingTree.get(
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

        output.flip();
        
        return saveStatus(storage, processingTree, currentElementIndex,
                TransformationResult.<E, Buffer>createCompletedResult(
                                output, null, false));
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
    public boolean hasInputRemaining(E input) {
        return input != null;
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

            if (codec != null && codec.encoder() != null) {
                String prefTransformerTypeName = codec.encoder();
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
                sequence.add(new SequenceUnit.StepInUnit(field));
                createTransformerSequence(type, sequence);
                sequence.add(new SequenceUnit.StepOutUnit());
            }
        }
    }

    private void initializePredefinedTransformers() {
        predefinedTransformers = new HashMap<Class, Class<? extends Transformer>>();
        predefinedTransformers.put(Byte.class, ByteEncoder.class);
        predefinedTransformers.put(byte.class, ByteEncoder.class);

        predefinedTransformers.put(Short.class, ShortEncoder.class);
        predefinedTransformers.put(short.class, ShortEncoder.class);

        predefinedTransformers.put(Character.class, CharEncoder.class);
        predefinedTransformers.put(char.class, CharEncoder.class);

        predefinedTransformers.put(Integer.class, IntegerEncoder.class);
        predefinedTransformers.put(int.class, IntegerEncoder.class);

        predefinedTransformers.put(Long.class, LongEncoder.class);
        predefinedTransformers.put(long.class, LongEncoder.class);

        predefinedTransformers.put(Float.class, FloatEncoder.class);
        predefinedTransformers.put(float.class, FloatEncoder.class);

        predefinedTransformers.put(Double.class, DoubleEncoder.class);
        predefinedTransformers.put(double.class, DoubleEncoder.class);

        predefinedTransformers.put(String.class, SmartStringEncoder.class);

        predefinedTransformers.put(Array.class, ArrayEncoder.class);
    }

    private TransformationResult<E, Buffer> saveStatus(AttributeStorage storage,
            List messageProcessingTree, int index,
            TransformationResult<E, Buffer> lastResult) {
        currentTransformerIdxAttribute.set(storage, index);
        messageProcessingTreeAttribute.set(storage, messageProcessingTree);
        return saveLastResult(storage, lastResult);
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
