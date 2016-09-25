/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015-2016 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http2;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.FilterChainEvent;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.http.HttpBrokenContentException;
import org.glassfish.grizzly.http.HttpContent;
import org.glassfish.grizzly.http.HttpContext;
import org.glassfish.grizzly.http.HttpEvents;
import org.glassfish.grizzly.http.HttpHeader;
import org.glassfish.grizzly.http.HttpPacket;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.HttpResponsePacket;
import org.glassfish.grizzly.http.Method;
import org.glassfish.grizzly.http.Protocol;
import org.glassfish.grizzly.http.util.DataChunk;
import org.glassfish.grizzly.http.util.FastHttpDateFormat;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http.util.MimeHeaders;
import org.glassfish.grizzly.http2.frames.ErrorCode;
import org.glassfish.grizzly.http2.frames.HeaderBlockHead;
import org.glassfish.grizzly.http2.frames.HeadersFrame;
import org.glassfish.grizzly.http2.frames.Http2Frame;
import org.glassfish.grizzly.http2.frames.SettingsFrame;
import org.glassfish.grizzly.ssl.SSLUtils;
import org.glassfish.grizzly.utils.Pair;

import javax.net.ssl.SSLEngine;

import static org.glassfish.grizzly.http2.Constants.IN_FIN_TERMINATION;
import static org.glassfish.grizzly.http2.Http2Constants.HTTP2_CLEAR;

/**
 *
 * @author oleksiys
 */
public class Http2ServerFilter extends Http2BaseFilter {
    private final static Logger LOGGER = Grizzly.logger(Http2ServerFilter.class);

