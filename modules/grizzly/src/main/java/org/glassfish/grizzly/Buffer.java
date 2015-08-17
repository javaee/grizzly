/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 2008-2015 Oracle and/or its affiliates. All rights reserved.
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

package org.glassfish.grizzly;

import java.nio.BufferOverflowException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.InvalidMarkException;
import java.nio.ReadOnlyBufferException;
import java.nio.charset.Charset;

import org.glassfish.grizzly.asyncqueue.WritableMessage;
import org.glassfish.grizzly.memory.BufferArray;
import org.glassfish.grizzly.memory.ByteBufferArray;
import org.glassfish.grizzly.memory.CompositeBuffer;

/**
 * JDK {@link ByteBuffer} was taken as base for Grizzly
 * <tt>Buffer</tt> interface, but <tt>Buffer</tt> has several extensions:
 * it's possible to prepend some data to a Buffer and release Buffer, when
 * it's not required any more.
 *
 * @author Alexey Stashok
 */
public interface Buffer extends Comparable<Buffer>, WritableMessage {

    /**
     * @return {@code true} if this {@link Buffer} represents a composite
     *  of individual {@link Buffer} instances.
     */
    boolean isComposite();

    /**
     * Prepend data from header.position() to header.limit() to the
     * current buffer.  This will change the value returned by buffer()!
     * @throws IllegalArgumentException if header.limit() - header.position()
     * is greater than headerSize.
     */
    Buffer prepend(Buffer header);

    /**
     * Trim the buffer by reducing capacity to position, if possible.
     * May return without changing capacity. Also resets the position to 0,
     * like {@link #flip()}.
     */
    void trim();
    
    /**
     * Disposes the buffer part, outside [position, limit] interval if possible.
     * May return without changing capacity.
     * After shrink is called, position/limit/capacity values may have
     * different values, than before, but still point to the same <tt>Buffer</tt>
     * elements.
     */
    void shrink();

    /**
     * Split up the buffer into two parts: [0..splitPosition) and [splitPosition, capacity).
     * This <tt>Buffer</tt> will represent the first part: [0..splitPosition) and
     * returned <tt>Buffer</tt> will represent the second part: [splitPosition, capacity).
     * 
     * @param splitPosition position of split.
     *
     * @return the <tt>Buffer</tt>, which represents split part [splitPosition, capacity)
     */
    Buffer split(int splitPosition);

    boolean allowBufferDispose();

    void allowBufferDispose(boolean allowBufferDispose);

    /**
     * Tells whether or not this buffer is
     * <a href="ByteBuffer.html#direct"><i>direct</i></a>. </p>
     *
     * @return  <tt>true</tt> if, and only if, this buffer is direct
     */
    boolean isDirect();
    
    /**
     * Try to dispose <tt>Buffer</tt> if it's allowed.
     */
    boolean tryDispose();

    /**
     * Notify the allocator that the space for this <tt>Buffer</tt> is no
     * longer needed. All calls to methods on a <tt>Buffer</tt>
     * will fail after a call to dispose().
     */
    void dispose();
    
    /**
     * Return the underlying buffer
     * 
     * @return the underlying buffer
     */
    Object underlying();

    /**
     * Returns this buffer's capacity. </p>
     *
     * @return  The capacity of this buffer
     */
    int capacity();

    /**
     * Returns this buffer's position. </p>
     *
     * @return  The position of this buffer
     */
    int position();

    /**
     * Sets this buffer's position.  If the mark is defined and larger than the
     * new position then it is discarded. </p>
     *
     * @param  newPosition
     *         The new position value; must be non-negative
     *         and no larger than the current limit
     *
     * @return  This buffer
     *
     * @throws  IllegalArgumentException
     *          If the preconditions on <tt>newPosition</tt> do not hold
     */
    Buffer position(int newPosition);

    /**
     * Returns this buffer's limit. </p>
     *
     * @return  The limit of this buffer
     */
    int limit();

    /**
     * Sets this buffer's limit.  If the position is larger than the new limit
     * then it is set to the new limit.  If the mark is defined and larger than
     * the new limit then it is discarded. </p>
     *
     * @param  newLimit
     *         The new limit value; must be non-negative
     *         and no larger than this buffer's capacity
     *
     * @return  This buffer
     *
     * @throws  IllegalArgumentException
     *          If the preconditions on <tt>newLimit</tt> do not hold
     */
    Buffer limit(int newLimit);

    /**
     * Sets this buffer's mark at its position. </p>
     *
     * @return  This buffer
     */
    Buffer mark();

    /**
     * Resets this buffer's position to the previously-marked position.
     *
     * <p> Invoking this method neither changes nor discards the mark's
     * value. </p>
     *
     * @return  This buffer
     *
     * @throws  InvalidMarkException If the mark has not been set
     */
    Buffer reset();

    /**
     * Clears this buffer.  The position is set to zero, the limit is set to
     * the capacity, and the mark is discarded.
     *
     * <p> Invoke this method before using a sequence of channel-read or
     * <i>put</i> operations to fill this buffer.  For example:
     *
     * <blockquote><pre>
     * buf.clear();     // Prepare buffer for reading
     * in.read(buf);    // Read data</pre></blockquote>
     *
     * <p> This method does not actually erase the data in the buffer, but it
     * is named as if it did because it will most often be used in situations
     * in which that might as well be the case. </p>
     *
     * @return  This buffer
     */
    Buffer clear();

    /**
     * Flips this buffer.  The limit is set to the current position and then
     * the position is set to zero.  If the mark is defined then it is
     * discarded.
     *
     * <p> After a sequence of channel-read or <i>put</i> operations, invoke
     * this method to prepare for a sequence of channel-write or relative
     * <i>get</i> operations.  For example:
     *
     * <blockquote><pre>
     * buf.put(magic);    // Prepend header
     * in.read(buf);      // Read data into rest of buffer
     * buf.flip();        // Flip buffer
     * out.write(buf);    // Write header + data to channel</pre></blockquote>
     *
     * <p> This method is often used in conjunction with the 
     * {@link Buffer#compact compact} method when transferring data from
     * one place to another.  </p>
     *
     * @return  This buffer
     */
    Buffer flip();

