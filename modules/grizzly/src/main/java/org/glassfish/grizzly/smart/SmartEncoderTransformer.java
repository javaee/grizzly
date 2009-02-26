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
package org.glassfish.grizzly.smart;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.glassfish.grizzly.AbstractTransformer;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.TransformationException;
import org.glassfish.grizzly.TransformationResult;
import org.glassfish.grizzly.TransformationResult.Status;
import org.glassfish.grizzly.Transformer;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeHolder;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.smart.SequenceUnit.StepInUnit;
import org.glassfish.grizzly.smart.SequenceUnit.TransformUnit;
import org.glassfish.grizzly.smart.annotations.Codec;
import org.glassfish.grizzly.smart.transformers.ArrayEncoder;
import org.glassfish.grizzly.smart.transformers.ByteEncoder;
import org.glassfish.grizzly.smart.transformers.CharEncoder;
import org.glassfish.grizzly.smart.transformers.DoubleEncoder;
import org.glassfish.grizzly.smart.transformers.FloatEncoder;
import org.glassfish.grizzly.smart.transformers.IntegerEncoder;
import org.glassfish.grizzly.smart.transformers.LongEncoder;
import org.glassfish.grizzly.smart.transformers.ShortEncoder;
import org.glassfish.grizzly.smart.transformers.SmartMemberTransformer;
import org.glassfish.grizzly.smart.transformers.SmartStringEncoder;
import org.glassfish.grizzly.util.AttributeStorage;

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

    public Class<E> getMessageClass() {
        return messageClass;
    }

    public TransformationResult<Buffer> transform(AttributeStorage storage,
            E input, Buffer output) throws TransformationException {

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

        int currentElementIndex = 0;

        List processingTree = getValue(storage, messageProcessingTreeAttribute);

        if (processingTree == null) {
            processingTree = new ArrayList();
            processingTree.add(input);
            messageProcessingTreeAttribute.set(storage.obtainAttributes(),
                    processingTree);
            currentElementIndex = 0;
        } else {
            currentElementIndex = getValue(storage, currentTransformerIdxAttribute);
        }

        while(currentElementIndex < encodingSequence.size()) {
            SequenceUnit sequenceUnit =
                    encodingSequence.get(currentElementIndex);

            switch(sequenceUnit.getType()) {
                case TRANSFORM:
                    TransformUnit transformerUnit =
                            (TransformUnit) sequenceUnit;

                    Transformer transformer = transformerUnit.transformer;

                    Object processingObject =
                            processingTree.get(processingTree.size() - 1);
                    Object fieldValue;
                    try {
                        fieldValue = transformerUnit.field.get(processingObject);
                    } catch (Exception e) {
                        throw new TransformationException(e);
                    }

                    TransformationResult result =
                            transformer.transform(storage, fieldValue, output);

                    Status status = result.getStatus();
                    if (status == Status.COMPLETED) {
                        transformer.release(storage);
                        currentElementIndex++;
                    } else if (status == Status.INCOMPLED) {
                        if (isAllocated) {
                            output = memoryManager.reallocate(output,
                                    output.capacity() * 2);
                            continue;
                        }

                        saveStatus(storage, processingTree, currentElementIndex,
                                incompletedResult);
                        return incompletedResult;
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

        TransformationResult<Buffer> result = new TransformationResult<Buffer>(
                Status.COMPLETED, output.duplicate().flip());
        saveStatus(storage, processingTree, currentElementIndex, result);
        return result;
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

    public Map<Class, Class<? extends Transformer>> getPredefinedTransformers() {
        return predefinedTransformers;
    }

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

    private void saveStatus(AttributeStorage storage,
            List messageProcessingTree, int index,
            TransformationResult<Buffer> lastResult) {
        setValue(storage, currentTransformerIdxAttribute, index);
        setValue(storage, messageProcessingTreeAttribute, messageProcessingTree);
        setValue(storage, lastResultAttribute, lastResult);
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
