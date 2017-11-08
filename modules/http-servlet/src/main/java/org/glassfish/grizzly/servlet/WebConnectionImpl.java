/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2017 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://oss.oracle.com/licenses/CDDL+GPL-1.1
 * or LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at LICENSE.txt.
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

package org.glassfish.grizzly.servlet;

import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.servlet.ServletInputStream;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpUpgradeHandler;
import javax.servlet.http.WebConnection;
import org.glassfish.grizzly.http.server.Request;

/**
 * Implementation of WebConnection for Servlet 3.1
 *
 * @author Amy Roh
 * @author Shing Wai Chan
 * @version $Revision: 1.23 $ $Date: 2007/07/09 20:46:45 $
 */
public class WebConnectionImpl implements WebConnection {

    private final ServletInputStream inputStream;

    private final ServletOutputStream outputStream;

    private final HttpServletRequestImpl request;

    private final AtomicBoolean isClosed = new AtomicBoolean();
    
    // ----------------------------------------------------------- Constructor

    public WebConnectionImpl(HttpServletRequestImpl request,
            ServletInputStream inputStream, ServletOutputStream outputStream) {
        this.request = request;
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    /**
     * Returns an input stream for this web connection.
     *
     * @return a ServletInputStream for reading binary data
     *
     * @exception java.io.IOException if an I/O error occurs
     */
    @Override
    public ServletInputStream getInputStream() throws IOException {
        return inputStream;
    }

    /**
     * Returns an output stream for this web connection.
     *
     * @return a ServletOutputStream for writing binary data
     *
     * @exception IOException if an I/O error occurs
     */
    @Override
    public ServletOutputStream getOutputStream() throws IOException{
        return outputStream;
    }

    @Override
    public void close() throws Exception {
        // Make sure we run close logic only once
        if (isClosed.compareAndSet(false, true)) {
            final Request grizzlyRequest = request.getRequest();

            HttpUpgradeHandler httpUpgradeHandler =
                    request.getHttpUpgradeHandler();
            try {
                httpUpgradeHandler.destroy();
            } finally {
                try {
                    inputStream.close();
                } catch(Exception ignored) {
                }
                try {
                    outputStream.close();
                } catch(Exception ignored) {
                }

                grizzlyRequest.getResponse().resume();
            }
        }
    }
}
