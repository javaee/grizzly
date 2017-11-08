/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2017 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.util;

import java.util.HashMap;
import java.util.Map;

/**
 * Hardcoded mime-type supported by default.   Additional extension/mime-type mappings may be added by calling {@link
 * MimeType#add(String, String)}, however, keep in mind that these mappings are per-JVM.
 *
 * @author Jeanfrancois Arcand
 */
public class MimeType {
    private final static Map<String, String> contentTypes = new HashMap<String, String>();

    static {
        contentTypes.put("abs", "audio/x-mpeg");
        contentTypes.put("ai", "application/postscript");
        contentTypes.put("aif", "audio/x-aiff");
        contentTypes.put("aifc", "audio/x-aiff");
        contentTypes.put("aiff", "audio/x-aiff");
        contentTypes.put("aim", "application/x-aim");
        contentTypes.put("art", "image/x-jg");
        contentTypes.put("asf", "video/x-ms-asf");
        contentTypes.put("asx", "video/x-ms-asf");
        contentTypes.put("au", "audio/basic");
        contentTypes.put("avi", "video/x-msvideo");
        contentTypes.put("avx", "video/x-rad-screenplay");
        contentTypes.put("bcpio", "application/x-bcpio");
        contentTypes.put("bin", "application/octet-stream");
        contentTypes.put("bmp", "image/bmp");
        contentTypes.put("body", "text/html");
        contentTypes.put("cdf", "application/x-cdf");
        contentTypes.put("cer", "application/x-x509-ca-cert");
        contentTypes.put("class", "application/java");
        contentTypes.put("cpio", "application/x-cpio");
        contentTypes.put("csh", "application/x-csh");
        contentTypes.put("css", "text/css");
        contentTypes.put("dib", "image/bmp");
        contentTypes.put("doc", "application/msword");
        contentTypes.put("dtd", "application/xml-dtd");
        contentTypes.put("dv", "video/x-dv");
        contentTypes.put("dvi", "application/x-dvi");
        contentTypes.put("eps", "application/postscript");
        contentTypes.put("etx", "text/x-setext");
        contentTypes.put("exe", "application/octet-stream");
        contentTypes.put("gif", "image/gif");
        contentTypes.put("gk", "application/octet-stream");
        contentTypes.put("gtar", "application/x-gtar");
        contentTypes.put("gz", "application/x-gzip");
        contentTypes.put("hdf", "application/x-hdf");
        contentTypes.put("hqx", "application/mac-binhex40");
        contentTypes.put("htc", "text/x-component");
        contentTypes.put("htm", "text/html");
        contentTypes.put("html", "text/html");
        contentTypes.put("hqx", "application/mac-binhex40");
        contentTypes.put("ief", "image/ief");
        contentTypes.put("jad", "text/vnd.sun.j2me.app-descriptor");
        contentTypes.put("jar", "application/java-archive");
        contentTypes.put("java", "text/plain");
        contentTypes.put("jnlp", "application/x-java-jnlp-file");
        contentTypes.put("jpe", "image/jpeg");
        contentTypes.put("jpeg", "image/jpeg");
        contentTypes.put("jpg", "image/jpeg");
        contentTypes.put("js", "text/javascript");
        contentTypes.put("kar", "audio/x-midi");
        contentTypes.put("latex", "application/x-latex");
        contentTypes.put("m3u", "audio/x-mpegurl");
        contentTypes.put("mac", "image/x-macpaint");
        contentTypes.put("man", "application/x-troff-man");
        contentTypes.put("mathml", "application/mathml+xml");
        contentTypes.put("me", "application/x-troff-me");
        contentTypes.put("mid", "audio/x-midi");
        contentTypes.put("midi", "audio/x-midi");
        contentTypes.put("mif", "application/x-mif");
        contentTypes.put("mov", "video/quicktime");
        contentTypes.put("movie", "video/x-sgi-movie");
        contentTypes.put("mp1", "audio/x-mpeg");
        contentTypes.put("mp2", "audio/x-mpeg");
        contentTypes.put("mp3", "audio/x-mpeg");
        contentTypes.put("mpa", "audio/x-mpeg");
        contentTypes.put("mpe", "video/mpeg");
        contentTypes.put("mpeg", "video/mpeg");
        contentTypes.put("mpega", "audio/x-mpeg");
        contentTypes.put("mpg", "video/mpeg");
        contentTypes.put("mpv2", "video/mpeg2");
        contentTypes.put("ms", "application/x-wais-source");
        contentTypes.put("nc", "application/x-netcdf");
        contentTypes.put("oda", "application/oda");
        contentTypes.put("ogg", "application/ogg");
        contentTypes.put("pbm", "image/x-portable-bitmap");
        contentTypes.put("pct", "image/pict");
        contentTypes.put("pdf", "application/pdf");
        contentTypes.put("pgm", "image/x-portable-graymap");
        contentTypes.put("pic", "image/pict");
        contentTypes.put("pict", "image/pict");
        contentTypes.put("pls", "audio/x-scpls");
        contentTypes.put("png", "image/png");
        contentTypes.put("pnm", "image/x-portable-anymap");
        contentTypes.put("pnt", "image/x-macpaint");
        contentTypes.put("ppm", "image/x-portable-pixmap");
        contentTypes.put("ppt", "application/powerpoint");
        contentTypes.put("ps", "application/postscript");
        contentTypes.put("psd", "image/x-photoshop");
        contentTypes.put("qt", "video/quicktime");
        contentTypes.put("qti", "image/x-quicktime");
        contentTypes.put("qtif", "image/x-quicktime");
        contentTypes.put("ras", "image/x-cmu-raster");
        contentTypes.put("rdf", "application/rdf+xml");
        contentTypes.put("rgb", "image/x-rgb");
        contentTypes.put("rm", "application/vnd.rn-realmedia");
        contentTypes.put("roff", "application/x-troff");
        contentTypes.put("rtf", "application/rtf");
        contentTypes.put("rtx", "text/richtext");
        contentTypes.put("sh", "application/x-sh");
        contentTypes.put("shar", "application/x-shar");
        contentTypes.put("shtml", "text/x-server-parsed-html");
        contentTypes.put("sit", "application/x-stuffit");
        contentTypes.put("smf", "audio/x-midi");
        contentTypes.put("snd", "audio/basic");
        contentTypes.put("src", "application/x-wais-source");
        contentTypes.put("sv4cpio", "application/x-sv4cpio");
        contentTypes.put("sv4crc", "application/x-sv4crc");
        contentTypes.put("svg", "image/svg+xml");
        contentTypes.put("svgz", "image/svg+xml");
        contentTypes.put("swf", "application/x-shockwave-flash");
        contentTypes.put("t", "application/x-troff");
        contentTypes.put("tar", "application/x-tar");
        contentTypes.put("tcl", "application/x-tcl");
        contentTypes.put("tex", "application/x-tex");
        contentTypes.put("texi", "application/x-texinfo");
        contentTypes.put("texinfo", "application/x-texinfo");
        contentTypes.put("tif", "image/tiff");
        contentTypes.put("tiff", "image/tiff");
        contentTypes.put("tr", "application/x-troff");
        contentTypes.put("tsv", "text/tab-separated-values");
        contentTypes.put("txt", "text/plain");
        contentTypes.put("ulw", "audio/basic");
        contentTypes.put("ustar", "application/x-ustar");
        contentTypes.put("xbm", "image/x-xbitmap");
        contentTypes.put("xml", "application/xml");
        contentTypes.put("xpm", "image/x-xpixmap");
        contentTypes.put("xsl", "application/xml");
        contentTypes.put("xslt", "application/xslt+xml");
        contentTypes.put("xwd", "image/x-xwindowdump");
        contentTypes.put("vsd", "application/x-visio");
        contentTypes.put("vxml", "application/voicexml+xml");
        contentTypes.put("wav", "audio/x-wav");
        contentTypes.put("wbmp", "image/vnd.wap.wbmp");
        contentTypes.put("wml", "text/vnd.wap.wml");
        contentTypes.put("wmlc", "application/vnd.wap.wmlc");
        contentTypes.put("wmls", "text/vnd.wap.wmls");
        contentTypes.put("wmlscriptc", "application/vnd.wap.wmlscriptc");
        contentTypes.put("wrl", "x-world/x-vrml");
        contentTypes.put("xht", "application/xhtml+xml");
        contentTypes.put("xhtml", "application/xhtml+xml");
        contentTypes.put("xls", "application/vnd.ms-excel");
        contentTypes.put("xul", "application/vnd.mozilla.xul+xml");
        contentTypes.put("Z", "application/x-compress");
        contentTypes.put("z", "application/x-compress");
        contentTypes.put("zip", "application/zip");
    }

