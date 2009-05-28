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

package com.sun.grizzly.samples.filterchain;

import java.io.IOException;
import com.sun.grizzly.filterchain.FilterAdapter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import com.sun.grizzly.streams.StreamWriter;

/**
 * {@link Filter}, which writes back the passed {@link GIOPMessage}
 * 
 * @author Alexey Stashok
 */
public class EchoGIOPMessageFilter extends FilterAdapter {

    @Override
    public NextAction handleRead(FilterChainContext ctx, NextAction nextAction)
            throws IOException {
        GIOPMessage message = (GIOPMessage) ctx.getMessage();
        StreamWriter writer = ctx.getStreamWriter();

        // GIOP header
        writer.writeByteArray(message.getGIOPHeader());

        // Major version
        writer.writeByte(message.getMajor());

        // Minor version
        writer.writeByte(message.getMinor());

        // Flags
        writer.writeByte(message.getFlags());

        // Value
        writer.writeByte(message.getValue());

        // Body length
        writer.writeInt(message.getBodyLength());

        // Body
        writer.writeByteArray(message.getBody());

        // Flush the message
        writer.flush();

        return nextAction;
    }
}
