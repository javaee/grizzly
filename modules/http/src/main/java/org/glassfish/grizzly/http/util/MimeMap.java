/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2010 Oracle and/or its affiliates. All rights reserved.
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
 *
 *
 * This file incorporates work covered by the following copyright and
 * permission notice:
 *
 * Copyright 2004 The Apache Software Foundation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.glassfish.grizzly.http.util;

import java.net.FileNameMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * A mime type map that implements the java.net.FileNameMap interface.
 *
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author Jason Hunter [jch@eng.sun.com]
 */
public class MimeMap implements FileNameMap {

    // Defaults - all of them are "well-known" types,
    // you can add using normal web.xml.
    public final static Map<String, String> DEFAULT_MAP =
            new HashMap<String, String>(103);

    static {
        DEFAULT_MAP.put("txt", "text/plain");
        DEFAULT_MAP.put("html", "text/html");
        DEFAULT_MAP.put("htm", "text/html");
        DEFAULT_MAP.put("gif", "image/gif");
        DEFAULT_MAP.put("jpg", "image/jpeg");
        DEFAULT_MAP.put("jpe", "image/jpeg");
        DEFAULT_MAP.put("jpeg", "image/jpeg");
        DEFAULT_MAP.put("java", "text/plain");
        DEFAULT_MAP.put("js", "text/javascript");
        DEFAULT_MAP.put("css", "text/css");
        DEFAULT_MAP.put("body", "text/html");
        DEFAULT_MAP.put("rtx", "text/richtext");
        DEFAULT_MAP.put("tsv", "text/tab-separated-values");
        DEFAULT_MAP.put("etx", "text/x-setext");
        DEFAULT_MAP.put("ps", "application/x-postscript");
        DEFAULT_MAP.put("class", "application/java");
        DEFAULT_MAP.put("csh", "application/x-csh");
        DEFAULT_MAP.put("sh", "application/x-sh");
        DEFAULT_MAP.put("tcl", "application/x-tcl");
        DEFAULT_MAP.put("tex", "application/x-tex");
        DEFAULT_MAP.put("texinfo", "application/x-texinfo");
        DEFAULT_MAP.put("texi", "application/x-texinfo");
        DEFAULT_MAP.put("t", "application/x-troff");
        DEFAULT_MAP.put("tr", "application/x-troff");
        DEFAULT_MAP.put("roff", "application/x-troff");
        DEFAULT_MAP.put("man", "application/x-troff-man");
        DEFAULT_MAP.put("me", "application/x-troff-me");
        DEFAULT_MAP.put("ms", "application/x-wais-source");
        DEFAULT_MAP.put("src", "application/x-wais-source");
        DEFAULT_MAP.put("zip", "application/zip");
        DEFAULT_MAP.put("bcpio", "application/x-bcpio");
        DEFAULT_MAP.put("cpio", "application/x-cpio");
        DEFAULT_MAP.put("gtar", "application/x-gtar");
        DEFAULT_MAP.put("shar", "application/x-shar");
        DEFAULT_MAP.put("sv4cpio", "application/x-sv4cpio");
        DEFAULT_MAP.put("sv4crc", "application/x-sv4crc");
        DEFAULT_MAP.put("tar", "application/x-tar");
        DEFAULT_MAP.put("ustar", "application/x-ustar");
        DEFAULT_MAP.put("dvi", "application/x-dvi");
        DEFAULT_MAP.put("hdf", "application/x-hdf");
        DEFAULT_MAP.put("latex", "application/x-latex");
        DEFAULT_MAP.put("bin", "application/octet-stream");
        DEFAULT_MAP.put("oda", "application/oda");
        DEFAULT_MAP.put("pdf", "application/pdf");
        DEFAULT_MAP.put("ps", "application/postscript");
        DEFAULT_MAP.put("eps", "application/postscript");
        DEFAULT_MAP.put("ai", "application/postscript");
        DEFAULT_MAP.put("rtf", "application/rtf");
        DEFAULT_MAP.put("nc", "application/x-netcdf");
        DEFAULT_MAP.put("cdf", "application/x-netcdf");
        DEFAULT_MAP.put("cer", "application/x-x509-ca-cert");
        DEFAULT_MAP.put("exe", "application/octet-stream");
        DEFAULT_MAP.put("gz", "application/x-gzip");
        DEFAULT_MAP.put("Z", "application/x-compress");
        DEFAULT_MAP.put("z", "application/x-compress");
        DEFAULT_MAP.put("hqx", "application/mac-binhex40");
        DEFAULT_MAP.put("mif", "application/x-mif");
        DEFAULT_MAP.put("ief", "image/ief");
        DEFAULT_MAP.put("tiff", "image/tiff");
        DEFAULT_MAP.put("tif", "image/tiff");
        DEFAULT_MAP.put("ras", "image/x-cmu-raster");
        DEFAULT_MAP.put("pnm", "image/x-portable-anymap");
        DEFAULT_MAP.put("pbm", "image/x-portable-bitmap");
        DEFAULT_MAP.put("pgm", "image/x-portable-graymap");
        DEFAULT_MAP.put("ppm", "image/x-portable-pixmap");
        DEFAULT_MAP.put("rgb", "image/x-rgb");
        DEFAULT_MAP.put("xbm", "image/x-xbitmap");
        DEFAULT_MAP.put("xpm", "image/x-xpixmap");
        DEFAULT_MAP.put("xwd", "image/x-xwindowdump");
        DEFAULT_MAP.put("au", "audio/basic");
        DEFAULT_MAP.put("snd", "audio/basic");
        DEFAULT_MAP.put("aif", "audio/x-aiff");
        DEFAULT_MAP.put("aiff", "audio/x-aiff");
        DEFAULT_MAP.put("aifc", "audio/x-aiff");
        DEFAULT_MAP.put("wav", "audio/x-wav");
        DEFAULT_MAP.put("mpeg", "video/mpeg");
        DEFAULT_MAP.put("mpg", "video/mpeg");
        DEFAULT_MAP.put("mpe", "video/mpeg");
        DEFAULT_MAP.put("qt", "video/quicktime");
        DEFAULT_MAP.put("mov", "video/quicktime");
        DEFAULT_MAP.put("avi", "video/x-msvideo");
        DEFAULT_MAP.put("movie", "video/x-sgi-movie");
        DEFAULT_MAP.put("avx", "video/x-rad-screenplay");
        DEFAULT_MAP.put("wrl", "x-world/x-vrml");
        DEFAULT_MAP.put("mpv2", "video/mpeg2");

        /* Add XML related MIMEs */

        DEFAULT_MAP.put("xml", "text/xml");
        DEFAULT_MAP.put("xsl", "text/xml");
        DEFAULT_MAP.put("svg", "image/svg+xml");
        DEFAULT_MAP.put("svgz", "image/svg+xml");
        DEFAULT_MAP.put("wbmp", "image/vnd.wap.wbmp");
        DEFAULT_MAP.put("wml", "text/vnd.wap.wml");
        DEFAULT_MAP.put("wmlc", "application/vnd.wap.wmlc");
        DEFAULT_MAP.put("wmls", "text/vnd.wap.wmlscript");
        DEFAULT_MAP.put("wmlscriptc", "application/vnd.wap.wmlscriptc");
    }
    private Map<String, String> map = new HashMap<String, String>();