    /**
     * Rewinds this buffer.  The position is set to zero and the mark is
     * discarded.
     *
     * <p> Invoke this method before a sequence of channel-write or <i>get</i>
     * operations, assuming that the limit has already been set
     * appropriately.  For example:
     *
     * <blockquote><pre>
     * out.write(buf);    // Write remaining data
     * buf.rewind();      // Rewind buffer
     * buf.get(array);    // Copy data into array</pre></blockquote>
     *
     * @return  This buffer
     */
    Buffer rewind();

    /**
     * Returns the number of elements between the current position and the
     * limit. </p>
     *
     * @return  The number of elements remaining in this buffer
     */
    int remaining();

    /**
     * Tells whether there are any elements between the current position and
     * the limit. </p>
     *
     * @return  <tt>true</tt> if, and only if, there is at least one element
     *          remaining in this buffer
     */
    boolean hasRemaining();

    /**
     * Tells whether or not this buffer is read-only. </p>
     *
     * @return  <tt>true</tt> if, and only if, this buffer is read-only
     */
    @SuppressWarnings("UnusedDeclaration")
    boolean isReadOnly();

    /**
     * Creates a new <code>Buffer</code> whose content is a shared subsequence
     * of this buffer's content.
     *
     * <p> The content of the new buffer will start at this buffer's current
     * position.  Changes to this buffer's content will be visible in the new
     * buffer, and vice versa; the two buffers' position, limit, and mark
     * values will be independent.
     *
     * <p> The new buffer's position will be zero, its capacity and its limit
     * will be the number of bytes remaining in this buffer, and its mark
     * will be undefined.  The new buffer will be direct if, and only if, this
     * buffer is direct, and it will be read-only if, and only if, this buffer
     * is read-only.  </p>
     *
     * @return  The new <code>Buffer</code>
     */
    Buffer slice();

    /**
     * Creates a new <code>Buffer</code> whose content is a shared subsequence of
     * this buffer's content.
     *
     * <p> The content of the new buffer will start at passed position and end
     * at passed limit.
     * Changes to this buffer's content will be visible in the new
     * buffer, and vice versa; the two buffer's position, limit, and mark
     * values will be independent.
     *
     * <p> The new buffer's position will be zero, its capacity and its limit
     * will be the number of bytes remaining in this buffer, and its mark
     * will be undefined.  The new buffer will be direct if, and only if, this
     * buffer is direct, and it will be read-only if, and only if, this buffer
     * is read-only.  </p>
     *
     * @return  The new <code>Buffer</code>
     */
    Buffer slice(int position, int limit);

    /**
     * Creates a new <code>Buffer</code> that shares this buffer's content.
     *
     * <p> The content of the new buffer will be that of this buffer.  Changes
     * to this buffer's content will be visible in the new buffer, and vice
     * versa; the two buffer's position, limit, and mark values will be
     * independent.
     *
     * <p> The new buffer's capacity, limit, position, and mark values will be
     * identical to those of this buffer.  The new buffer will be direct if,
     * and only if, this buffer is direct, and it will be read-only if, and
     * only if, this buffer is read-only.  </p>
     *
     * @return  The new <code>Buffer</code>
     */
    Buffer duplicate();

    /**
     * Creates a new, read-only <code>Buffer</code> that shares this buffer's
     * content.
     *
     * <p> The content of the new buffer will be that of this buffer.  Changes
     * to this buffer's content will be visible in the new buffer; the new
     * buffer itself, however, will be read-only and will not allow the shared
     * content to be modified.  The two buffer's position, limit, and mark
     * values will be independent.
     *
     * <p> The new buffer's capacity, limit, position, and mark values will be
     * identical to those of this buffer.
     *
     * <p> If this buffer is itself read-only then this method behaves in
     * exactly the same way as the {@link #duplicate duplicate} method.  </p>
     *
     * @return  The new, read-only <code>Buffer</code>
     */
    @SuppressWarnings("UnusedDeclaration")
    Buffer asReadOnlyBuffer();
    
    // -- Singleton get/put methods --
    /**
     * Relative <i>get</i> method.  Reads the byte at this buffer's
     * current position, and then increments the position. </p>
     *
     * @return  The byte at the buffer's current position
     *
     * @throws  BufferUnderflowException
     *          If the buffer's current position is not smaller than its limit
     */
    byte get();

    /**
     * Relative <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> Writes the given byte into this buffer at the current
     * position, and then increments the position. </p>
     *
     * @param  b
     *         The byte to be written
     *
     * @return  This buffer
     *
     * @throws BufferOverflowException
     *          If this buffer's current position is not smaller than its limit
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer put(byte b);

    /**
     * Absolute <i>get</i> method.  Reads the byte at the given
     * index. </p>
     *
     * @param  index
     *         The index from which the byte will be read
     *
     * @return  The byte at the given index
     *
     * @throws  IndexOutOfBoundsException
     *          If <tt>index</tt> is negative
     *          or not smaller than the buffer's limit
     */
    byte get(int index);

    /**
     * Absolute <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> Writes the given byte into this buffer at the given
     * index. </p>
     *
     * @param  index
     *         The index at which the byte will be written
     *
     * @param  b
     *         The byte value to be written
     *
     * @return  This buffer
     *
     * @throws  IndexOutOfBoundsException
     *          If <tt>index</tt> is negative
     *          or not smaller than the buffer's limit
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer put(int index, byte b);

    // -- Bulk get operations --

    /**
     * Relative bulk <i>get</i> method.
     *
     * <p> This method transfers bytes from this buffer into the given
     * destination array.  An invocation of this method of the form
     * <tt>src.get(a)</tt> behaves in exactly the same way as the invocation
     *
     * <pre>
     *     src.get(a, 0, a.length) </pre>
     *
     * @return  This buffer
     *
     * @throws BufferUnderflowException
     *          If there are fewer than <tt>length</tt> bytes
     *          remaining in this buffer
     */
    Buffer get(byte[] dst);

