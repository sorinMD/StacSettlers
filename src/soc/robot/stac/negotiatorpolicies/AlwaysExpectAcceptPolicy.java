package soc.robot.stac.negotiatorpolicies;

import soc.game.SOCTradeOffer;
import static soc.robot.SOCRobotNegotiator.ACCEPT_OFFER;
import soc.robot.stac.PersuasionStacRobotNegotiator;
import soc.robot.stac.StacRobotBrain;
import soc.robot.stac.StacRobotNegotiator;

/**
 * This policy always expects that an offer to another agent with be accepted. 
 * Thus, this is another implementation of an optimistic proposer.
 * @author markusguhe
 */
public class AlwaysExpectAcceptPolicy extends PersuasionStacRobotNegotiator {

    public AlwaysExpectAcceptPolicy(StacRobotBrain br, boolean fullPlan) {
        super(br, fullPlan);
    }

    /**
     * We don't check whether we believe the opponent has the desired resources (as it is done in the super implementation).
     * @param offer
     * @param receiverNum
     * @param forced
     * @return 
     */
    @Override
    protected int guessOpponentResponseWrapper(SOCTradeOffer offer, int receiverNum) {
        return guessOpponentResponse(offer, receiverNum);
    }

    @Override
    protected int guessOpponentResponse(SOCTradeOffer offer, int receiverNum) {
        return ACCEPT_OFFER;
    }

}
