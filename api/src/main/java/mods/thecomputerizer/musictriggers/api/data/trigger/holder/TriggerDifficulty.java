package mods.thecomputerizer.musictriggers.api.data.trigger.holder;

import mods.thecomputerizer.musictriggers.api.data.channel.ChannelAPI;
import mods.thecomputerizer.musictriggers.api.data.parameter.Parameter;
import mods.thecomputerizer.musictriggers.api.data.parameter.primitive.ParameterInt;

import java.util.Map;

public class TriggerDifficulty extends HolderTrigger {

    public TriggerDifficulty(ChannelAPI channel) {
        super(channel,"difficulty");
    }

    @Override
    protected void initExtraParameters(Map<String,Parameter<?>> map) {
        super.initExtraParameters(map);
        addParameter(map,"level",new ParameterInt(0));
    }

    @Override
    public boolean isActive() {
        return false;
    }
}
