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
import java.util.List;
import java.util.ArrayList;

/**
 * Since the Custom Protocol is a fixed sized Protocol
 * when the size of an Message is larger than {@link com.sun.grizzly.filter.Message#MessageMaxLength}
 * 8192 an Message gets split into Fragments.  The Contract is that the first Message  for Example
 * {@link com.sun.grizzly.filter.RequestMessage} gets its Flag moreFragmentsToFollow set and then
 *  n FragmentMessage Messages follow the last one returning moreFragmentsToFollow()==false.
 *
 *
 * @author John Vieten 22.06.2008
 * @version 1.0
 */
public class FragmentMessage extends MessageBase{
    private List<ByteBuffer> byteBufferList= new ArrayList<ByteBuffer>();
    public void addByteBuffer(ByteBuffer byteBuffer) {
         super.addByteBuffer(byteBuffer);
         byteBufferList.add(byteBuffer);
    }

    public  List<ByteBuffer> getByteBufferList() {
        return byteBufferList;
    }
}