    protected static final String[] CIPHER_SUITE_BLACK_LIST = {
            "TLS_NULL_WITH_NULL_NULL",
            "TLS_RSA_WITH_NULL_MD5",
            "TLS_RSA_WITH_NULL_SHA",
            "TLS_RSA_EXPORT_WITH_RC4_40_MD5",
            "TLS_RSA_WITH_RC4_128_MD5",
            "TLS_RSA_WITH_RC4_128_SHA",
            "TLS_RSA_EXPORT_WITH_RC2_CBC_40_MD5",
            "TLS_RSA_WITH_IDEA_CBC_SHA",
            "TLS_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "TLS_RSA_WITH_DES_CBC_SHA",
            "TLS_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_DH_DSS_EXPORT_WITH_DES40_CBC_SHA",
            "TLS_DH_DSS_WITH_DES_CBC_SHA",
            "TLS_DH_DSS_WITH_3DES_EDE_CBC_SHA",
            "TLS_DH_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "TLS_DH_RSA_WITH_DES_CBC_SHA",
            "TLS_DH_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_DHE_DSS_EXPORT_WITH_DES40_CBC_SHA",
            "TLS_DHE_DSS_WITH_DES_CBC_SHA",
            "TLS_DHE_DSS_WITH_3DES_EDE_CBC_SHA",
            "TLS_DHE_RSA_EXPORT_WITH_DES40_CBC_SHA",
            "TLS_DHE_RSA_WITH_DES_CBC_SHA",
            "TLS_DHE_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_DH_anon_EXPORT_WITH_RC4_40_MD5",
            "TLS_DH_anon_WITH_RC4_128_MD5",
            "TLS_DH_anon_EXPORT_WITH_DES40_CBC_SHA",
            "TLS_DH_anon_WITH_DES_CBC_SHA",
            "TLS_DH_anon_WITH_3DES_EDE_CBC_SHA",
            "TLS_KRB5_WITH_DES_CBC_SHA",
            "TLS_KRB5_WITH_3DES_EDE_CBC_SHA",
            "TLS_KRB5_WITH_RC4_128_SHA",
            "TLS_KRB5_WITH_IDEA_CBC_SHA",
            "TLS_KRB5_WITH_DES_CBC_MD5",
            "TLS_KRB5_WITH_3DES_EDE_CBC_MD5",
            "TLS_KRB5_WITH_RC4_128_MD5",
            "TLS_KRB5_WITH_IDEA_CBC_MD5",
            "TLS_KRB5_EXPORT_WITH_DES_CBC_40_SHA",
            "TLS_KRB5_EXPORT_WITH_RC2_CBC_40_SHA",
            "TLS_KRB5_EXPORT_WITH_RC4_40_SHA",
            "TLS_KRB5_EXPORT_WITH_DES_CBC_40_MD5",
            "TLS_KRB5_EXPORT_WITH_RC2_CBC_40_MD5",
            "TLS_KRB5_EXPORT_WITH_RC4_40_MD5",
            "TLS_PSK_WITH_NULL_SHA",
            "TLS_DHE_PSK_WITH_NULL_SHA",
            "TLS_RSA_PSK_WITH_NULL_SHA",
            "TLS_RSA_WITH_AES_128_CBC_SHA",
            "TLS_DH_DSS_WITH_AES_128_CBC_SHA",
            "TLS_DH_RSA_WITH_AES_128_CBC_SHA",
            "TLS_DHE_DSS_WITH_AES_128_CBC_SHA",
            "TLS_DHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_DH_anon_WITH_AES_128_CBC_SHA",
            "TLS_RSA_WITH_AES_256_CBC_SHA",
            "TLS_DH_DSS_WITH_AES_256_CBC_SHA",
            "TLS_DH_RSA_WITH_AES_256_CBC_SHA",
            "TLS_DHE_DSS_WITH_AES_256_CBC_SHA",
            "TLS_DHE_RSA_WITH_AES_256_CBC_SHA",
            "TLS_DH_anon_WITH_AES_256_CBC_SHA",
            "TLS_RSA_WITH_NULL_SHA256",
            "TLS_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_DH_DSS_WITH_AES_128_CBC_SHA256",
            "TLS_DH_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_DHE_DSS_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_WITH_CAMELLIA_128_CBC_SHA",
            "TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA",
            "TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA",
            "TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA",
            "TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA",
            "TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA",
            "TLS_DHE_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_DH_DSS_WITH_AES_256_CBC_SHA256",
            "TLS_DH_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_DHE_DSS_WITH_AES_256_CBC_SHA256",
            "TLS_DHE_RSA_WITH_AES_256_CBC_SHA256",
            "TLS_DH_anon_WITH_AES_128_CBC_SHA256",
            "TLS_DH_anon_WITH_AES_256_CBC_SHA256",
            "TLS_RSA_WITH_CAMELLIA_256_CBC_SHA",
            "TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA",
            "TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA",
            "TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA",
            "TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA",
            "TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA",
            "TLS_PSK_WITH_RC4_128_SHA",
            "TLS_PSK_WITH_3DES_EDE_CBC_SHA",
            "TLS_PSK_WITH_AES_128_CBC_SHA",
            "TLS_PSK_WITH_AES_256_CBC_SHA",
            "TLS_DHE_PSK_WITH_RC4_128_SHA",
            "TLS_DHE_PSK_WITH_3DES_EDE_CBC_SHA",
            "TLS_DHE_PSK_WITH_AES_128_CBC_SHA",
            "TLS_DHE_PSK_WITH_AES_256_CBC_SHA",
            "TLS_RSA_PSK_WITH_RC4_128_SHA",
            "TLS_RSA_PSK_WITH_3DES_EDE_CBC_SHA",
            "TLS_RSA_PSK_WITH_AES_128_CBC_SHA",
            "TLS_RSA_PSK_WITH_AES_256_CBC_SHA",
            "TLS_RSA_WITH_SEED_CBC_SHA",
            "TLS_DH_DSS_WITH_SEED_CBC_SHA",
            "TLS_DH_RSA_WITH_SEED_CBC_SHA",
            "TLS_DHE_DSS_WITH_SEED_CBC_SHA",
            "TLS_DHE_RSA_WITH_SEED_CBC_SHA",
            "TLS_DH_anon_WITH_SEED_CBC_SHA",
            "TLS_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_DH_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_DH_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_DH_DSS_WITH_AES_128_GCM_SHA256",
            "TLS_DH_DSS_WITH_AES_256_GCM_SHA384",
            "TLS_DH_anon_WITH_AES_128_GCM_SHA256",
            "TLS_DH_anon_WITH_AES_256_GCM_SHA384",
            "TLS_PSK_WITH_AES_128_GCM_SHA256",
            "TLS_PSK_WITH_AES_256_GCM_SHA384",
            "TLS_RSA_PSK_WITH_AES_128_GCM_SHA256",
            "TLS_RSA_PSK_WITH_AES_256_GCM_SHA384",
            "TLS_PSK_WITH_AES_128_CBC_SHA256",
            "TLS_PSK_WITH_AES_256_CBC_SHA384",
            "TLS_PSK_WITH_NULL_SHA256",
            "TLS_PSK_WITH_NULL_SHA384",
            "TLS_DHE_PSK_WITH_AES_128_CBC_SHA256",
            "TLS_DHE_PSK_WITH_AES_256_CBC_SHA384",
            "TLS_DHE_PSK_WITH_NULL_SHA256",
            "TLS_DHE_PSK_WITH_NULL_SHA384",
            "TLS_RSA_PSK_WITH_AES_128_CBC_SHA256",
            "TLS_RSA_PSK_WITH_AES_256_CBC_SHA384",
            "TLS_RSA_PSK_WITH_NULL_SHA256",
            "TLS_RSA_PSK_WITH_NULL_SHA384",
            "TLS_RSA_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_DH_DSS_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_DH_RSA_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_DHE_DSS_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_DHE_RSA_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_DH_anon_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_RSA_WITH_CAMELLIA_256_CBC_SHA256",
            "TLS_DH_DSS_WITH_CAMELLIA_256_CBC_SHA256",
            "TLS_DH_RSA_WITH_CAMELLIA_256_CBC_SHA256",
            "TLS_DHE_DSS_WITH_CAMELLIA_256_CBC_SHA256",
            "TLS_DHE_RSA_WITH_CAMELLIA_256_CBC_SHA256",
            "TLS_DH_anon_WITH_CAMELLIA_256_CBC_SHA256",
            "TLS_EMPTY_RENEGOTIATION_INFO_SCSV",
            "TLS_ECDH_ECDSA_WITH_NULL_SHA",
            "TLS_ECDH_ECDSA_WITH_RC4_128_SHA",
            "TLS_ECDH_ECDSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_NULL_SHA",
            "TLS_ECDHE_ECDSA_WITH_RC4_128_SHA",
            "TLS_ECDHE_ECDSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDH_RSA_WITH_NULL_SHA",
            "TLS_ECDH_RSA_WITH_RC4_128_SHA",
            "TLS_ECDH_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_NULL_SHA",
            "TLS_ECDHE_RSA_WITH_RC4_128_SHA",
            "TLS_ECDHE_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA",
            "TLS_ECDH_anon_WITH_NULL_SHA",
            "TLS_ECDH_anon_WITH_RC4_128_SHA",
            "TLS_ECDH_anon_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDH_anon_WITH_AES_128_CBC_SHA",
            "TLS_ECDH_anon_WITH_AES_256_CBC_SHA",
            "TLS_SRP_SHA_WITH_3DES_EDE_CBC_SHA",
            "TLS_SRP_SHA_RSA_WITH_3DES_EDE_CBC_SHA",
            "TLS_SRP_SHA_DSS_WITH_3DES_EDE_CBC_SHA",
            "TLS_SRP_SHA_WITH_AES_128_CBC_SHA",
            "TLS_SRP_SHA_RSA_WITH_AES_128_CBC_SHA",
            "TLS_SRP_SHA_DSS_WITH_AES_128_CBC_SHA",
            "TLS_SRP_SHA_WITH_AES_256_CBC_SHA",
            "TLS_SRP_SHA_RSA_WITH_AES_256_CBC_SHA",
            "TLS_SRP_SHA_DSS_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_ECDSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDHE_ECDSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDH_ECDSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDH_ECDSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDHE_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDHE_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDH_RSA_WITH_AES_128_CBC_SHA256",
            "TLS_ECDH_RSA_WITH_AES_256_CBC_SHA384",
            "TLS_ECDH_ECDSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDH_ECDSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDH_RSA_WITH_AES_128_GCM_SHA256",
            "TLS_ECDH_RSA_WITH_AES_256_GCM_SHA384",
            "TLS_ECDHE_PSK_WITH_RC4_128_SHA",
            "TLS_ECDHE_PSK_WITH_3DES_EDE_CBC_SHA",
            "TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA",
            "TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA",
            "TLS_ECDHE_PSK_WITH_AES_128_CBC_SHA256",
            "TLS_ECDHE_PSK_WITH_AES_256_CBC_SHA384",
            "TLS_ECDHE_PSK_WITH_NULL_SHA",
            "TLS_ECDHE_PSK_WITH_NULL_SHA256",
            "TLS_ECDHE_PSK_WITH_NULL_SHA384",
            "TLS_RSA_WITH_ARIA_128_CBC_SHA256",
            "TLS_RSA_WITH_ARIA_256_CBC_SHA384",
            "TLS_DH_DSS_WITH_ARIA_128_CBC_SHA256",
            "TLS_DH_DSS_WITH_ARIA_256_CBC_SHA384",
            "TLS_DH_RSA_WITH_ARIA_128_CBC_SHA256",
            "TLS_DH_RSA_WITH_ARIA_256_CBC_SHA384",
            "TLS_DHE_DSS_WITH_ARIA_128_CBC_SHA256",
            "TLS_DHE_DSS_WITH_ARIA_256_CBC_SHA384",
            "TLS_DHE_RSA_WITH_ARIA_128_CBC_SHA256",
            "TLS_DHE_RSA_WITH_ARIA_256_CBC_SHA384",
            "TLS_DH_anon_WITH_ARIA_128_CBC_SHA256",
            "TLS_DH_anon_WITH_ARIA_256_CBC_SHA384",
            "TLS_ECDHE_ECDSA_WITH_ARIA_128_CBC_SHA256",
            "TLS_ECDHE_ECDSA_WITH_ARIA_256_CBC_SHA384",
            "TLS_ECDH_ECDSA_WITH_ARIA_128_CBC_SHA256",
            "TLS_ECDH_ECDSA_WITH_ARIA_256_CBC_SHA384",
            "TLS_ECDHE_RSA_WITH_ARIA_128_CBC_SHA256",
            "TLS_ECDHE_RSA_WITH_ARIA_256_CBC_SHA384",
            "TLS_ECDH_RSA_WITH_ARIA_128_CBC_SHA256",
            "TLS_ECDH_RSA_WITH_ARIA_256_CBC_SHA384",
            "TLS_RSA_WITH_ARIA_128_GCM_SHA256",
            "TLS_RSA_WITH_ARIA_256_GCM_SHA384",
            "TLS_DH_RSA_WITH_ARIA_128_GCM_SHA256",
            "TLS_DH_RSA_WITH_ARIA_256_GCM_SHA384",
            "TLS_DH_DSS_WITH_ARIA_128_GCM_SHA256",
            "TLS_DH_DSS_WITH_ARIA_256_GCM_SHA384",
            "TLS_DH_anon_WITH_ARIA_128_GCM_SHA256",
            "TLS_DH_anon_WITH_ARIA_256_GCM_SHA384",
            "TLS_ECDH_ECDSA_WITH_ARIA_128_GCM_SHA256",
            "TLS_ECDH_ECDSA_WITH_ARIA_256_GCM_SHA384",
            "TLS_ECDH_RSA_WITH_ARIA_128_GCM_SHA256",
            "TLS_ECDH_RSA_WITH_ARIA_256_GCM_SHA384",
            "TLS_PSK_WITH_ARIA_128_CBC_SHA256",
            "TLS_PSK_WITH_ARIA_256_CBC_SHA384",
            "TLS_DHE_PSK_WITH_ARIA_128_CBC_SHA256",
            "TLS_DHE_PSK_WITH_ARIA_256_CBC_SHA384",
            "TLS_RSA_PSK_WITH_ARIA_128_CBC_SHA256",
            "TLS_RSA_PSK_WITH_ARIA_256_CBC_SHA384",
            "TLS_PSK_WITH_ARIA_128_GCM_SHA256",
            "TLS_PSK_WITH_ARIA_256_GCM_SHA384",
            "TLS_RSA_PSK_WITH_ARIA_128_GCM_SHA256",
            "TLS_RSA_PSK_WITH_ARIA_256_GCM_SHA384",
            "TLS_ECDHE_PSK_WITH_ARIA_128_CBC_SHA256",
            "TLS_ECDHE_PSK_WITH_ARIA_256_CBC_SHA384",
            "TLS_ECDHE_ECDSA_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_ECDHE_ECDSA_WITH_CAMELLIA_256_CBC_SHA384",
            "TLS_ECDH_ECDSA_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_ECDH_ECDSA_WITH_CAMELLIA_256_CBC_SHA384",
            "TLS_ECDHE_RSA_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_ECDHE_RSA_WITH_CAMELLIA_256_CBC_SHA384",
            "TLS_ECDH_RSA_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_ECDH_RSA_WITH_CAMELLIA_256_CBC_SHA384",
            "TLS_RSA_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_RSA_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_DH_RSA_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_DH_RSA_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_DH_DSS_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_DH_DSS_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_DH_anon_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_DH_anon_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_ECDH_ECDSA_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_ECDH_ECDSA_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_ECDH_RSA_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_ECDH_RSA_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_PSK_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_PSK_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_RSA_PSK_WITH_CAMELLIA_128_GCM_SHA256",
            "TLS_RSA_PSK_WITH_CAMELLIA_256_GCM_SHA384",
            "TLS_PSK_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_PSK_WITH_CAMELLIA_256_CBC_SHA384",
            "TLS_DHE_PSK_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_DHE_PSK_WITH_CAMELLIA_256_CBC_SHA384",
            "TLS_RSA_PSK_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_RSA_PSK_WITH_CAMELLIA_256_CBC_SHA384",
            "TLS_ECDHE_PSK_WITH_CAMELLIA_128_CBC_SHA256",
            "TLS_ECDHE_PSK_WITH_CAMELLIA_256_CBC_SHA384",
            "TLS_RSA_WITH_AES_128_CCM",
            "TLS_RSA_WITH_AES_256_CCM",
            "TLS_RSA_WITH_AES_128_CCM_8",
            "TLS_RSA_WITH_AES_256_CCM_8",
            "TLS_PSK_WITH_AES_128_CCM",
            "TLS_PSK_WITH_AES_256_CCM",
            "TLS_PSK_WITH_AES_128_CCM_8",
            "TLS_PSK_WITH_AES_256_CCM_8"
    };

