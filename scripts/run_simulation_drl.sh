#!/bin/bash

# script for running STAC simulation

if [ $# -ne 1 ]
then
    echo "Usage ./run_simulation.sh <config-file>"
    exit
else
    wdir=${0%/*}/..
    config=$1
    sup_lib=${wdir}/lib/JavaBayes.jar:${wdir}/lib/SimpleSimulator2.jar:${wdir}/lib/weka.jar
    ws_libs=${wdir}/lib/websocket/websocket-api-9.0.5.v20130815.jar:${wdir}/lib/websocket/websocket-client-9.0.5.v20130815.jar:${wdir}/lib/websocket/websocket-common-9.0.5.v20130815.jar:${wdir}/lib/websocket/websocket-server-9.0.5.v20130815.jar:${wdir}/lib/websocket/websocket-servlet-9.0.5.v20130815.jar
    jetty=${wdir}/lib/jetty/jetty-client-9.0.5.v20130815.jar:${wdir}/lib/jetty/jetty-http-9.0.5.v20130815.jar:${wdir}/lib/jetty/jetty-io-9.0.5.v20130815.jar:${wdir}/lib/jetty/jetty-security-9.0.5.v20130815.jar:${wdir}/lib/jetty/jetty-server-9.0.5.v20130815.jar:${wdir}/lib/jetty/jetty-servlet-9.0.5.v20130815.jar:${wdir}/lib/jetty/jetty-servlets-9.0.5.v20130815.jar:${wdir}/lib/jetty/jetty-util-9.0.5.v20130815.jar:${wdir}/lib/jetty/jetty-webapp-9.0.5.v20130815.jar:${wdir}/lib/jetty/servlet-api-3.0.jar
    java -cp ${wdir}/target/STACSettlers.jar:${wdir}/lib/mdp-library.jar:$sup_lib:$ws_libs:$jetty soc.robot.stac.simulation.Simulation ${config} 
#&
#    sleep 3
#    cd ${wdir}/web/main
#    pwd
#    node runclient.js test
fi

