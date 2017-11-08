/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2017 Oracle and/or its affiliates. All rights reserved.
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
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Handler, responsible for processing Expect: header in a HTTP requests,
 * for example "Expect: 100-Continue"
 * 
 * @author Alexey Stashok
 */
public interface ExpectationHandler {
    /**
     * Method is getting called by framework if HTTP request contains "Expect"
     * header.
     * Depending on request information, implementation may decide to accept
     * or refuse the HTTP message payload, using passed {@link AckAction}.
     * Use {@link AckAction#acknowledge()} to confirm expectation, or
     * {@link AckAction#fail()} to refuse it.
     * 
     * @param request {@link HttpServletRequest}
     * @param response {@link HttpServletRequest}
     * @param action {@link AckAction}.
     * 
     * @throws Exception 
     */
    void onExpectAcknowledgement(final HttpServletRequest request,
                                 final HttpServletResponse response, final AckAction action)
            throws Exception;
    
    /**
     * Interface, using which {@link ExpectationHandler} may confirm or refuse
     * client expectation.
     */
    interface AckAction {
        /**
         * Acknowledges a client that server wants to receive payload.
         * 
         * @throws IOException 
         */
        void acknowledge() throws IOException;
        
        /**
         * Notifies a client that server doesn't want to process HTTP message
         * payload.
         * 
         * @throws IOException 
         */
        void fail() throws IOException;
    }
}