    static {
        Arrays.sort(CIPHER_SUITE_BLACK_LIST);
    }
    
    // flag, which enables/disables payload support for HTTP methods,
    // for which HTTP spec doesn't clearly state whether they support payload.
    // Known "undefined" methods are: GET, HEAD, DELETE
    private boolean allowPayloadForUndefinedHttpMethods;

    private final Attribute<Connection> CIPHER_CHECKED =
            AttributeBuilder.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("BLACK_LIST_CIPHER_SUITE_CHEKCED");

    public Http2ServerFilter() {
        this(null);
    }

    public Http2ServerFilter(final ExecutorService threadPool) {
        super(threadPool);
    }


    /**
     * The flag, which enables/disables payload support for HTTP methods,
     * for which HTTP spec doesn't clearly state whether they support payload.
     * Known "undefined" methods are: GET, HEAD, DELETE.
     * 
     * @return <tt>true</tt> if "undefined" methods support payload, or <tt>false</tt> otherwise
     */
    public boolean isAllowPayloadForUndefinedHttpMethods() {
        return allowPayloadForUndefinedHttpMethods;
    }

    /**
     * The flag, which enables/disables payload support for HTTP methods,
     * for which HTTP spec doesn't clearly state whether they support payload.
     * Known "undefined" methods are: GET, HEAD, DELETE.
     * 
     * @param allowPayloadForUndefinedHttpMethods <tt>true</tt> if "undefined" methods support payload, or <tt>false</tt> otherwise
     */
    public void setAllowPayloadForUndefinedHttpMethods(boolean allowPayloadForUndefinedHttpMethods) {
        this.allowPayloadForUndefinedHttpMethods = allowPayloadForUndefinedHttpMethods;
    }
    
