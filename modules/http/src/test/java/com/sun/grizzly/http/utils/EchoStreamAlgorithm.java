/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the License).  You may not use this file except in
 * compliance with the License.
 *
 * You can obtain a copy of the license at
 * https://glassfish.dev.java.net/public/CDDLv1.0.html or
 * glassfish/bootstrap/legal/CDDLv1.0.txt.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * Header Notice in each file and include the License file
 * at glassfish/bootstrap/legal/CDDLv1.0.txt.
 * If applicable, add the following below the CDDL Header,
 * with the fields enclosed by brackets [] replaced by
 * you own identifying information:
 * "Portions Copyrighted [year] [name of copyright owner]"
 *
 * Copyright 2006 Sun Microsystems, Inc. All rights reserved.
 */

package com.sun.grizzly.http.utils;

import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.standalone.StaticStreamAlgorithm;
import com.sun.grizzly.util.OutputWriter;
import com.sun.grizzly.util.SSLOutputWriter;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.logging.Level;

/**
 * Simple echo StreamAlgorithm
 *
 * @author Alexey Stashok
 */
public class EchoStreamAlgorithm extends StaticStreamAlgorithm {

    private boolean isSecured;
    
    public EchoStreamAlgorithm() {
    }
    
    public EchoStreamAlgorithm(boolean isSecured) {
        this.isSecured = isSecured;
    }
    
    @Override
    public boolean parse(ByteBuffer buffer) {
        buffer.flip();
        if (buffer.hasRemaining()) {
            // Store incoming data in byte[]
            byte[] data = new byte[buffer.remaining()];
            int position = buffer.position();
            buffer.get(data);
            buffer.position(position);
            try {
                if (isSecured) {
                    SSLOutputWriter.flushChannel(socketChannel, buffer);
                } else {
                    OutputWriter.flushChannel(socketChannel, buffer);
                }
            } catch (IOException e) {
                SelectorThread.logger().log(Level.WARNING, 
                        "Exception. Echo \"" + new String(data) + "\"", e);
            }
        }

        buffer.clear();
        return false;
    }
}
