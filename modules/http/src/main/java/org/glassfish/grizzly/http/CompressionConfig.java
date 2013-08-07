/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2013 Oracle and/or its affiliates. All rights reserved.
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
package org.glassfish.grizzly.http;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpUtils;
import org.glassfish.grizzly.utils.ArraySet;

/**
 * Compression configuration class.
 */
public final class CompressionConfig {

    /**
     * Common CompressionMode interface.
     * It is a temporary solution used to deprecate CompressionLevel enum
     * in http-server module.
     * 
     * This interface is intended to be used in Grizzly core methods only.
     * Please don't use this interface directly in your code.
     * Please use {@link CompressionMode} enum in all the Grizzly core methods,
     * that expect <tt>CompressionModeI</tt>.
     * 
     * @see CompressionMode
     */
    public interface CompressionModeI {
    }

    public enum CompressionMode implements CompressionModeI {

        OFF,
        ON,
        FORCE;

        /**
         * Returns the {@link CompressionMode} based on the string representation.
         */
        public static CompressionMode fromString(final String mode) {
            if ("on".equalsIgnoreCase(mode)) {
                return CompressionMode.ON;
            } else if ("force".equalsIgnoreCase(mode)) {
                return CompressionMode.FORCE;
            } else if ("off".equalsIgnoreCase(mode)) {
                return CompressionMode.OFF;
            }
            
            throw new IllegalArgumentException("Compression mode is not recognized. Supported modes: " +
                    Arrays.toString(CompressionMode.values()));
        }
    }
    
    // compression mode
    private CompressionMode compressionMode;
    // the min size of the entities, which will be compressed
    private int compressionMinSize;
    // mime types of the enitties, which will be compressed
    private final ArraySet<String> compressableMimeTypes =
            new ArraySet<String>(String.class);
    // the user-agents, for which the payload will never be compressed
    private final ArraySet<String> noCompressionUserAgents =
            new ArraySet<String>(String.class);

    public CompressionConfig() {
        compressionMode = CompressionMode.OFF;
    }

    /**
     * The copy constructor.
     * The newly constructed CompressionConfig object will have the same settings as
     * the source one, but actual values will be independent, so changes to one
     * CompressionConfig object will not affect the other one.
     */
    public CompressionConfig(final CompressionConfig compression) {
        set(compression);
    }

    public CompressionConfig(final CompressionMode compressionMode,
            final int compressionMinSize,
            final Set<String> compressableMimeTypes,
            final Set<String> noCompressionUserAgents) {
        setCompressionMode(compressionMode);
        setCompressionMinSize(compressionMinSize);
        setCompressableMimeTypes(compressableMimeTypes);
        setNoCompressionUserAgents(noCompressionUserAgents);
    }

    /**
     * Copies the source CompressionConfig object value into this object.
     * As the result this CompressionConfig object will have the same settings as
     * the source one, but actual values will be independent, so changes to one
     * CompressionConfig object will not affect the other one.
     */
    public void set(final CompressionConfig compression) {
        compressionMode = compression.compressionMode;
        compressionMinSize = compression.compressionMinSize;
        setCompressableMimeTypes(compression.compressableMimeTypes);
        setNoCompressionUserAgents(compression.noCompressionUserAgents);
    }
    
    /**
     * Returns the {@link CompressionMode}.
     */
    public CompressionMode getCompressionMode() {
        return compressionMode;
    }

    /**
     * Sets the {@link CompressionMode}.
     */
    public void setCompressionMode(final CompressionMode mode) {
        this.compressionMode = mode != null ?
                mode :
                CompressionMode.OFF;
    }

    /**
     * Returns the minimum size of an entity, which will be compressed.
     */
    public int getCompressionMinSize() {
        return compressionMinSize;
    }

    /**
     * Sets the minimum size of an entity, which will be compressed.
     */
    public void setCompressionMinSize(int compressionMinSize) {
        this.compressionMinSize = compressionMinSize;
    }

    /**
     * Returns the read-only set of the mime-types, which are allowed to be compressed.
     * Empty set means *all* mime-types are allowed to be compressed.
     */
    public Set<String> getCompressableMimeTypes() {
        return Collections.unmodifiableSet(compressableMimeTypes);
    }

    /**
     * Sets the set of the mime-types, which are allowed to be compressed.
     * Empty set means *all* mime-types are allowed to be compressed.
     * 
     * Please note that CompressionConfig object will copy the source Set content,
     * so further changes made on the source Set will not affect CompressionConfig
     * object state.
     */
    public void setCompressableMimeTypes(final Set<String> compressableMimeTypes) {
        this.compressableMimeTypes.clear();
        
        if (compressableMimeTypes != null && !compressableMimeTypes.isEmpty()) {
            this.compressableMimeTypes.addAll(compressableMimeTypes);
        }
    }

    /**
     * Sets the set of the mime-types, which are allowed to be compressed.
     * Empty set means *all* mime-types are allowed to be compressed.
     * 
     * Please note that CompressionConfig object will copy the source Set content,
     * so further changes made on the source Set will not affect CompressionConfig
     * object state.
     */    
    public void setCompressableMimeTypes(final String... compressableMimeTypes) {
        this.compressableMimeTypes.clear();
        
        if (compressableMimeTypes.length > 0) {
            this.compressableMimeTypes.addAll(compressableMimeTypes);
        }
    }

    /**
     * Returns the read-only set of the user-agents, which will be always
     * responded with uncompressed are.
     * 
     * Empty set means that compressed data could be sent to *all* user-agents.
     */    
    public Set<String> getNoCompressionUserAgents() {
        return Collections.unmodifiableSet(noCompressionUserAgents);
    }

