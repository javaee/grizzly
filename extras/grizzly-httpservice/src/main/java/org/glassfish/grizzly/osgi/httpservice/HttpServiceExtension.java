/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.osgi.httpservice;/*

 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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

import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;

import javax.servlet.Filter;
import javax.servlet.ServletException;
import java.util.Dictionary;

/**
 * An extension to the OSGi {@link HttpService} interface allowing the
 * registration/unregistration of Servlet {@link Filter} instances.
 *
 * @since 2.3.3
 */
public interface HttpServiceExtension extends HttpService {


    /**
     * Registers a {@link Filter} and with the {@link HttpService}.
     *
     * As this is an extension to the standard {@link HttpService} and there
     * are no clear rules on how the mapping of filters should occur,
     * this implementation follows the mapping rules as defined by the Servlet
     * specification.
     *
     * Additionally, it should be noted that the registered {@link Filter}s are
     * effectively associated with a particular {@link HttpContext}.  Therefore,
     * if you wish to have multiple filters associated with a particular
     * {@link javax.servlet.Servlet}, then you should use the same {@link HttpContext}
     * instance to perform the registration.
     *
     * {@link Filter}s will be invoked in registration order.
     *
     * This method will invoke {@link Filter#init(javax.servlet.FilterConfig)} during
     * the registration process.
     *
     * When registering a {@link Filter}, take care not to reuse the same Filter
     * instance across multiple registration invocations.  This could cause issues
     * when removing the Filter as it may remove more url matching possibilities
     * than intended.
     *
     * @param filter the {@link Filter} to register.
     * @param urlPattern the url pattern that will invoke this {@link Filter}.
     * @param initParams the initialization params that will be passed to the
     *                   filter when {@link Filter#init(javax.servlet.FilterConfig)}
     *                   is invoked.
     * @param context the {@link HttpContext} associated with this {@link Filter}.
     *
     * @throws ServletException if an error occurs during {@link Filter} initialization.
     */
    public void registerFilter(final Filter filter,
                               final String urlPattern,
                               final Dictionary initParams,
                               final HttpContext context) throws ServletException;

    /**
     * Removes the specified {@link Filter} from the service.
     *
     * @param filter the {@link Filter} to remove.
     */
    public void unregisterFilter(final Filter filter);

}
