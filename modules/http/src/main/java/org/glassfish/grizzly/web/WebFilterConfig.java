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
package org.glassfish.grizzly.web;

import org.glassfish.grizzly.web.arp.AsyncHandler;
import org.glassfish.grizzly.web.ssl.SSLSelectorThread;
import java.io.IOException;
import java.util.Iterator;
import java.util.Properties;
import java.util.StringTokenizer;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import org.glassfish.grizzly.web.container.util.IntrospectionUtils;
import org.glassfish.grizzly.web.container.util.res.StringManager;

public class WebFilterConfig {
    public final static String SERVER_NAME =
            System.getProperty("product.name") != null
                ? System.getProperty("product.name") : "grizzly";

    private Properties properties;

    public WebFilterConfig() {
        this.properties = new Properties();
    }

    
    /**
     * Configure properties on {@link SelectorThread}
     */
    public static void configure(WebFilter webFilter){
        configureProperties(webFilter);
    }

    public Properties getProperties() {
        return properties;
    }

    /**
     * Use reflection to configure Grizzly setter.
     */
    private void configureProperties(){
        Iterator keys = properties.keySet().iterator();
        while( keys.hasNext() ) {
            String name = (String)keys.next();
            String value = properties.get(name).toString();
            IntrospectionUtils.setProperty(this, name, value);
        }
    }
}
