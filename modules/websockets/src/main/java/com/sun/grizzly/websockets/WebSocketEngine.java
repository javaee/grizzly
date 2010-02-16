package com.sun.grizzly.websockets;

import com.sun.grizzly.arp.AsyncProcessorTask;
import com.sun.grizzly.arp.AsyncTask;
import com.sun.grizzly.http.ProcessorTask;
import com.sun.grizzly.tcp.Request;
import com.sun.grizzly.util.ExtendedThreadPool;
import com.sun.grizzly.util.FixedThreadPool;
import com.sun.grizzly.util.SelectorFactory;
import com.sun.grizzly.util.http.MimeHeaders;

import java.io.IOException;
import java.nio.channels.SelectionKey;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Level;
import java.util.logging.Logger;

public class WebSocketEngine {
    static final Logger logger = Logger.getLogger("websockets");
    private static final int DISABLE_CLIENT_DISCONNECTION_DETECTION = 0;

    private final static String INVALID_WEBSOCKET = "WebSocket cannot be null.";

    private static volatile boolean isWebSocketEnabled;

    private static final IllegalStateException websocketNotEnabled =
            new IllegalStateException("Make sure you have enabled WebSocket " +
                    "or make sure the Thread invoking that method is the same " +
                    "as the Servlet.service() Thread.");

    private static final WebSocketEngine engine = new WebSocketEngine();
    private final ConcurrentMap<String, WebSocketContext> activeContexts =
            new ConcurrentHashMap<String, WebSocketContext>(16, 0.75f, 64);

    private final static ThreadLocal<WebSocketTask> updatedContexts = new ThreadLocal<WebSocketTask>();

    private ExtendedThreadPool threadPool;


    public WebSocketEngine() {
        setThreadPool(new FixedThreadPool(Runtime.getRuntime().availableProcessors(), "WebSocketWorker"));
    }

    public final void setThreadPool(ExtendedThreadPool pool) {
        if (pool != null) {
            int oldSize = 0;
            if (threadPool != null) {
                oldSize = threadPool.getMaximumPoolSize();
                threadPool.shutdownNow();
            }
            threadPool = pool;
            try {
                SelectorFactory.changeSelectorsBy(pool.getMaximumPoolSize() - oldSize);
            } catch (IOException ex) {
                logger.log(Level.WARNING, "WebSockets failed to resize Selector cache", ex);
            }
        }
    }

    public static boolean isWebSocketEnabled() {
        return isWebSocketEnabled;
    }

    public static WebSocketEngine getEngine() {
        return engine;
    }

    public boolean handle(AsyncProcessorTask apt) {
        if (!isWebSocketEnabled) {
            //volatile read is in general cheaper then a write.
            isWebSocketEnabled = true;
        }

        final Request request = apt.getAsyncExecutor().getProcessorTask().getRequest();
        final MimeHeaders headers = request.getMimeHeaders();
        try {
            ServerHandShake shake = new ServerHandShake(headers, new ClientHandShake(headers, false,
                    request.decodedURI().toString()));
            String topic = apt.getAsyncExecutor().getProcessorTask().getRequestURI();
            WebSocketContext context = topic == null ? null : activeContexts.get(topic);

            /* If the context is null, it means the context has never
             * been registered. The registration might happens during the2
             * Servlet.service() execution so we need to keep a reference
             * to the current thread so we can later retrieve the associated
             * SelectionKey. The SelectionKey is required in order to park the request.
             */
            ContinuationType continuationType =
                    context == null ? ContinuationType.AFTER_SERVLET_PROCESSING : context.continuationType;

            continuationType.executeServlet(apt);
            /* Will return a WebSocketTask instance if and only if the
            * Servlet.service() have invoked WebSocketContext.addWebSocketHandler().
            * If the returned WebSocketContext is null, it means we need to
            * execute a synchronous request.
            */
            WebSocketTask task = updatedContexts.get();
            if (task != null) {
                context = task.getWebSocketContext();
                if (task.isSuspended()) {  //alreadySuspended
                    task.setSuspended(true);
                    context.addActiveHandler(task);
                    return false;
                }
                task.setAsyncProcessorTask(apt);
                if (context.getExpirationDelay() != -1) {
                    task.setTimeout(System.currentTimeMillis());
                }
                SelectionKey mainKey = apt.getAsyncExecutor().getProcessorTask().getSelectionKey();
                if (mainKey.isValid() && context.getExpirationDelay() != DISABLE_CLIENT_DISCONNECTION_DETECTION) {
                    try {
                        mainKey.interestOps(SelectionKey.OP_READ);
                        mainKey.attach(task);
                        context.initialize(task.getWebSocketHandler());
                    } catch (Exception e) {
                        mainKey.attach(Long.MIN_VALUE);
                        return false;
                    }
                    context.addActiveHandler(task);
                    return true;
                }
            }
            return false;
        } catch (IOException e) {
            return false;
        } finally {
            updatedContexts.set(null);
        }
    }

