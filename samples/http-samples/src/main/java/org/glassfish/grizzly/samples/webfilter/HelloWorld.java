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

package org.glassfish.grizzly.samples.webfilter;

import java.io.IOException;
import org.glassfish.grizzly.TransportFactory;
import org.glassfish.grizzly.filterchain.TransportFilter;
import org.glassfish.grizzly.nio.transport.TCPNIOTransport;
import org.glassfish.grizzly.web.WebFilter;
import org.glassfish.grizzly.web.container.http11.GrizzlyAdapter;
import org.glassfish.grizzly.web.container.http11.GrizzlyRequest;
import org.glassfish.grizzly.web.container.http11.GrizzlyResponse;

/**
 * Example shows, how HTTP protocol could be enabled using Grizzly
 * {@link FilterChain} by initializing and adding {@link WebFilter}.
 *
 * @see WebFilter
 * @see GrizzlyAdapter
 * @see FilterChain
 *
 * @author Alexey Stashok
 */
public class HelloWorld {
    public static final String HOST = "localhost";
    public static final int PORT = 7777;

    public static void main(String[] args) throws IOException {
        // Initialize custom GrizzlyAdapter
        final HelloWorldAdapter adapter = new HelloWorldAdapter();

        // Initialize WebFilter.
        final WebFilter webFilter = new WebFilter(Integer.toString(PORT));
        // Display configuration at startup
        webFilter.getConfig().setDisplayConfiguration(true);
        // Set custom HTTP processing Adapter
        webFilter.getConfig().setAdapter(adapter);

        // Initialize TCP transport
        final TCPNIOTransport transport =
                TransportFactory.getInstance().createTCPTransport();

        // Add transport filter is required!
        transport.getFilterChain().add(new TransportFilter());
        // Add WebFilter to process HTTP
        transport.getFilterChain().add(webFilter);

        // Starting....
        try {
            // Initialize and start adapter (optional).
            adapter.start();
            // Initialize WebFilter
            webFilter.initialize();
            // Enable WebFilter monitoring (optional).
            webFilter.enableMonitoring();
            // Bind TCP transport to certain host and port
            transport.bind(HOST, PORT);
            // Start TCP transport
            transport.start();

            // <-------------- RUNNING -------------->

            System.out.println("You can access adapter by: http://" +
                    HOST + ":" + PORT + "/");
            System.out.println("Press <ENTER> key to exit...");
            System.in.read();
            
        } finally {
            // Release resources....

            // Destroy adapter (optional)
            adapter.destroy();
            // Release WebFilter resources
            webFilter.release();
            // Stop TCP transport
            transport.stop();
            // Release Grizzly resources
            TransportFactory.getInstance().close();
        }
    }

    /**
     * Custom HTTP Adapter implementation
     */
    public static class HelloWorldAdapter extends GrizzlyAdapter {

        /**
         * {@inheritDoc}
         */
        @Override
        public void service(GrizzlyRequest request, GrizzlyResponse response) {
            // Return "Hello World" in response to any HTTP request
            try {
                response.getWriter().println("Hello World");
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }
}
