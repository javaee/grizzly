#!/bin/bash
java -cp ../lib/http.jar:../lib/framework.jar:./build/classes com.sun.grizzly.benchmark.HttpGetServer $@
