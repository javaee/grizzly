/*
 *   DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 *   Copyright 2007-2008 Sun Microsystems, Inc. All rights reserved.
 *
 *   The contents of this file are subject to the terms of either the GNU
 *   General Public License Version 2 only ("GPL") or the Common Development
 *   and Distribution License("CDDL") (collectively, the "License").  You
 *   may not use this file except in compliance with the License. You can obtain
 *   a copy of the License at https://glassfish.dev.java.net/public/CDDL+GPL.html
 *   or glassfish/bootstrap/legal/LICENSE.txt.  See the License for the specific
 *   language governing permissions and limitations under the License.
 *
 *   When distributing the software, include this License Header Notice in each
 *   file and include the License file at glassfish/bootstrap/legal/LICENSE.txt.
 *   Sun designates this particular file as subject to the "Classpath" exception
 *   as provided by Sun in the GPL Version 2 section of the License file that
 *   accompanied this code.  If applicable, add the following below the License
 *   Header, with the fields enclosed by brackets [] replaced by your own
 *   identifying information: "Portions Copyrighted [year]
 *   [name of copyright owner]"
 *
 *   Contributor(s):
 *
 *   If you wish your version of this file to be governed by only the CDDL or
 *   only the GPL Version 2, indicate your decision by adding "[Contributor]
 *   elects to include this software in this distribution under the [CDDL or GPL
 *   Version 2] license."  If you don't indicate a single choice of license, a
 *   recipient has the option to distribute your version of this file under
 *   either the CDDL, the GPL Version 2 or to extend the choice of license to
 *   its licensees as provided above.  However, if you add GPL Version 2 code
 *   and therefore, elected the GPL Version 2 license, then the option applies
 *   only if the new code is made subject to such option by the copyright
 *   holder.
 *
 */
package com.sun.grizzly.config;

import com.sun.grizzly.Controller;
import com.sun.grizzly.config.dom.NetworkConfig;
import com.sun.grizzly.config.dom.NetworkListener;
import com.sun.grizzly.config.dom.Protocol;
import com.sun.grizzly.util.LoggerUtils;
import org.jvnet.hk2.component.Habitat;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Created Nov 24, 2008
 *
 * @author <a href="mailto:justin.lee@sun.com">Justin Lee</a>
 */
public class GrizzlyConfig {
    private static final Logger logger = LoggerUtils.getLogger();

    private final Controller controller = new Controller();
    private final NetworkConfig config;
    private Habitat habitat;

    public GrizzlyConfig(final String file) {
        habitat = Utils.getHabitat(file);
        config = habitat.getComponent(NetworkConfig.class);

    }

    public void setupNetwork() throws IOException, InstantiationException {
        for (final NetworkListener listener : config.getNetworkListeners().getNetworkListener()) {
            final GrizzlyServiceListener grizzlyListener = new GrizzlyServiceListener(controller);
            grizzlyListener.configure(listener, true, habitat);

            final Thread thread = new Thread(new ListenerRunnable(grizzlyListener));
            thread.setDaemon(true);
            thread.start();
        }
        try {
            Thread.sleep(5000); // wait for the system to finish settin up the listener
        } catch (InterruptedException e) {
            logger.warning(e.getMessage());
            throw new RuntimeException(e.getMessage());
        }
    }

    private static class ListenerRunnable implements Runnable {
        private final GrizzlyServiceListener grizzlyListener;

        public ListenerRunnable(final GrizzlyServiceListener grizzlyListener) {
            this.grizzlyListener = grizzlyListener;
        }

        public void run() {
            try {
                grizzlyListener.start();
            } catch (IOException e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
                throw new RuntimeException(e.getMessage());
            } catch (InstantiationException e) {
                logger.log(Level.SEVERE, e.getMessage(), e);
                throw new RuntimeException(e.getMessage());
            }
        }
    }

    public static Protocol getProtocol(NetworkListener listener) {

        return null;
    }
}
