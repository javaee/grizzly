/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package org.glassfish.grizzly.http.server;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.glassfish.grizzly.Grizzly;
import org.glassfish.grizzly.filterchain.Filter;
import org.glassfish.grizzly.filterchain.FilterChain;
import org.glassfish.grizzly.filterchain.FilterChainContext;
import org.glassfish.grizzly.http.server.filecache.FileCache;
import org.glassfish.grizzly.http.server.io.OutputBuffer;
import org.glassfish.grizzly.http.server.util.MimeType;
import org.glassfish.grizzly.http.util.HttpStatus;
import org.glassfish.grizzly.utils.ArraySet;

/**
 * {@link HttpRequestProcessor}, which processes requests to a static resources.
 *
 * @author Jeanfrancois Arcand
 * @author Alexey Stashok
 */
public class StaticResourcesService extends HttpRequestProcessor {
    private static final Logger LOGGER = Grizzly.logger(StaticResourcesService.class);

    protected final ArraySet<File> docRoots = new ArraySet<File>(File.class);

    private volatile int fileCacheFilterIdx = -1;


   /**
     * Create <tt>HttpService</tt>, which, by default, will handle requests
     * to the static resources located in the current directory.
     */
    public StaticResourcesService() {
        addDocRoot(".");
    }


    /**
     * Create a new instance which will look for static pages located
     * under the <tt>docRoot</tt>. If the <tt>docRoot</tt> is <tt>null</tt> -
     * static pages won't be served by this <tt>HttpService</tt>
     *
     * @param docRoot the folder where the static resource are located.
     * If the <tt>docRoot</tt> is <tt>null</tt> - static pages won't be served
     * by this <tt>HttpService</tt>
     */
    public StaticResourcesService(String... docRoots) {
        if (docRoots != null) {
            for (String docRoot : docRoots) {
                addDocRoot(docRoot);
            }
        }
    }

    /**
     * Create a new instance which will look for static pages located
     * under the <tt>docRoot</tt>. If the <tt>docRoot</tt> is <tt>null</tt> -
     * static pages won't be served by this <tt>HttpService</tt>
     *
     * @param docRoots the folders where the static resource are located.
     * If the <tt>docRoot</tt> is empty - static pages won't be served
     * by this <tt>HttpService</tt>
     */
    public StaticResourcesService(Set<String> docRoots) {
        if (docRoots != null) {
            for (String docRoot : docRoots) {
                addDocRoot(docRoot);
            }
        }
    }

    /**
     * Return the default directory from where files will be serviced.
     * @return the default directory from where file will be serviced.
     */
    public File getDefaultDocRoot() {
        final File[] array = docRoots.getArray();
        return (array != null && array.length > 0) ? array[0] : null;
    }

    /**
     * Return the list of directories where files will be serviced from.
     *
     * @return the list of directories where files will be serviced from.
     */
    public ArraySet<File> getDocRoots() {
        return docRoots;
    }

    /**
     * Add the directory to the list of directories where files will be serviced from.
     *
     * @param docRoot the directory to be added to the list of directories
     *                where files will be serviced from.
     *
     * @return return the {@link File} representation of the passed <code>docRoot</code>.
     */
    public final File addDocRoot(String docRoot) {
        if (docRoot == null) {
            throw new NullPointerException("docRoot can't be null");
        }

        final File file = new File(docRoot);
        addDocRoot(file);

        return file;
    }

    /**
     * Add the directory to the list of directories where files will be serviced from.
     *
     * @param docRoot the directory to be added to the list of directories
     *                where files will be serviced from.
     */
    public final void addDocRoot(File docRoot) {
        docRoots.add(docRoot);
    }

    /**
     * Removes the directory from the list of directories where static files will be serviced from.
     *
     * @param docRoot the directory to remove.
     */
    public void removeDocRoot(File docRoot) {
        docRoots.remove(docRoot);
    }
    
    /**
     * Based on the {@link Request} URI, try to map the file from the
     * {@link #getDocRoots()}, and send it back to a client.
     * @param req the {@link Request}
     * @param res the {@link Response}
     * @throws Exception
     */
    @Override
    public void service(final Request request, final Response response) throws Exception {
        final String uri = getRelativeURI(request);

        if (uri == null || !handle(uri, request, response)) {
            onMissingResource(request, response);
        }
    }

