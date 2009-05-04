package com.sun.grizzly.samples.http.embed;

import com.sun.grizzly.http.embed.GrizzlyWebServer;
import com.sun.grizzly.tcp.CompletionHandler;
import com.sun.grizzly.tcp.http11.GrizzlyAdapter;
import com.sun.grizzly.tcp.http11.GrizzlyRequest;
import com.sun.grizzly.tcp.http11.GrizzlyResponse;

import java.util.concurrent.LinkedBlockingQueue;

import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

public class GrizzlyEmbedWebServer {

    public static void main(String arg[]) throws Exception {
        GrizzlyWebServer ws = new GrizzlyWebServer();
        ws.getSelectorThread().setDisplayConfiguration(true);
        ws.addGrizzlyAdapter(new testGW());
        ws.start();
    }
}

class testGW extends GrizzlyAdapter {

    SP t = new SP();

    public void service(final GrizzlyRequest grizzlyRequest, final GrizzlyResponse httpResp) {
        System.out.println("in");
        if (!httpResp.isSuspended()) {

            httpResp.suspend(Integer.MAX_VALUE, this, new CompletionHandler<testGW>() {

                public void resumed(testGW attachment) {
                    try {
                        System.out.println("!!!!");
                        attachment.service(grizzlyRequest,httpResp);
                    } catch (Exception e) {

                    }
                }

                public void cancelled(testGW attachment) {
                    throw new Error("Request cancelled?!?");
                }
            });
            t.add(httpResp);
            return;
        }
        
        System.out.println("==========> ");
        
        try {
            String error = "bau";
            httpResp.setContentLength(error.length());
            httpResp.setContentType("text/html");
            httpResp.setStatus(200);

            httpResp.getWriter().print(error);
        } catch (Exception e) {

        }
    }
}

class SP extends Thread {

    LinkedBlockingQueue<GrizzlyResponse> v = new LinkedBlockingQueue<GrizzlyResponse>();

    public void add(GrizzlyResponse httpResp) {
        v.add(httpResp);
    }

    public SP() {
        this.start();
    }

    public void run() {

        while (true) {
            try {
                GrizzlyResponse e = v.poll(5, TimeUnit.SECONDS);
                if (e == null) {
                    continue;
                }
                Thread.sleep(10);
                e.resume();
            } catch (InterruptedException ex) {
                Logger.getLogger(SP.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
    }
} 
