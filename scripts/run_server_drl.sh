#!/bin/bash

# script for running the STAC server

if [ $# -ne 3 ]
then
    echo "Usage ./run_server_drl.sh <config-file> <db-user> <db-password> "
    exit
else
    wdir=${0%/*}/..
    config=$1
    sup_lib=${wdir}/lib/JavaBayes.jar:${wdir}/lib/SimpleSimulator2.jar:${wdir}/lib/weka.jar
    ws_libs=${wdir}/lib/websocket/websocket-api-9.0.5.v20130815.jar:${wdir}/lib/websocket/websocket-client-9.0.5.v20130815.jar:${wdir}/lib/websocket/websocket-common-9.0.5.v20130815.jar:${wdir}/lib/websocket/websocket-server-9.0.5.v20130815.jar:${wdir}/lib/websocket/websocket-servlet-9.0.5.v20130815.jar
    jetty=${wdir}/lib/jetty/jetty-client-9.0.5.v20130815.jar:${wdir}/lib/jetty/jetty-http-9.0.5.v20130815.jar:${wdir}/lib/jetty/jetty-io-9.0.5.v20130815.jar:${wdir}/lib/jetty/jetty-security-9.0.5.v20130815.jar:${wdir}/lib/jetty/jetty-server-9.0.5.v20130815.jar:${wdir}/lib/jetty/jetty-servlet-9.0.5.v20130815.jar:${wdir}/lib/jetty/jetty-servlets-9.0.5.v20130815.jar:${wdir}/lib/jetty/jetty-util-9.0.5.v20130815.jar:${wdir}/lib/jetty/jetty-webapp-9.0.5.v20130815.jar:${wdir}/lib/jetty/servlet-api-3.0.jar
    dbUser=$2
    dbPass=$3
#    java -cp ${wdir}/target/STACSettlers.jar:${wdir}/lib/mdp-library.jar:$sup_lib:$ws_libs:$jetty soc.client.SOCPlayerClient &


today=`date '+%Y-%m-%d-%H-%M-%S'`;
logname="console_logs/server_debug-$today.log"
mkdir -p console_logs

#(ls -l 2>&1) | tee file.txt
(java -cp ${wdir}/STACSettlers.jar:${wdir}/lib/mdp-library.jar:$sup_lib:$ws_libs:$jetty soc.server.SOCServer -Dstac.robots=8 8880 100 ${dbUser} ${dbPass} 2>&1) | tee $logname


    sleep 3
    cd ${wdir}/web/main
    pwd
    node runclient.js test
fi

