/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
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
 */
package org.glassfish.grizzly.spdy;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.utils.NullaryFunction;

/**
 *
 * @author oleksiys
 */
public class FramesDecoderFilter extends BaseFilter {
    static final int HEADER_LEN = 8;
    
    private final Attribute<ArrayList<Buffer>> framesAttr =
            AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER.<ArrayList<Buffer>>createAttribute(
            FramesDecoderFilter.class.getName() + "." + hashCode(), new NullaryFunction<ArrayList<Buffer>>() {
        @Override
        public ArrayList<Buffer> evaluate() {
            return new ArrayList<Buffer>(4);
        }
    });

    @Override
    public NextAction handleRead(final FilterChainContext ctx) throws IOException {
        final Buffer message = ctx.getMessage();
        if (message.remaining() < HEADER_LEN) {
            return ctx.getStopAction(message);
        }
        
        int position = message.position();
        
        int len = ((message.get(position + 5) & 0xFF) << 16)
                + ((message.get(position + 6) & 0xFF) << 8)
                + (message.get(position + 7) & 0xFF);
        int totalLen = len + HEADER_LEN;
        
        if (message.remaining() < totalLen) {
            return ctx.getStopAction(message);
        }
        
        if (message.remaining() == totalLen) {
            return ctx.getInvokeAction();
        }
        
        Buffer remainder = message.split(position + totalLen);
        if (remainder.remaining() < HEADER_LEN) {
            return ctx.getInvokeAction(remainder);
        }
        
        final List<Buffer> frameList = framesAttr.get(ctx.getConnection());
        frameList.add(message);
        
        while (remainder.remaining() >= HEADER_LEN) {
            position = remainder.position();
            len = ((remainder.get(position + 5) & 0xFF) << 16)
                    + ((remainder.get(position + 6) & 0xFF) << 8)
                    + (remainder.get(position + 7) & 0xFF);
            totalLen = len + HEADER_LEN;
            
            if (message.remaining() < totalLen) {
                break;
            }
            
            final Buffer remainder2 = remainder.split(position + totalLen);
            frameList.add(remainder);
            remainder = remainder2;
        }
        
        ctx.setMessage(frameList.size() > 1 ? frameList : message);
        
        return ctx.getInvokeAction(
                (remainder != null && remainder.hasRemaining()) ? remainder : null);
    }

    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        return super.handleWrite(ctx);
    }

    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        return super.handleClose(ctx);
    }
}
