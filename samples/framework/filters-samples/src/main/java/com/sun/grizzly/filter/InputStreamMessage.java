/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.filter;

import java.nio.ByteBuffer;
import java.io.InputStream;


/**
 * Gives  an Message an {@link RemoteInputStream} so that clients can retrieve the Message Payload
 * by getting this Message InputStream.
 *
 * When parsing a Message {@link com.sun.grizzly.filter.CustomProtocolParser} will by calling
 * {@link #addByteBuffer} to  add  Payload to this Message. If this Message is expecting
 * further Payload bytes {@link com.sun.grizzly.filter.MessageDispatcher} will keep on adding Fragments until
 * End-Fragment is read in.
 * When this Message has all its bytes read in {@link #allDataParsed} will be called to signal EOF to the Inputstream.
 *
 * @author John Vieten 27.06.2008
 * @version 1.0
 */
public  class InputStreamMessage extends MessageBase {


  private RemoteInputStream inputRemoteStream=new RemoteInputStream();




    /**
     * Adds bytes to this message. These bytes should only consist of payload bytes.
     * Therefore any  Header Protocol bytes should have been stripped from the bytebuffer to add.
     *
     * @param byteBuffer add payload
     */
    void addByteBuffer(ByteBuffer byteBuffer) {
        super.addByteBuffer(byteBuffer);

        inputRemoteStream.add(byteBuffer);
    }

    /**
     * Adds Payload bytes of an Fragment to this Message.
     *
     * @param fragment holding part of payload
     */
    void add(FragmentMessage fragment) {

        inputRemoteStream.add(fragment.getByteBufferList());
    }

    /**
     * Marks the end of an Inputstream. This method must be called otherwise
     * Client may keep on waiting on  {@link RemoteInputStream}
     */
    void allDataParsed() {
        if (inputRemoteStream != null) inputRemoteStream.close();
    }

     public InputStream getInputStream() {
        return inputRemoteStream;
    }

}
