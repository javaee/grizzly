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
package com.sun.grizzly.ssl;

import java.io.IOException;
import java.nio.ByteBuffer;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import com.sun.grizzly.Buffer;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.attributes.Attribute;
import com.sun.grizzly.attributes.AttributeStorage;

/**
 * Utility class, which implements the set of useful SSL related operations.
 * 
 * @author Alexey Stashok
 */
public class SSLUtils {
    public static final String SSL_ENGINE_ATTR_NAME = "SSLEngineAttr";

    public static final Attribute<SSLEngine> sslEngineAttribute =
            Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute(SSL_ENGINE_ATTR_NAME);

    public static final SSLEngine getSSLEngine(AttributeStorage storage) {
        return sslEngineAttribute.get(storage);
    }

    public static final void setSSLEngine(AttributeStorage storage,
            SSLEngine sslEngine) {
        sslEngineAttribute.set(storage, sslEngine);
    }
    
    public static SSLEngineResult unwrap(SSLEngine sslEngine,
            Buffer securedInBuffer, Buffer plainBuffer) throws IOException {
        return sslEngine.unwrap((ByteBuffer) securedInBuffer.underlying(),
                (ByteBuffer) plainBuffer.underlying());
    }

    public static SSLEngineResult wrap(SSLEngine sslEngine, Buffer plainBuffer,
            Buffer securedOutBuffer) throws IOException {
        return sslEngine.wrap((ByteBuffer) plainBuffer.underlying(),
                (ByteBuffer) securedOutBuffer.underlying());
    }

    /**
     * Complete hanshakes operations.
     * @param sslEngine The SSLEngine used to manage the SSL operations.
     * @return SSLEngineResult.HandshakeStatus
     */
    public static void executeDelegatedTask(SSLEngine sslEngine) {

        Runnable runnable;
        while ((runnable = sslEngine.getDelegatedTask()) != null) {
            runnable.run();
        }
    }


    public static boolean isHandshaking(SSLEngine sslEngine) {
        HandshakeStatus handshakeStatus = sslEngine.getHandshakeStatus();
        return !(handshakeStatus == HandshakeStatus.FINISHED ||
                handshakeStatus == HandshakeStatus.NOT_HANDSHAKING);
    }


    static void clearOrCompact(Buffer buffer) {
        if (buffer == null) {
            return;
        }

        if (!buffer.hasRemaining()) {
            buffer.clear();
        } else if (buffer.position() > 0) {
            buffer.compact();
        }
    }
}