    @SuppressWarnings("unchecked")
    @Override
    public NextAction handleRead(final FilterChainContext ctx)
            throws IOException {

        // if it's a stream chain (the stream is already assigned) - just
        // bypass the parsing part
        if (checkIfHttp2StreamChain(ctx)) {
            return ctx.getInvokeAction();
        }
        
        final Connection connection = ctx.getConnection();
        Http2State http2State = Http2State.get(connection);
        
        if (http2State != null && http2State.isNeverHttp2()) {
            // NOT HTTP2 connection and never will be
            return ctx.getInvokeAction();
        }
        
        final HttpContent httpContent = ctx.getMessage();
        final HttpHeader httpHeader = httpContent.getHttpHeader();
        
        if (http2State == null) { // Not HTTP/2 (yet?)
            assert httpHeader.isRequest();

            if (httpHeader.isSecure()) {
                // ALPN should've set the Http2State, but in our case it's null.
                // It means ALPN was bypassed - SSL without ALPN shoudn't work.
                // Don't try HTTP/2 in this case.
                Http2State.create(connection).setNeverHttp2();
                return ctx.getInvokeAction();
            }
            
            final HttpRequestPacket httpRequest =
                    (HttpRequestPacket) httpHeader;
            
            if (!Method.PRI.equals(httpRequest.getMethod())) {
                final boolean isLast = httpContent.isLast();
                if (tryHttpUpgrade(ctx, httpRequest, isLast) && isLast) {
                    enableOpReadNow(ctx);
                }
                
                return ctx.getInvokeAction();
            }
            
            // PRI method
            // DIRECT HTTP/2.0 request
            http2State = doDirectUpgrade(ctx);
        }
        
        final Http2Connection http2Connection =
                obtainHttp2Connection(http2State, ctx, true);

        if (!CIPHER_CHECKED.isSet(connection)) {
            CIPHER_CHECKED.set(connection, connection);
            final SSLEngine engine = SSLUtils.getSSLEngine(connection);
            if (engine != null) {
                if (Arrays.binarySearch(CIPHER_SUITE_BLACK_LIST, engine.getSession().getCipherSuite()) >= 0) {
                    http2Connection.goAway(ErrorCode.INADEQUATE_SECURITY);
                    ctx.getConnection().closeSilently();
                    return ctx.getStopAction();
                }
            }
        }
        
        final Buffer framePayload;
        if (!http2Connection.isHttp2InputEnabled()) { // Preface is not received yet
            
            if (http2State.isHttpUpgradePhase()) {
                // It's plain HTTP/1.1 data coming with upgrade request
                if (httpContent.isLast()) {
                    http2State.setDirectUpgradePhase(); // expecting preface
                    enableOpReadNow(ctx);
                }
                
                return ctx.getInvokeAction();
            }
            
            final HttpRequestPacket httpRequest = (HttpRequestPacket) httpHeader;
             
           // PRI message hasn't been checked
            try {
                if (!checkPRI(ctx, httpRequest, httpContent)) {
                    // Not enough PRI content read
                    return ctx.getStopAction(httpContent);
                }
            } catch (Exception e) {
                httpRequest.getProcessingState().setError(true);
                httpRequest.getProcessingState().setKeepAlive(false);

                final HttpResponsePacket httpResponse = httpRequest.getResponse();
                httpResponse.setStatus(HttpStatus.BAD_REQUEST_400);
                ctx.write(httpResponse);
                connection.closeSilently();

                return ctx.getStopAction();
            }

            final Buffer payload = httpContent.getContent();
            framePayload = payload.split(payload.position() + PRI_PAYLOAD.length);
        } else {
            framePayload = httpContent.getContent();
        }
        
        httpContent.recycle();
        
        final List<Http2Frame> framesList =
                frameCodec.parse(http2Connection,
                        http2State.getFrameParsingState(),
                        framePayload);
        
        if (!processFrames(ctx, http2Connection, framesList)) {
            return ctx.getSuspendAction();
        }
        
        return ctx.getStopAction();
    }

