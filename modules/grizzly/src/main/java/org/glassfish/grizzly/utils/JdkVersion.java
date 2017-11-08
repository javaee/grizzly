/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2012-2017 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.utils;

import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.glassfish.grizzly.Grizzly;

/**
 *
 * @since 2.2.11
 */
public class JdkVersion implements Comparable<JdkVersion> {
    private static final Logger LOGGER = Grizzly.logger(JdkVersion.class);
    
    // take max 4 parts of the JDK version and cut the rest (usually the build number)
    private static final Pattern VERSION_PATTERN = Pattern.compile(
                "([0-9]+)(\\.([0-9]+))?(\\.([0-9]+))?([_\\.]([0-9]+))?.*");

    private static final JdkVersion UNKNOWN_VERSION = new JdkVersion(-1, -1, -1, -1);
    private static final JdkVersion JDK_VERSION = parseVersion(System.getProperty("java.version"));

    private final int major;
    private final int minor;
    private final int maintenance;
    private final int update;

    // ------------------------------------------------------------ Constructors

    private JdkVersion(final int major,
                       final int minor,
                       final int maintenance,
                       final int update) {
        this.major = major;
        this.minor = minor;
        this.maintenance = maintenance;
        this.update = update;
    }

    // ---------------------------------------------------------- Public Methods

    public static JdkVersion parseVersion(final String versionString) {

        try {
            final Matcher matcher = VERSION_PATTERN.matcher(versionString);
            if (matcher.matches()) {
                return new JdkVersion(parseInt(matcher.group(1)),
                        parseInt(matcher.group(3)),
                        parseInt(matcher.group(5)),
                        parseInt(matcher.group(7)));
            }
            
            LOGGER.log(Level.FINE,
                    "Can't parse the JDK version {0}", versionString);
            
        } catch (Exception e) {
            LOGGER.log(Level.FINE,
                    "Error parsing the JDK version " + versionString, e);
        }

        return UNKNOWN_VERSION;
    }

    public static JdkVersion getJdkVersion() {
        return JDK_VERSION;
    }

    @SuppressWarnings("UnusedDeclaration")
    public int getMajor() {
        return major;
    }

    @SuppressWarnings("UnusedDeclaration")
    public int getMinor() {
        return minor;
    }

    @SuppressWarnings("UnusedDeclaration")
    public int getMaintenance() {
        return maintenance;
    }

    @SuppressWarnings("UnusedDeclaration")
    public int getUpdate() {
        return update;
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("JdkVersion");
        sb.append("{major=").append(major);
        sb.append(", minor=").append(minor);
        sb.append(", maintenance=").append(maintenance);
        sb.append(", update=").append(update);
        sb.append('}');
        return sb.toString();
    }

    // ------------------------------------------------- Methods from Comparable

    public int compareTo(String versionString) {
        return compareTo(JdkVersion.parseVersion(versionString));
    }

    @Override
    public int compareTo(JdkVersion otherVersion) {
        if (major < otherVersion.major) {
            return -1;
        }
        if (major > otherVersion.major) {
            return 1;
        }
        if (minor < otherVersion.minor) {
            return -1;
        }
        if (minor > otherVersion.minor) {
            return 1;
        }
        if (maintenance < otherVersion.maintenance) {
            return -1;
        }
        if (maintenance > otherVersion.maintenance) {
            return 1;
        }
        if (update < otherVersion.update) {
            return -1;
        }
        if (update > otherVersion.update) {
            return 1;
        }
        return 0;
    }

    private static int parseInt(final String s) {
        return s != null ? Integer.parseInt(s) : 0;
    }
}
