
my $usage = "USAGE: getTrades.pl <logfile>\n";

#if ( $#ARGV ne 0 ) {
#    print $usage;
#    exit;
#}

foreach $logfile ( @ARGV ) {

#my $logfile = $ARGV[0];
open ( LOG, $logfile ) or die "Cannot open file $logfile\n";
#print "===> $logfile\n";

my $line = "";
my $player = "";
my $offer_txt = "";
while ( <LOG> ) {
    $line = $_;
    if ( $line =~ /.*GAME-TEXT-MESSAGE.*\|player=(\w+)\|.*\|(clay=\S+\|.*\|text=(.*))/ ) {
	$player = $1;
	$offer_txt = $2;
	$msg_txt = $3;
	#if ( $player eq "MDP" ) {
	    print "\n";
	    print "$player => $offer_txt\n";
	#}
	#if ( $player eq "MDP" ) { print "\n"; }
	if ( $msg_txt =~ /BUILD_PLAN.*type=(\d+)/ ) {
	    print "build_plan=$1\n";
	}
    }
    if ( $line =~ /.*SOCGameTextMsg.*\|nickname=(\w+)\|.*(text=.* ((has won)|(rolled )|(built )|(trade)).*)/ ) {
	print "\n";
	print "  $1 -> $2\n";
    }

}

close( LOG );

}

