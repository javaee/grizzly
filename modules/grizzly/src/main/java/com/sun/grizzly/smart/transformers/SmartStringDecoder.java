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

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Field;
import java.nio.charset.Charset;
import java.util.List;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.smart.SmartDecoderTransformer;
import com.sun.grizzly.smart.SmartTransformer;
import com.sun.grizzly.smart.annotations.CharSequence;
import com.sun.grizzly.attributes.AttributeStorage;
import com.sun.grizzly.utils.StringDecoder;

/**
 * String decoder, which decodes {@link Buffer} to {@link String}
 * 
 * @author Alexey Stashok
 */
public class SmartStringDecoder extends StringDecoder
        implements SmartMemberTransformer<Buffer, String> {

    protected Attribute<List> messageProcessingTreeAttribute;

    public SmartStringDecoder() {
        this((String) null);
    }

    public SmartStringDecoder(String stringTerminator) {
        this(Charset.forName("UTF-8"), stringTerminator);
    }

    public SmartStringDecoder(Charset charset) {
        this(charset, null);
    }

    public SmartStringDecoder(Charset charset, String stringTerminator) {
        super(charset, stringTerminator);
        
        messageProcessingTreeAttribute = attributeBuilder.createAttribute(
                SmartDecoderTransformer.MESSAGE_PROCESSING_TREE_ATTR_NAME);

    }

    public void initialize(SmartTransformer parentTransformer,
            Field field) {
        CharSequence charSequenceAnn =
                field.getAnnotation(CharSequence.class);
        if (charSequenceAnn != null) {
            String charsetAnn = charSequenceAnn.charset();
            if (!charsetAnn.equals(charset.name())) {
                charset = Charset.forName(charsetAnn);
            }

            String terminateSeqAnn = charSequenceAnn.terminate();
            if (terminateSeqAnn != null && terminateSeqAnn.length() > 0) {
                try {
                    stringTerminateBytes = terminateSeqAnn.getBytes(charset.name());
                } catch (UnsupportedEncodingException e) {
                    // should never happen as we are getting charset name from Charset
                }
            }
        }
    }

    public List getMessageProcessingTree(AttributeStorage storage) {
        return messageProcessingTreeAttribute.get(storage);
    }

    public Object getCurrentMessageProcessingObject(AttributeStorage storage) {
        List messageProcessingTree = getMessageProcessingTree(storage);
        return messageProcessingTree.get(messageProcessingTree.size() - 1);
    }
}
