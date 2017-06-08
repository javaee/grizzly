# Grizzly NIO

Writing scalable server applications in the Java™ programming language
has always been difficult. Before the advent of the Java New I/O API (NIO),
thread management issues made it impossible for a server to scale to
thousands of users. The Grizzly NIO framework has been designed to help
developers to take advantage of the Java™ NIO API. Grizzly’s goal is to
help developers to build scalable and robust servers using NIO as well
as offering extended framework components: Web Framework (HTTP/S),
WebSocket, Comet, and more!

## Getting Started

Grizzly currently has several lines of development in the following
branches:

- 2.3.x : This is our current release sustaining branch.
- 2.4.x : This is our upcoming release that includes HTTP/2 and support for Servlet 4.0 features.
- master : This is our upcoming 3.0 release.  Fair warning; it's not backwards compatibile with previous releases.

There are other branches for older releases of Grizzly that we don't
actively maintain at this time, but we keep them for the history.


### Prerequisites

We have different JDK requirements depending on the branch in use:

- Oracle JDK 1.8 for 2.4.x and master branches.
- Oracle JDK 1.7 for 2.3.x.

### Installing

See https://javaee.github.io/grizzly/dependencies.html for the maven
coordinates of the 2.3.x release artifacts.

If building in your local environment:

```
mvn clean install
```


## Running the tests

```
mvn clean install
```

## Contributing

Please read [CONTRIBUTING.md](https://github.com/javaee/grizzly/blob/master/CONTRIBUTING.md) for details on our code of conduct, and the process for submitting pull requests to us.

## License

This project is licensed under the CDDLw/CPE - see the [LICENSE.txt](https://github.com/javaee/grizzly/blob/master/LICENSE.txt) file for details.

