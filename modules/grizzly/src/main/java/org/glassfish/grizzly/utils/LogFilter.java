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

package org.glassfish.grizzly.utils;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.BaseFilter;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.NextAction;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Simple log {@link Filter}
 * 
 * @author Alexey Stashok
 */
public class LogFilter extends BaseFilter {
    private final Logger logger;
    private final Level level;

    public LogFilter() {
        this(null, null);
    }

    public LogFilter(Logger logger) {
        this(logger, null);
    }

    public LogFilter(Logger logger, Level level) {
        if (logger != null) {
            this.logger = logger;
        } else {
            this.logger = Grizzly.logger(LogFilter.class);
        }

        if (level != null) {
            this.level = level;
        } else {
            this.level = Level.INFO;
        }
    }

    public Logger getLogger() {
        return logger;
    }

    public Level getLevel() {
        return level;
    }

    @Override
    public void onAdded(FilterChain filterChain) {
        logger.log(level, "LogFilter onAdded");
    }

    @Override
    public void onRemoved(FilterChain filterChain) {
        logger.log(level, "LogFilter onRemoved");
    }

    @Override
    public void onFilterChainChanged(FilterChain filterChain) {
        logger.log(level, "LogFilter onFilterChainChanged");
    }

    @Override
    public NextAction handleRead(FilterChainContext ctx) throws IOException {
        logger.log(level, "LogFilter handleRead. Connection={0} message={1}",
                new Object[] {ctx.getConnection(), ctx.getMessage()});
        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleWrite(FilterChainContext ctx) throws IOException {
        logger.log(level, "LogFilter handleWrite. Connection={0} message={1}",
                new Object[] {ctx.getConnection(), ctx.getMessage()});
        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleConnect(FilterChainContext ctx) throws IOException {
        logger.log(level, "LogFilter handleConnect. Connection={0} message={1}",
                new Object[] {ctx.getConnection(), ctx.getMessage()});
        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleAccept(FilterChainContext ctx) throws IOException {
        logger.log(level, "LogFilter handleAccept. Connection={0} message={1}",
                new Object[] {ctx.getConnection(), ctx.getMessage()});
        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleClose(FilterChainContext ctx) throws IOException {
        logger.log(level, "LogFilter handleClose. Connection={0} message={1}",
                new Object[] {ctx.getConnection(), ctx.getMessage()});
        return ctx.getInvokeAction();
    }

    @Override
    public void exceptionOccurred(FilterChainContext ctx,
            Throwable error) {
        logger.log(level, "LogFilter exceptionOccured. Connection={0} message={1}",
                new Object[] {ctx.getConnection(), ctx.getMessage()});
    }
}
