#!/bin/bash

# script for running STAC simulation

if [ $# -ne 1 ]
then
    echo "Usage ./run_simulation.sh <config-file>"
    exit
else
    wdir=${0%/*}/..
    config=$1
    java -cp ${wdir}/target/STACSettlers.jar soc.robot.stac.simulation.Simulation ${config}
fi