    protected String getRelativeURI(final Request request) {
        String uri = request.getRequestURI();
        if (uri.indexOf("..") >= 0) {
            return null;
        }

        final String resourcesContextPath = request.getContextPath();
        if (!"".equals(resourcesContextPath)) {
            if (!uri.startsWith(resourcesContextPath)) {
                return null;
            }

            uri = uri.substring(resourcesContextPath.length());
        }

        return uri;
    }

    /**
     * The method will be called, if the static resource requested by the {@link Request}
     * wasn't found, so {@link StaticResourcesService} implementation may try to
     * workaround this situation.
     * The default implementation - sends a 404 response page by calling {@link #customizedErrorPage(org.glassfish.grizzly.http.server.HttpServer, org.glassfish.grizzly.http.server.Request, org.glassfish.grizzly.http.server.Response)}.
     *
     * @param request the {@link Request}
     * @param response the {@link Response}
     * @throws Exception
     */
    protected void onMissingResource(final Request request, final Response response)
            throws Exception {
        customizedErrorPage(request, response);
    }

    /**
     * Lookup a resource based on the request URI, and send it using send file.
     *
     * @param uri The request URI
     * @param req the {@link Request}
     * @param res the {@link Response}
     * @throws Exception
     */
    protected boolean handle(final String uri,
            final Request req,
            final Response res) throws Exception {

        FileInputStream fis = null;
        try {

            boolean found = false;

            final File[] fileFolders = docRoots.getArray();
            if (fileFolders == null) return false;

            File resource = null;

            for (int i = 0; i < fileFolders.length; i++) {
                final File webDir = fileFolders[i];
                // local file
                resource = new File(webDir, uri);
                final boolean exists = resource.exists();
                final boolean isDirectory = resource.isDirectory();

                if (exists && isDirectory) {
                    final File f = new File(resource, "/index.html");
                    if (f.exists()) {
                        resource = f;
                        found = true;
                        break;
                    }
                }

                if (isDirectory || !exists) {
                    found = false;
                } else {
                    found = true;
                    break;
                }
            }

            if (!found) {
                if (LOGGER.isLoggable(Level.FINE)) {
                    LOGGER.log(Level.FINE, "File not found  {0}", resource);
                }
                return false;
            }

            res.setStatus(HttpStatus.OK_200);
            String substr;
            int dot = uri.lastIndexOf(".");
            if (dot < 0) {
                substr = resource.toString();
                dot = substr.lastIndexOf(".");
            } else {
                substr = uri;
            }
            if (dot > 0) {
                String ext = substr.substring(dot + 1);
                String ct = MimeType.get(ext);
                if (ct != null) {
                    res.setContentType(ct);
                }
            } else {
                res.setContentType(MimeType.get("html"));
            }

            final long length = resource.length();
            res.setContentLengthLong(length);

            addToFileCache(req, resource);
            // Send the header, and flush the bytes as we will now move to use
            // send file.
            res.flush();
            //res.sendHeaders();

            fis = new FileInputStream(resource);
            OutputBuffer outputBuffer = res.getOutputBuffer();

            byte b[] = new byte[8192];
            int rd;
            while ((rd = fis.read(b)) > 0) {
                //chunk.setBytes(b, 0, rd);
                outputBuffer.write(b, 0, rd);
            }

            return true;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException ignore) {
                }
            }
        }
    }

    public final boolean addToFileCache(Request req, File resource) {
        final FilterChainContext fcContext = req.getContext();
        final FileCacheFilter fileCacheFilter = lookupFileCache(fcContext);
        if (fileCacheFilter != null) {
            final FileCache fileCache = fileCacheFilter.getFileCache();
            fileCache.add(req.getRequest(), resource);
            return true;
        }

        return false;
    }

    private FileCacheFilter lookupFileCache(final FilterChainContext fcContext) {
        final FilterChain fc = fcContext.getFilterChain();
        final int lastFileCacheIdx = fileCacheFilterIdx;

        if (lastFileCacheIdx != -1) {
            final Filter filter = fc.get(lastFileCacheIdx);
            if (filter instanceof FileCacheFilter) {
                return (FileCacheFilter) filter;
            }
        }

        final int size = fc.size();
        for (int i = 0; i < size; i++) {
            final Filter filter = fc.get(i);

            if (filter instanceof FileCacheFilter) {
                fileCacheFilterIdx = i;
                return (FileCacheFilter) filter;
            }
        }

        fileCacheFilterIdx = -1;
        return null;
    }
    
}
