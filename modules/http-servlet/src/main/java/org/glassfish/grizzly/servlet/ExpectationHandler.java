/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package org.glassfish.grizzly.servlet;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 *
 * @author oleksiys
 */
public interface ExpectationHandler {
    public void onExpectAcknowledgement(final HttpServletRequest request,
            final HttpServletResponse response) throws Exception;
}
