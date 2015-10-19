/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2007-2015 Oracle and/or its affiliates. All rights reserved.
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

import java.io.UnsupportedEncodingException;
import java.text.DateFormat;
import java.text.FieldPosition;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import org.glassfish.grizzly.utils.DataStructures;

import static org.glassfish.grizzly.http.util.HttpCodecUtils.*;
import org.glassfish.grizzly.utils.Charsets;

/**
 * Utility class to generate HTTP dates.
 *
 * @author Gustav Trede 
 * @author Remy Maucherat
 */
public final class FastHttpDateFormat {

    private static final String ASCII_CHARSET_NAME = Charsets.ASCII_CHARSET.name();
    
    private static final int CACHE_SIZE = 1000;

    private static final TimeZone GMT_TIME_ZONE = TimeZone.getTimeZone("GMT");

    private static final SimpleDateFormatter FORMATTER = new SimpleDateFormatter();
    
    /**
     * HTTP date format.
     */
    private static final ThreadLocal<SimpleDateFormatter> FORMAT =
        new ThreadLocal<SimpleDateFormatter>() {
            @Override
            protected SimpleDateFormatter initialValue() {
                return new SimpleDateFormatter();
            }
        };

    private static final class SimpleDateFormatter {
        private final Date date;
        private final SimpleDateFormat f;
        private final FieldPosition pos = new FieldPosition(-1);

        public SimpleDateFormatter() {
            date = new Date();
            f = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US);
            f.setTimeZone(GMT_TIME_ZONE);
        }

        public final String format(final long timeMillis) {
            date.setTime(timeMillis);
            return f.format(date);
        }
        
        public final StringBuffer formatTo(final long timeMillis,
                final StringBuffer buffer) {
            date.setTime(timeMillis);
            return f.format(date, buffer, pos);
        }        
    }

    /**
     * ThreadLocal for the set of SimpleDateFormat formats to use in getDateHeader().
     * GMT timezone - all HTTP dates are on GMT
     */
    private static final ThreadLocal FORMATS =
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
    private static volatile long nextGeneration;

    private static final AtomicBoolean isGeneratingNow = new AtomicBoolean();
    
    private static final StringBuffer currentDateBuffer = new StringBuffer();
    
    /**
     * Current formatted date as byte[].
     */
    private static byte[] currentDateBytes;

    /**
     * Current formatted date.
     */
    private static String cachedStringDate;
    private static volatile byte[] dateBytesForCachedStringDate;

    
    /**
     * Formatter cache.
     */
    private static final ConcurrentMap<Long, String> formatCache = 
        DataStructures.getConcurrentMap(CACHE_SIZE, 0.75f, 64);


    /**
     * Parser cache.
     */
    private static final ConcurrentMap<String, Long> parseCache = 
        DataStructures.getConcurrentMap(CACHE_SIZE, 0.75f, 64);


    // --------------------------------------------------------- Public Methods


    /**
     * Get the current date in HTTP format.
     */
    public static String getCurrentDate() {
        final byte[] currentDateBytesNow = getCurrentDateBytes();
        if (currentDateBytesNow != dateBytesForCachedStringDate) {
            try {
                cachedStringDate = new String(currentDateBytesNow, ASCII_CHARSET_NAME);
                dateBytesForCachedStringDate = currentDateBytesNow;
            } catch (UnsupportedEncodingException ignored) {
                // should never reach this line
            }
        }
        
        return cachedStringDate;
    }

    /**
     * Get the current date in HTTP format.
     */
    public static byte[] getCurrentDateBytes() {
        final long now = System.currentTimeMillis();
        final long diff = now - nextGeneration;
        
        if (diff > 0 &&
                (diff > 5000 ||
                    (!isGeneratingNow.get() &&
                     isGeneratingNow.compareAndSet(false, true)))) {
            synchronized (FORMAT) {
                if (now > nextGeneration) {
                    currentDateBuffer.setLength(0);
                    FORMATTER.formatTo(now, currentDateBuffer);
                    currentDateBytes = toCheckedByteArray(currentDateBuffer);
                    nextGeneration = now + 1000;
                }
                
                isGeneratingNow.set(false);
            }
        }
        return currentDateBytes;
    }
    
    /**
     * Get the HTTP format of the specified date.<br>
     * http spec only requre second precision http://tools.ietf.org/html/rfc2616#page-20 <br>
     * therefore we dont use the millisecond precision , but second .
     * truncation is done in the same way for second precision in SimpleDateFormat:<br>
     * (999 millisec. = 0 sec.)
     * @param value in milli-seconds
     * @param threadLocalFormat the {@link DateFormat} used if cache value was 
     *  not found
     */
    public static String formatDate(long value, DateFormat threadLocalFormat) {
        // truncating to second precision
        // this way we optimally use the cache to only store needed http values
        value = (value/1000)*1000;
        final Long longValue =  value;
        String cachedDate = formatCache.get(longValue);
        if (cachedDate != null){
            return cachedDate;
        }
        String newDate;
//        Date dateValue = new Date(value);
        if (threadLocalFormat != null) {
            newDate = threadLocalFormat.format(value);
        } else {
            newDate = FORMAT.get().format(value);
        }
        updateFormatCache(longValue, newDate);
        return newDate;

    }


    /**
     * Try to parse the given date as a HTTP date.
     */
    public static long parseDate(final String value,
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
    private static long internalParseDate (String value, DateFormat[] formats){
        for (int i = 0;i < formats.length; i++) {
            try {
                return formats[i].parse(value).getTime();
            } catch (ParseException ignore) {
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