    /**
     * Relative bulk <i>get</i> method.
     *
     * <p> This method transfers bytes from this buffer into the given
     * destination array.  If there are fewer bytes remaining in the
     * buffer than are required to satisfy the request, that is, if
     * <tt>length</tt>&nbsp;<tt>&gt;</tt>&nbsp;<tt>remaining()</tt>, then no
     * bytes are transferred and a {@link BufferUnderflowException} is
     * thrown.
     *
     * <p> Otherwise, this method copies <tt>length</tt> bytes from this
     * buffer into the given array, starting at the current position of this
     * buffer and at the given offset in the array.  The position of this
     * buffer is then incremented by <tt>length</tt>.
     *
     * <p> In other words, an invocation of this method of the form
     * <tt>src.get(dst,&nbsp;off,&nbsp;len)</tt> has exactly the same effect as
     * the loop
     *
     * <pre>
     *     for (int i = off; i < off + len; i++)
     *         dst[i] = src.get(); </pre>
     *
     * except that it first checks that there are sufficient bytes in
     * this buffer and it is potentially much more efficient. </p>
     *
     * @param  dst
     *         The array into which bytes are to be written
     *
     * @param  offset
     *         The offset within the array of the first byte to be
     *         written; must be non-negative and no larger than
     *         <tt>dst.length</tt>
     *
     * @param  length
     *         The maximum number of bytes to be written to the given
     *         array; must be non-negative and no larger than
     *         <tt>dst.length - offset</tt>
     *
     * @return  This buffer
     *
     * @throws BufferUnderflowException
     *          If there are fewer than <tt>length</tt> bytes
     *          remaining in this buffer
     *
     * @throws  IndexOutOfBoundsException
     *          If the preconditions on the <tt>offset</tt> and <tt>length</tt>
     *          parameters do not hold
     */
    Buffer get(byte[] dst, int offset, int length);

    /**
     * Relative bulk <i>get</i> method.
     *
     * <p> This method transfers bytes from this buffer into the given
     * destination {@link ByteBuffer}.  An invocation of this method of the form
     * <tt>src.get(a)</tt> behaves in exactly the same way as the invocation
     *
     * <pre>
     *     src.get(a, 0, a.remaining()) </pre>
     *
     * @return  This buffer
     *
     * @throws BufferUnderflowException
     *          If there are fewer than <tt>length</tt> bytes
     *          remaining in this buffer
     */
    Buffer get(ByteBuffer dst);