    @Override
    public NextAction handleEvent(final FilterChainContext ctx,
            final FilterChainEvent event) throws IOException {
        
        final Object type = event.type();
        
        if (type == HttpEvents.IncomingHttpUpgradeEvent.TYPE) {
            final HttpHeader header
                    = ((HttpEvents.IncomingHttpUpgradeEvent) event).getHttpHeader();
            if (header.isRequest()) {
                //@TODO temporary not optimal solution, because we check the req here and in the handleRead()
                if (checkRequestHeadersOnUpgrade((HttpRequestPacket) header)) {
                    // for the HTTP/2 upgrade request we want to obbey HTTP/1.1
                    // content modifiers (transfer and content encodings)
                    header.setIgnoreContentModifiers(false);
                    
                    return ctx.getStopAction();
                }
            }
            
            return ctx.getInvokeAction();
        }

        final Http2State state = Http2State.get(ctx.getConnection());
        
        if (state == null || state.isNeverHttp2()) {
            return ctx.getInvokeAction();
        }
        
        if (type == HttpEvents.ResponseCompleteEvent.TYPE) {
            final HttpContext httpContext = HttpContext.get(ctx);
            final Http2Stream stream = (Http2Stream) httpContext.getContextStorage();
            stream.onProcessingComplete();
            
            final Http2Connection http2Connection = stream.getHttp2Connection();
            
            if (!http2Connection.isHttp2InputEnabled()) {
                // it's the first HTTP/1.1 -> HTTP/2.0 upgrade request.
                // We have to notify regular HTTP codec filter as well
                state.finishHttpUpgradePhase(); // mark HTTP upgrade as finished (in case it's still on)
                
                return ctx.getInvokeAction();
            }
            
            // it's pure HTTP/2.0 request processing
            return ctx.getStopAction();
        }
        
        return super.handleEvent(ctx, event);
    }

