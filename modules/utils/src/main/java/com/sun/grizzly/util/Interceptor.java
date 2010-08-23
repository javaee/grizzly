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

package com.sun.grizzly.util;

import java.io.IOException;
import java.nio.channels.Channel;

/**
 * Simple interception hook used to trap events inside Grizzly core.
 *
 * @author Jeanfrancois Arcand
 */
public interface Interceptor<E,F extends Channel>{
 
    /**
     * Continue the processing
     */
    public final static int CONTINUE = 0;

    
    /**
     * Do not continue the processing.
     */
    public final static int BREAK = 1;
     
    
    /**
     * The request header has been parsed.  This includes the request uri
     * as well as any important headers (such as Host).
     */
    public final static int REQUEST_LINE_PARSED = 0;
    
    
    /**
     * The response has been proceeded.
     */
    public final static int RESPONSE_PROCEEDED = 1;  
    
    
    /**
     * The request has been buffered.
     */
    public final static int REQUEST_BUFFERED = 2;

    
    /**
     * Handle <E> and the associated handler code.
     * @param e
     * @param handlerCode 
     * @return 
     * @throws java.io.IOException 
     */
    public int handle(E e, int handlerCode) throws IOException;
    
    
    /**
     * The {@link Channel} associated with this handler.
     * @param channel 
     */
    public void attachChannel(F channel);
    
}
