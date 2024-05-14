package mods.thecomputerizer.musictriggers.api.data.trigger;

import lombok.Getter;
import mods.thecomputerizer.musictriggers.api.data.MTDataRef.TableRef;
import mods.thecomputerizer.musictriggers.api.data.audio.AudioPool;
import mods.thecomputerizer.musictriggers.api.data.channel.ChannelAPI;
import mods.thecomputerizer.musictriggers.api.data.channel.ChannelData;
import mods.thecomputerizer.musictriggers.api.data.channel.ChannelElement;
import mods.thecomputerizer.musictriggers.api.data.parameter.ParameterWrapper;
import mods.thecomputerizer.musictriggers.api.data.trigger.basic.BasicTrigger;

import javax.annotation.Nullable;
import java.util.*;

import static mods.thecomputerizer.musictriggers.api.data.trigger.TriggerAPI.State.ACTIVE;
import static mods.thecomputerizer.musictriggers.api.data.trigger.TriggerAPI.State.IDLE;
import static mods.thecomputerizer.musictriggers.api.data.trigger.TriggerAPI.State.PLAYABLE;

@Getter
public class TriggerSelector extends ChannelElement {

    protected final TriggerContext context;
    protected TriggerAPI activeTrigger;
    protected TriggerAPI previousTrigger;
    protected AudioPool activePool;
    protected AudioPool previousPool;
    protected String crashHelper = "";
    private boolean cleared;

    public TriggerSelector(ChannelAPI channel, TriggerContext context) {
        super(channel,"selector");
        this.context = context;
    }

    public void clear() {
        this.activeTrigger = null;
        this.previousTrigger = null;
        this.activePool = null;
        this.previousPool = null;
        this.crashHelper = "cleared";
    }

    @Override
    public void close() {
        clear();
    }

    protected Collection<TriggerAPI> collectPlayableTriggers(Collection<TriggerAPI> triggers) {
        setCrashHelper("playable (trigger collection)");
        Set<TriggerAPI> playable = new HashSet<>();
        for(TriggerAPI trigger : triggers) {
            setCrashHelper("playable ("+trigger.getNameWithID()+")");
            if(trigger.isDisabled() || trigger instanceof BasicTrigger) continue;
            if(trigger.query(this.context)) {
                if(trigger.getState()!=ACTIVE) trigger.setState(PLAYABLE);
                playable.add(trigger);
            }
            else trigger.setState(IDLE);
        }
        playable.removeIf(trigger -> !trigger.canActivate());
        return playable;
    }

    /**
     * Only used when COMBINE_EQUAL_PRIORITY is enabled
     */
    public Collection<TriggerAPI> getPriorityTriggers(Collection<TriggerAPI> triggers) {
        Set<TriggerAPI> priority = new HashSet<>();
        int priorityVal = 0;
        for(TriggerAPI trigger : triggers) {
            int tPriority = trigger.getParameterAsInt("priority");
            if(priority.isEmpty()) {
                priority.add(trigger);
                priorityVal = tPriority;
            }
            else if(tPriority==priorityVal) priority.add(trigger);
            else {
                if(this.channel.getHelper().getDebugBool("reverse_priority")) {
                    if(tPriority<priorityVal) {
                        priority.clear();
                        priority.add(trigger);
                        priorityVal = tPriority;
                    }
                } else if(tPriority>priorityVal) {
                    priority.clear();
                    priority.add(trigger);
                    priorityVal = tPriority;
                }
            }
        }
        return priority;
    }

    protected @Nullable TriggerAPI getPriorityTrigger(Collection<TriggerAPI> registeredTriggers) {
        Collection<TriggerAPI> triggers = collectPlayableTriggers(registeredTriggers);
        return this.channel.getHelper().getDebugBool("independent_audio_pools") ?
                TriggerHelper.getPriorityTrigger(this.channel.getHelper(),triggers) :
                new TriggerMerged(this.channel,getPriorityTriggers(triggers));
    }
    
    @Override protected TableRef getReferenceData() {
        return null;
    }
    
    @Override protected String getSubTypeName() {
        return "Trigger";
    }
    
    @Override public Class<? extends ParameterWrapper> getTypeClass() {
        return TriggerSelector.class;
    }

    public boolean isClient() {
        return this.channel.isClientChannel();
    }
    
    @Override
    public boolean isResource() {
        return false;
    }
    
    public TriggerAPI queryOrIdle(@Nullable TriggerAPI priority, @Nullable TriggerAPI trigger) {
        if(Objects.nonNull(trigger)) {
            if(Objects.isNull(priority) && trigger.query(this.context)) priority = trigger;
            else if(!trigger.isDisabled()) trigger.setState(IDLE);
        }
        return priority;
    }

    public void select() {
        if(!setContext()) return;
        setCrashHelper("trigger selection");
        TriggerAPI priorityTrigger = null;
        ChannelData data = this.channel.getData();
        if(!this.context.hasPlayer()) {
            setCrashHelper("early triggers");
            if(isClient()) {
                setCrashHelper("loading trigger");
                priorityTrigger = queryOrIdle(priorityTrigger,data.getLoadingTrigger());
                setCrashHelper("menu trigger");
                priorityTrigger = queryOrIdle(priorityTrigger,data.getMenuTrigger());
            }
        } else {
            setCrashHelper("normal triggers");
            priorityTrigger = queryOrIdle(priorityTrigger,getPriorityTrigger(data.getTriggerEventMap().keySet()));
        }
        setCrashHelper("generic trigger");
        priorityTrigger = queryOrIdle(priorityTrigger,data.getGenericTrigger());
        setActivePool(priorityTrigger instanceof BasicTrigger ? setBasicTrigger(priorityTrigger) :
                              setActiveTrigger(priorityTrigger));
    }

    protected @Nullable AudioPool setActiveTrigger(TriggerAPI trigger) {
        if(this.channel.checkDeactivate(this.activeTrigger,trigger)) {
            if(Objects.nonNull(this.activeTrigger)) this.channel.deactivate();
            this.previousTrigger = this.activeTrigger;
            this.activeTrigger = trigger;
            if(Objects.nonNull(this.activeTrigger)) this.channel.activate();
        }
        return Objects.nonNull(this.activeTrigger) ? this.activeTrigger.getAudioPool() : null;
    }

    protected void setActivePool(AudioPool pool) {
        this.previousPool = this.activePool;
        this.activePool = pool;
    }

    protected @Nullable AudioPool setBasicTrigger(TriggerAPI trigger) {
        return setActiveTrigger(trigger);
    }

    private boolean setContext() {
        if(Objects.isNull(this.context)) {
            if(!this.cleared) {
                clear();
                this.cleared = true;
            }
            return false;
        }
        this.cleared = false;
        this.context.cache();
        return true;
    }

    protected void setCrashHelper(@Nullable String status) {
        this.crashHelper = Objects.nonNull(status) ? status : "";
    }

    @Override
    public String toString() {
        return (isClient() ? "Client" : "Server")+" Trigger Selector ["+this.crashHelper+"]";
    }
}