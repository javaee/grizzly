/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2017 Oracle and/or its affiliates. All rights reserved.
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

import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.threadpool.ThreadPoolConfig;

import java.util.concurrent.ExecutorService;

/**
 * Configuration information for HTTP/2 {@link Filter} implementations.
 *
 * @see Http2ClientFilter
 * @see Http2ServerFilter
 */
public class Http2Configuration {

    public static final int DEFAULT_MAX_HEADER_LIST_SIZE = 4096;
    public static final float DEFAULT_STREAMS_HIGH_WATER_MARK = 0.5f;
    public static final float DEFAULT_STREAMS_CLEAN_PERCENTAGE = 0.5f;
    public static final int DEFAULT_CLEAN_FREQUENCY_CHECK = 50;

    private volatile int maxConcurrentStreams;
    private volatile int initialWindowSize;
    private volatile int maxFramePayloadSize;
    private volatile int maxHeaderListSize;
    private volatile float streamsHighWaterMark;
    private volatile float cleanPercentage;
    private volatile int cleanFrequencyCheck;
    private volatile boolean disableCipherCheck;
    private volatile boolean priorKnowledge;
    private volatile boolean pushEnabled;
    private final ExecutorService executorService;
    private final ThreadPoolConfig threadPoolConfig;


    // ----------------------------------------------------------- Constructors


