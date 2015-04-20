/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2015 Oracle and/or its affiliates. All rights reserved.
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
/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.glassfish.grizzly.http2.hpack;

import java.util.*;

/**
 * Header field table, includes static and dynamic parts.
 * Static table is shared across all HeaderFieldTable instances and the dynamic
 * part is per TCP/HTTP2 connection.
 * 
 * table abstraction maintains the illusion of bytes in memory "as-if"
 * the table is supposed to work on side-effects: if you try to put entry bigger
 * than the room left, it will try remove last entry. So the next time the table
 * might be in a different state.
 *
 *
 * every entry occupies some space, i.e. it cannot be that adding or evicting
 * an entry doesn't change occupied space (size)
 */
public class HeaderFieldTable {
    private final static HeaderField[] staticEntries = {
            HeaderField.of(":authority"),
            HeaderField.of(":method", "GET"),
            HeaderField.of(":method", "POST"),
            HeaderField.of(":path", "/"),
            HeaderField.of(":path", "/index.html"),
            HeaderField.of(":scheme", "http"),
            HeaderField.of(":scheme", "https"),
            HeaderField.of(":status", "200"),
            HeaderField.of(":status", "204"),
            HeaderField.of(":status", "206"),
            HeaderField.of(":status", "304"),
            HeaderField.of(":status", "400"),
            HeaderField.of(":status", "404"),
            HeaderField.of(":status", "500"),
            HeaderField.of("accept-charset"),
            HeaderField.of("accept-encoding", "gzip, deflate"),
            HeaderField.of("accept-language"),
            HeaderField.of("accept-ranges"),
            HeaderField.of("accept"),
            HeaderField.of("access-control-allow-origin"),
            HeaderField.of("age"),
            HeaderField.of("allow"),
            HeaderField.of("authorization"),
            HeaderField.of("cache-control"),
            HeaderField.of("content-disposition"),
            HeaderField.of("content-encoding"),
            HeaderField.of("content-language"),
            HeaderField.of("content-length"),
            HeaderField.of("content-location"),
            HeaderField.of("content-range"),
            HeaderField.of("content-type"),
            HeaderField.of("cookie"),
            HeaderField.of("date"),
            HeaderField.of("etag"),
            HeaderField.of("expect"),
            HeaderField.of("expires"),
            HeaderField.of("from"),
            HeaderField.of("host"),
            HeaderField.of("if-match"),
            HeaderField.of("if-modified-since"),
            HeaderField.of("if-none-match"),
            HeaderField.of("if-range"),
            HeaderField.of("if-unmodified-since"),
            HeaderField.of("last-modified"),
            HeaderField.of("link"),
            HeaderField.of("location"),
            HeaderField.of("max-forwards"),
            HeaderField.of("proxy-authenticate"),
            HeaderField.of("proxy-authorization"),
            HeaderField.of("range"),
            HeaderField.of("referer"),
            HeaderField.of("refresh"),
            HeaderField.of("retry-after"),
            HeaderField.of("server"),
            HeaderField.of("set-cookie"),
            HeaderField.of("strict-transport-security"),
            HeaderField.of("transfer-encoding"),
            HeaderField.of("user-agent"),
            HeaderField.of("vary"),
            HeaderField.of("via"),
            HeaderField.of("www-authenticate")
    };
    private final static int STATIC_TABLE_SIZE = staticEntries.length;
    private final static Map<HeaderField, Integer> staticIndexes;

    static {
        staticIndexes = new HashMap<>(STATIC_TABLE_SIZE);
        for (int i = 0; i < STATIC_TABLE_SIZE; i++) {
            staticIndexes.put(staticEntries[i], i + 1);
        }
    }
    
    public static DecTable createDecodingTable(final int maxSize,
            final int initialCapacity) {
        return new DecTable(maxSize, initialCapacity);
    }

    public static EncTable createEncodingTable(final int maxSize,
            final int initialCapacity) {
        return new EncTable(maxSize, initialCapacity);
    }
    
    /**
     * Decoding {@link HeaderField} table, that contains index -> HeaderField mapping.
     */
    public static class DecTable {

