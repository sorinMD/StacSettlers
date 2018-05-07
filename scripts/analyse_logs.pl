
# this is a script for analysing the results log file!

my $usage = "USAGE: analyse_log.pl <logfile>\n";

if ( $#ARGV ne 0 ) {
    print $usage;
    exit;
}

my $logfile = $ARGV[0];
open ( LOG, $logfile ) or die "Cannot open file $logfile\n";
print "===> $logfile\n";

my @agt_inds = ( 2 ); #, 10, 18, 26 ); # 2, 10, 18, 26
my @fld_inds = ( 0, 1, 2, 3, 4, 5, 6, 7 ); # 0-7 ( 0:name, 1:victPoints, 2:winner, 3:offers, 4:succOffers, 5:trades, 6:resByTrades, 7:resByDice )

my $line = "";
my @fields = ();
while ( <LOG> ) {

    $line = $_;
    @fields = split( /\t+/, $line );

    print ""; # $fields[0];
    foreach $agt_ind ( @agt_inds ) {
	foreach $fld_ind ( @fld_inds ) {
	    print "\t" . $fields[$agt_ind+$fld_ind];
	}
    }
    print "\n";

}