    /**
     * Relative bulk <i>get</i> method.
     *
     * <p> This method transfers bytes from this buffer into the given
     * destination {@link ByteBuffer}.  If there are fewer bytes remaining in the
     * buffer than are required to satisfy the request, that is, if
     * <tt>length</tt>&nbsp;<tt>&gt;</tt>&nbsp;<tt>remaining()</tt>, then no
     * bytes are transferred and a {@link BufferUnderflowException} is
     * thrown.
     *
     * <p> Otherwise, this method copies <tt>length</tt> bytes from this
     * buffer into the given {@link ByteBuffer}, starting at the current position of this
     * buffer and at the given offset in the {@link ByteBuffer}.  The position of this
     * buffer is then incremented by <tt>length</tt>.
     *
     * <p> In other words, an invocation of this method of the form
     * <tt>src.get(dst,&nbsp;off,&nbsp;len)</tt> has exactly the same effect as
     * the loop
     *
     * <pre>
     *     for (int i = off; i < off + len; i++)
     *         dst.put(i) = src.get(); </pre>
     *
     * except that it first checks that there are sufficient bytes in
     * this buffer and it is potentially much more efficient. </p>
     *
     * @param  dst
     *         The {@link ByteBuffer} into which bytes are to be written
     *
     * @param  offset
     *         The offset within the {@link ByteBuffer} of the first byte to be
     *         written; must be non-negative and no larger than
     *         <tt>dst.remaining()</tt>
     *
     * @param  length
     *         The maximum number of bytes to be written to the given
     *         array; must be non-negative and no larger than
     *         <tt>dst.remaining() - offset</tt>
     *
     * @return  This buffer
     *
     * @throws BufferUnderflowException
     *          If there are fewer than <tt>length</tt> bytes
     *          remaining in this buffer
     *
     * @throws  IndexOutOfBoundsException
     *          If the preconditions on the <tt>offset</tt> and <tt>length</tt>
     *          parameters do not hold
     */
    Buffer get(ByteBuffer dst, int offset, int length);

    
    // -- Bulk put operations --
    /**
     * Relative bulk <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> This method transfers the bytes remaining in the given source
     * buffer into this buffer.  If there are more bytes remaining in the
     * source buffer than in this buffer, that is, if
     * <tt>src.remaining()</tt>&nbsp;<tt>&gt;</tt>&nbsp;<tt>remaining()</tt>,
     * then no bytes are transferred and a {@link
     * BufferOverflowException} is thrown.
     *
     * <p> Otherwise, this method copies
     * <i>n</i>&nbsp;=&nbsp;<tt>src.remaining()</tt> bytes from the given
     * buffer into this buffer, starting at each buffer's current position.
     * The positions of both buffers are then incremented by <i>n</i>.
     *
     * <p> In other words, an invocation of this method of the form
     * <tt>dst.put(src)</tt> has exactly the same effect as the loop
     *
     * <pre>
     *     while (src.hasRemaining())
     *         dst.put(src.get()); </pre>
     *
     * except that it first checks that there is sufficient space in this
     * buffer and it is potentially much more efficient. </p>
     *
     * @param  src
     *         The source buffer from which bytes are to be read;
     *         must not be this buffer
     *
     * @return  This buffer
     *
     * @throws BufferOverflowException
     *          If there is insufficient space in this buffer
     *          for the remaining bytes in the source buffer
     *
     * @throws  IllegalArgumentException
     *          If the source buffer is this buffer
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer put(Buffer src);

    // -- Bulk put operations --
    /**
     * Relative bulk <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> This method transfers the "length" bytes from the given source
     * buffer into this buffer.  If this buffer has less bytes remaining than
     * length, that is, if
     * <tt>length</tt>&nbsp;<tt>&gt;</tt>&nbsp;<tt>remaining()</tt>,
     * then no bytes are transferred and a {@link
     * BufferOverflowException} is thrown.
     *
     * <p> Otherwise, this method copies
     * <i>n</i>&nbsp;=&nbsp;<tt>length</tt> bytes from the given
     * <tt>postion</tt> in the source buffer into this buffer, starting from
     * the current buffer position.
     * The positions of this buffer is then incremented by <i>length</i>.
     *
     * <p> In other words, an invocation of this method of the form
     * <tt>dst.put(src, position, length)</tt> has exactly the same effect
     * as the loop
     *
     * <pre>
     *     for (int i = 0; i < length; i++)
     *         dst.put(src.get(i + position)); </pre>
     *
     * except that it first checks that there is sufficient space in this
     * buffer and it is potentially much more efficient. </p>
     *
     * @param  src
     *         The source buffer from which bytes are to be read;
     *         must not be this buffer
     *
     * @param position starting position in the source buffer
     *
     * @param length number of bytes to be copied
     *
     * @return  This buffer
     *
     * @throws BufferOverflowException
     *          If there is insufficient space in this buffer
     *          for the remaining bytes in the source buffer
     *
     * @throws  IllegalArgumentException
     *          If the source buffer is this buffer
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer put(Buffer src, int position, int length);

    // -- Bulk put operations --
    /**
     * Relative bulk <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> This method transfers the bytes remaining in the given source
     * buffer into this buffer.  If there are more bytes remaining in the
     * source buffer than in this buffer, that is, if
     * <tt>src.remaining()</tt>&nbsp;<tt>&gt;</tt>&nbsp;<tt>remaining()</tt>,
     * then no bytes are transferred and a {@link
     * BufferOverflowException} is thrown.
     *
     * <p> Otherwise, this method copies
     * <i>n</i>&nbsp;=&nbsp;<tt>src.remaining()</tt> bytes from the given
     * buffer into this buffer, starting at each buffer's current position.
     * The positions of both buffers are then incremented by <i>n</i>.
     *
     * <p> In other words, an invocation of this method of the form
     * <tt>dst.put(src)</tt> has exactly the same effect as the loop
     *
     * <pre>
     *     while (src.hasRemaining())
     *         dst.put(src.get()); </pre>
     *
     * except that it first checks that there is sufficient space in this
     * buffer and it is potentially much more efficient. </p>
     *
     * @param  src
     *         The source buffer from which bytes are to be read;
     *         must not be this buffer
     *
     * @return  This buffer
     *
     * @throws BufferOverflowException
     *          If there is insufficient space in this buffer
     *          for the remaining bytes in the source buffer
     *
     * @throws  IllegalArgumentException
     *          If the source buffer is this buffer
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer put(ByteBuffer src);

    // -- Bulk put operations --
    /**
     * Relative bulk <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> This method transfers the "length" bytes from the given source
     * buffer into this buffer.  If this buffer has less bytes remaining than
     * length, that is, if
     * <tt>length</tt>&nbsp;<tt>&gt;</tt>&nbsp;<tt>remaining()</tt>,
     * then no bytes are transferred and a {@link
     * BufferOverflowException} is thrown.
     *
     * <p> Otherwise, this method copies
     * <i>n</i>&nbsp;=&nbsp;<tt>length</tt> bytes from the given
     * <tt>postion</tt> in the source buffer into this buffer, starting from
     * the current buffer position.
     * The positions of this buffer is then incremented by <i>length</i>.
     *
     * <p> In other words, an invocation of this method of the form
     * <tt>dst.put(src, position, length)</tt> has exactly the same effect
     * as the loop
     *
     * <pre>
     *     for (int i = 0; i < length; i++)
     *         dst.put(src.get(i + position)); </pre>
     *
     * except that it first checks that there is sufficient space in this
     * buffer and it is potentially much more efficient. </p>
     *
     * @param  src
     *         The source buffer from which bytes are to be read;
     *         must not be this buffer
     *
     * @param position starting position in the source buffer
     *
     * @param length number of bytes to be copied
     *
     * @return  This buffer
     *
     * @throws BufferOverflowException
     *          If there is insufficient space in this buffer
     *          for the remaining bytes in the source buffer
     *
     * @throws  IllegalArgumentException
     *          If the source buffer is this buffer
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer put(ByteBuffer src, int position, int length);

    /**
     * Relative bulk <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> This method transfers the entire content of the given source
     * byte array into this buffer.  An invocation of this method of the
     * form <tt>dst.put(a)</tt> behaves in exactly the same way as the
     * invocation
     *
     * <pre>
     *     dst.put(a, 0, a.length) </pre>
     *
     * @return  This buffer
     *
     * @throws BufferOverflowException
     *          If there is insufficient space in this buffer
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer put(byte[] src);
    
    /**
     * Relative bulk <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> This method transfers bytes into this buffer from the given
     * source array.  If there are more bytes to be copied from the array
     * than remain in this buffer, that is, if
     * <tt>length</tt>&nbsp;<tt>&gt;</tt>&nbsp;<tt>remaining()</tt>, then no
     * bytes are transferred and a {@link BufferOverflowException} is
     * thrown.
     *
     * <p> Otherwise, this method copies <tt>length</tt> bytes from the
     * given array into this buffer, starting at the given offset in the array
     * and at the current position of this buffer.  The position of this buffer
     * is then incremented by <tt>length</tt>.
     *
     * <p> In other words, an invocation of this method of the form
     * <tt>dst.put(src,&nbsp;off,&nbsp;len)</tt> has exactly the same effect as
     * the loop
     *
     * <pre>
     *     for (int i = off; i < off + len; i++)
     *         dst.put(a[i]); </pre>
     *
     * except that it first checks that there is sufficient space in this
     * buffer and it is potentially much more efficient. </p>
     *
     * @param  src
     *         The array from which bytes are to be read
     *
     * @param  offset
     *         The offset within the array of the first byte to be read;
     *         must be non-negative and no larger than <tt>array.length</tt>
     *
     * @param  length
     *         The number of bytes to be read from the given array;
     *         must be non-negative and no larger than
     *         <tt>array.length - offset</tt>
     *
     * @return  This buffer
     *
     * @throws BufferOverflowException
     *          If there is insufficient space in this buffer
     *
     * @throws  IndexOutOfBoundsException
     *          If the preconditions on the <tt>offset</tt> and <tt>length</tt>
     *          parameters do not hold
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer put(byte[] src, int offset, int length);

    /**
     * Relative bulk <i>put</i> method&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> This method transfers bytes into this buffer from the given
     * 8-bit source {@link String}.  If the source {@link String#length()} is
     * bigger than this buffer's remaining, that is, if
     * <tt>length()</tt>&nbsp;<tt>&gt;</tt>&nbsp;<tt>remaining()</tt>, then no
     * bytes are transferred and a {@link BufferOverflowException} is
     * thrown.
     *
     * <p> Otherwise, this method copies <tt>length</tt> bytes from the
     * given {@link String} into this buffer.
     *
     * <p> In other words, an invocation of this method of the form
     * <tt>dst.put8BitString(src)</tt> has exactly the same effect as
     * the loop
     *
     * <pre>
     *     for (int i = 0; i < src.length(); i++)
     *         dst.put((byte) src.charAt(i)); </pre>
     *
     * except that it first checks that there is sufficient space in this
     * buffer and it is potentially much more efficient. </p>
     *
     * @param  s
     *         The {@link String} from which bytes are to be read
     *
     * @return  This buffer
     *
     * @throws BufferOverflowException
     *          If there is insufficient space in this buffer
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    @SuppressWarnings("UnusedDeclaration")
    Buffer put8BitString(String s);
    
    /**
     * Compacts this buffer&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> The bytes between the buffer's current position and its limit,
     * if any, are copied to the beginning of the buffer.  That is, the
     * byte at index <i>p</i>&nbsp;=&nbsp;<tt>position()</tt> is copied
     * to index zero, the byte at index <i>p</i>&nbsp;+&nbsp;1 is copied
     * to index one, and so forth until the byte at index
     * <tt>limit()</tt>&nbsp;-&nbsp;1 is copied to index
     * <i>n</i>&nbsp;=&nbsp;<tt>limit()</tt>&nbsp;-&nbsp;<tt>1</tt>&nbsp;-&nbsp;<i>p</i>.
     * The buffer's position is then set to <i>n+1</i> and its limit is set to
     * its capacity.  The mark, if defined, is discarded.
     *
     * <p> The buffer's position is set to the number of bytes copied,
     * rather than to zero, so that an invocation of this method can be
     * followed immediately by an invocation of another relative <i>put</i>
     * method. </p>
     *

     *
     * <p> Invoke this method after writing data from a buffer in case the
     * write was incomplete.  The following loop, for example, copies bytes
     * from one channel to another via the buffer <tt>buf</tt>:
     *
     * <blockquote><pre>
     * buf.clear();          // Prepare buffer for use
     * for (;;) {
     *     if (in.read(buf) < 0 && !buf.hasRemaining())
     *         break;        // No more bytes to transfer
     *     buf.flip();
     *     out.write(buf);
     *     buf.compact();    // In case of partial write
     * }</pre></blockquote>
     *

     *
     * @return  This buffer
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer compact();

    /**
     * Retrieves this buffer's byte order.
     *
     * <p> The byte order is used when reading or writing multibyte values, and
     * when creating buffers that are views of this <code>Buffer</code>.  The order of
     * a newly-created <code>Buffer</code> is always {@link ByteOrder#BIG_ENDIAN
     * BIG_ENDIAN}.  </p>
     *
     * @return  This buffer's byte order
     */
    ByteOrder order();

