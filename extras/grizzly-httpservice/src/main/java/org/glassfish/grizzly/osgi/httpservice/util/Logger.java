/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2009-2017 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.osgi.httpservice.util;

import org.osgi.util.tracker.ServiceTracker;
import org.osgi.service.log.LogService;

/**
 * Logger Utility.
 *
 * @author Hubert Iwaniuk
 * @since Jan 20, 2009
 */
public class Logger {
    private final ServiceTracker logTracker;

    public Logger(final ServiceTracker logTracker) {
        this.logTracker = logTracker;
    }

    private void log(final int logLevel, final String msg) {
        LogService log = (LogService) logTracker.getService();
        if (log == null) {
            if (logLevel < LogService.LOG_WARNING)
                System.out.println(msg);
        } else {
            log.log(logLevel, msg);
        }
    }

    private void log(final int logLevel, final String msg, final Throwable e) {
        LogService log = (LogService) logTracker.getService();
        if (log == null) {
            System.out.println(msg);
            e.printStackTrace(System.out);
        } else {
            log.log(logLevel, msg, e);
        }
    }

    public void info(String msg) {
        log(LogService.LOG_INFO, msg);
    }

    public void debug(String msg) {
        log(LogService.LOG_DEBUG, msg);
    }

    public void warn(String msg) {
        log(LogService.LOG_WARNING, msg);
    }

    public void warn(String msg, Throwable e) {
        log(LogService.LOG_WARNING, msg, e);
    }

    public void error(String msg, Throwable e) {
        log(LogService.LOG_ERROR, msg, e);
    }
}
