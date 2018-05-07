package soc.robot.stac.negotiatorpolicies;

import soc.game.SOCTradeOffer;
import soc.robot.stac.PersuasionStacRobotNegotiator;
import soc.robot.stac.StacRobotBrain;
import soc.robot.stac.StacRobotNegotiator;

/**
 * Negotiation policy that first does a normal evaluation via considerOffer 
 * but additionally accepts all trades where we gain more resources than we give away.
 * @author markusguhe
 */
public class GreedyAcceptPolicy extends PersuasionStacRobotNegotiator {

    public GreedyAcceptPolicy(StacRobotBrain br, boolean fullPlan) {
        super(br, fullPlan);
    }

    @Override
    protected int considerOfferToMe(SOCTradeOffer offer) {
        int result = super.considerOfferToMe(offer);
        if (result == REJECT_OFFER) {
            if (offer.getGiveSet().getTotal() > offer.getGetSet().getTotal()) {
                result = ACCEPT_OFFER;
            }
        }
        return result;
    }
}