    /**
     * Modifies this buffer's byte order.  </p>
     *
     * @param  bo
     *         The new byte order,
     *         either {@link ByteOrder#BIG_ENDIAN BIG_ENDIAN}
     *         or {@link ByteOrder#LITTLE_ENDIAN LITTLE_ENDIAN}
     *
     * @return  This buffer
     */
    Buffer order(ByteOrder bo);

    /**
     * Relative <i>get</i> method for reading a char value.
     *
     * <p> Reads the next two bytes at this buffer's current position,
     * composing them into a char value according to the current byte order,
     * and then increments the position by two.  </p>
     *
     * @return  The char value at the buffer's current position
     *
     * @throws BufferUnderflowException
     *          If there are fewer than two bytes
     *          remaining in this buffer
     */
    char getChar();

    /**
     * Relative <i>put</i> method for writing a char
     * value&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> Writes two bytes containing the given char value, in the
     * current byte order, into this buffer at the current position, and then
     * increments the position by two.  </p>
     *
     * @param  value
     *         The char value to be written
     *
     * @return  This buffer
     *
     * @throws BufferOverflowException
     *          If there are fewer than two bytes
     *          remaining in this buffer
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer putChar(char value);

    /**
     * Absolute <i>get</i> method for reading a char value.
     *
     * <p> Reads two bytes at the given index, composing them into a
     * char value according to the current byte order.  </p>
     *
     * @param  index
     *         The index from which the bytes will be read
     *
     * @return  The char value at the given index
     *
     * @throws  IndexOutOfBoundsException
     *          If <tt>index</tt> is negative
     *          or not smaller than the buffer's limit,
     *          minus one
     */
    char getChar(int index);

    /**
     * Absolute <i>put</i> method for writing a char
     * value&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> Writes two bytes containing the given char value, in the
     * current byte order, into this buffer at the given index.  </p>
     *
     * @param  index
     *         The index at which the bytes will be written
     *
     * @param  value
     *         The char value to be written
     *
     * @return  This buffer
     *
     * @throws  IndexOutOfBoundsException
     *          If <tt>index</tt> is negative
     *          or not smaller than the buffer's limit,
     *          minus one
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer putChar(int index, char value);

    /**
     * Relative <i>get</i> method for reading a short value.
     *
     * <p> Reads the next two bytes at this buffer's current position,
     * composing them into a short value according to the current byte order,
     * and then increments the position by two.  </p>
     *
     * @return  The short value at the buffer's current position
     *
     * @throws BufferUnderflowException
     *          If there are fewer than two bytes
     *          remaining in this buffer
     */
    short getShort();

    /**
     * Relative <i>put</i> method for writing a short
     * value&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> Writes two bytes containing the given short value, in the
     * current byte order, into this buffer at the current position, and then
     * increments the position by two.  </p>
     *
     * @param  value
     *         The short value to be written
     *
     * @return  This buffer
     *
     * @throws BufferOverflowException
     *          If there are fewer than two bytes
     *          remaining in this buffer
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer putShort(short value);

    /**
     * Absolute <i>get</i> method for reading a short value.
     *
     * <p> Reads two bytes at the given index, composing them into a
     * short value according to the current byte order.  </p>
     *
     * @param  index
     *         The index from which the bytes will be read
     *
     * @return  The short value at the given index
     *
     * @throws  IndexOutOfBoundsException
     *          If <tt>index</tt> is negative
     *          or not smaller than the buffer's limit,
     *          minus one
     */
    short getShort(int index);

