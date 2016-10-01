# Jibe

[![License](http://img.shields.io/:license-apache-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0.html)

## Getting Started

Ensure that you have recent versions of [sbt](http://www.scala-sbt.org/) and [Vagrant](https://www.vagrantup.com/) 
installed.

Currently, we have as a demo a few commands hard-coded into Main, including some that are designed to fail to 
demonstrate what that looks like in the "results" output.

### To prepare your system

```
git clone git@github.com:scalawag/jibe.git
cd jibe
vagrant up
```

### To run the mandates in Main

```
sbt "core/run-main org.scalawag.jibe.Main"
```

### To view the results of the run

```
sbt "core/run-main org.scalawag.jibe.report.ReportServer"
open http://localhost:8080/results/latest/index.html
```

The first command is a long-running process, so you may need to use
another shell window (or your browser) to open the results URL. 

You can leave this report server running while you run the mandates
again (see above) and just refresh the browser each time to see the
results of the latest run.

### To run the unit tests

```
sbt test
```

### To run the integration tests

Your vagrant machines must be running for the integration tests to work.

```
sbt it:test
```

## TODOs

### Things to prove/demonstrate:
 - command timeouts
 - environment where end-users can add:
   - custom Commands (abstract system-apathetic operations)
   - custom Mandates (higher-level system-apathetic operations)
   - custom (system-specific) implementations of abstract commands
   - custom data types to use as arguments to custom Commands/Mandates
   - custom arbitrary code (e.g., a function that generates a composite mandate)
   - custom Resources that were not foreseen in the jibe core

### Things to decide on:
 - Should the scala code be able to get data from the stdout of commands or should all decisions be made inside the mandates?
 - What else should be resources?  Is the weak-matching-by-name-only thing I've done so far sufficient?
   - How can you handle things like one mandate producing a directory of files while another uses only one file in that directory?
   - How do you handle negative resources?  A "userdel" command's consequence is the removal of a resource.  Can a mandate depend on that?
