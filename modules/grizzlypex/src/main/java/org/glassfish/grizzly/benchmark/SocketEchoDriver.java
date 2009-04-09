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

package org.glassfish.grizzly.benchmark;

import com.sun.japex.TestCase;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 *
 * @author oleksiys
 */
public class SocketEchoDriver extends EchoDriverBase {
    private byte[] buffer;
    private Socket socket;

    private InputStream is;
    private OutputStream os;

    private byte counter;
    @Override
    public void prepare(TestCase testCase) {
        super.prepare(testCase);
        try {
            socket = new Socket(getHost(testCase), getPort(testCase));
            is = socket.getInputStream();
            os = socket.getOutputStream();
        } catch (IOException e) {
            finish(testCase);
            throw new IllegalStateException(e);
        }
        
        int bufferSize = getMessageSize(testCase);
        if (bufferSize <= 0) {
            throw new IllegalStateException("Buffer size should be >= 0");
        }
        buffer = new byte[bufferSize];
    }

    @Override
    public void run(TestCase testCase) {
        buffer[0] = counter++;
        try {
            os.write(buffer);
            os.flush();
            int readNum = 0;
            while(readNum < buffer.length) {
                int count = is.read(buffer, readNum, buffer.length - readNum);
                if (count == -1) throw new EOFException();
                readNum += count;
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void finish(TestCase testCase) {
        if (is != null) {
            try {
                is.close();
            } catch (IOException e) {
            }
            is = null;
        }

        if (os != null) {
            try {
                os.close();
            } catch (IOException e) {
            }
            os = null;
        }

        if (socket != null) {
            try {
                socket.close();
            } catch (IOException e) {
            }
            socket = null;
        }
        super.finish(testCase);
    }
}
