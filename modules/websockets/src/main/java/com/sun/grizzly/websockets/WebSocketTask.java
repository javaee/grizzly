package com.sun.grizzly.websockets;

import com.sun.grizzly.Controller;
import com.sun.grizzly.NIOContext;
import com.sun.grizzly.ProtocolChain;
import com.sun.grizzly.arp.AsyncProcessorTask;
import com.sun.grizzly.http.SelectorThread;
import com.sun.grizzly.util.SelectedKeyAttachmentLogic;
import com.sun.grizzly.util.WorkerThread;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.logging.Level;

public class WebSocketTask extends SelectedKeyAttachmentLogic implements Runnable {
    private WebSocketContext webSocketContext;

    private AsyncProcessorTask asyncProcessorTask;

    WebSocketHandler webSocketHandler;

    private boolean suspended;

    private boolean callInterrupt;

    public AsyncProcessorTask getAsyncProcessorTask() {
        return asyncProcessorTask;
    }

    public void setAsyncProcessorTask(AsyncProcessorTask apt) {
        asyncProcessorTask = apt;
    }

    public WebSocketContext getWebSocketContext() {
        return webSocketContext;
    }

    public void setWebSocketContext(WebSocketContext webSocketContext) {
        this.webSocketContext = webSocketContext;
    }

    public WebSocketHandler getWebSocketHandler() {
        return webSocketHandler;
    }

    public void setWebSocketHandler(WebSocketHandler webSocketHandler) {
        this.webSocketHandler = webSocketHandler;
    }

    public boolean isSuspended() {
        return suspended;
    }

    public void setSuspended(boolean suspended) {
        this.suspended = suspended;
    }

    public void run() {
        if (callInterrupt) {
            WebSocketEngine.getEngine().interrupt0(this, true);
        } else {
            doTask();
        }

    }

    private void doTask() {
        // The WebSocketHandler has been resumed.
        if (!webSocketContext.isActive(webSocketHandler)) {
            return;
        }
        /**
         * The WebSocketHandler in that case is **always** invoked using this
         * thread so we can re-use the Thread attribute safely.
         */
        ByteBuffer byteBuffer = null;
        boolean connectionClosed = false;
        boolean clearBuffer = true;
        final SelectionKey key = getSelectionKey();
        try {
            byteBuffer = ((WorkerThread) Thread.currentThread()).getByteBuffer();
            if (byteBuffer == null) {
                byteBuffer = ByteBuffer.allocate(asyncProcessorTask.getSelectorThread().getBufferSize());
                ((WorkerThread) Thread.currentThread()).setByteBuffer(byteBuffer);
            } else {
                byteBuffer.clear();
            }

            SocketChannel socketChannel = (SocketChannel) key.channel();
            if (!suspended) {
                /*
                 * We must execute the first read to prevent client abort.
                 */
                int nRead = socketChannel.read(byteBuffer);
                if (nRead == -1) {
                    connectionClosed = true;
                } else {
                    /*
                     * This is an HTTP pipelined request. We need to resume
                     * the continuation and invoke the http parsing
                     * request code.
                     */
                    boolean webSocketHandlerIsAsyncRegistered = false;
                    if (!webSocketHandlerIsAsyncRegistered) {
                        /**
                         * Something went wrong, most probably the WebSocketHandler
                         * has been resumed or removed by the WebSocket implementation.
                         */
                        if (!webSocketContext.isActive(webSocketHandler)) {
                            return;
                        }

                        // Before executing, make sure the connection is still
                        // alive. This situation happens with SSL and there
                        // is not a cleaner way fo handling the browser closing
                        // the connection.
                        nRead = socketChannel.read(byteBuffer);
                        if (nRead == -1) {
                            connectionClosed = true;
                            return;
                        }
                        //resume without remove:
                        webSocketHandler.onInterrupt(webSocketContext.eventInterrupt);
                        WebSocketEngine.getEngine().flushPostExecute(this, false);

                        clearBuffer = false;

                        Controller controller = getSelectorThread().getController();
                        ProtocolChain protocolChain =
                                controller.getProtocolChainInstanceHandler().poll();
                        NIOContext ctx = (NIOContext) controller.pollContext();
                        ctx.setController(controller);
                        ctx.setSelectionKey(key);
                        ctx.setProtocolChain(protocolChain);
                        ctx.setProtocol(Controller.Protocol.TCP);
                        protocolChain.execute(ctx);
                    } else {
/*
                        byteBuffer.flip();
                        WebSocketReader reader = new WebSocketReader();
                        reader.setNRead(nRead);
                        reader.setByteBuffer(byteBuffer);
                        WebSocketEvent event = new WebSocketEvent(WebSocketEvent.Type.READ, webSocketContext);
                        event.attach(reader);
                        webSocketContext.invokeWebSocketHandler(event, webSocketHandler);
                        reader.setByteBuffer(null);

                        // This Reader is now invalid. Any attempt to use
                        // it will results in an IllegalStateException.
                        reader.setReady(false);
*/
                    }
                }
            } else {
/*
                WebSocketEvent event = new WebSocketEvent(WebSocketEvent.Type.WRITE, webSocketContext);
                WebSocketWriter writer = new WebSocketWriter();
                writer.setChannel(socketChannel);
                event.attach(writer);
                WebSocketContext.invokeWebSocketHandler(event, webSocketHandler);

                // This Writer is now invalid. Any attempt to use
                // it will results in an IllegalStateException.
                writer.setReady(false);
*/
            }
        } catch (IOException ex) {
            connectionClosed = true;
            // Bug 6403933 & GlassFish 2013
            if (SelectorThread.logger().isLoggable(Level.FINEST)) {
                SelectorThread.logger().log(Level.FINEST, "WebSocket exception", ex);
            }
        } catch (Throwable t) {
            connectionClosed = true;
            SelectorThread.logger().log(Level.SEVERE, "WebSocket exception", t);
        } finally {
//            webSocketHandlerIsAsyncRegistered = false;

            // Bug 6403933
            if (connectionClosed) {
                asyncProcessorTask.getSelectorThread().cancelKey(key);
            }

            if (clearBuffer && byteBuffer != null) {
                byteBuffer.clear();
            }
        }
    }

    private SelectorThread getSelectorThread() {
        return asyncProcessorTask.getSelectorThread();
    }

    public SelectionKey getSelectionKey() {
        return asyncProcessorTask.getAsyncExecutor().getProcessorTask().getSelectionKey();
    }

    @Override
    public void handleSelectedKey(SelectionKey selectionKey) {
    }
}
