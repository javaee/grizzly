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
package com.sun.grizzly.utils;

import java.io.IOException;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.Transformer;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.attributes.AttributeBuilder;
import com.sun.grizzly.attributes.AttributeHolder;
import com.sun.grizzly.filterchain.FilterAdapter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.TransformationResult;
import com.sun.grizzly.TransformationResult.Status;
import com.sun.grizzly.filterchain.CodecFilter;
import com.sun.grizzly.memory.MemoryManager;

/**
 *
 * @author Alexey Stashok
 */
public class UTFStringFilter extends FilterAdapter implements CodecFilter {

    private AttributeBuilder attributeBuilder;
    private Attribute<Buffer> remainderAttribute;
    
    private MemoryManager<Buffer> memoryManager;
    private Transformer<Buffer, String> decoder;
    private Transformer<String, Buffer> encoder;

    public UTFStringFilter(MemoryManager<Buffer> memoryManager) {
        this(memoryManager, Grizzly.DEFAULT_ATTRIBUTE_BUILDER);
    }

    public UTFStringFilter(MemoryManager<Buffer> memoryManager,
            AttributeBuilder attributeBuilder) {
        this.memoryManager = memoryManager;
        this.attributeBuilder = attributeBuilder;
        remainderAttribute = attributeBuilder.createAttribute(
                "UTFStringFilter.remainder");
        decoder = new StringDecoder();
        encoder = new StringEncoder();
    }

    public Transformer getDecoder() {
        return decoder;
    }

    public Transformer getEncoder() {
        return encoder;
    }

    @Override
    public NextAction handleRead(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        TransformationResult result;
        Buffer message = (Buffer) ctx.getMessage();
        Connection connection = ctx.getConnection();
        
        result = decoder.transform(connection, message, null);

        if (result.getStatus() == Status.COMPLETED) {
            decoder.release(connection);
            ctx.setMessage(result.getMessage());

            // Important. If message has remaining - we will need to
            // reinvoke the chain in postExecute phase
            if (message.hasRemaining()) {
                remainderAttribute.set(ctx.obtainAttributes(), message);
            }
        } else {
            nextAction = ctx.getStopAction();
        }

        return nextAction;
    }

    @Override
    public NextAction postRead(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        AttributeHolder holder = ctx.getAttributes();
        if (holder != null) {
            Buffer remainder = remainderAttribute.remove(holder);
            if (remainder != null) {
                ctx.setMessage(remainder);
                nextAction = ctx.getRerunChainAction();
            }
        }

        return nextAction;
    }


    @Override
    public NextAction handleWrite(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        TransformationResult result;
        Connection connection = ctx.getConnection();

        result = encoder.transform(connection, (String) ctx.getMessage(), null);

        ctx.setMessage(result.getMessage());
        encoder.release(connection);
        return nextAction;
    }
}
