/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly;

import java.io.IOException;

/**
 * Simple Life cycle interface used to manage Grizzly component.
 *
 * @author Jeanfrancois Arcand
 */
public interface Lifecycle {
    
    /**
     * Start the Lifecycle.  This is the interface where an object that
     * implements Lifecycle will start the object and begin its processing.
     * @throws java.io.IOException 
     */
    public void start() throws IOException;
    
    /**
     * Stops the Lifecycle.  This is the interface where an object that
     * implements Lifecycle will stop the object's processing and perform
     * any additional cleanup before it shutdown.
     * @throws java.io.IOException 
     */
    public void stop() throws IOException;
    
    /**
     * Pause this Lifecycle. This is the interface where an object that
     * implements Lifecycle will pause the object's processing.  Processing
     * may be resumed via the resume() interface or stopped via the stop()
     * interface after this interface has been called. Common uses for pause()
     * and resume() will be to support use cases such as reconfiguration.
     * @throws java.io.IOException 
     */
    public void pause() throws IOException;
    
    /**
     * Resume this Lifecycle.  This is the interface where an object that
     * implements Lifecycle will resume a paused object's processing. When
     * called processing will resume. Common uses for pause() and resume()
     * will be to support use cases such as reconfiguration.
     * @throws java.io.IOException 
     */
    public void resume() throws IOException;
    
}
