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

package com.sun.grizzly.util.http;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Utility class to generate HTTP dates.
 *
 * @author Gustav Trede 
 * @author Remy Maucherat
 */
public final class FastHttpDateFormat {

    protected static final int CACHE_SIZE = 1000;

    protected static final TimeZone GMT_TIME_ZONE = TimeZone.getTimeZone("GMT");
    /**
     * HTTP date format.
     */
    protected static final ThreadLocal<SimpleDateFormat> FORMAT = 
        new ThreadLocal<SimpleDateFormat>() {
            @Override
            protected SimpleDateFormat initialValue() {
                SimpleDateFormat f = 
                    new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
                f.setTimeZone(GMT_TIME_ZONE);
                return f;
            }
        };


    protected final static TimeZone gmtZone = TimeZone.getTimeZone("GMT");

    /**
     * ThreadLocal for the set of SimpleDateFormat formats to use in getDateHeader().
     * GMT timezone - all HTTP dates are on GMT
     */
    protected static final ThreadLocal FORMATS =
        new ThreadLocal() {
            @Override
            protected Object initialValue() {
                SimpleDateFormat[] f = new SimpleDateFormat[3];
                f[0] = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz",
                        Locale.US);
                f[0].setTimeZone(GMT_TIME_ZONE);
                f[1] = new SimpleDateFormat("EEEEEE, dd-MMM-yy HH:mm:ss zzz",
                        Locale.US);
                f[1].setTimeZone(GMT_TIME_ZONE);
                f[2] = new SimpleDateFormat("EEE MMMM d HH:mm:ss yyyy",
                        Locale.US);
                f[2].setTimeZone(GMT_TIME_ZONE);
                return f;
            }
        };


    /**
     * Instant on which the currentDate object was generated.
     */
    protected static volatile long nextGeneration;


    /**
     * Current formatted date.
     */
    protected static volatile String currentDate;


    /**
     * Formatter cache.
     */
    protected static final ConcurrentHashMap<Long, String> formatCache = 
        new ConcurrentHashMap<Long, String>(CACHE_SIZE,0.75f,64);


    /**
     * Parser cache.
     */
    protected static final ConcurrentHashMap<String, Long> parseCache = 
        new ConcurrentHashMap<String, Long>(CACHE_SIZE,0.75f,64);


    // --------------------------------------------------------- Public Methods


    /**
     * Get the current date in HTTP format.
     */
    public static final String getCurrentDate() {
        long now = System.currentTimeMillis();
        if (now > nextGeneration) {
            synchronized(FORMAT) {
                if (now > nextGeneration) {
                    nextGeneration = now + 1000;
                    currentDate = FORMAT.get().format(new Date(now));
                }
            }
        }
        return currentDate;
    }


    /**
     * Get the HTTP format of the specified date.<br>
     * http spec only requre second precision http://tools.ietf.org/html/rfc2616#page-20 <br>
     * therefore we dont use the millisecond precision , but second .
     * truncation is done in the same way for second precision in SimpleDateFormat:<br>
     * (999 millisec. = 0 sec.)
     * @param timestamp in millisec
     * @param the formater used if cache value was not found
     */
    public static final String formatDate(long value, DateFormat threadLocalformat) {
        // truncating to second precision
        // this way we optimally use the cache to only store needed http values
        value = (value/1000)*1000;
        Long longValue =  value;
        String cachedDate = formatCache.get(longValue);
        if (cachedDate != null){
            return cachedDate;
        }
        String newDate = null;
        Date dateValue = new Date(value);
        if (threadLocalformat != null) {
            newDate = threadLocalformat.format(dateValue);
        } else {
            newDate = FORMAT.get().format(dateValue);
        }
        updateFormatCache(longValue, newDate);
        return newDate;

    }


    /**
     * Try to parse the given date as a HTTP date.
     */
    public static final long parseDate(final String value,
            DateFormat[] threadLocalformats) {

        Long cachedDate = parseCache.get(value);
        if (cachedDate != null){
            return cachedDate;
        }
        long date ;
        if (threadLocalformats != null) {
            date = internalParseDate(value, threadLocalformats);
        } else {
            date = internalParseDate(value, (SimpleDateFormat[])FORMATS.get());
        }        
        if (date != -1) {
            updateParseCache(value, date);
        }
        return date;
    }


    /**
     * Parse date with given formatters.
     */
    private static final long internalParseDate (String value, DateFormat[] formats){
        for (int i = 0;i < formats.length; i++) {
            try {
                return formats[i].parse(value).getTime();
            } catch (ParseException e) {
                return -1;
            }
        }
        return -1;
    }


    /**
     * Update cache.
     */
    private static void updateFormatCache(Long key, String value) {
        if (value == null) {
            return;
        }
        if (formatCache.size() > CACHE_SIZE) {
            formatCache.clear();
        }
        formatCache.put(key, value);
    }


    /**
     * Update cache.
     */
    private static void updateParseCache(String key, Long value) {
        if (parseCache.size() > CACHE_SIZE) {
            parseCache.clear(); 
        }
        parseCache.put(key, value);
    }


}
