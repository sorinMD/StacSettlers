#!/bin/bash

# script for installing a jar library to a local maven repository

if [ $# -ne 2 ]
then
    echo "Usage ./install_local_library.sh <path-to-jar-file> <artifact-ID>"
    exit
else
    pathToJar=$1
    artifactId=$2
    mvn install:install-file \
        -Dfile=${pathToJar} \
        -DgroupId=local \
        -DartifactId=${artifactId} \
        -Dversion=1.0 \
        -Dpackaging=jar \
        -DgeneratePom=true
fi

