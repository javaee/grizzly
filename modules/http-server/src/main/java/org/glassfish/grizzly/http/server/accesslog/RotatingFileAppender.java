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

import static java.util.logging.Level.WARNING;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.Logger;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.server.HttpServer;

/**
 * An {@link AccessLogAppender appender} writing log entries to {@link File}s,
 * and rotating/archiving them when necessary.
 *
 * @author <a href="mailto:pier@usrz.com">Pier Fumagalli</a>
 * @author <a href="http://www.usrz.com/">USRZ.com</a>
 */
public class RotatingFileAppender implements AccessLogAppender {

    private static final Logger LOGGER = Grizzly.logger(HttpServer.class);

    /* The SDF that will format the "current" file name */
    private final SimpleDateFormatThreadLocal fileFormat;
    /* The SDF that will format the "archive" file name */
    private final SimpleDateFormatThreadLocal archiveFormat;

    /* Our current file appender */
    private FileAppender appender;
    /* The directory where to keep files */
    private final File directory;
    /* The name of the current archive file name */
    private File currentArchive;
    /* The name of the file we're actualy writing to */
    private File currentFile;
    /* Flag, closed, byebye */
    private boolean closed;

    /**
     * Create a {@link RotatingFileAppender} writing access log files in the
     * specified directory and using the specified {@link SimpleDateFormat}
     * pattern to generate file names.
     *
     * <p>For example when the specified pattern is
     * <code>'access-'yyyyMMDDhh'.log'</code> (note the quotes), access log
     * files will be rotated on a hourly basis, and the output will be written
     * to files like <code>access-2013120422.log</code>,
     * <code>access-2013120423.log</code>, ... and so on.
     *
     * @param directory The directory where access log files will be written to.
     * @param filePattern A properly escaped {@link SimpleDateFormat} pattern
     *                    for the access log files.
     * @throws IOException If an I/O error occurred accessing the filesystem.
     */
    public RotatingFileAppender(File directory, String filePattern)
    throws IOException {
        this(filePattern, filePattern, directory);
        LOGGER.fine("Creating rotating log appender in \"" + directory + "\" with file pattern \"" + filePattern+ "\"");
    }

    /**
     * Create a {@link RotatingFileAppender} writing access log files in the
     * specified directory.
     *
     * <p>When using this constructor the <em>current</em> log file (the one
     * being written to) will <em>always</em> be the one identified by the
     * <code>fileName</code> parameter, and then archival of files will be
     * delegated to the archive pattern.</p>
     *
     * <p>For example when <code>fileName</code> is <code>current.log</code> and
     * <code>archivePattern</code> is <code>'archive-'yyyyMMDD'.log'</code>
     * (note the quotes), access logs will be written to the
     * <code>current.log</code> file and this file will be rotated on a daily
     * basis to files like <code>archive-20131204.log</code>,
     * <code>archive-20131205.log</code>, ... and so on.
     *
     * @param directory The directory where access log files will be written to.
     * @param fileName A file name where log entries will be written to.
     * @param archivePattern A properly escaped {@link SimpleDateFormat} pattern
     *                       for the access log archive files.
     * @throws IOException If an I/O error occurred accessing the filesystem.
     */
    public RotatingFileAppender(File directory, String fileName, String archivePattern)
    throws IOException {
        this(escape(fileName), archivePattern, directory);
        LOGGER.fine("Creating rotating log appender in \"" + directory + "\" writing to \"" + fileName + "\" and archive pattern \"" + archivePattern + "\"");
    }

    /* ====================================================================== */

    private static String escape(String fileName) {
        if (fileName == null) throw new NullPointerException("Null file name");
        return "'" + fileName.replace("'", "''") + "'";
    }

    /* ====================================================================== */

    private RotatingFileAppender(String filePattern, String archivePattern, File directory)
    throws IOException {

        this.directory = directory.getCanonicalFile();
        archiveFormat = new SimpleDateFormatThreadLocal(archivePattern);
        fileFormat    = new SimpleDateFormatThreadLocal(filePattern);

        final Date now = new Date();
        currentArchive = new File(directory, archiveFormat.get().format(now)).getCanonicalFile();
        currentFile    = new File(directory,    fileFormat.get().format(now)).getCanonicalFile();

        /* Validate the arguments */
        if (!this.directory.equals(currentArchive.getParentFile()))
            throw new IllegalArgumentException("Archive file \"" + currentArchive + "\" is not a child of the configured directory \"" + this.directory + "\"");
        if (!this.directory.equals(currentFile.getParentFile()))
            throw new IllegalArgumentException("Access log file \"" + currentFile + "\" is not a child of the configured directory \"" + this.directory + "\"");
        if (currentArchive.equals(currentFile))
            throw new IllegalArgumentException("Access log file and archive file point to the same file \"" + currentFile + "\"");

        /* Validated, we can open files */
        appender = new FileAppender(currentFile, true);
    }

    /* ====================================================================== */
    /* DO SOME ACTUAL WORK                                                    */
    /* ====================================================================== */

    @Override
    public void append(String accessLogEntry)
    throws IOException {
        if (closed) return;

        /* It's all about date and time */
        final Date date = new Date();
        synchronized(this) {
            /* Calculate the name of the current archive */
            final SimpleDateFormat archiveFormat = this.archiveFormat.get();
            final File archive = new File(directory, archiveFormat.format(date));

            /* If this archive is *NOT* the one we wrote to last, rotate */
            if (!archive.equals(currentArchive)) try {

                /* Close our current appender */
                appender.close();

                /* If we have different file names, move the file to archive */
                if (!currentFile.equals(currentArchive)) {
                    LOGGER.info("Archiving \"" + currentFile + "\" to \"" + currentArchive +"\"");
                    if (!currentFile.renameTo(currentArchive))
                        throw new IOException("Unable to rename \"" + currentFile + "\" to \"" + currentArchive + "\"");
                }

                /* Save our new state */
                currentArchive = archive;
                currentFile = new File(directory, fileFormat.get().format(date));

                /* Create our new appender */
                appender = new FileAppender(currentFile, true);

            } catch (IOException exception) {
                LOGGER.log(WARNING, "I/O error rotating access log file", exception);
            }

            appender.append(accessLogEntry);
        }

    }

    @Override
    public void close()
    throws IOException {
        closed = true;
        appender.close();
    }

}