    @Override
    protected void onPrefaceReceived(Http2Connection http2Connection) {
        // In ALPN case server will send the preface only after receiving preface
        // from a client
        http2Connection.sendPreface();
    }
    
    private Http2State doDirectUpgrade(final FilterChainContext ctx) {
        final Connection connection = ctx.getConnection();
        
        final Http2Connection http2Connection =
            new Http2Connection(connection, true, this);

        // Create HTTP/2.0 connection for the given Grizzly Connection
        final Http2State http2State = Http2State.create(connection);
        http2State.setHttp2Connection(http2Connection);
        http2State.setDirectUpgradePhase();
        http2Connection.setupFilterChains(ctx, true);
        
        // server preface
        http2Connection.sendPreface();
        
        return http2State;
    }
    
    private boolean tryHttpUpgrade(final FilterChainContext ctx,
            final HttpRequestPacket httpRequest, final boolean isLast)
            throws Http2StreamException {
        
        if (!checkHttpMethodOnUpgrade(httpRequest)) {
            return false;
        }
        
        if (!checkRequestHeadersOnUpgrade(httpRequest)) {
            return false;
        }
        
        final boolean http2Upgrade = isHttp2UpgradingVersion(httpRequest);
        
        if (!http2Upgrade) {
            // Not HTTP/2.0 HTTP packet
            return false;
        }

        final SettingsFrame settingsFrame =
                getHttp2UpgradeSettings(httpRequest);
        
        if (settingsFrame == null) {
            // Not HTTP/2.0 HTTP packet
            return false;
        }
        
        final Connection connection = ctx.getConnection();
        
        final Http2Connection http2Connection =
                new Http2Connection(connection, true, this);
        // Create HTTP/2.0 connection for the given Grizzly Connection
        final Http2State http2State = Http2State.create(connection);
        http2State.setHttp2Connection(http2Connection);
        
        if (isLast) {
            http2State.setDirectUpgradePhase(); // expecting preface
        }

        try {
            applySettings(http2Connection, settingsFrame);
        } catch (Http2ConnectionException e) {
            Http2State.remove(connection);
            return false;
        }
        
        // Send 101 Switch Protocols back to the client
        final HttpResponsePacket httpResponse = httpRequest.getResponse();
        httpResponse.setStatus(HttpStatus.SWITCHING_PROTOCOLS_101);
        httpResponse.setHeader(Header.Connection, "Upgrade");
        httpResponse.setHeader(Header.Upgrade, HTTP2_CLEAR);
        httpResponse.setIgnoreContentModifiers(true);
        
        ctx.write(httpResponse);

        // uncommit the response
        httpResponse.setCommitted(false);
        
        http2Connection.setupFilterChains(ctx, true);
        
        // server preface
        http2Connection.sendPreface();

        // reset the response object
        httpResponse.setStatus(HttpStatus.OK_200);
        httpResponse.getHeaders().clear();
        httpRequest.setProtocol(Protocol.HTTP_2_0);
        httpResponse.setProtocol(Protocol.HTTP_2_0);

        httpRequest.getUpgradeDC().recycle();
        httpResponse.getProcessingState().setKeepAlive(true);
        
        // create a virtual stream for this request
        final Http2Stream stream = http2Connection.acceptUpgradeStream(
                httpRequest, 0, !httpRequest.isExpectContent());
        
        // replace the HttpContext
        final HttpContext httpContext = HttpContext.newInstance(stream,
                stream, stream, httpRequest);
        httpRequest.getProcessingState().setHttpContext(httpContext);
        // add read-only HTTP2Stream attribute
        httpRequest.setAttribute(Http2Stream.HTTP2_STREAM_ATTRIBUTE, stream);
        httpContext.attach(ctx);
        
        return true;
    }
    
