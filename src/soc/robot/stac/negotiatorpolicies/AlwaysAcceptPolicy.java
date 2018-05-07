package soc.robot.stac.negotiatorpolicies;

import soc.game.SOCTradeOffer;
import soc.robot.stac.PersuasionStacRobotNegotiator;
import soc.robot.stac.StacRobotBrain;
import soc.robot.stac.StacRobotNegotiator;

/**
 * Negotiation policy accepting all trade offers.
 * @author markusguhe
 */
public class AlwaysAcceptPolicy extends PersuasionStacRobotNegotiator {

    public AlwaysAcceptPolicy(StacRobotBrain br, boolean fullPlan) {
        super(br, fullPlan);
    }

    @Override
    protected int considerOfferToMe(SOCTradeOffer offer) {
        return ACCEPT_OFFER;
    }
}