    /**
     * Absolute <i>put</i> method for writing a short
     * value&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> Writes two bytes containing the given short value, in the
     * current byte order, into this buffer at the given index.  </p>
     *
     * @param  index
     *         The index at which the bytes will be written
     *
     * @param  value
     *         The short value to be written
     *
     * @return  This buffer
     *
     * @throws  IndexOutOfBoundsException
     *          If <tt>index</tt> is negative
     *          or not smaller than the buffer's limit,
     *          minus one
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer putShort(int index, short value);

    /**
     * Relative <i>get</i> method for reading an int value.
     *
     * <p> Reads the next four bytes at this buffer's current position,
     * composing them into an int value according to the current byte order,
     * and then increments the position by four.  </p>
     *
     * @return  The int value at the buffer's current position
     *
     * @throws BufferUnderflowException
     *          If there are fewer than four bytes
     *          remaining in this buffer
     */
    int getInt();

    /**
     * Relative <i>put</i> method for writing an int
     * value&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> Writes four bytes containing the given int value, in the
     * current byte order, into this buffer at the current position, and then
     * increments the position by four.  </p>
     *
     * @param  value
     *         The int value to be written
     *
     * @return  This buffer
     *
     * @throws BufferOverflowException
     *          If there are fewer than four bytes
     *          remaining in this buffer
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer putInt(int value);

    /**
     * Absolute <i>get</i> method for reading an int value.
     *
     * <p> Reads four bytes at the given index, composing them into a
     * int value according to the current byte order.  </p>
     *
     * @param  index
     *         The index from which the bytes will be read
     *
     * @return  The int value at the given index
     *
     * @throws  IndexOutOfBoundsException
     *          If <tt>index</tt> is negative
     *          or not smaller than the buffer's limit,
     *          minus three
     */
    int getInt(int index);

    /**
     * Absolute <i>put</i> method for writing an int
     * value&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> Writes four bytes containing the given int value, in the
     * current byte order, into this buffer at the given index.  </p>
     *
     * @param  index
     *         The index at which the bytes will be written
     *
     * @param  value
     *         The int value to be written
     *
     * @return  This buffer
     *
     * @throws  IndexOutOfBoundsException
     *          If <tt>index</tt> is negative
     *          or not smaller than the buffer's limit,
     *          minus three
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer putInt(int index, int value);

    /**
     * Relative <i>get</i> method for reading a long value.
     *
     * <p> Reads the next eight bytes at this buffer's current position,
     * composing them into a long value according to the current byte order,
     * and then increments the position by eight.  </p>
     *
     * @return  The long value at the buffer's current position
     *
     * @throws BufferUnderflowException
     *          If there are fewer than eight bytes
     *          remaining in this buffer
     */
    long getLong();

    /**
     * Relative <i>put</i> method for writing a long
     * value&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> Writes eight bytes containing the given long value, in the
     * current byte order, into this buffer at the current position, and then
     * increments the position by eight.  </p>
     *
     * @param  value
     *         The long value to be written
     *
     * @return  This buffer
     *
     * @throws BufferOverflowException
     *          If there are fewer than eight bytes
     *          remaining in this buffer
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer putLong(long value);

    /**
     * Absolute <i>get</i> method for reading a long value.
     *
     * <p> Reads eight bytes at the given index, composing them into a
     * long value according to the current byte order.  </p>
     *
     * @param  index
     *         The index from which the bytes will be read
     *
     * @return  The long value at the given index
     *
     * @throws  IndexOutOfBoundsException
     *          If <tt>index</tt> is negative
     *          or not smaller than the buffer's limit,
     *          minus seven
     */
    long getLong(int index);

    /**
     * Absolute <i>put</i> method for writing a long
     * value&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> Writes eight bytes containing the given long value, in the
     * current byte order, into this buffer at the given index.  </p>
     *
     * @param  index
     *         The index at which the bytes will be written
     *
     * @param  value
     *         The long value to be written
     *
     * @return  This buffer
     *
     * @throws  IndexOutOfBoundsException
     *          If <tt>index</tt> is negative
     *          or not smaller than the buffer's limit,
     *          minus seven
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer putLong(int index, long value);

    /**
     * Relative <i>get</i> method for reading a float value.
     *
     * <p> Reads the next four bytes at this buffer's current position,
     * composing them into a float value according to the current byte order,
     * and then increments the position by four.  </p>
     *
     * @return  The float value at the buffer's current position
     *
     * @throws BufferUnderflowException
     *          If there are fewer than four bytes
     *          remaining in this buffer
     */
    float getFloat();

    /**
     * Relative <i>put</i> method for writing a float
     * value&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> Writes four bytes containing the given float value, in the
     * current byte order, into this buffer at the current position, and then
     * increments the position by four.  </p>
     *
     * @param  value
     *         The float value to be written
     *
     * @return  This buffer
     *
     * @throws BufferOverflowException
     *          If there are fewer than four bytes
     *          remaining in this buffer
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer putFloat(float value);

    /**
     * Absolute <i>get</i> method for reading a float value.
     *
     * <p> Reads four bytes at the given index, composing them into a
     * float value according to the current byte order.  </p>
     *
     * @param  index
     *         The index from which the bytes will be read
     *
     * @return  The float value at the given index
     *
     * @throws  IndexOutOfBoundsException
     *          If <tt>index</tt> is negative
     *          or not smaller than the buffer's limit,
     *          minus three
     */
    float getFloat(int index);

    /**
     * Absolute <i>put</i> method for writing a float
     * value&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> Writes four bytes containing the given float value, in the
     * current byte order, into this buffer at the given index.  </p>
     *
     * @param  index
     *         The index at which the bytes will be written
     *
     * @param  value
     *         The float value to be written
     *
     * @return  This buffer
     *
     * @throws  IndexOutOfBoundsException
     *          If <tt>index</tt> is negative
     *          or not smaller than the buffer's limit,
     *          minus three
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer putFloat(int index, float value);

    /**
     * Relative <i>get</i> method for reading a double value.
     *
     * <p> Reads the next eight bytes at this buffer's current position,
     * composing them into a double value according to the current byte order,
     * and then increments the position by eight.  </p>
     *
     * @return  The double value at the buffer's current position
     *
     * @throws BufferUnderflowException
     *          If there are fewer than eight bytes
     *          remaining in this buffer
     */
    double getDouble();

