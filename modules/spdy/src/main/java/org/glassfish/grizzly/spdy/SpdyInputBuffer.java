/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.grizzly.spdy;

import java.io.EOFException;
import java.io.IOException;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.glassfish.grizzly.Buffer;
import org.glassfish.grizzly.memory.Buffers;
import org.glassfish.grizzly.memory.CompositeBuffer;
import org.glassfish.grizzly.memory.MemoryManager;
import org.glassfish.grizzly.utils.DataStructures;

import static org.glassfish.grizzly.spdy.SpdyEncoderUtils.*;

/**
 *
 * @author oleksiys
 */
final class SpdyInputBuffer {
    private static final Buffer LAST_BUFFER = Buffers.wrap(
            MemoryManager.DEFAULT_MEMORY_MANAGER, new byte[] {'l', 'a', 's', 't'});

    private final AtomicInteger inputQueueSize = new AtomicInteger();
    private final BlockingQueue<Buffer> inputQueue =
            DataStructures.getLTQInstance(Buffer.class);
    private boolean isLastInputDataPolled;
    
    private final AtomicBoolean isInputClosed = new AtomicBoolean();
    
    private final SpdyStream spdyStream;
    private final SpdySession spdySession;
    
    SpdyInputBuffer(SpdyStream spdyStream) {
        this.spdyStream = spdyStream;
        spdySession = spdyStream.getSpdySession();
    }
    
    void offer(final Buffer data, final boolean isLast) {
        if (spdyStream.isInputClosed()) {
            throw new IllegalStateException("SpdyStream input is closed");
        }
        
        System.out.println("OFFER " + data + " isLast=" + isLast);
        inputQueue.offer(data);
        if (!isLast) {
            inputQueueSize.incrementAndGet();
        } else {
            inputQueue.offer(LAST_BUFFER);
            inputQueueSize.addAndGet(2);
        }
    }
    
    Buffer poll() throws IOException {
        if (isLastInputDataPolled) {
            throw new EOFException();
        }
        
        Buffer buffer;
        
        final int inputQueueSizeNow = inputQueueSize.getAndSet(0);
        
        switch (inputQueueSizeNow) {
            case 0: {
                try {
                    buffer = inputQueue.poll(spdySession.getConnection()
                                .getBlockingReadTimeout(TimeUnit.MILLISECONDS),
                            TimeUnit.MILLISECONDS);
                } catch (InterruptedException e) {
                    throw new IOException("Blocking read was interrupted");
                }

                if (buffer == null) {
                    throw new IOException("Blocking read timeout");
                } else {
                    if (buffer == LAST_BUFFER) {
                        isLastInputDataPolled = true;
                        buffer = Buffers.EMPTY_BUFFER;
                    }
                    
                    inputQueueSize.decrementAndGet();
                }
                
                break;
            }
            case 1: {
                buffer = inputQueue.poll();
                if (buffer == LAST_BUFFER) {
                    isLastInputDataPolled = true;
                    buffer = Buffers.EMPTY_BUFFER;
                }
                
                break;
            }
                
            default: {
                final CompositeBuffer compositeBuffer =
                        CompositeBuffer.newBuffer(spdySession.getMemoryManager());

                for (int i = 0; i < inputQueueSizeNow; i++) {
                    final Buffer currentBuffer = inputQueue.poll();
                    if (currentBuffer == LAST_BUFFER) {
                        isLastInputDataPolled = true;
                        break;
                    }
                    
                    compositeBuffer.append(currentBuffer);
                }
                compositeBuffer.allowBufferDispose(true);
                compositeBuffer.allowInternalBuffersDispose(true);

                buffer = compositeBuffer;
                
                break;
            }
        }
        
        if (buffer != null && buffer.hasRemaining()) {
//            System.out.println("--> Update Window delta=" + buffer.remaining());
            spdyStream.outputSink.writeDownStream0(
                    encodeWindowUpdate(spdySession.getMemoryManager(),
                    spdyStream, buffer.remaining()), null);
        }
        
        return buffer;
    }
    
    boolean isLastDataPolled() {
        return isLastInputDataPolled;
    }
    
    boolean close() {
        if (isInputClosed.compareAndSet(false, true)) {
            System.out.println("OFFER_LAST_CLOSE");
            inputQueue.offer(LAST_BUFFER);
            inputQueueSize.incrementAndGet();
            
            return true;
        }
        
        return false;
    }
    
    boolean isClosed() {
        return isInputClosed.get();
    }
}
