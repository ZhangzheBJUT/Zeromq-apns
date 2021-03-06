#!/bin/bash
PID_FILE=/var/run/zeromq-apns.pid

sudo kill -9 `ps aux | grep com.icestar.Server | grep -v grep | awk '{print $2}'`

mydir="`dirname $0`"
mylib="`dirname $mydir`"/lib
echo mydir=$mydir 
echo mylib=$mylib

libs=`echo "$mylib"/*.jar "$mydir"/conf | sed 's/ /:/g'`
echo libs=$libs

sudo daemon \
  -n zmq-apnserver \
  `java -server -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=1536m -Xmx1024M -Xss4M -classpath $libs com.icestar.Server "$@"` &