    /**
     * Relative <i>put</i> method for writing a double
     * value&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> Writes eight bytes containing the given double value, in the
     * current byte order, into this buffer at the current position, and then
     * increments the position by eight.  </p>
     *
     * @param  value
     *         The double value to be written
     *
     * @return  This buffer
     *
     * @throws BufferOverflowException
     *          If there are fewer than eight bytes
     *          remaining in this buffer
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer putDouble(double value);

    /**
     * Absolute <i>get</i> method for reading a double value.
     *
     * <p> Reads eight bytes at the given index, composing them into a
     * double value according to the current byte order.  </p>
     *
     * @param  index
     *         The index from which the bytes will be read
     *
     * @return  The double value at the given index
     *
     * @throws  IndexOutOfBoundsException
     *          If <tt>index</tt> is negative
     *          or not smaller than the buffer's limit,
     *          minus seven
     */
    double getDouble(int index);

    /**
     * Absolute <i>put</i> method for writing a double
     * value&nbsp;&nbsp;<i>(optional operation)</i>.
     *
     * <p> Writes eight bytes containing the given double value, in the
     * current byte order, into this buffer at the given index.  </p>
     *
     * @param  index
     *         The index at which the bytes will be written
     *
     * @param  value
     *         The double value to be written
     *
     * @return  This buffer
     *
     * @throws  IndexOutOfBoundsException
     *          If <tt>index</tt> is negative
     *          or not smaller than the buffer's limit,
     *          minus seven
     *
     * @throws ReadOnlyBufferException
     *          If this buffer is read-only
     */
    Buffer putDouble(int index, double value);

    /**
     * Returns {@link Buffer} content as {@link String}, using default {@link Charset}
     *
     * @return {@link String} representation of this {@link Buffer} content.
     */
    String toStringContent();

    /**
     * Returns {@link Buffer} content as {@link String}
     * @param charset the {@link Charset}, which will be use
     * for byte[] -> {@link String} transformation.
     *
     * @return {@link String} representation of this {@link Buffer} content.
     */
    String toStringContent(Charset charset);

    /**
     * Returns {@link Buffer}'s chunk content as {@link String}
     * @param charset the {@link Charset}, which will be use
     * for byte[] -> {@link String} transformation.
     * @param position the first byte offset in the <tt>Buffer</tt> (inclusive)
     * @param limit the last byte offset in the <tt>Buffer</tt> (exclusive)
     *
     * @return {@link String} representation of part of this {@link Buffer}.
     */
    String toStringContent(Charset charset, int position, int limit);

    /**
     * Generate a hex dump of this {@link Buffer}'s content.
     *
     * @param appendable the {@link Appendable} to dump this {@link Buffer}'s
     *                   content to.
     * @since 2.3.23
     */
    @SuppressWarnings("unused")
    void dumpHex(final java.lang.Appendable appendable);

    /**
     * <p>
     * Converts this <code>Buffer</code> to a {@link ByteBuffer}.
     * If this <code>Buffer</code> is not composite - then returned
     * {@link ByteBuffer}'s content is a shared subsequence of this buffer's
     * content, with {@link CompositeBuffer} this is not guaranteed.
     * The position of the returned {@link ByteBuffer} is not guaranteed to be 0,
     * the capacity of the returned {@link ByteBuffer} is not guaranteed to be
     * equal to the capacity of this <code>Buffer</code>.
     * It is guaranteed that the result of the returned ByteBuffer's
     * {@link ByteBuffer#remaining()} call will be equal to this Buffer's
     * {@link #remaining()} call.
     * The Buffer's and ByteBuffer's position, limit, and mark values are not
     * guaranteed to be independent, so it's recommended to save and restore
     * position, limit values if it is planned to change them or
     * {@link ByteBuffer#slice()} the returned {@link ByteBuffer}.
     * <p/>
     *
     * @return this <code>Buffer</code> as a {@link ByteBuffer}.
     */
    ByteBuffer toByteBuffer();

    /**
     * <p>
     * Converts this <code>Buffer</code> to a {@link ByteBuffer}.
     * If this <code>Buffer</code> is not composite - then returned
     * {@link ByteBuffer}'s content is a shared subsequence of this buffer's
     * content, with {@link CompositeBuffer} this is not guaranteed.
     * The position of the returned {@link ByteBuffer} is not guaranteed to be 0,
     * the capacity of the returned {@link ByteBuffer} is not guaranteed to be
     * equal to the capacity of this <code>Buffer</code>.
     * It is guaranteed that the result of the returned ByteBuffer's
     * {@link ByteBuffer#remaining()} call will be equal (limit - position).
     * The Buffer's and ByteBuffer's position, limit, and mark values are not
     * guaranteed to be independent, so it's recommended to save and restore
     * position, limit values if it is planned to change them or
     * {@link ByteBuffer#slice()} the returned {@link ByteBuffer}.
     * <p/>
     *
     * @param position the position for the starting subsequence for the
     *                 returned {@link ByteBuffer}.
     * @param limit the limit for the ending of the subsequence of the
     *              returned {@link ByteBuffer}.
     *
     * @return this <code>Buffer</code> as a {@link ByteBuffer}.
     */
    ByteBuffer toByteBuffer(int position, int limit);

    /**
     * <p>
     * Converts this <code>Buffer</code> to a {@link ByteBuffer} per {@link #toByteBuffer()}
     * and returns a {@link ByteBufferArray} containing the converted {@link ByteBuffer}.
     * It is guaranteed that returned array's ByteBuffer elements' content is
     * a shared subsequence of this buffer's content no matter if it's a
     * {@link CompositeBuffer} or not.
     * </p>
     *
     * @return Converts this <code>Buffer</code> to a {@link ByteBuffer} per {@link #toByteBuffer()}
     * and returns a {@link ByteBufferArray} containing the converted {@link ByteBuffer}.
     *
     * @see {@link #toByteBuffer()}
     */
    @SuppressWarnings("UnusedDeclaration")
    ByteBufferArray toByteBufferArray();