    private Http2Configuration(final Http2ConfigurationBuilder builder) {
        maxConcurrentStreams = builder.maxConcurrentStreams;
        initialWindowSize = builder.initialWindowSize;
        maxFramePayloadSize = builder.maxFramePayloadSize;
        maxHeaderListSize = builder.maxHeaderListSize;
        disableCipherCheck = builder.disableCipherCheck;
        priorKnowledge = builder.priorKnowledge;
        threadPoolConfig = builder.threadPoolConfig;
        executorService = builder.executorService;
        pushEnabled = builder.pushEnabled;
        streamsHighWaterMark = builder.streamsHighWaterMark;
        cleanPercentage = builder.cleanPercentage;
        cleanFrequencyCheck = builder.cleanFrequencyCheck;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * @return a new {@link Http2ConfigurationBuilder} instance.
     */
    public static Http2ConfigurationBuilder builder() {
        return new Http2ConfigurationBuilder();
    }

    /**
     * @return the default maximum number of concurrent streams allowed for one session.
     * Negative value means "unlimited".
     */
    public int getMaxConcurrentStreams() {
        return maxConcurrentStreams;
    }

    /**
     * Sets the default maximum number of concurrent streams allowed for one session.
     * Negative value means "unlimited".
     */
    public void setMaxConcurrentStreams(final int maxConcurrentStreams) {
        this.maxConcurrentStreams = maxConcurrentStreams;
    }

    /**
     * @return the default initial stream window size (in bytes) for new HTTP2 connections.
     */
    public int getInitialWindowSize() {
        return initialWindowSize;
    }

    /**
     * Sets the default initial stream window size (in bytes) for new HTTP2 connections.
     */
    public void setInitialWindowSize(final int initialWindowSize) {
        this.initialWindowSize = initialWindowSize;
    }

    /**
     * @return the maximum allowed HTTP2 frame payload size.
     */
    public int getMaxFramePayloadSize() {
        return maxFramePayloadSize;
    }

    /**
     * Sets the maximum allowed HTTP2 frame payload size.
     */
    public void setMaxFramePayloadSize(final int maxFramePayloadSize) {
        this.maxFramePayloadSize = maxFramePayloadSize;
    }

    /**
     * @return the maximum size, in bytes, of header list.  If not explicitly configured, the default of
     *  {@link #DEFAULT_MAX_HEADER_LIST_SIZE} is used.
     */
    public int getMaxHeaderListSize() {
        return maxHeaderListSize;
    }

    /**
     * Set the maximum size, in bytes, of the header list.
     */
    public void setMaxHeaderListSize(final int maxHeaderListSize) {
        this.maxHeaderListSize = maxHeaderListSize;
    }

    /**
     * @return whether or not strict cipher suite checking against RFC 7540's blacklist is performed or not.
     *  If not explicitly configured, checking will be performed.
     */
    public boolean isDisableCipherCheck() {
        return disableCipherCheck;
    }

    /**
     * Allows the developer to disable strict cipher suite checking of the connection against RFC 7540's blacklist.
     *
     * @param disableCipherCheck pass <code>true</code> to disable the checking.
     */
    public void setDisableCipherCheck(final boolean disableCipherCheck) {
        this.disableCipherCheck = disableCipherCheck;
    }

    /**
     * @return <code>true</code> if this filter will bypass using the HTTP/1.1 upgrade mechanism and send the
     *  client preface immediately with the knowledge that the remote endpoint supports HTTP/2.
     */
    public boolean isPriorKnowledge() {
        return priorKnowledge;
    }

    /**
     * Control how the HTTP/2 connection is established with the server.
     * @param priorKnowledge <code>true</code> if it's known that the server supports HTTP/2.  By default no
     *                       prior knowledge is assumed.
     */
    public void setPriorKnowledge(final boolean priorKnowledge) {
        this.priorKnowledge = priorKnowledge;
    }

    /**
     * @return <code>true</code> if push to be allowed by the server endpoint, otherwise <code>false</code>.
     */
    public boolean isPushEnabled() {
        return pushEnabled;
    }

    /**
     * Configure whether push should be allowed by the server endpoint.
     */
    public void setPushEnabled(final boolean pushEnabled) {
        this.pushEnabled = pushEnabled;
    }

    /**
     * @return the high-water mark indicating streams old closed streams should be cleaned up.
     *  If not explicitly configured, this returns {@value #DEFAULT_STREAMS_HIGH_WATER_MARK}.
     */
    public float getStreamsHighWaterMark() {
        return streamsHighWaterMark;
    }

    /**
     * Streams are maintained and periodically cleaned when the stream count (open or otherwise) surpasses the
     * the specified high-water mark.  This value is applied against the max concurrent streams for the
     * endpoint which HTTP2 is being configured for.  For example, if the max concurrent streams is 100 and the mark
     * percentage is .5, then the high water mark for a clean attempt would be 50.
     */
    public void setStreamsHighWaterMark(final float streamsHighWaterMark) {
        this.streamsHighWaterMark = streamsHighWaterMark;
    }

    /**
     * @return the number of streams to attempt to remove from the streams structure.  Note that only closed streams
     * will ultimately be removed.  If not explicitly configured, this returns {@value #DEFAULT_STREAMS_CLEAN_PERCENTAGE}.
     */
    public float getCleanPercentage() {
        return cleanPercentage;
    }

    /**
     * The number of streams to attempt to remove from the streams structure.  Note that only closed streams
     * will ultimately be removed.  This value is applied against the computed result for the streams high water
     * mark.  For example, if the max concurrent streams is 100 and the mark percentage is .5, then the high water mark
     * for a clean attempt would be 50.  The number of streams to process in the clean attempt, assuming the clean
     * percentage is .5, would be 25.
     */
    public void setCleanPercentage(final float cleanPercentage) {
        this.cleanPercentage = cleanPercentage;
    }

    /**
     * @return how often, in terms of closed streams, the streams structure will be checked for cleaning.
     *  If not explicitly configured, this returns {@value #DEFAULT_CLEAN_FREQUENCY_CHECK}
     */
    public int getCleanFrequencyCheck() {
        return cleanFrequencyCheck;
    }

    /**
     * Set the number of streams that must be closed before checking if the number of streams exceeds the high-water
     * mark.
     */
    public void setCleanFrequencyCheck(final int cleanFrequencyCheck) {
        this.cleanFrequencyCheck = cleanFrequencyCheck;
    }

    /**
     * @return the thread pool configuration for servicing HTTP/2 streams, if any.
     */
    public ThreadPoolConfig getThreadPoolConfig() {
        return threadPoolConfig;
    }

    /**
     * @return a pre-existing {@link ExecutorService}, if any.
     */
    public ExecutorService getExecutorService() {
        return executorService;
    }

    // --------------------------------------------------------- Nested Classes


    public static final class Http2ConfigurationBuilder {
        private int maxConcurrentStreams = -1;
        private int initialWindowSize = -1;
        private int maxFramePayloadSize = -1;
        private int maxHeaderListSize = DEFAULT_MAX_HEADER_LIST_SIZE;
        private boolean disableCipherCheck;
        private boolean priorKnowledge;
        private boolean pushEnabled = true;
        private float streamsHighWaterMark = DEFAULT_STREAMS_HIGH_WATER_MARK;
        private float cleanPercentage = DEFAULT_STREAMS_CLEAN_PERCENTAGE;
        private int cleanFrequencyCheck = DEFAULT_CLEAN_FREQUENCY_CHECK;
        private ThreadPoolConfig threadPoolConfig;
        private ExecutorService executorService;

        private Http2ConfigurationBuilder() {
        }

        /**
         * @see #setMaxConcurrentStreams(int)
         */
        public Http2ConfigurationBuilder maxConcurrentStreams(final int val) {
            maxConcurrentStreams = val;
            return this;
        }

        /**
         * @see #setInitialWindowSize(int)
         */
        public Http2ConfigurationBuilder initialWindowSize(final int val) {
            initialWindowSize = val;
            return this;
        }

        /**
         * @see #setMaxFramePayloadSize(int)
         */
        public Http2ConfigurationBuilder maxFramePayloadSize(final int val) {
            maxFramePayloadSize = val;
            return this;
        }

        /**
         * @see #setMaxHeaderListSize(int)
         */
        public Http2ConfigurationBuilder maxHeaderListSize(final int val) {
            maxHeaderListSize = val;
            return this;
        }

        /**
         * @see #setDisableCipherCheck(boolean)
         */
        public Http2ConfigurationBuilder disableCipherCheck(final boolean val) {
            disableCipherCheck = val;
            return this;
        }

        /**
         * Specifies a thread pool configuration that will be used to create a new
         *  {@link ExecutorService} handling HTTP/2 streams.
         */
        public Http2ConfigurationBuilder threadPoolConfig(final ThreadPoolConfig val) {
            threadPoolConfig = val;
            return this;
        }

        /**
         * Specifies a pre-existing {@link ExecutorService} for handling HTTP/2 streams.
         * If called, it will clear any value set previously by {@link #threadPoolConfig(ThreadPoolConfig)}.
         */
        public Http2ConfigurationBuilder executorService(final ExecutorService val) {
            threadPoolConfig = null;
            executorService = val;
            return this;
        }

        /**
         * @see #setPriorKnowledge(boolean)
         */
        public Http2ConfigurationBuilder priorKnowledge(final boolean val) {
            priorKnowledge = val;
            return this;
        }

        /**
         * @see #setPushEnabled(boolean)
         */
        public Http2ConfigurationBuilder enablePush(final boolean val) {
            pushEnabled = val;
            return this;
        }

        /**
         * @see #setStreamsHighWaterMark(float)
         */
        public Http2ConfigurationBuilder streamsHighWaterMark(final float val) {
            streamsHighWaterMark = val;
            return this;
        }

        /**
         * @see #setCleanPercentage(float)
         */
        public Http2ConfigurationBuilder cleanPercentage(final float val) {
            cleanPercentage = val;
            return this;
        }

        /**
         * @see #setCleanFrequencyCheck(int)
         */
        public Http2ConfigurationBuilder cleanFrequencyCheck(final int val) {
            cleanFrequencyCheck = val;
            return this;
        }

        /**
         * @return a new {@link Http2Configuration} instances based on the values of this builder.
         */
        public Http2Configuration build() {
            return new Http2Configuration(this);
        }
    }
}