    /**
     * @param extension the extension
     *
     * @return the content type associated with <code>extension</code>.  If no association is found, this method will
     *         return <code>text/plain</code>
     */
    public static String get(String extension) {
        return get(extension, "text/plain");
    }

    /**
     * @param extension the extension
     * @param defaultCt the content type to return if there is no known association for the specified extension
     *
     * @return the content type associated with <code>extension</code> or if no associate is found, returns
     *         <code>defaultCt</code>
     */
    public static String get(String extension, String defaultCt) {
        final String mime = contentTypes.get(extension);
        return mime == null ? defaultCt : mime;
    }

    /**
     * @param extension the extension
     *
     * @return <code>true</code> if the specified extension has been registered otherwise, returns <code>false</code>
     */
    public static boolean contains(String extension) {
        return contentTypes.containsKey(extension);
    }

    /**
     * <p> Associates the specified extension and content type </p>
     *
     * @param extension the extension
     * @param contentType the content type associated with the extension
     */
    public static void add(String extension, String contentType) {
        if (extension != null && extension.length() != 0
            && contentType != null && contentType.length() != 0) {

            contentTypes.put(extension, contentType);
        }
    }
    
    /**
     * @param fileName the filename
     *
     * @return the content type associated with <code>extension</code> of the 
     *         given filename or if no associate is found, returns
     *         <code>null</code>
     */
    public static String getByFilename(String fileName) {
        String extn = getExtension(fileName);
        if (extn != null) {
            return get(extn);
        } else {
            // no extension, no content type
            return null;
        }
    }
    
    /**
     * Get extension of file, without fragment id
     */
    private static String getExtension(String fileName) {
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
}

