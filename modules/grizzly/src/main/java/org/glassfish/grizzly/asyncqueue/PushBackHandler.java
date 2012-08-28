/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2012 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.asyncqueue;

import org.glassfish.grizzly.Connection;

/**
 * Callback handler, which will be called by Grizzly {@link org.glassfish.grizzly.Writer}
 * implementation, if message can not be neither written nor added to write queue
 * at the moment due to I/O or memory limitations.
 * User may perform one of the actions proposed by {@link PushBackContext} or
 * implement any other custom processing logic.
 * 
 * @since 2.2
 * 
 * @deprecated push back logic is deprecated.
 * 
 * @author Alexey Stashok
 */
@SuppressWarnings("deprecation")
public interface PushBackHandler {

    /**
     * The method is invoked once message is accepted by
     * {@link org.glassfish.grizzly.Writer}. It means either message was written
     * or scheduled to be written asynchronously.
     * 
     * @param connection {@link Connection}
     * @param message {@link WritableMessage}
     */
    public void onAccept(Connection connection, WritableMessage message);

    /**
     * The method is invoked if message was refused by {@link org.glassfish.grizzly.Writer}
     * due to I/O or memory limitations.
     * At this point user can perform one of the actions proposed by {@link PushBackContext},
     * or implement any custom processing logic.
     * 
     * @param connection {@link Connection}
     * @param message {@link WritableMessage}
     * @param pushBackContext {@link PushBackContext}
     */
    public void onPushBack(Connection connection, WritableMessage message,
            PushBackContext pushBackContext);
    
}
