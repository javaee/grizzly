/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2011-2013 Oracle and/or its affiliates. All rights reserved.
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

package com.sun.grizzly.util;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.UnsupportedCharsetException;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Charset utility class.
 *
 * @author Alexey Stashok
 */
public final class Charsets {
    static {
        if (Boolean.getBoolean(Charsets.class.getName() + ".preloadAllCharsets")) {
            preloadAllCharsets();
        }
    }
    
    public static final String DEFAULT_CHARACTER_ENCODING = "ISO-8859-1";

    private static final ConcurrentHashMap<String, Charset> charsetAliasMap =
            new ConcurrentHashMap<String, Charset>();

    public static final Charset ASCII_CHARSET = lookupCharset("ASCII");
    public static final Charset UTF8_CHARSET = lookupCharset("UTF-8");
    public static final Charset DEFAULT_CHARSET = lookupCharset(DEFAULT_CHARACTER_ENCODING);

    public static final int CODECS_CACHE_SIZE = 2;
    private static final CharsetCodecResolver DECODER_RESOLVER =
            new DecoderResolver();
    private static final CharsetCodecResolver ENCODER_RESOLVER =
            new EncoderResolver();
    
    private static volatile boolean areCharsetsPreloaded;
    
    /**
     * Lookup a {@link Charset} by name.
     * Fixes Charset concurrency issue (http://paul.vox.com/library/post/the-mysteries-of-java-character-set-performance.html)
     *
     * @param charsetName
     * @return {@link Charset}
     */
    public static Charset lookupCharset(final String charsetName) {
        final String charsetLowerCase = charsetName.toLowerCase(Locale.US);
        
        Charset charset = charsetAliasMap.get(charsetLowerCase);
        if (charset == null) {
            if (areCharsetsPreloaded) {
                // if all charsets are preloaded - throw Exception right away
                throw new UnsupportedCharsetException(charsetName);
            }
            
            final Charset newCharset = Charset.forName(charsetLowerCase);
            final Charset prevCharset =
                    charsetAliasMap.putIfAbsent(charsetLowerCase, newCharset);
            
            charset = prevCharset == null ? newCharset : prevCharset;
        }

        return charset;
    }
    
    /**
     * Preloads all JVM available {@link Charset}s, which makes charset search
     * faster (for memory cost), especially non-existed charsets, because it
     * lets us avoid pretty expensive {@link Charset#forName(java.lang.String)}
     * call.
     */
    public static void preloadAllCharsets() {
        synchronized (charsetAliasMap) {
            final Map<String, Charset> charsetsMap = Charset.availableCharsets();
            for (Charset charset : charsetsMap.values()) {
                charsetAliasMap.put(
                        charset.name().toLowerCase(Locale.US), charset);
                for (String alias : charset.aliases()) {
                    charsetAliasMap.put(alias.toLowerCase(Locale.US), charset);
                }
            }
            
            areCharsetsPreloaded = true;
        }
    }
    
    /**
     * Removes all the charsets preloaded before.
     */
    public static void drainAllCharsets() {
        synchronized (charsetAliasMap) {
            areCharsetsPreloaded = false;
            charsetAliasMap.clear();
        }
    }
    
    /**
     * Return the {@link Charset}'s {@link CharsetDecoder}.
     * The <tt>Charsets</tt> class maintains the {@link CharsetDecoder} thread-local
     * cache.  Be aware - this shouldn't be used by multiple threads.
     * 
     * @param charset {@link Charset}.
     * @return the {@link Charset}'s {@link CharsetDecoder}.
     */
    public static CharsetDecoder getCharsetDecoder(final Charset charset) {
        if (charset == null) {
            throw new IllegalArgumentException("Charset can not be null");
        }
        
        final CharsetDecoder decoder = obtainCodecsCache().getDecoder(charset);
        decoder.reset();
        
        return decoder;
    }
    
    /**
     * Return the {@link Charset}'s {@link CharsetEncoder}.
     * The <tt>Charsets</tt> class maintains the {@link CharsetEncoder} thread-local
     * cache.  Be aware - this shouldn't be used by multiple threads.
     * 
     * @param charset {@link Charset}.
     * @return the {@link Charset}'s {@link CharsetEncoder}.
     */
    public static CharsetEncoder getCharsetEncoder(final Charset charset) {
        if (charset == null) {
            throw new IllegalArgumentException("Charset can not be null");
        }
        
        final CharsetEncoder encoder = obtainCodecsCache().getEncoder(charset);
        encoder.reset();
        
        return encoder;
    }
    
    private final static ThreadLocal<CodecsCache> CODECS_CACHE =
            new ThreadLocal<CodecsCache>() {
        @Override
        protected CodecsCache initialValue() {
            return new CodecsCache();
        }
    };
    
    private static CodecsCache obtainCodecsCache() {
        return CODECS_CACHE.get();
    }

    private static final class CodecsCache {
        private final Object[] decoders =
                new Object[CODECS_CACHE_SIZE];
        private final Object[] encoders =
                new Object[CODECS_CACHE_SIZE];
        
        public CharsetDecoder getDecoder(final Charset charset) {
            return (CharsetDecoder) obtainElementByCharset(
                    decoders, charset, DECODER_RESOLVER);
        }
        
        public CharsetEncoder getEncoder(final Charset charset) {
            return (CharsetEncoder) obtainElementByCharset(
                    encoders, charset, ENCODER_RESOLVER);
        }
        
        private static Object obtainElementByCharset(final Object[] array,
                final Charset charset, final CharsetCodecResolver resolver) {
            
            int i = 0;
            for (; i < array.length; i++) {
                final Object currentElement = array[i];
                
                if (currentElement == null) {
                    i++; // to make 
                    break;
                }
                
                if (charset.equals(resolver.charset(currentElement))) {
                    makeFirst(array, i, currentElement);
                    return currentElement;
                }
            }

            final Object newElement = resolver.newElement(charset);
            makeFirst(array, i - 1, newElement);
            return newElement;            
        }
        
        private static void makeFirst(final Object[] array, final int offs,
                final Object element) {
            for (int i = offs - 1; i >= 0; i--) {
                array[i + 1] = array[i];
            }
            
            array[0] = element;
        }       
    }
    
    private static interface CharsetCodecResolver {
        Charset charset(Object element);
        Object newElement(Charset charset);
    }

    private final static class DecoderResolver implements CharsetCodecResolver {
        public Charset charset(final Object element) {
            return ((CharsetDecoder) element).charset();
        }

        public Object newElement(final Charset charset) {
            return charset.newDecoder();
        }

    }

    private final static class EncoderResolver implements CharsetCodecResolver {

        public Charset charset(final Object element) {
            return ((CharsetEncoder) element).charset();
        }

        public Object newElement(final Charset charset) {
            return charset.newEncoder();
        }
    }
    
}
