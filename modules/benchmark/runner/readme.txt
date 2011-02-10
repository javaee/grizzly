
Purpose of these scripts
-------------------------------------------
These are intended to provide a way to run mulitple grizzly benchmarks
and gather statistics from the runs with no interaction from the developer.

Scripts
-------------------------------------------
 - fhb-runner 
   + This script runs benchmarks using Faban's fhb tool.  This script can 
     be run on a different machine from that of the benchmark script.  
 - benchmark
   + This script submits benchmark requests to the fhb-running and accumulates/logs
     the results of the benchmark.
     
Prerequisites
-------------------------------------------
 - JDK 1.6 or later must be present on the machines running these scripts.
 - wget must be present on the machine running fhb-runner.
 - subversion must be present on the machine running benchmark.
 - Maven 2 must be present on the machine running benchmark.
 
Running
-------------------------------------------
 - fhb-runner needs to be run first.  The script can be exeucted with no arguments,
   however, the network interface and port that this script will bind to can be
   custimized by passing in --port and/or --host arguments.  If these arguments
   aren't specified, the defaults of "0.0.0.0" (all interfaces) and 5000 will be assumed.
  
   If faban isn't available (as referenced by the environment variable
   FABAN_HOME), this script will attempt to download and extract faban using wget if 
   the faban directory isn't present relative the the script location at
   invocation time (this will likely be enhanced in the future).
   
   When:
      Faban (fhb) Benchmark Runner accepting connections [localhost:5000]
   
   is printed to the console, it is possible to beging submitting benchmark 
   requests.
   
 - Next, create a benchmark definition file for the benchmark script to work with.
   A benchmark is a single line comprised of three fields:
   
      + <benchmark server> <number of runs> <fhb parameters>
      
   An example would be:
   
      2dot0/http-echo|-server,-Xmx512m|-binary=true,-chunked=false 3 1|5|10|5|1|http://localhost:9011/echo


   The first field:
   
      2dot0/http-echo|-server,-Xmx512m|-binary=true,-chunked=false
      
   is comprised of three sub-fields separated by the pipe (|) character.
   The first sub-field is the benchmark to run relative to the grizzly 2.0
   modules/benchmarks/usecase directory.  
   
   The second sub-field represents the VM args to use when starting the 
   code to benchmark.
   
   The last sub-field represents arguments to the code being benchmarked.
   
   The second field:
   
       3
       
   represents how many times the particular faban benchmark (the third field 
   in the benchmark definition) will be run.
   
   The third field:
   
       1|5|10|5|1|http://localhost:9011/echo
       
   represents the fhb command parameters.  Again we see a pipe delimited series of values.
   
   first (1 in the example): represents the number of client threads
   second (5 in the exampe): represents the ramp up time
   third (10 in the example): represents the steady state time
   fourth (5 in the example): represents the ramp down time
   fifth (1 in the example): represents how many bytes to post with the request (may be 0
                             in which case an HTTP GET is performed)
   sixth (http:... in the example): represents the request URL
   
   It should be noted that the benchmark definition file can include multiple lines.
   Consider:
   
       2dot0/http-echo|-server,-Xmx512m|-binary=true,-chunked=false 3 1|5|10|5|1|http://localhost:9011/echo
       2dot0/http-echo|-server,-Xmx512m|-binary=true,-chunked=true 3 1|5|10|5|1|http://localhost:9011/echo
   
   in this case, we're running two distinct bencharks 3 times each.  The first use a fixed-length
   transfer encoding, while the second use chunked transfer encoding.  
   
 - With the benchmark definition file in place, invoke the benchmark script:
 
     ./benchmark --benchmark <bm definition file> --host <host of the fhb-runner> \
     --port <port the fhb-runner is listening on>
     
   When the script is invoked, the latest Grizzly 2.0 code will be checked out of svn
   and built.  Next, the bencmark will be built after which the server will be started.
   The fhb command parameters are then sent to the fhb-runner to invoke.
   
 - Once all benchmarks have been run, the accumulated results will have been logged
   to benchmark_results.txt.  The results will include the times for each run, and,
   if there was more than one run of a particular benchmark, the arithmetic mean,
   median and standard deviation of the runs.
  
