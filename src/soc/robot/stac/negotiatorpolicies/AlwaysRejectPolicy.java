package soc.robot.stac.negotiatorpolicies;

import soc.game.SOCTradeOffer;
import soc.robot.stac.PersuasionStacRobotNegotiator;
import soc.robot.stac.StacRobotBrain;
import soc.robot.stac.StacRobotNegotiator;

/**
 * This policy rejects all trade offers.
 * @author markusguhe
 */
public class AlwaysRejectPolicy extends PersuasionStacRobotNegotiator {

    public AlwaysRejectPolicy(StacRobotBrain br, boolean fullPlan) {
        super(br, fullPlan);
    }

    @Override
    protected int considerOfferToMe(SOCTradeOffer offer) {
        return REJECT_OFFER;
    }
}
