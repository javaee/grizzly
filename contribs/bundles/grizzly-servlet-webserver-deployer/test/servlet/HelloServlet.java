package servlet;

import java.io.*;

import javax.servlet.http.*;
import javax.servlet.*;

/**
 * Hello world servlet.  Most servlets will extend
 * javax.servlet.http.HttpServlet as this one does.
 */
public class HelloServlet extends HttpServlet {
  /**
   * Implements the HTTP GET method.  The GET method is the standard
   * browser method.
   *
   * @param request the request object, containing data from the browser
   * @param repsonse the response object to send data to the browser
   */
  public void doGet (HttpServletRequest request,
                     HttpServletResponse response)
    throws ServletException, IOException
  {
  	
    // Returns a writer to write to the browser
    PrintWriter out = response.getWriter();

    // Writes the string to the browser.
    out.println("Hello, world!");
    out.close();
  }
}
