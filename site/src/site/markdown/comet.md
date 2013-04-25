Comet
=====

Introduction
============

From the wikipedia page:

Comet is an umbrella term used to describe a technique allowing web
browser to receive almost real time updates from the server. The two
most common approaches are long polling and streaming. Long polling
differs from streaming in that each update from the server ultimately
results in another follow up request from the server. With streaming,
there is one long lived request serving multiple updates. The following
sections will cover each option with samples of how to implement each
approach.

Long Polling
------------

With long polling an initial request is made to the server. This request
is "parked" waiting for an update. This sleeping request is then
awakened when an event is called on the CometHandler for the request.
CometHandler is an interface in the Grizzly framework which an
application developer implements to register a suspended request with
the comet system and manage event and lifecyle issues. CometHandler is
typically where your application logic for your comet-based applications
lives. The following example shows how to set up a long polling request
and notify it about events. This code is taken from the count-clicker
comet sample in the grizzly source repository
(http://java.net/projects/grizzly/sources/git/show/samples/comet/comet-counter).

```java
public class CounterHandler extends DefaultCometHandler<HttpServletResponse> {

    private HttpServletResponse httpResponse;
    private AtomicInteger counter;

    CounterHandler(HttpServletResponse httpResponse, final AtomicInteger counter) {
        this.httpResponse = httpResponse;
        this.counter = counter;
    }

    public void onEvent(CometEvent event) throws IOException {
        if (CometEvent.Type.NOTIFY == event.getType()) {
            httpResponse.addHeader("X-JSON", "{\"counter\":" + counter.get() + " }");

            PrintWriter writer = httpResponse.getWriter();
            writer.write("success");
            writer.flush();

            event.getCometContext().resumeCometHandler(this);
        }
    }

    public void onInterrupt(CometEvent event) throws IOException {
        httpResponse.addHeader("X-JSON", "{\"counter\":" + counter.get() + " }");

        PrintWriter writer = httpResponse.getWriter();
        writer.write("success");
        writer.flush();
    }
}
```

This is the CometHandler for our simple counter application. In this
simple case, it has an AtomicInteger for tracking count requests and
return the incremented value for each event. This handler is registered
in a servlet as shown below.

```java
public class LongPollingServlet extends HttpServlet {


    final AtomicInteger counter = new AtomicInteger();
    private static final long serialVersionUID = 1L;

    private String contextPath = null;

    @Override
    public void init(ServletConfig config) throws ServletException {
        super.init(config);

        ServletContext context = config.getServletContext();
        contextPath = context.getContextPath() + "/long_polling";

        CometEngine engine = CometEngine.getEngine();
        CometContext cometContext = engine.register(contextPath);
        cometContext.setExpirationDelay(5 * 30 * 1000);
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res)
    throws ServletException, IOException {
        CometEngine engine = CometEngine.getEngine();
        CometContext<HttpServletResponse> context = engine.getCometContext(contextPath);
        final int hash = context.addCometHandler(new CounterHandler(res, counter));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res)
    throws ServletException, IOException {
        counter.incrementAndGet();
        CometContext<HttpServletResponse> context = CometEngine.getEngine().getCometContext(contextPath);
        context.notify(null);

        PrintWriter writer = res.getWriter();
        writer.write("success");
        writer.flush();
    }
}
```

The first request comes into doGet() which will create the CometHandler
and add it to the CometContext. CometContext.addHandler() will suspend
this request. In the html for the application, there's a link which
executes a POST against the server which calls doPost(). Here, what
we're doing is notifying the entire context of an event. This will cause
onEvent() to be called for each registered CometHandler. That, as we've
seen, will return the counter value to the browser. After this event is
processed, the suspended requests will be resumed and eventually
terminated normally. On the client side, once that GET request is
terminated, the javascript in the page will submit yet another GET
request. This request is suspended again as describe above and the
process can repeat indefinitely. The javascript needed for this is shown
below:

```java
var counter = {
      'poll' : function() {
         new Ajax.Request('long_polling', {
            method : 'GET',
            onSuccess : counter.update
         });
      },
      'increment' : function() {
         new Ajax.Request('long_polling', {
            method : 'POST'
         });
      },
      'update' : function(req, json) {
         $('count').innerHTML = json.counter;
         counter.poll();
      }
}
```

There are three basic functions involved in this application: poll,
increment, and update.

1.  poll: This function makes the GET requests to the server which are
    ultimately suspended. On a successful return from that call,
    update() is called...

2.  update: This where the page is updated with the results from the
    server. This function takes the json object returned from the
    servlet and updates the display accordingly. The last thing it does
    is call back to poll() which initiates another suspended request.

3.  increment: This function is called whenever you click the link in
    the application. It initiates the POST request that causes the
    server state to change and results in the client side updates. There
    is no handling of the response from this request.

That's all it takes for a simple long polling based application. Long
polling applications are well suited for handling low frequency events
from the server such as updates to pages in response to user actions.
Because of the need for renegotiation of subsequent requests after each
event, applications with high event frequency are best served by
streaming applications as we'll see below. Long polling, however, is
more proxy friendly in many cases. Many proxies balk at seeing
long-lived connections and might close them according to various
security or connection timeout policies.

Streaming
---------

TBD
