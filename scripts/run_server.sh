#!/bin/bash

#Start the STACSettlers server so that it connects to settlers.inf and pipes all output to a logfile

today=`date '+%Y-%m-%d-%H-%M-%S'`;
logname="console_logs/server_debug-$today.log"
mkdir -p console_logs

wdir=${0%/*}/..
(java -cp ${wdir}/STACSettlers.jar soc.server.SOCServer -Dstac.robots=8 8880 100 ${dbUser} ${dbPass} 2>&1) | tee $logname

