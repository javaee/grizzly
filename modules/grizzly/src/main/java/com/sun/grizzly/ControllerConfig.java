/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2010 Oracle and/or its affiliates. All rights reserved.
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

/**
 * Simple configuration helper to configure a {@link Controller} using 
 * system properties.
 * 
 * @author Jeanfrancois Arcand
 */
class ControllerConfig{

    /**
     * Disable the leader/follower strategy used when accepting new requests.
     * @deprecated - Use USE_LEADER_FOLLOWER
     */
    public final static String LEADER_FOLLOWER
            = "com.sun.grizzly.disableLeaderFollower";

    /**
     * Enable/Disable the leader/follower strategy when accepting requests.
     * Default is false.
     */
    public final static String USE_LEADER_FOLLOWER
            = "com.sun.grizzly.useLeaderFollower";

    /**
     * Auto Configure the number of {@link ReadController} based on the OS
     * code. Auto Configure the number of threads based on the core as well.
     * Default is false.
     */
    public final static String AUTO_CONFIGURE
            = "com.sun.grizzly.autoConfigure";

    /**
     * Use the selector thread to execute an I/O operations (closing a connection),
     * or execute a task in the current thread
     */
    public final static String PENDING_IO_STRATEGY
            = "com.sun.grizzly.executePendingIOUsingSelectorThread";

    /**
     * Use the current thread ot finish an I/O operations (closing a connection)
     * or queue the task to be executed by an {@link ExecutorService}
     */
    public final static String PENDING_IO_STRATEGY_OLD
            = "com.sun.grizzly.finishIOUsingCurrentThread";

    /**
     * How many pending I/O a Thread can handles before rejecting some.
     */
    public final static String MAX_PENDING_IO_PER_THREAD
            = "com.sun.grizzly.pendingIOlimitPerThread";

    /**
     * Define the maximum  number of failed accept() operations before rejecting
     * a connection.
     * Default is 5.
     */
    public final static String MAX_ACCEPT_RETRIES
            = "com.sun.grizzly.maxAcceptRetries";

    /**
     * Display the internal configuration of a {@link Controller}.
     * Default is false.
     */
    public final static String DISPLAY_CONFIGURATION
            = "com.sun.grizzly.displayConfiguration";

    /**
     * Configure the {@link Controller}
     */
    void configure(Controller c){

        c.setAutoConfigure(Boolean.getBoolean(AUTO_CONFIGURE));
        c.useLeaderFollowerStrategy(Boolean.getBoolean(USE_LEADER_FOLLOWER));

        // Avoid overriding the default with false
        if (System.getProperty(PENDING_IO_STRATEGY) != null){
            c.setExecutePendingIOUsingSelectorThread(Boolean.getBoolean(PENDING_IO_STRATEGY));
        }

        if (System.getProperty(PENDING_IO_STRATEGY_OLD) != null) {
            Controller.logger.fine("Property " + PENDING_IO_STRATEGY_OLD + " is not longer supported. Please use: " + PENDING_IO_STRATEGY);
        }

        if (System.getProperty(MAX_PENDING_IO_PER_THREAD) != null) {
            Controller.logger.fine("Property " + MAX_PENDING_IO_PER_THREAD + " is not longer supported.");
        }

        c.setMaxAcceptRetries(Integer.getInteger(MAX_ACCEPT_RETRIES, 5));

        c.setDisplayConfiguration(Boolean.getBoolean(DISPLAY_CONFIGURATION));
    }


}
