/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015-2017 Oracle and/or its affiliates. All rights reserved.
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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.Connection;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.Transport;
import org.glassfish.grizzly.attributes.Attribute;
import org.glassfish.grizzly.attributes.AttributeBuilder;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.filterchain.FilterChainEvent;
import org.glassfish.grizzly.filterchain.NextAction;
import org.glassfish.grizzly.filterchain.ShutdownEvent;
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
import org.glassfish.grizzly.http.server.http2.PushEvent;
import org.glassfish.grizzly.http.util.FastHttpDateFormat;
import org.glassfish.grizzly.http.util.Header;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.http2.frames.ErrorCode;
import org.glassfish.grizzly.http2.frames.HeaderBlockHead;
import org.glassfish.grizzly.http2.frames.HeadersFrame;
import org.glassfish.grizzly.http2.frames.Http2Frame;
import org.glassfish.grizzly.http2.frames.SettingsFrame;
import org.glassfish.grizzly.impl.FutureImpl;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.ssl.SSLUtils;

import javax.net.ssl.SSLEngine;

import static org.glassfish.grizzly.http2.Termination.IN_FIN_TERMINATION;

/**
 *
 * @author oleksiys
 */
public class Http2ServerFilter extends Http2BaseFilter {
    private final static Logger LOGGER = Grizzly.logger(Http2ServerFilter.class);

