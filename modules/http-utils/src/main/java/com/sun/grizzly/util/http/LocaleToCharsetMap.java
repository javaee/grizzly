

/*
 * The contents of this file are subject to the terms
 * of the Common Development and Distribution License
 * (the "License").  You may not use this file except
 * in compliance with the License.
 *
 * You can obtain a copy of the license at
 * glassfish/bootstrap/legal/CDDLv1.0.txt or
 * https://glassfish.dev.java.net/public/CDDLv1.0.html.
 * See the License for the specific language governing
 * permissions and limitations under the License.
 *
 * When distributing Covered Code, include this CDDL
 * HEADER in each file and include the License file at
 * glassfish/bootstrap/legal/CDDLv1.0.txt.  If applicable,
 * add the following below this CDDL HEADER, with the
 * fields enclosed by brackets "[]" replaced with your
 * own identifying information: Portions Copyright [yyyy]
 * [name of copyright owner]
 *
 * Copyright 2005 Sun Microsystems, Inc. All rights reserved.
 *
 * Portions Copyright Apache Software Foundation.
 */ 

/*
 *
 * This class was originally written by Jason Hunter <jhunter@acm.org>
 * as part of the book "Java Servlet Programming" (O'Reilly).  
 * See http://www.servlets.com/book for more information.
 * Used by Sun Microsystems with permission.
 *
 */

package com.sun.grizzly.util.http;

import java.util.*;

/** 
 * A mapping to determine the (somewhat arbitrarily) preferred charset for 
 * a given locale.  Supports all locales recognized in JDK 1.1.
 * This class was originally written by Jason Hunter [jhunter@acm.org]
 * as part of the book "Java Servlet Programming" (O'Reilly).
 * See <a href="http://www.servlets.com/book">
 * http://www.servlets.com/book</a> for more information.
 * Used by Sun Microsystems with permission.
 */
public class LocaleToCharsetMap {

  private static Hashtable map;

  static {
    map = new Hashtable();

    map.put("ar", "ISO-8859-6");
    map.put("be", "ISO-8859-5");
    map.put("bg", "ISO-8859-5");
    map.put("ca", "ISO-8859-1");
    map.put("cs", "ISO-8859-2");
    map.put("da", "ISO-8859-1");
    map.put("de", "ISO-8859-1");
    map.put("el", "ISO-8859-7");
    map.put("en", "ISO-8859-1");
    map.put("es", "ISO-8859-1");
    map.put("et", "ISO-8859-1");
    map.put("fi", "ISO-8859-1");
    map.put("fr", "ISO-8859-1");
    map.put("hr", "ISO-8859-2");
    map.put("hu", "ISO-8859-2");
    map.put("is", "ISO-8859-1");
    map.put("it", "ISO-8859-1");
    map.put("iw", "ISO-8859-8");
    map.put("ja", "Shift_JIS");
    map.put("ko", "EUC-KR");     // Requires JDK 1.1.6
    map.put("lt", "ISO-8859-2");
    map.put("lv", "ISO-8859-2");
    map.put("mk", "ISO-8859-5");
    map.put("nl", "ISO-8859-1");
    map.put("no", "ISO-8859-1");
    map.put("pl", "ISO-8859-2");
    map.put("pt", "ISO-8859-1");
    map.put("ro", "ISO-8859-2");
    map.put("ru", "ISO-8859-5");
    map.put("sh", "ISO-8859-5");
    map.put("sk", "ISO-8859-2");
    map.put("sl", "ISO-8859-2");
    map.put("sq", "ISO-8859-2");
    map.put("sr", "ISO-8859-5");
    map.put("sv", "ISO-8859-1");
    map.put("tr", "ISO-8859-9");
    map.put("uk", "ISO-8859-5");
    map.put("zh", "GB2312");
    map.put("zh_TW", "Big5");

  }

  /**
   * Gets the preferred charset for the given locale, or null if the locale
   * is not recognized.
   *
   * @param loc the locale
   * @return the preferred charset
   */
  public static String getCharset(Locale loc) {
    String charset;

    // Try for an full name match (may include country)
    charset = (String) map.get(loc.toString());
    if (charset != null) return charset;

    // If a full name didn't match, try just the language
    charset = (String) map.get(loc.getLanguage());
    return charset;  // may be null
  }
}