        // The head and the tail fields are used to point entries in the cirlular list.
        // The head field value can be greater than entriesCircList.length for
        // situations, where circlular list values cross the entriesCircList right
        // boundary.
        // When an element is added - it will be put to the cell pointed by the head and
        // the head pointer will be increased by one.
        // When an element is removed - the tail pointer will be increased by one, and
        // if it's value >= entriesCircList.length - both tail and head will be decreased by
        // entriesCircList.length. This way we make sure the tail will never go
        // beyond entriesCircList.length and the head will be always less than (entriesCircList.length * 2).
        //
        // the head of the circlular list, points to the cell, where next entry will be stored (exclusive)
        private int head;
        // the tail of the circular list, points to the oldest entry in the list (inclusive) 
        private int tail;
        private HeaderField[] entriesCircList;

        /**
         * Maximum size of the table in bytes. {@literal 0 <= size <= maxSize}
         */
        private int maxSize;

        /**
         * Actual size of the table in bytes. {@literal 0 <= size <= maxSize}
         */
        private int dynamicEntriesSize;

        /**
         * @param maxSize maximum table size in bytes
         * @param initialCapacity initial capacity for dynamic entries
         * collections
         * @throws IllegalArgumentException
         */
        protected DecTable(final int maxSize, final int initialCapacity) {
            entriesCircList = new HeaderField[initialCapacity];
            head = tail = 0;
            this.maxSize = maxSize;
        }

        /**
         * @return number of entries currently in the table
         */
        public int entriesCount() {
            return head - tail;
        }

        public final HeaderField get(final int index) {
            if (index < 1 || index > STATIC_TABLE_SIZE + entriesCount()) {
                throw new IllegalArgumentException(
                        "Index should satisfy 1 <= index <= last_entry_index: "
                        + "index=" + index);
            }

            return index <= STATIC_TABLE_SIZE
                    ? staticEntries[index - 1]
                    : entriesCircList[(head - (index - STATIC_TABLE_SIZE)) % entriesCircList.length];
        }

        protected final int realToTableIdx(final int realIdx) {
            return (head - realIdx) % entriesCircList.length;
        }

        public final void put(String name) {
            put(HeaderField.of(name));
        }

        public final void put(String name, String value) {
            put(HeaderField.of(name, value));
        }

        public final void put(final HeaderField entry) {
            int entrySize = sizeOf(entry);

            if (entrySize > maxSize) {
                clear();
                return;
            }

            while (entrySize > maxSize - dynamicEntriesSize) {
                evictEntry();
            }

            final int entriesArraySz = entriesCircList.length;

            if (head - tail == entriesArraySz) {
                expandDynamicEntriesCollection();
            }

            final int realIdx = head % entriesArraySz;
            entriesCircList[realIdx] = entry;

            onAdded(entry, realIdx);

            head++;
            dynamicEntriesSize += entrySize;
        }

        public final void setMaxSize(final int maxSize) {
            if (maxSize < 0) {
                throw new IllegalArgumentException("maxSize < 0: maxSize=" + maxSize);
            }
            while (maxSize < dynamicEntriesSize && dynamicEntriesSize != 0) {
                evictEntry();
            }
            this.maxSize = maxSize;
        }

        private void evictEntry() {
            assert tail < head;

            final HeaderField entry = entriesCircList[tail];
            entriesCircList[tail] = null;

            incTail();

            dynamicEntriesSize -= sizeOf(entry);

            onRemoved(entry);
        }

        protected void clear() {
            if (head <= entriesCircList.length) {
                Arrays.fill(entriesCircList, tail, head, null);
            } else {
                Arrays.fill(entriesCircList, tail, entriesCircList.length, null);
                Arrays.fill(entriesCircList, 0, head - entriesCircList.length, null);
            }

            head = tail = 0;
            dynamicEntriesSize = 0;
        }

        @Override
        public String toString() {
            double used = 100 * (((double) dynamicEntriesSize) / maxSize);
            return String.format("entries: %d; used %s/%s (%.1f%%)", entriesCount(),
                    dynamicEntriesSize, maxSize, used);
        }

