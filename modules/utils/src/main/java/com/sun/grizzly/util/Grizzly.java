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

package com.sun.grizzly.util;

import java.io.IOException;
import java.util.Properties;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Class contains information about Grizzly framework
 *
 * @author Charlie Hunt
 * @author Hubert Iwaniuk
 */
public class Grizzly {

    private static final Pattern versionPattern = Pattern.compile("((\\d+)\\.(\\d+)\\.(\\d+)){1}(?:-(.+))?");
    private static final String dotedVersion;
    private static final int major;
    private static final int minor;

    public static void main(String[] args) {
        System.out.println(Grizzly.getDotedVersion());
    }

    /** Reads version from properties and parses it. */
    static {
        Properties prop = new Properties();
        try {
            prop.load(Grizzly.class.getResourceAsStream("version.properties"));
        } catch (IOException e) {
            e.printStackTrace();
        }
        String version = prop.getProperty("grizzly.version");
        Matcher matcher = versionPattern.matcher(version);
        if (matcher.matches()) {
            dotedVersion = matcher.group(1);
            major = Integer.parseInt(matcher.group(2));
            minor = Integer.parseInt(matcher.group(3));
        } else {
            dotedVersion = "no.version";
            major = -1;
            minor = -1;
        }
    }

    /**
     * Return the dotted version of the curent release.
     *
     * @return like "2.0.1"
     */
    public static String getDotedVersion() {
        return dotedVersion;
    }


    /**
     * Get Grizzly framework major version
     *
     * @return Grizzly framework major version
     */
    public static int getMajorVersion() {
        return major;
    }

    /**
     * Get Grizzly framework minor version
     *
     * @return Grizzly framework minor version
     */
    public static int getMinorVersion() {
        return minor;
    }

    /**
     * Checks if current Grizzly framework version equals to one passed
     *
     * @param major Grizzly framework major version
     * @param minor Grizzly framework minor version
     * @return true, if versions are equal; false otherwise
     */
    public static boolean equalVersion(int major, int minor) {
        return minor == Grizzly.minor && major == Grizzly.major;
    }
}
