/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server.accesslog;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.TimeZone;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.HttpServerMonitoringConfig;
import org.glassfish.grizzly.http.server.ServerConfiguration;

/**
 * A simple <em>builder</em> to configure access logging for Grizzly.
 *
 * <p>If the {@linkplain #format(AccessLogFormat) format} is left unspecified,
 * the default {@linkplain ApacheLogFormat#COMBINED Apache combined format}
 * will be used.</p>
 *
 * <p>If the {@linkplain #timeZone(TimeZone) time zone} is left unspecified,
 * the {@link TimeZone#getDefault() default time zone} will be used.</p>
 *
 * @author <a href="mailto:pier@usrz.com">Pier Fumagalli</a>
 * @author <a href="http://www.usrz.com/">USRZ.com</a>
 */
public class AccessLogBuilder {

    /* Our default access log format (Apache "combined" log) */
    private AccessLogFormat format = ApacheLogFormat.COMBINED;
    /* The default status threshold (log everything) */
    private int statusThreshold = AccessLogProbe.DEFAULT_STATUS_THRESHOLD;
    /* Null rotation pattern, do NOT rotate by default */
    private String rotationPattern;
    /* Non-synchronous, always use a Queue+Thread */
    private boolean synchronous;

    /* The base file name of the access log */
    private final File file;

    /**
     * Create a new {@link AccessLogBuilder} writing logs to the specified file.
     *
     * @param file The location of the access log file.
     */
    public AccessLogBuilder(String file) {
        if (file == null) throw new NullPointerException("Null file");
        this.file = new File(file).getAbsoluteFile();
    }

    /**
     * Create a new {@link AccessLogBuilder} writing logs to the specified file.
     *
     * @param file The location of the access log file.
     */
    public AccessLogBuilder(File file) {
        if (file == null) throw new NullPointerException("Null file");
        this.file = file;
    }