    private boolean checkHttpMethodOnUpgrade(
            final HttpRequestPacket httpRequest) {
        
        return httpRequest.getMethod() != Method.CONNECT;
    }
    
    private boolean checkPRI(final FilterChainContext ctx,
            final HttpRequestPacket httpRequest,
            final HttpContent httpContent) {
        if (!Method.PRI.equals(httpRequest.getMethod())) {
            // If it's not PRI after upgrade is completed - it must be an error
            throw new HttpBrokenContentException();
        }

        // Check the PRI message payload
        final Buffer payload = httpContent.getContent();
        if (payload.remaining() < PRI_PAYLOAD.length) {
            return false;
        }

        final int pos = payload.position();
        for (int i = 0; i < PRI_PAYLOAD.length; i++) {
            if (payload.get(pos + i) != PRI_PAYLOAD[i]) {
                // Unexpected PRI payload
                throw new HttpBrokenContentException();
            }
        }
        
        return true;
    }

    @Override
    protected void processCompleteHeader(
            final Http2Connection http2Connection,
            final FilterChainContext context,
            final HeaderBlockHead firstHeaderFrame) throws IOException {

        processInRequest(http2Connection, context, (HeadersFrame) firstHeaderFrame);
    }
    
    private void processInRequest(final Http2Connection http2Connection,
            final FilterChainContext context, final HeadersFrame headersFrame)
            throws IOException {

        final Http2Request request = Http2Request.create();
        request.setConnection(context.getConnection());

        final Http2Stream stream = http2Connection.acceptStream(request,
                                                                headersFrame.getStreamId(),
                                                                0,
                                                                0,
                                                                Http2StreamState.IDLE);
        if (stream == null) { // GOAWAY has been sent, so ignoring this request
            request.recycle();
            return;
        }

        try {
            DecoderUtils.decodeRequestHeaders(http2Connection, request);
        } catch (IOException ioe) {
            if (ioe instanceof InvalidCharacterException) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.warning(ioe.getMessage());
                }
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, ioe.getMessage(), ioe);
                }
                http2Connection.sendRstFrame(ErrorCode.PROTOCOL_ERROR, stream.getId());
                return;
            } else {
                throw ioe;
            }
        }
        onHttpHeadersParsed(request, context);        

        prepareIncomingRequest(stream, request);
        
        final boolean isEOS = headersFrame.isEndStream();
        stream.onRcvHeaders(isEOS);
        
        // stream HEADERS frame will be transformed to HTTP request packet
        if (isEOS) {
            request.setExpectContent(false);
        }

        final boolean isExpectContent = request.isExpectContent();
        if (!isExpectContent) {
            stream.inputBuffer.terminate(IN_FIN_TERMINATION);
        }

        sendUpstream(http2Connection, stream, request, isExpectContent);
    }
    
    /**
     *
     * @param ctx
     * @param http2Connection
     * @param httpHeader
     * @param entireHttpPacket
     * @throws IOException
     */
    @Override
    @SuppressWarnings("unchecked")
    protected void processOutgoingHttpHeader(final FilterChainContext ctx,
            final Http2Connection http2Connection,
            final HttpHeader httpHeader,
            final HttpPacket entireHttpPacket) throws IOException {

        final HttpResponsePacket response = (HttpResponsePacket) httpHeader;
        final Http2Stream stream = Http2Stream.getStreamFor(response);
        assert stream != null;

        if (!response.isCommitted()) {
            prepareOutgoingResponse(response);
            pushAssociatedResoureses(ctx, stream);
        }

        final FilterChainContext.TransportContext transportContext = ctx.getTransportContext();

        stream.getOutputSink().writeDownStream(entireHttpPacket,
                                   ctx,
                                   transportContext.getCompletionHandler(),
                                   transportContext.getMessageCloner());
    }
        
    private void pushAssociatedResoureses(final FilterChainContext ctx,
            final Http2Stream stream) throws IOException {
        final Map<String, PushResource> pushResourceMap =
                stream.getAssociatedResourcesToPush();
        
        if (pushResourceMap != null) {           
            final int streamId = stream.getId();
            final HttpRequestPacket streamReq = stream.getRequest();
            final String referer = composeRefererOf(stream.getRequest());
            
            final List<Pair<Http2Stream, Source>> pushStreams =
                    new ArrayList<>(pushResourceMap.size());
            
            final Http2Connection http2Connection = stream.getHttp2Connection();
            
            boolean isNewClientStreamLocked = true;
            boolean isDeflaterLocked = true;
            http2Connection.getNewClientStreamLock().lock();
            
            try {
                for (Map.Entry<String, PushResource> entry : pushResourceMap.entrySet()) {
                    final PushResource pushResource = entry.getValue();
                    final Source source = pushResource.getSource();
                    
                    final Http2Request request = Http2Request.create();
                    final HttpResponsePacket response = request.getResponse();
                    
                    request.setRequestURI(entry.getKey());
                    request.setProtocol(Protocol.HTTP_2_0);
                    request.setMethod(Method.GET);
                    
                    final MimeHeaders reqHeaders = request.getHeaders();
                    
                    DataChunk valueDC = reqHeaders.setValue(Header.Host);
                    if (valueDC.isNull()) {
                        valueDC.setString(streamReq.getHeader(Header.Host));
                    }
                    
                    final String userAgent = streamReq.getHeader(Header.UserAgent);
                    if (userAgent != null) {
                        valueDC = reqHeaders.setValue(Header.UserAgent);
                        if (valueDC.isNull()) {
                            valueDC.setString(userAgent);
                        }
                    }
                    
                    valueDC = reqHeaders.setValue(Header.Referer);
                    if (valueDC.isNull()) {
                        valueDC.setString(referer);
                    }
                    
                    for (String cookie : streamReq.getHeaders().values(Header.Cookie)) {
                        request.addHeader(Header.Cookie, cookie);
                    }
                    
                    request.setSecure(streamReq.isSecure());
                    request.setExpectContent(false);
                    
                    response.setStatus(pushResource.getStatusCode());
                    response.setProtocol(Protocol.HTTP_2_0);
                    response.setContentType(pushResource.getContentType());
                    
                    if (source != null) {
                        response.setContentLengthLong(source.remaining());
                    }
                    
                    // Add extra headers if any
                    final Map<String, String> extraHeaders = pushResource.getHeaders();
                    if (extraHeaders != null) {
                        for (Map.Entry<String, String> headerEntry : extraHeaders.entrySet()) {
                            response.addHeader(headerEntry.getKey(), headerEntry.getValue());
                        }
                    }
                    
                    prepareOutgoingRequest(request);
                    prepareOutgoingResponse(response);
                    
                    try {
                        final Http2Stream pushStream = http2Connection.openStream(
                                request,
                                http2Connection.getNextLocalStreamId(), streamId,
                                pushResource.getPriority(),
                                Http2StreamState.RESERVED_LOCAL);
                        pushStream.inputBuffer.terminate(IN_FIN_TERMINATION);
                        
                        pushStreams.add(new Pair<>(
                                pushStream, source));
                    } catch (Exception e) {
                        LOGGER.log(Level.FINE,
                                "Can not push: " + entry.getKey(), e);
                    }
                }
                
                // Push streams are created - now we're ready to to serialize
                // push promises.
                // Lock deflater, unlock newClientStreamLock.
                // This order guarantees that nobody can create a new stream
                // and serialize its headers asynchronously before we finish
                // the push promises serialization.
                http2Connection.getDeflaterLock().lock();
                http2Connection.getNewClientStreamLock().unlock();
                isDeflaterLocked = true;
                isNewClientStreamLocked = false;

                List<Http2Frame> pushPromiseFrames = null;
                
                for (Pair<Http2Stream, Source> pair : pushStreams) {
                    final Http2Stream pushStream = pair.getFirst();
                    pushPromiseFrames =
                            http2Connection.encodeHttpRequestAsPushPromiseFrames(
                                    ctx, pushStream.getRequest(), streamId,
                                    pushStream.getId(), pushPromiseFrames);
                }
                
                http2Connection.getOutputSink().writeDownStream(
                        pushPromiseFrames);
                
                // release the deflater lock
                http2Connection.getDeflaterLock().unlock();
                isDeflaterLocked = false;
                
                // now we're ready to initiate payload push
                for (Pair<Http2Stream, Source> pair : pushStreams) {
                    final Http2Stream pushStream = pair.getFirst();
                    pushStream.getOutputSink().writeDownStream(
                            pair.getSecond(), ctx);
                }
            } finally {
                if (isDeflaterLocked) {
                    http2Connection.getDeflaterLock().unlock();
                }
                
                if (isNewClientStreamLocked) {
                    http2Connection.getNewClientStreamLock().unlock();
                }
            }
        }
    }

    private void prepareOutgoingResponse(final HttpResponsePacket response) {
        response.setProtocol(Protocol.HTTP_2_0);

        String contentType = response.getContentType();
        if (contentType != null) {
            response.getHeaders().setValue(Header.ContentType).setString(contentType);
        }

        if (response.getContentLength() != -1) {
            // FixedLengthTransferEncoding will set proper Content-Length header
            FIXED_LENGTH_ENCODING.prepareSerialize(null, response, null);
        }

        if (!response.containsHeader(Header.Date)) {
            response.getHeaders().addValue(Header.Date)
                    .setBytes(FastHttpDateFormat.getCurrentDateBytes());
        }
    }

    private String composeRefererOf(final HttpRequestPacket request) {
        return new StringBuilder().append(request.isSecure() ? "https" : "http")
                .append("://")
                .append(request.getHeader(Header.Host))
                .append(request.getRequestURI())
                .toString();
    }
    
    private void enableOpReadNow(final FilterChainContext ctx) {
        // make sure we won't enable OP_READ once upper layer complete HTTP request processing
        final FilterChainContext newContext = ctx.copy();
        ctx.getInternalContext().removeAllLifeCycleListeners();

        // enable read now to start accepting HTTP2 frames
        newContext.resume(newContext.getStopAction());
    }        
}