    //
        // The size (in bytes) this header field occupies in a table.
        //
        // It must always be positive, because of this:
        //
        // "...The size of an entry is the sum of its name's length in
        // octets (as defined in Section 6.2), its value's length in octets
        // (see Section 6.2), plus 32..."
        //
        // and this (otherwise, reducing maximum size will be an infinite loop):
        //
        // "5.2.  Entry Eviction when Header Table Size Changes
        //
        // Whenever the maximum size for the header table is reduced, entries
        // are evicted from the end of the header table until the size of the
        // header table is less than or equal to the maximum size..."
        //
        // [*] The latter implies that eviction of entries reduces the size of the
        // table.
        //
        public final int sizeOf(final HeaderField f) {
            final String name = f.getName();
            final String value = f.getValue();
            return name.length() + (value == null ? 0 : value.length()) + 32;
        }

    //
        // diagnostic information in a convenient form
        //
        public String getStateString() {
            if (dynamicEntriesSize == 0) {
                return "empty.";
            } else {
                StringBuilder b = new StringBuilder();
                for (int i = 1; i <= entriesCount(); i++) {
                    HeaderField e = get(i);
                    b.append(String.format("[%3d] (s = %3d) %s: %s%n", i,
                            sizeOf(e), e.getName(), e.getValue()));
                }
                b.append(String.format("      Table size:%4s", dynamicEntriesSize));
                return b.toString();
            }
        }

        private void incTail() {
            tail++;
            final int arraySize = entriesCircList.length;
            if (tail >= arraySize) {
                tail -= arraySize;
                head -= arraySize;
            }
        }

        private void expandDynamicEntriesCollection() {
            final HeaderField[] tmp = new HeaderField[entriesCircList.length * 2];

            if (head <= entriesCircList.length) {
                System.arraycopy(entriesCircList, tail, tmp, tail, head - tail);
            } else {  // line up the old dynamicEntries (which is circular now)
                System.arraycopy(entriesCircList, tail, tmp, tail, entriesCircList.length - tail);
                System.arraycopy(entriesCircList, 0, tmp, entriesCircList.length, head - entriesCircList.length);

                onChangeAbsIndexes(tmp, entriesCircList.length, head);
            }

        // head and tail remain the same
            entriesCircList = tmp;
        }

        protected void onAdded(HeaderField entry, int realIdx) {
        }

        protected void onRemoved(HeaderField entry) {
        }

        protected void onChangeAbsIndexes(HeaderField[] entries, int startIdx, int endIdx) {
        }
    }
    
    /**
     * Encoding {@link HeaderField} table, that extends decoding table {@link DecTable}
     * by maintaining additional {@link HeaderField} -> index mapping.
     */    
    public static class EncTable extends DecTable {

        /**
         * The HeaderField <-> *real* index mapping. The real index is an index
         * of the HeadField in the circular queue in the DynamicDecTable, so it
         * *doesn't* represent the table index. In order to convert read index
         * to table index, please use {@link #realToTableIdx(int)}.
         */
        private final HashMap<HeaderField, Integer> dynamicIndexes;

        private final HeaderField.DynamicHeaderField dynamicEntry =
                HeaderField.dynamic();
        /**
         * @param maxSize maximum table size in bytes
         * @param initialCapacity initial capacity for dynamic entries
         * collections
         * @throws IllegalArgumentException
         */
        protected EncTable(final int maxSize, final int initialCapacity) {
            super(maxSize, initialCapacity);
            dynamicIndexes = new HashMap<>(initialCapacity);
        }

        public int indexOf(final String name) {
            return indexOf(name, null);
        }
        
        public int indexOf(final String name, final String value) {
            dynamicEntry.set(name, value);
            final Integer realIdx = dynamicIndexes.get(dynamicEntry);
            dynamicEntry.reset();
            
            return realIdx != null
                    ? realToTableIdx(realIdx) + STATIC_TABLE_SIZE
                    : -1;
        }

        @Override
        protected void onRemoved(final HeaderField entry) {
            dynamicIndexes.remove(entry);
        }

        @Override
        protected void onAdded(HeaderField entry, int realIdx) {
            dynamicIndexes.put(entry, realIdx);
        }

        @Override
        protected void onChangeAbsIndexes(final HeaderField[] entries,
                final int startIdx, final int endIdx) {
            for (int i = startIdx; i < endIdx; i++) {
                dynamicIndexes.put(entries[i], i);
            }
        }

        @Override
        protected void clear() {
            super.clear();
            dynamicIndexes.clear();
        }
    }
}