    public void addContentType(String extn, String type) {
        map.put(extn, type.toLowerCase());
    }

    public Iterator<String> getExtensions() {
        return map.keySet().iterator();
    }

    public String getContentType(String extn) {
        String type = map.get(extn.toLowerCase());
        if (type == null) {
            type = DEFAULT_MAP.get(extn);
        }
        return type;
    }

    public void removeContentType(String extn) {
        map.remove(extn.toLowerCase());
    }

    /** Get extension of file, without fragment id
     */
    public static String getExtension(String fileName) {
        // play it safe and get rid of any fragment id
        // that might be there
        int length = fileName.length();

        int newEnd = fileName.lastIndexOf('#');
        if (newEnd == -1) {
            newEnd = length;
        }
        // Instead of creating a new string.
        //         if (i != -1) {
        //             fileName = fileName.substring(0, i);
        //         }
        int i = fileName.lastIndexOf('.', newEnd);
        if (i != -1) {
            return fileName.substring(i + 1, newEnd);
        } else {
            // no extension, no content type
            return null;
        }
    }

    @Override
    public String getContentTypeFor(String fileName) {
        String extn = getExtension(fileName);
        if (extn != null) {
            return getContentType(extn);
        } else {
            // no extension, no content type
            return null;
        }
    }
}