    /**
     * Sets the set of the user-agents, which will be always responded with
     * uncompressed data.
     * Empty set means that compressed data could be sent to *all* user-agents.
     * 
     * Please note that CompressionConfig object will copy the source Set content,
     * so further changes made on the source Set will not affect CompressionConfig
     * object state.
     */
    public void setNoCompressionUserAgents(final Set<String> noCompressionUserAgents) {
        this.noCompressionUserAgents.clear();
        
        if (noCompressionUserAgents != null && !noCompressionUserAgents.isEmpty()) {
            this.noCompressionUserAgents.addAll(noCompressionUserAgents);
        }
    }

    /**
     * Sets the set of the user-agents, which will be always responded with
     * uncompressed data.
     * Empty set means that compressed data could be sent to *all* user-agents.
     * 
     * Please note that CompressionConfig object will copy the source Set content,
     * so further changes made on the source Set will not affect CompressionConfig
     * object state.
     */    
    public void setNoCompressionUserAgents(final String... noCompressionUserAgents) {
        this.noCompressionUserAgents.clear();
        
        if (noCompressionUserAgents.length > 0) {
            this.noCompressionUserAgents.addAll(noCompressionUserAgents);
        }
    }
    
    /**
     * Returns <tt>true</tt> if a client, based on its {@link HttpRequestPacket},
     * could be responded with compressed data, or <tt>false</tt> otherwise.
     * 
     * @param compressionConfig {@link CompressionConfig}
     * @param request client-side {@link HttpRequestPacket}
     * @param aliases compression algorithm aliases (to match with Accept-Encoding header)
     * @return <tt>true</tt> if a client, based on its {@link HttpRequestPacket},
     *         could be responded with compressed data, or <tt>false</tt> otherwise
     */
    public static boolean isClientSupportCompression(
            final CompressionConfig compressionConfig,
            final HttpRequestPacket request,
            final String[] aliases) {
        final CompressionMode mode = compressionConfig.getCompressionMode();

        switch (mode) {
            case OFF:
                return false;
            default:
                // Compress only since HTTP 1.1
                if (org.glassfish.grizzly.http.Protocol.HTTP_1_1 !=
                        request.getProtocol()) {
                    return false;
                }

                if (!isClientSupportContentEncoding(request, aliases)) {
                    return false;
                }

                // If force mode, always compress (test purposes only)
                if (mode == CompressionMode.FORCE) {
                    return true;
                }

                return compressionConfig.checkUserAgent(request);
        }
    }
    
    /**
     * Returns <tt>true</tt> if based on this configuration user-agent,
     * specified in the {@link HttpRequestPacket}, can receive compressed
     * data.
     */
    public boolean checkUserAgent(final HttpRequestPacket request) {
        // Check for incompatible Browser
        if (!noCompressionUserAgents.isEmpty()) {
            final DataChunk userAgentValueDC =
                    request.getHeaders().getValue(Header.UserAgent);
            if (userAgentValueDC != null
                    && indexOf(noCompressionUserAgents.getArray(),
                    userAgentValueDC) != -1) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Returns <tt>true</tt> if the resource with the given content-type
     * could be compressed, or <tt>false</tt> otherwise.
     */
    public boolean checkMimeType(final String contentType) {
        if (!compressableMimeTypes.isEmpty()) {
            return indexOfStartsWith(compressableMimeTypes.getArray(),
                    contentType) != -1;
        }
        
        return true;
    }
    
    private static boolean isClientSupportContentEncoding(
            HttpRequestPacket request, final String[] aliases) {
        // Check if browser support gzip encoding
        final DataChunk acceptEncodingDC =
                request.getHeaders().getValue(Header.AcceptEncoding);
        if (acceptEncodingDC == null) {
            return false;
        }
        String alias = null;
        int idx = -1;
        for (int i = 0, len = aliases.length; i < len; i++) {
            alias = aliases[i];
            idx = acceptEncodingDC.indexOf(alias, 0);
            if (idx != -1) {
                break;
            }
        }

        if (idx == -1) {
            return false;
        }

        assert alias != null;
        
        // we only care about q=0/q=0.0.  If present, the user-agent
        // doesn't support this particular compression.
        int qvalueStart = acceptEncodingDC.indexOf(';', idx + alias.length());
        if (qvalueStart != -1) {
            qvalueStart = acceptEncodingDC.indexOf('=', qvalueStart);
            final int commaIdx = acceptEncodingDC.indexOf(',', qvalueStart);
            final int qvalueEnd = commaIdx != -1 ? commaIdx : acceptEncodingDC.getLength();
            if (HttpUtils.convertQValueToFloat(acceptEncodingDC,
                    qvalueStart + 1,
                    qvalueEnd) == 0.0f) {
                return false;
            }
        }

        return true;
    }
    
    private static int indexOf(String[] aliases, DataChunk dc) {
        if (dc == null || dc.isNull()) {
            return -1;
        }
        for (int i = 0; i < aliases.length; i++) {
            final String alias = aliases[i];
            if (dc.indexOf(alias, 0) != -1) {
                return i;
            }
        }
        return -1;
    }

    private static int indexOfStartsWith(String[] aliases, String s) {
        if (s == null || s.length() == 0) {
            return -1;
        }
        for (int i = 0; i < aliases.length; i++) {
            final String alias = aliases[i];
            if (s.startsWith(alias)) {
                return i;
            }
        }
        return -1;
    }    
}