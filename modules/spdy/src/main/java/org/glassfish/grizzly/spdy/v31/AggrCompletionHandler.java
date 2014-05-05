/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2014 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly.spdy.v31;

import java.util.Arrays;
import org.glassfish.grizzly.CompletionHandler;
import org.glassfish.grizzly.WriteResult;

/**
 *
 * @author oleksiys
 */
class AggrCompletionHandler implements CompletionHandler<WriteResult> {
    private Record[] completionHandlerRecords = new Record[2];
    private int recordsCount;
    
    public void register(final CompletionHandler<WriteResult> completionHandler,
            final int bytesWrittenToReport) {
        ensureCapacity();
        Record record = completionHandlerRecords[recordsCount];
        if (record == null) {
            record = new Record(completionHandler, bytesWrittenToReport);
            completionHandlerRecords[recordsCount] = record;
        } else {
            record.set(completionHandler, bytesWrittenToReport);
        }
        
        recordsCount++;
    }
    
    @Override
    public void cancelled() {
        for (int i = 0; i < recordsCount; i++) {
            try {
                final Record record = completionHandlerRecords[i];
                final CompletionHandler<WriteResult> completionHandler =
                        record.completionHandler;
                record.reset();
                
                completionHandler.cancelled();
            } catch (Exception ignored) {
            }
        }
        
        recordsCount = 0;
    }

    @Override
    public void failed(final Throwable throwable) {
        for (int i = 0; i < recordsCount; i++) {
            try {
                final Record record = completionHandlerRecords[i];
                final CompletionHandler<WriteResult> completionHandler =
                        record.completionHandler;
                record.reset();
                
                completionHandler.failed(throwable);
            } catch (Exception ignored) {
            }
        }
        
        recordsCount = 0;
    }

    @Override
    public void completed(final WriteResult result) {
        final long originalWrittenSize = result.getWrittenSize();

        for (int i = 0; i < recordsCount; i++) {
            try {
                final Record record = completionHandlerRecords[i];
                final CompletionHandler<WriteResult> completionHandler =
                        record.completionHandler;
                final int bytesWrittenToReport = record.bytesWrittenToReport;
                
                record.reset();
                
                result.setWrittenSize(bytesWrittenToReport);
                completionHandler.completed(result);
            } catch (Exception ignored) {
            }
        }
        
        result.setWrittenSize(originalWrittenSize);
        recordsCount = 0;
    }

    @Override
    public void updated(WriteResult result) {
        // don't call update on internal CompletionHandlers
    }

    private void ensureCapacity() {
        if (completionHandlerRecords.length == recordsCount) {
            completionHandlerRecords = Arrays.copyOf(completionHandlerRecords,
                    recordsCount + (recordsCount >> 1) + 1);
        }
    }
    
    private static class Record {

        private CompletionHandler<WriteResult> completionHandler;
        private int bytesWrittenToReport;

        Record(final CompletionHandler<WriteResult> completionHandler,
                final int bytesWrittenToReport) {
            this.completionHandler = completionHandler;
            this.bytesWrittenToReport = bytesWrittenToReport;
        }

        void set(final CompletionHandler<WriteResult> completionHandler,
                final int bytesWrittenToReport) {
            this.completionHandler = completionHandler;
            this.bytesWrittenToReport = bytesWrittenToReport;
        }

        void reset() {
            this.completionHandler = null;
        }
    }
}
