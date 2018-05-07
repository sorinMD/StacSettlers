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
    java -cp ${wdir}/target/STACSettlers.jar:${wdir}/lib/mdp-library.jar:$sup_lib soc.robot.stac.simulation.Simulation ${config}
fi

