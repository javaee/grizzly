
package com.sun.grizzly.samples.http.embed;

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.tcp.CompletionHandler;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;

import com.sun.grizzly.tcp.http11.GrizzlyResponse;
import com.sun.grizzly.util.http.Cookie;

import java.io.IOException;
import java.util.concurrent.LinkedBlockingQueue;

import java.util.concurrent.TimeUnit;

/**
 * Created by IntelliJ IDEA.
 * User: rama
 * Date: 11-mag-2009
 * Time: 13.33.19
 * To change this 
template use File | Settings | File Templates.
 */
public class GrizzlyEmbedWebServer {

    public GrizzlyEmbedWebServer() {
        GrizzlyWebServer gws = new GrizzlyWebServer();
        gws.addGrizzlyAdapter(new SuspendTest(), new String[]{"/"});
        gws.getSelectorThread().setDisplayConfiguration(true);
        try {
            gws.start();
        } catch (IOException e) {
            e.printStackTrace();  //To change body of 
        }
    }
    static LinkedBlockingQueue<GrizzlyResponse> queue = new LinkedBlockingQueue<GrizzlyResponse>();

    static {

        new Thread() {

            public void run() {
                while (true) {
                    try {
                        GrizzlyResponse s =
                                queue.poll(1, TimeUnit.HOURS);
                        Thread.sleep(1000);
                        s.resume();
                    } catch (Exception e) {

                    }
                }
            }
        }.start();
    }
    boolean USERESUME = true;

    public static void main(String args[]) {
        new GrizzlyEmbedWebServer();
    }

    class SuspendTest extends GrizzlyAdapter {

        public void service(final GrizzlyRequest grizzlyRequest, final GrizzlyResponse grizzlyResponse) {
            //read the cookie!
            Cookie[] s = grizzlyRequest.getCookies();

            if (s != null) {

                for (Cookie value : s) {
                    System.out.println("COOKIE " + value.getName() + " -- " + value.getValue());
                    if (!value.getValue().equals("123")){
                        System.exit(0);
                    }

                }
            }
            if (!USERESUME) {

                resume(grizzlyResponse, "123");
            } else {

                if (!grizzlyResponse.isSuspended()) {

                    grizzlyResponse.suspend(100000, this, new CompletionHandler<SuspendTest>() {

                        public void resumed(SuspendTest attachment) {
                            try {

                                attachment.resume(grizzlyResponse, "123");
                                attachment.afterService(grizzlyRequest, grizzlyResponse);
                            } catch (Exception e) {

                            }

                        }

                        public void cancelled(SuspendTest attachment) {
                            throw new Error("Request cancelled?!?");
                        }
                    });
                    queue.add(grizzlyResponse);

                    return;
                }
            }


        }

        private void resume(GrizzlyResponse grizzlyResponse, String SID) {
            System.out.println("RESUMING -->" + SID);


            grizzlyResponse.setHeader("Set-Cookie", "SID=" + SID);

            grizzlyResponse.setCharacterEncoding("UTF-8");

            grizzlyResponse.setStatus(200);
            grizzlyResponse.setContentType("text/xml");
            try {

                grizzlyResponse.getWriter().print("<bla>hi</bla>");
            } catch (IOException e) {
                e.printStackTrace();  //To change 

            }
        }
    }
}

