/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2010-2013 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.http.server.filecache;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.http.CompressionConfig;
import org.glassfish.grizzly.http.HttpRequestPacket;
import org.glassfish.grizzly.http.util.ContentType;

/**
 * The entry value in the file cache map.
 *
 * @author Alexey Stashok
 */
public final class FileCacheEntry implements Runnable {

    private static final Logger LOGGER = Grizzly.logger(FileCacheEntry.class);

    public FileCacheKey key;
    public String host;
    public String requestURI;
    public long lastModified = -1;
    public ContentType contentType;
    ByteBuffer bb;
    // The reference to the plain file to be served
    File plainFile;
    long plainFileSize = -1;
    
    private boolean canBeCompressed;
    private AtomicBoolean isCompressed;
    volatile File compressedFile;
    ByteBuffer compressedBb;
    long compressedFileSize = -1;
    
    public String xPoweredBy;
    public FileCache.CacheType type;
    public String date;
    public String Etag;
    public String lastModifiedHeader;
    public String server;

    public volatile long timeoutMillis;

    private final FileCache fileCache;

    public FileCacheEntry(FileCache fileCache) {
        this.fileCache = fileCache;
    }

    /**
     * <tt>true</tt> means this entry could be served compressed, if client
     * supports compression, or <tt>false</tt> if this entry should be always
     * served as it is.
     */
    void setCanBeCompressed(final boolean canBeCompressed) {
        this.canBeCompressed = canBeCompressed;
        
        if (canBeCompressed) {
            isCompressed = new AtomicBoolean();
        }
    }
    
    /**
     * Returns <tt>true</tt> if this entry could be served compressed as response
     * to this (passed) specific {@link HttpRequestPacket}. Or <tt>false</tt>
     * will be returned otherwise.
     */
    public boolean canServeCompressed(final HttpRequestPacket request) {
        if (!canBeCompressed ||
                !CompressionConfig.isClientSupportCompression(
                fileCache.getCompressionConfig(), request,
                FileCache.COMPRESSION_ALIASES)) {
            return false;
        }
        
        if (isCompressed.compareAndSet(false, true)) {
            fileCache.compressFile(this);
        }
        
        // compressedFile could be still "null" if the file compression was
        // initiated by other request and it is still not completed
        return compressedFile != null;
    }
    
    /**
     * Returns the entry file size.
     * @param isCompressed if <tt>true</tt> the compressed file size will be
     *        returned, otherwise uncompressed file size will be returned as the result.
     * @return the entry file size
     */
    public long getFileSize(final boolean isCompressed) {
        return isCompressed ? compressedFileSize : plainFileSize;
    }
    
    /**
     * Returns the entry's {@link File} reference.
     * @param isCompressed if <tt>true</tt> the compressed {@link File} reference
     *        will be returned, otherwise uncompressed {@link File} reference will
     *        be returned as the result.
     * @return the entry's {@link File} reference
     */
    public File getFile(final boolean isCompressed) {
        return isCompressed ? compressedFile : plainFile;
    }
    
    /**
     * Returns the entry's {@link ByteBuffer} representation.
     * @param isCompressed if <tt>true</tt> the compressed {@link ByteBuffer}
     *        will be returned, otherwise uncompressed {@link ByteBuffer} will
     *        be returned as the result.
     * @return the entry's {@link ByteBuffer} reference
     */
    public ByteBuffer getByteBuffer(final boolean isCompressed) {
        return isCompressed ? compressedBb : bb;
    }
    
    @Override
    public void run() {
        fileCache.remove(this);
    }

    @Override
    public String toString() {
        final StringBuilder sb = new StringBuilder();
        sb.append("FileCacheEntry");
        sb.append("{host='").append(host).append('\'');
        sb.append(", requestURI='").append(requestURI).append('\'');
        sb.append(", lastModified=").append(lastModified);
        sb.append(", contentType='").append(contentType).append('\'');
        sb.append(", type=").append(type);
        sb.append(", plainFileSize=").append(plainFileSize);
        sb.append(", canBeCompressed=").append(canBeCompressed);
        sb.append(", compressedFileSize=").append(compressedFileSize);
        sb.append(", timeoutMillis=").append(timeoutMillis);
        sb.append(", fileCache=").append(fileCache);
        sb.append(", server=").append(server);
        sb.append('}');
        return sb.toString();
    }

    @Override
    protected void finalize() throws Throwable {
        if (compressedFile != null) {
            if (!compressedFile.delete()) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE,
                               "Unable to delete file {0}.  Will try to delete again upon VM exit.",
                               compressedFile.getCanonicalPath());
                }
                compressedFile.deleteOnExit();
            }
        }
        
        super.finalize();
    }
}