    public WebSocketContext register(String contextPath) {
        return register(contextPath, ContinuationType.AFTER_SERVLET_PROCESSING);
    }

    private WebSocketContext register(String contextPath, ContinuationType type) {
        return register(contextPath, ContinuationType.AFTER_SERVLET_PROCESSING, DefaultNotificationHandler.class);
    }

    private WebSocketContext register(String contextPath, ContinuationType type,
            Class<DefaultNotificationHandler> handlerClass) {
        // Double checked locking used used to prevent the otherwise static/global 
        // locking, cause example code does heavy usage of register calls
        // for existing topics from http get calls etc.
        WebSocketContext context = activeContexts.get(contextPath);
        if (context == null) {
            synchronized (activeContexts) {
                context = activeContexts.get(contextPath);
                if (context == null) {
                    context = new WebSocketContext(contextPath, type);
                    NotificationHandler notificationHandler;
                    try {
                        notificationHandler = handlerClass.newInstance();
                    } catch (Throwable t) {
                        logger.log(Level.SEVERE, "Invalid NotificationHandler class : "
                                + handlerClass.getName() + " Using default.", t);
                        notificationHandler = new DefaultNotificationHandler();
                    }
                    context.setNotificationHandler(notificationHandler);
                    if (notificationHandler != null && notificationHandler instanceof DefaultNotificationHandler) {
                        ((DefaultNotificationHandler) notificationHandler)
                                .setThreadPool(threadPool);
                    }
                    activeContexts.put(contextPath, context);
                }

            }
        }
        context.continuationType = type;
        return context;

    }

    public void interrupt0(WebSocketTask task, boolean finishExecution) {
        if (finishExecution) {
            task.getWebSocketHandler().onInterrupt(task.getWebSocketContext().eventInterrupt);
        }
        flushPostExecute(task, finishExecution);
    }

    public void flushPostExecute(WebSocketTask task, boolean cancelKey) {
        AsyncProcessorTask apt = task.getAsyncProcessorTask();
        ProcessorTask p = task.getAsyncProcessorTask().getAsyncExecutor().getProcessorTask();
        p.setReRegisterSelectionKey(false);
        p.setAptCancelKey(cancelKey);
        if (apt.getStage() == AsyncTask.POST_EXECUTE) {
            try {
                synchronized (task.getWebSocketHandler()) {
                    apt.doTask();
                }
            } catch (IllegalStateException ex) {
                if (logger.isLoggable(Level.FINEST)) {
                    logger.log(Level.FINEST, "Resuming Response failed at apt flush", ex);
                }
            } catch (Throwable ex) {
                logger.log(Level.SEVERE, "Resuming failed at apt flush", ex);
            }
        } else {
            logger.warning("APT flush called at wrong stage");
        }
    }
}