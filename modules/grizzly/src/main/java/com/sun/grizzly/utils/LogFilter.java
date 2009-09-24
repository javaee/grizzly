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

package com.sun.grizzly.utils;

import com.sun.grizzly.Connection;
import com.sun.grizzly.Grizzly;
import com.sun.grizzly.filterchain.Filter;
import com.sun.grizzly.filterchain.FilterChainContext;
import com.sun.grizzly.filterchain.NextAction;
import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.grizzly.TransformationException;
import com.sun.grizzly.TransformationResult;

/**
 * Simple log {@link Filter}
 * 
 * @author Alexey Stashok
 */
public class LogFilter implements Filter {
    private int index;
    
    private Logger logger;
    private Level level;

    public LogFilter() {
        this(Grizzly.logger);
    }

    public LogFilter(Logger logger) {
        this(logger, Level.INFO);
    }

    public LogFilter(Logger logger, Level level) {
        this.logger = logger;
        this.level = level;
    }

    public Logger getLogger() {
        return logger;
    }

    public void setLogger(Logger logger) {
        this.logger = logger;
    }

    public Level getLevel() {
        return level;
    }

    public void setLevel(Level level) {
        this.level = level;
    }


    @Override
    public NextAction handleRead(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        logger.log(level, "LogFilter handleRead. Connection=" +
                ctx.getConnection() + "IOEvent=" + ctx.getIoEvent() +
                " message=" + ctx.getMessage());
        return nextAction;
    }

    @Override
    public NextAction postRead(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        logger.log(level, "LogFilter postRead. Connection=" +
                ctx.getConnection() + "IOEvent=" + ctx.getIoEvent() +
                " message=" + ctx.getMessage());
        return nextAction;
    }

    @Override
    public NextAction handleWrite(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        logger.log(level, "LogFilter handleWrite. Connection=" +
                ctx.getConnection() + "IOEvent=" + ctx.getIoEvent() +
                " message=" + ctx.getMessage());
        return nextAction;
    }

    @Override
    public NextAction postWrite(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        logger.log(level, "LogFilter postWrite. Connection=" +
                ctx.getConnection() + "IOEvent=" + ctx.getIoEvent() +
                " message=" + ctx.getMessage());
        return nextAction;
    }

    @Override
    public NextAction handleConnect(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        logger.log(level, "LogFilter handleConnect. Connection=" +
                ctx.getConnection() + "IOEvent=" + ctx.getIoEvent() +
                " message=" + ctx.getMessage());
        return nextAction;
    }

    @Override
    public NextAction postConnect(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        logger.log(level, "LogFilter postConnect. Connection=" +
                ctx.getConnection() + "IOEvent=" + ctx.getIoEvent() +
                " message=" + ctx.getMessage());
        return nextAction;
    }

    @Override
    public NextAction handleAccept(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        logger.log(level, "LogFilter handleAccept. Connection=" +
                ctx.getConnection() + "IOEvent=" + ctx.getIoEvent() +
                " message=" + ctx.getMessage());
        return nextAction;
    }

    @Override
    public NextAction postAccept(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        logger.log(level, "LogFilter postAccept. Connection=" +
                ctx.getConnection() + "IOEvent=" + ctx.getIoEvent() +
                " message=" + ctx.getMessage());
        return nextAction;
    }

    @Override
    public NextAction handleClose(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        logger.log(level, "LogFilter handleClose. Connection=" +
                ctx.getConnection() + "IOEvent=" + ctx.getIoEvent() +
                " message=" + ctx.getMessage());
        return nextAction;
    }

    @Override
    public NextAction postClose(FilterChainContext ctx,
            NextAction nextAction) throws IOException {
        logger.log(level, "LogFilter postClose. Connection=" +
                ctx.getConnection() + "IOEvent=" + ctx.getIoEvent() +
                " message=" + ctx.getMessage());
        return nextAction;
    }

    @Override
    public void exceptionOccurred(FilterChainContext ctx,
            Throwable error) {
        logger.log(level, "LogFilter exceptionOccured. Connection=" + 
                ctx.getConnection() + "IOEvent=" + ctx.getIoEvent() +
                " message=" + ctx.getMessage());
    }

    public TransformationResult decode(Connection connection,
            Object originalMessage) throws TransformationException {
        logger.log(level, "LogFilter. decode(" + connection +
                ") message=" + originalMessage);
        return null;
    }

    public TransformationResult encode(Connection connection,
            Object originalMessage) throws TransformationException {
        logger.log(level, "LogFilter. encode(" + connection +
                ") message=" + originalMessage);
        return null;
    }

    public boolean isIndexable() {
        return true;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }
}