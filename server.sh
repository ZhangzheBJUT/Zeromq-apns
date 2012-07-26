#!/bin/bash

PID_FILE=/var/run/iphonenotifier.pid

mydir="`dirname $0`"
mylib="`dirname $mydir`"/lib

libs=`echo "$mylib"/*.jar "$mydir"/conf | sed 's/ /:/g'`

daemon \
  -n iphonenotifier \
  java -classpath $libs com.notnoop.notifier.Boot "$@"
