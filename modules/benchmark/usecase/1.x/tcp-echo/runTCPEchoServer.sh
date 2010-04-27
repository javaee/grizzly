#!/bin/bash
java -cp ./lib/framework.jar:./lib/utils.jar:./build/classes com.sun.grizzly.benchmark.TCPEchoServer $@

#java -cp ./lib/framework.jar:./lib/utils.jar:./build/classes -Djava.util.logging.config.file=./logging.properties com.sun.grizzly.benchmark.TCPEchoServer $@