    /**
     * Build an {@link AccessLogProbe} instance which can be injected into an
     * {@link HttpServer}'s {@linkplain HttpServerMonitoringConfig monitoring
     * configuration} to provide access logging.
     */
    public AccessLogProbe build() {
        /* Build an appender, plain or rotating */
        AccessLogAppender appender;
        try {
            if (rotationPattern == null) {
                appender = new FileAppender(file.getCanonicalFile());
            } else {
                /* Get directory and base file name (encode ' single quotes) */
                final File directory = file.getCanonicalFile().getParentFile();
                final String name = file.getName();

                /* Split "name.ext" name in "name" + ".ext" */
                final String base;
                final String extension;
                final int position = name.lastIndexOf(".");
                if (position < 0) {
                    base = name.replace("'", "''");
                    extension = "";
                } else {
                    base = name.substring(0, position).replace("'", "''");
                    extension = name.substring(position).replace("'", "''");
                }

                /* Build a simple date format pattern like "'name-'pattern'.ext'"  */
                final String archive = new StringBuilder()
                                        .append('\'').append(base).append("'-")
                                        .append(rotationPattern)
                                        .append('\'').append(extension).append('\'')
                                        .toString();

                /* Create our appender */
                appender = new RotatingFileAppender(directory, name, archive);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("I/O error creating acces log", exception);
        }

        /* Wrap the synch in a queue in a-synchronous */
        if (!synchronous) appender = new QueueingAppender(appender);

        /* Create and return our probe */
        return new AccessLogProbe(appender, format, statusThreshold);
    }

    /**
     * Build an {@link AccessLogProbe} instance and directly instrument it in an
     * {@link HttpServer}'s {@linkplain HttpServerMonitoringConfig monitoring
     * configuration} to provide access logging.
     *
     * @param serverConfiguration The {@link ServerConfiguration} to instrument.
     */
    public ServerConfiguration instrument(ServerConfiguration serverConfiguration) {
        serverConfiguration.getMonitoringConfig()
                           .getWebServerConfig()
                           .addProbes(build());
        return serverConfiguration;
    }

    /**
     * Set the {@link AccessLogFormat} instance that will be used by the
     * access logs configured by this instance.
     */
    public AccessLogBuilder format(AccessLogFormat format) {
        if (format == null) throw new NullPointerException("Null format");
        this.format = format;
        return this;
    }

    /**
     * Set the <em>format</em> as a {@link String} compatible with the default
     * {@linkplain ApacheLogFormat Apache access log format} that will be used
     * by the access logs configured by this instance.
     */
    public AccessLogBuilder format(String format) {
        if (format == null) throw new NullPointerException("Null format");
        return this.format(new ApacheLogFormat(format));
    }

    /**
     * Set the <em>time zone</em> that will be used to represent dates.
     */
    public AccessLogBuilder timeZone(TimeZone timeZone) {
        if (timeZone == null) throw new NullPointerException("Null time zone");
        if (format instanceof ApacheLogFormat) {
            final ApacheLogFormat apacheFormat = (ApacheLogFormat) format;
            format = new ApacheLogFormat(timeZone, apacheFormat.getFormat());
            return this;
        }
        throw new IllegalStateException("TimeZone can not be set for " + format.getClass().getName());
    }

    /**
     * Set the <em>time zone</em> that will be used to represent dates.
     *
     * <p>The time zone will be looked up by
     * {@linkplain TimeZone#getTimeZone(String) time zone identifier}, and if
     * this is invalid or unrecognized, it will default to <em>GMT</em>.</p>
     */
    public AccessLogBuilder timeZone(String timeZone) {
        if (timeZone == null) throw new NullPointerException("Null time zone");
        return this.timeZone(TimeZone.getTimeZone(timeZone));
    }

    /**
     * Set the minimum <em>response status</em> that will trigger an entry
     * in an access log configured by this instance.
     *
     * <p>For example a threshold of <code>500</code> will only generate log
     * entries for requests that terminated in error.</p>
     */
    public AccessLogBuilder statusThreshold(int statusThreshold) {
        this.statusThreshold = statusThreshold;
        return this;
    }

    /**
     * Set up automatic log-file rotation, on a hourly basis.
     *
     * <p>For example, if the file name specified at
     * {@linkplain #AccessLogBuilder(File) construction} was
     * <code>access.log</code>, files will be archived on a hourly basis
     * with names like <code>access-yyyyMMDDhh.log</code>.</p>
     */
    public AccessLogBuilder rotatedHourly() {
        return rotationPattern("yyyyMMDDhh");
    }

    /**
     * Set up automatic log-file rotation, on a daily basis.
     *
     * <p>For example, if the file name specified at
     * {@linkplain #AccessLogBuilder(File) construction} was
     * <code>access.log</code>, files will be archived on a daily basis
     * with names like <code>access-yyyyMMDD.log</code>.</p>
     */
    public AccessLogBuilder rotatedDaily() {
        return rotationPattern("yyyyMMDD");
    }

    /**
     * Set up automatic log-file rotation based on a specified
     * {@link SimpleDateFormat} <em>pattern</em>.
     *
     * <p>For example, if the file name specified at
     * {@linkplain #AccessLogBuilder(File) construction} was
     * <code>access.log</code> and the <em>rotation pattern</code> specified
     * here is <code>EEE</code> <em>(day name in week)</em>, files will be
     * archived on a daily basis with names like
     * <code>access-Mon.log</code>, <code>access-Tue.log</code>, ...</p>
     */
    public AccessLogBuilder rotationPattern(String rotationPattern) {
        if (rotationPattern == null) throw new NullPointerException("Null rotation pattern");
        this.rotationPattern = rotationPattern;
        return this;
    }

    /**
     * Specify whether access log entries should be written
     * <en>synchronously</em> or not.
     *
     * <p>If <b>false</b> (the default) a {@link QueueingAppender} will be used
     * to enqueue entries and append to the final appenders when possible.</p>
     */
    public AccessLogBuilder synchronous(boolean synchronous) {
        this.synchronous = synchronous;
        return this;
    }
}
