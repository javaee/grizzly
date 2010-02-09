/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package com.sun.grizzly.http;

/**
 *
 * @author oleksiys
 */
public interface HttpRequest extends HttpPacket {
    // -------------------- Request data --------------------

    public String getScheme();

    public String getMethod();

    public String getRequestURI();

    public String getQueryString();

    public String getProtocol();

    /**
     * Return the buffer holding the server name, if
     * any. Use isNull() to check if there is no value
     * set.
     * This is the "virtual host", derived from the
     * Host: header.
     */
    public String getServerName();

    public int getServerPort();

    public void setServerPort(int serverPort );

    public String getRemoteAddr();

    public String getRemoteHost();

    public String getLocalName();

    public String getLocalAddr();

    public String getLocalHost();

    public void setLocalHost(String host);

    public int getRemotePort();

    public void setRemotePort(int port);

    public int getLocalPort();

    public void setLocalPort(int port);


    // -------------------- Associated response --------------------

    public HttpResponse getResponse();

    public void setResponse(HttpResponse response );
}
