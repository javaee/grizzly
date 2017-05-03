## Cookbook

Sample recipes on how to solve different use cases.

### Multi-threaded client

This recipe presents how to write a multi-threaded client. That is, a client that have many threads issuing requests to a server at the same time. Each thread will register a listener for a server response and will receive a call back when the server responds to that request.
The challenge for this recipe is that the filters in the filter chain should be stateless but still find the correct listener to invoke when a response comes from the server.

This sample assumes a simple Echo server is running, you can use a server implementation similar to the [Quick start](quickstart.html) to try out the client.

The client sample code will start 10 threads, each thread will send a request to the server running on localhost port 7777. It will then wait for all listener callbacks and exit. The code is written using Java8 lambda expressions but you can use anonymous inner classes or whatever style you like to register a listener.

```java
public class CallbackClient {

	private final static int NUMBER_OF_CLIENT_THREADS = 10;
	private final CountDownLatch finishLatch = new CountDownLatch(NUMBER_OF_CLIENT_THREADS);
	private TCPNIOTransport transport;
	private final Attribute<CallbackListener> listenerAttribute
			= Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("listenerAttribute");

	public static void main(String... args) throws InterruptedException, IOException {
		CallbackClient app = new CallbackClient();
		app.startup();
		app.run();
	}

	private void startup() throws IOException {

		FilterChainBuilder clientFilterChainBuilder = FilterChainBuilder.stateless();
		// These are standard grizzly filters
		clientFilterChainBuilder.add(new TransportFilter());
		clientFilterChainBuilder.add(new StringFilter(Charset.defaultCharset(), "\n"));
		// This filter is defined below
		clientFilterChainBuilder.add(new CallbackFilter(listenerAttribute));
		transport = TCPNIOTransportBuilder.newInstance().build();
		transport.setProcessor(clientFilterChainBuilder.build());
		transport.start();
	}

	private void run() throws InterruptedException {
		for (int n = 0; n < NUMBER_OF_CLIENT_THREADS; n++) {
			final String listenerID = "Listener: " + n;
			final String message = "Hello from client: " + n + "\n";
			Thread clientThread = new Thread(() -> {
				final GrizzlyFuture<Connection> connectionGrizzlyFuture = transport.connect("127.0.0.1", 7777);
				try {
					final Connection connection = connectionGrizzlyFuture.get();
					listenerAttribute.set(connection, (String serverMessage) -> {
						System.out.println(listenerID + " callback result: " + serverMessage);
						finishLatch.countDown();
					});
					connection.write(message);
				} catch (InterruptedException | ExecutionException e) {
					e.printStackTrace();
				}
			}
			);
			clientThread.start();
		}

		finishLatch.await(5, TimeUnit.SECONDS);
		System.out.println("Exiting client");
	}

	public static class CallbackFilter extends BaseFilter {

		private final Attribute<CallbackListener> listenerAttribute;

		public CallbackFilter(Attribute<CallbackListener> listenerAttribute) {
			this.listenerAttribute = listenerAttribute;
		}

		@Override
		public NextAction handleRead(FilterChainContext ctx) throws IOException {
			final String message = ctx.getMessage();
			final CallbackListener callbackListener = listenerAttribute.get(ctx.getConnection());
			callbackListener.callback(message);
			return ctx.getStopAction();
		}
	}

	public interface CallbackListener {

		void callback(String message);
	}
}
```

As stated, the challenge in this recipe is to write stateless filters but still have the correct listener invoked when a response comes back from the server. To do that the client must find some unique instance of a class that the filter can pick up. Luckily the connection will be unique for each thread that sends a request to the server. So we will use the connection as a bucket for a data holder that the client thread will use to pass on a callback listener to the filter. The data holders are called 'attributes'. An attribute is defined like this:

```java
private final Attribute<CallbackListener> listenerAttribute = Grizzly.DEFAULT_ATTRIBUTE_BUILDER.createAttribute("listenerAttribute");
```

The client then uses the transport it has set up to create a new connection to the server for each thread. When the connection has been created the client stores data on the connection using the attribute like this:

```java
final GrizzlyFuture<Connection> connectionGrizzlyFuture = transport.connect("127.0.0.1", 7777);
try {
	final Connection connection = connectionGrizzlyFuture.get();
	listenerAttribute.set(connection, (String message) -> {
		System.out.println(listenerID + " callback result: " + message);
		finishLatch.countDown();
	});
```

In this sample it stores a reference to the callback listener that the filter will use when the server responds.

The callback filter used in the filter chain will extract the response from the server, retreive the listener stored on the connection, and invoke the listener passing on the data.

```java
public NextAction handleRead(FilterChainContext ctx) throws IOException {
	final String message = ctx.getMessage();
	final CallbackListener callbackListener = listenerAttribute.get(ctx.getConnection());
	callbackListener.callback(message);
```

Things to consider:
- You might want to take care to issue the callback on another thread than the one the filter uses since that will hold up grizzly processing while the listener performs work. An aternative can be to use pass instances of 'futures' instead of listener callbacks and let the client thread wait for the future to complete.