    /**
     * <p>
     * Converts this <code>Buffer</code> to a {@link ByteBuffer} per {@link #toByteBuffer()}
     * and adds the result to the provided {@link ByteBufferArray}.
     * It is guaranteed that returned array's ByteBuffer elements' content is
     * a shared subsequence of this buffer's content no matter if it's a
     * {@link CompositeBuffer} or not.
     * </p>
     *
     * @return returns the provided {@link ByteBufferArray} with the converted
     *  {@link ByteBuffer} added to provided <code>array</code>.
     *
     * @see {@link #toByteBuffer()}
     */
    ByteBufferArray toByteBufferArray(ByteBufferArray array);

    /**
     * <p>
     * Converts this <code>Buffer</code> to a {@link ByteBuffer} per {@link #toByteBuffer(int, int)}
     * and returns a {@link ByteBufferArray} containing the converted {@link ByteBuffer}.
     * It is guaranteed that returned array's ByteBuffer elements' content is
     * a shared subsequence of this buffer's content no matter if it's a
     * {@link CompositeBuffer} or not.
     * </p>
     *
     * @param position the start position within the source <code>buffer</code>
     * @param limit the limit, or number, of bytes to include in the resulting
     *              {@link ByteBuffer}
     *
     * @return Converts this <code>Buffer</code> to a {@link ByteBuffer} per {@link #toByteBuffer(int, int)}
     *         and returns a {@link ByteBufferArray} containing the converted {@link ByteBuffer}.
     *
     * @see {@link #toByteBuffer(int, int)}
     */
    ByteBufferArray toByteBufferArray(int position, int limit);

    /**
     * <p>
     * Converts this <code>Buffer</code> to a {@link ByteBuffer} per {@link #toByteBuffer(int, int)}
     * and adds the result to the provided {@link ByteBufferArray}.
     * It is guaranteed that returned array's ByteBuffer elements' content is
     * a shared subsequence of this buffer's content no matter if it's a
     * {@link CompositeBuffer} or not.
     * </p>
     *
     * @return returns the provided {@link ByteBufferArray} with the converted
     *         {@link ByteBuffer} added to provided <code>array</code>.
     *
     * @see {@link #toByteBuffer(int, int)}
     */
    ByteBufferArray toByteBufferArray(ByteBufferArray array, int position, int limit);

    /**
     * <p>
     * Returns a new {@link BufferArray} instance with this <code>Buffer</code>
     * added as an element to the {@link BufferArray}.
     * It is guaranteed that returned array's Buffer elements' content is
     * a shared subsequence of this buffer's content no matter if it's a
     * {@link CompositeBuffer} or not.
     * </p>
     *
     * @return Returns a new {@link BufferArray} instance with this <code>Buffer</code>
     *         added as an element to the {@link BufferArray}.
     */
    BufferArray toBufferArray();

    /**
     * <p>
     * Returns the specified {@link BufferArray} after adding this <code>Buffer</code>.
     * It is guaranteed that returned array's Buffer elements' content is
     * a shared subsequence of this buffer's content no matter if it's a
     * {@link CompositeBuffer} or not.
     * </p>
     *
     * @return Returns the specified {@link BufferArray} after adding this <code>Buffer</code>.
     */
    BufferArray toBufferArray(BufferArray array);

    /**
     * <p>
     * Updates this <code>Buffer</code>'s <code>position</code> and <code>limit</code>
     * and adds it to a new {@link BufferArray} instance.
     * It is guaranteed that returned array's Buffer elements' content is
     * a shared subsequence of this buffer's content no matter if it's a
     * {@link CompositeBuffer} or not.
     * </p>
     *
     * @param position the new position for this <code>Buffer</code>
     * @param limit the new limit for this <code>Buffer</code>
     *
     * @return adds this <code>Buffer</code> and returns the specified
     *         {@link BufferArray}.
     */
    BufferArray toBufferArray(int position, int limit);

    /**
     * <p>
     * Updates this <code>Buffer</code>'s <code>position</code> and <code>limit</code>
     * and adds it to the specified {@link BufferArray}.
     * It is guaranteed that returned array's Buffer elements' content is
     * a shared subsequence of this buffer's content no matter if it's a
     * {@link CompositeBuffer} or not.
     * </p>
     *
     * @param position the new position for this <code>Buffer</code>
     * @param limit the new limit for this <code>Buffer</code>
     *
     * @return adds this <code>Buffer</code> and returns the specified
     *         {@link BufferArray}.
     */
    BufferArray toBufferArray(BufferArray array, int position, int limit);

    /**
     * Tells whether or not this buffer is backed by an accessible byte array.
     *
     * If this method returns true then the array and arrayOffset methods may
     * safely be invoked.
     *
     * @return <tt>true</tt> if, and only if, this buffer is backed by an array
     *  and is not read-only
     *
     * @since 2.1.12
     */
    boolean hasArray();

    /**
     * Returns the byte array that backs this buffer  (optional operation).
     *
     * Modifications to this buffer's content will cause the returned array's
     * content to be modified, and vice versa.
     *
     * Invoke the hasArray method before invoking this method in order to ensure
     * that this buffer has an accessible backing array.
     *
     * @return The array that backs this buffer
     *
     * @throws ReadOnlyBufferException If this buffer is backed by an array
     *  but is read-only
     * @throws UnsupportedOperationException If this buffer is not backed by an
     *  accessible array
     *
     * @since 2.1.12
     */
    byte[] array();

    /**
     * Returns the offset within this buffer's backing array of the first
     * element of the buffer  (optional operation).
     *
     * If this buffer is backed by an array then buffer position p corresponds
     * to array index p + arrayOffset().
     *
     * Invoke the hasArray method before invoking this method in order to ensure
     * that this buffer has an accessible backing array.
     *
     * @return The offset within this buffer's array of the first element of
     *  the buffer
     *
     * @throws ReadOnlyBufferException If this buffer is backed by an array
     *  but is read-only
     * @throws UnsupportedOperationException If this buffer is not backed by an
     *  accessible array
     *
     * @since 2.1.12
     */
    int arrayOffset();

}