    private static final String[] CIPHER_SUITE_BLACK_LIST = {
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

    private Collection<Connection> activeConnections = new HashSet<>(1024);
    private AtomicBoolean shuttingDown = new AtomicBoolean();

    /**
     * Create a new {@link Http2ServerFilter} using the specified {@link Http2Configuration}.
     * Configuration may be changed post-construction by calling {@link #getConfiguration()}.
     */
    public Http2ServerFilter(final Http2Configuration configuration) {
        super(configuration);
    }


    /**
     * The flag, which enables/disables payload support for HTTP methods,
     * for which HTTP spec doesn't clearly state whether they support payload.
     * Known "undefined" methods are: GET, HEAD, DELETE.
     * 
     * @return <tt>true</tt> if "undefined" methods support payload, or <tt>false</tt> otherwise
     */
    @SuppressWarnings("unused")
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
    @SuppressWarnings("unused")
    public void setAllowPayloadForUndefinedHttpMethods(boolean allowPayloadForUndefinedHttpMethods) {
        this.allowPayloadForUndefinedHttpMethods = allowPayloadForUndefinedHttpMethods;
    }

    @Override
    public NextAction handleAccept(final FilterChainContext ctx) throws IOException {
        if (!shuttingDown.get()) {
            activeConnections.add(ctx.getConnection());
        }
        return ctx.getInvokeAction();
    }

    @Override
    public NextAction handleClose(final FilterChainContext ctx) throws IOException {
        if (!shuttingDown.get()) {
            activeConnections.remove(ctx.getConnection());
        }
        return ctx.getInvokeAction();
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
                // It means ALPN was bypassed - SSL without ALPN shouldn't work.
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
        
        final Http2Session http2Session =
                obtainHttp2Session(http2State, ctx, true);

        if (httpHeader.isSecure() && !getConfiguration().isDisableCipherCheck() && !CIPHER_CHECKED.isSet(connection)) {
            CIPHER_CHECKED.set(connection, connection);
            final SSLEngine engine = SSLUtils.getSSLEngine(connection);
            if (engine != null) {
                if (Arrays.binarySearch(CIPHER_SUITE_BLACK_LIST, engine.getSession().getCipherSuite()) >= 0) {
                    http2Session.terminate(ErrorCode.INADEQUATE_SECURITY, null);
                    return ctx.getStopAction();
                }
            }
        }
        
        final Buffer framePayload;
        if (!http2Session.isHttp2InputEnabled()) { // Preface is not received yet
            
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
                if (!checkPRI(httpRequest, httpContent)) {
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

        // Prime the initial value of push.  Will be overridden if the settings contain a
        // new value.
        if (connection.getAttributes().getAttribute(HTTP2_PUSH_ENABLED) == null) {
            connection.getAttributes().setAttribute(HTTP2_PUSH_ENABLED, Boolean.TRUE);
        }
        
        final List<Http2Frame> framesList =
                frameCodec.parse(http2Session,
                        http2State.getFrameParsingState(),
                        framePayload);

        if (!processFrames(ctx, http2Session, framesList)) {
            return ctx.getSuspendAction();
        }
        
        return ctx.getStopAction();
    }

    @Override
    public NextAction handleEvent(final FilterChainContext ctx,
            final FilterChainEvent event) throws IOException {
        
        final Object type = event.type();

        if (type == ShutdownEvent.TYPE) {
            if (shuttingDown.compareAndSet(false, true)) {
                ((ShutdownEvent) event).addShutdownTask(new Callable<Filter>() {
                    @Override
                    public Filter call() throws Exception {
                        final Collection<Connection> activeConnections = shuttingDown();
                        if (!activeConnections.isEmpty()) {
                            final List<FutureImpl> futures = new ArrayList<>(activeConnections.size());
                            for (final Connection c : activeConnections) {
                                if (c.isOpen()) {
                                    final Http2Session session = Http2Session.get(c);
                                    if (session != null) {
                                        futures.add(session.terminateGracefully());
                                    }
                                }
                            }
                            for (final FutureImpl f : futures) {
                                f.get();
                            }
                        }
                        return Http2ServerFilter.this;
                    }
                });
            }
        }
        
        if (type == HttpEvents.IncomingHttpUpgradeEvent.TYPE) {
            final HttpHeader header
                    = ((HttpEvents.IncomingHttpUpgradeEvent) event).getHttpHeader();
            if (header.isRequest()) {
                //@TODO temporary not optimal solution, because we check the req here and in the handleRead()
                if (checkRequestHeadersOnUpgrade((HttpRequestPacket) header)) {
                    // for the HTTP/2 upgrade request we want to obey HTTP/1.1
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

        if (type == PushEvent.TYPE) {
            doPush(ctx, (PushEvent) event);
            return ctx.getSuspendAction();
        }
        
        if (type == HttpEvents.ResponseCompleteEvent.TYPE) {
            final HttpContext httpContext = HttpContext.get(ctx);
            final Http2Stream stream = (Http2Stream) httpContext.getContextStorage();
            stream.onProcessingComplete();
            
            final Http2Session http2Session = stream.getHttp2Session();
            
            if (!http2Session.isHttp2InputEnabled()) {
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
    protected void onPrefaceReceived(Http2Session http2Session) {
        // In ALPN case server will send the preface only after receiving preface
        // from a client
        http2Session.sendPreface();
    }
    
    private Http2State doDirectUpgrade(final FilterChainContext ctx) {
        final Connection connection = ctx.getConnection();
        
        final Http2Session http2Session =
            new Http2Session(connection, true, this);

        // Create HTTP/2.0 connection for the given Grizzly Connection
        final Http2State http2State = Http2State.create(connection);
        http2State.setHttp2Session(http2Session);
        http2State.setDirectUpgradePhase();
        http2Session.setupFilterChains(ctx, true);
        
        // server preface
        http2Session.sendPreface();
        
        return http2State;
    }

    Collection<Connection> shuttingDown() {
        shuttingDown.compareAndSet(false, true);
        return activeConnections;
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
        
        final Http2Session http2Session =
                new Http2Session(connection, true, this);
        // Create HTTP/2.0 connection for the given Grizzly Connection
        final Http2State http2State = Http2State.create(connection);
        http2State.setHttp2Session(http2Session);
        
        if (isLast) {
            http2State.setDirectUpgradePhase(); // expecting preface
        }

        try {
            applySettings(http2Session, settingsFrame);
        } catch (Http2SessionException e) {
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

        // un-commit the response
        httpResponse.setCommitted(false);
        
        http2Session.setupFilterChains(ctx, true);
        
        // server preface
        http2Session.sendPreface();

        // reset the response object
        httpResponse.setStatus(HttpStatus.OK_200);
        httpResponse.getHeaders().clear();
        httpRequest.setProtocol(Protocol.HTTP_2_0);
        httpResponse.setProtocol(Protocol.HTTP_2_0);

        httpRequest.getUpgradeDC().recycle();
        httpResponse.getProcessingState().setKeepAlive(true);

        if (http2Session.isGoingAway()) {
            Http2State.remove(connection);
            return false;
        }
        // create a virtual stream for this request
        final Http2Stream stream = http2Session.acceptUpgradeStream(
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
    
    private boolean checkPRI(final HttpRequestPacket httpRequest,
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
            final Http2Session http2Session,
            final FilterChainContext context,
            final HeaderBlockHead firstHeaderFrame) throws IOException {

        if (!ignoreFrameForStreamId(http2Session, firstHeaderFrame.getStreamId())) {
            processInRequest(http2Session, context, (HeadersFrame) firstHeaderFrame);
        }
    }
    
    private void processInRequest(final Http2Session http2Session,
            final FilterChainContext context, final HeadersFrame headersFrame)
            throws IOException {

        final Http2Request request = Http2Request.create();
        request.setConnection(context.getConnection());

        Http2Stream stream = http2Session.getStream(headersFrame.getStreamId());
        if (stream != null) {
            // trailers
            assert headersFrame.isEndStream();
            try {
                stream.onRcvHeaders(true);
                DecoderUtils.decodeTrailerHeaders(http2Session, stream.getRequest());
            } catch (IOException ioe) {
                handleDecodingError(http2Session, ioe);
                return;
            }
            if (headersFrame.isTruncated()) {
                if (LOGGER.isLoggable(Level.WARNING)) {
                    LOGGER.log(Level.WARNING,
                               "[{0}, {1}] Trailer headers truncated.  Some headers may not be available.",
                               new Object[] { http2Session.toString(), headersFrame.getStreamId()});
                }
            }
            stream.flushInputData();
            stream.inputBuffer.close(IN_FIN_TERMINATION);
            return;
        }

        stream = http2Session.acceptStream(request,
                                              headersFrame.getStreamId(),
                                              headersFrame.getStreamDependency(),
                                              headersFrame.isExclusive(),
                                              0);
        if (stream == null) { // GOAWAY has been sent, so ignoring this request
            request.recycle();
            return;
        }

        try {
            DecoderUtils.decodeRequestHeaders(http2Session, request);
        } catch (IOException ioe) {
            handleDecodingError(http2Session, ioe);
            return;
        }
        if (headersFrame.isTruncated()) {
            final HttpResponsePacket response = request.getResponse();
            HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE.setValues(response);
            final HttpHeader header = response.getHttpHeader();
            header.setContentLength(0);
            header.setExpectContent(false);
            processOutgoingHttpHeader(context, http2Session, header, response);
            return;
        }
        onHttpHeadersParsed(request, context);
        request.getHeaders().mark();

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

        sendUpstream(http2Session,
                     stream,
                     request.httpContentBuilder().content(Buffers.EMPTY_BUFFER).last(!isExpectContent).build());
    }



    /**
     *
     * @param ctx the current {@link FilterChainContext}
     * @param http2Session the {@link Http2Session} associated with this {@link HttpHeader}
     * @param httpHeader the out-going {@link HttpHeader}
     * @param entireHttpPacket the complete {@link HttpPacket}
     *
     * @throws IOException if an error occurs sending the packet
     */
    @Override
    @SuppressWarnings("unchecked")
    protected void processOutgoingHttpHeader(final FilterChainContext ctx,
            final Http2Session http2Session,
            final HttpHeader httpHeader,
            final HttpPacket entireHttpPacket) throws IOException {

        final HttpResponsePacket response = (HttpResponsePacket) httpHeader;
        final Http2Stream stream = Http2Stream.getStreamFor(response);
        assert stream != null;

        if (!response.isCommitted()) {
            prepareOutgoingResponse(response);
        }

        final FilterChainContext.TransportContext transportContext = ctx.getTransportContext();

        stream.getOutputSink().writeDownStream(entireHttpPacket,
                                   ctx,
                                   transportContext.getCompletionHandler(),
                                   transportContext.getMessageCloner());
    }

    private void doPush(final FilterChainContext ctx, final PushEvent pushEvent) {
        final Http2Session h2c = Http2Session.get(ctx.getConnection());
        if (h2c == null) {
            throw new IllegalStateException("Unable to find valid Http2Session");
        }

        try {
            final HttpRequestPacket source = (HttpRequestPacket) pushEvent.getHttpRequest();
            Http2Stream parentStream = (Http2Stream) source.getAttribute(Http2Stream.HTTP2_PARENT_STREAM_ATTRIBUTE);
            if (parentStream == null) {
                parentStream = Http2Stream.getStreamFor(pushEvent.getHttpRequest());
            }

            if (parentStream == null) {
                return;
            }
            final String eventPath = pushEvent.getPath();
            String path = eventPath;
            String query = null;
            final int idx = eventPath.indexOf('?');
            if (idx != -1) {
                path = eventPath.substring(0, idx);
                query = eventPath.substring(idx + 1);
            }
            final Http2Request request = Http2Request.create();
            request.setAttribute(Http2Stream.HTTP2_PARENT_STREAM_ATTRIBUTE, parentStream);
            request.setConnection(ctx.getConnection());
            request.getRequestURIRef().init(path);
            request.getQueryStringDC().setString(query);
            request.setProtocol(Protocol.HTTP_2_0);
            request.setMethod(pushEvent.getMethod());
            request.setSecure(pushEvent.getHttpRequest().isSecure());
            request.getHeaders().copyFrom(pushEvent.getHeaders());
            request.setExpectContent(false);

            prepareOutgoingRequest(request);
            prepareOutgoingResponse(request.getResponse());
            final Http2Stream pushStream;

            h2c.getNewClientStreamLock().lock();
            try {
                pushStream = h2c.openStream(
                        request,
                        h2c.getNextLocalStreamId(), parentStream.getId(),
                        false, 0);
                pushStream.inputBuffer.terminate(IN_FIN_TERMINATION);

                h2c.getDeflaterLock().lock();
                try {
                    List<Http2Frame> pushPromiseFrames =
                            h2c.encodeHttpRequestAsPushPromiseFrames(
                                    ctx, pushStream.getRequest(), parentStream.getId(),
                                    pushStream.getId(), null);
                    h2c.getOutputSink().writeDownStream(pushPromiseFrames);

                } finally {
                    pushStream.onSendPushPromise();
                    h2c.getDeflaterLock().unlock();
                }
            } finally {
                h2c.getNewClientStreamLock().unlock();
            }

            request.getProcessingState().setHttpContext(
                    HttpContext.newInstance(pushStream, pushStream, pushStream, request));
            // now send the request upstream...

            submit(ctx.getConnection(), new Runnable() {
                @Override
                public void run() {
                    h2c.sendMessageUpstream(pushStream,
                                            HttpContent
                                                .builder(request)
                                                .content(Buffers.EMPTY_BUFFER)
                                                    .build());
                }
            });


        } catch (Exception e) {
            LOGGER.log(Level.SEVERE,
                    "Unable to push resource identified by path [{0}]", pushEvent.getPath());
            LOGGER.log(Level.SEVERE, e.getMessage(), e);
        } finally {
            pushEvent.recycle();
            ctx.resume(ctx.getStopAction());
        }


    }

    private void submit(final Connection c, final Runnable runnable) {
        if (threadPool != null) {
            threadPool.submit(runnable);
        } else {
            final Transport t = c.getTransport();
            final ExecutorService workerThreadPool = t.getWorkerThreadPool();
            if (workerThreadPool != null) {
                workerThreadPool.submit(runnable);
            } else {
                t.getKernelThreadPool().submit(runnable);
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

    private void enableOpReadNow(final FilterChainContext ctx) {
        // make sure we won't enable OP_READ once upper layer complete HTTP request processing
        final FilterChainContext newContext = ctx.copy();
        ctx.getInternalContext().removeAllLifeCycleListeners();

        // enable read now to start accepting HTTP2 frames
        newContext.resume(newContext.getStopAction());
    }

    private static void handleDecodingError(final Http2Session http2Session,
                                            final IOException ioe) throws IOException {
        http2Session.terminate(ErrorCode.COMPRESSION_ERROR, ioe.getCause().getMessage());
    }
}
