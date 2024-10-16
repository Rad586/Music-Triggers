package mods.thecomputerizer.musictriggers.api.data.render;

import lombok.Getter;
import mods.thecomputerizer.musictriggers.api.data.channel.ChannelAPI;
import mods.thecomputerizer.musictriggers.api.data.channel.ChannelElement;
import mods.thecomputerizer.musictriggers.api.data.channel.ChannelElementRunner;
import mods.thecomputerizer.musictriggers.api.data.trigger.TriggerAPI;
import mods.thecomputerizer.theimpossiblelibrary.api.toml.Toml;

import java.util.ArrayList;
import java.util.List;

@Getter
public abstract class CardAPI extends ChannelElementRunner {

    private final List<TriggerAPI> triggers;

    protected CardAPI(ChannelAPI channel, String name) {
        super(channel,name);
        this.triggers = new ArrayList<>();
    }

    @Override public void close() {
        this.triggers.clear();
    }

    @Override public Class<? extends ChannelElement> getTypeClass() {
        return CardAPI.class;
    }
    
    @Override public boolean isClient() {
        return true;
    }
    
    @Override public boolean isServer() {
        return true;
    }

    @Override public boolean isResource() {
        return true;
    }

    @Override public boolean parse(Toml table) {
        return super.parse(table) && parseTriggers(this.channel,this.triggers);
    }
}