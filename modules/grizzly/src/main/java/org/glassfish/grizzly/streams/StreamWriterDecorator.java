/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright 1997-2007 Sun Microsystems, Inc. All rights reserved.
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
 */

package org.glassfish.grizzly.streams;

import java.io.IOException;
import java.util.concurrent.Future;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.Connection;

/**
 *
 * @author oleksiys
 */
public abstract class StreamWriterDecorator extends AbstractStreamWriter {

    protected StreamWriter underlyingWriter;
    
    public StreamWriterDecorator(StreamWriter underlyingWriter) {
        setUnderlyingWriter(underlyingWriter);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Connection getConnection() {
        return underlyingWriter.getConnection();
    }

    public StreamWriter getUnderlyingWriter() {
        return underlyingWriter;
    }

    public void setUnderlyingWriter(StreamWriter underlyingWriter) {
        this.underlyingWriter = underlyingWriter;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isBlocking() {
        return underlyingWriter.isBlocking();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setBlocking(boolean isBlocking) {
        underlyingWriter.setBlocking(isBlocking);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected Future close0(CompletionHandler completionHandler) throws IOException {
        flush();
        return underlyingWriter.close(completionHandler);
    }
}
