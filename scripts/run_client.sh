#!/bin/bash

# script for running the STAC client

if [ $# -ne 0 ]
then
    echo "Usage ./run_client.sh"
    exit
else
    wdir=${0%/*}/..
    config=$1
    sup_lib=${wdir}/lib/JavaBayes.jar:${wdir}/lib/SimpleSimulator2.jar:${wdir}/lib/weka.jar
    java -cp ${wdir}/target/STACSettlers.jar:${wdir}/lib/mdp-library.jar:$sup_lib soc.client.SOCPlayerClient 
fi

