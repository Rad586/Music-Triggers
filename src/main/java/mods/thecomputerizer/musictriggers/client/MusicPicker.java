package mods.thecomputerizer.musictriggers.client;

import com.rits.cloning.Cloner;
import corgitaco.betterweather.helpers.BetterWeatherWorldData;
import corgitaco.betterweather.weather.event.AcidRain;
import corgitaco.betterweather.weather.event.Blizzard;
import corgitaco.betterweather.weather.event.Cloudy;
import corgitaco.betterweather.weather.event.Rain;
import corgitaco.enhancedcelestials.EnhancedCelestialsWorldData;
import corgitaco.enhancedcelestials.LunarContext;
import corgitaco.enhancedcelestials.lunarevent.BloodMoon;
import corgitaco.enhancedcelestials.lunarevent.BlueMoon;
import corgitaco.enhancedcelestials.lunarevent.HarvestMoon;
import corgitaco.enhancedcelestials.lunarevent.Moon;
import mods.thecomputerizer.musictriggers.MusicTriggers;
import mods.thecomputerizer.musictriggers.client.audio.Channel;
import mods.thecomputerizer.musictriggers.client.audio.ChannelManager;
import mods.thecomputerizer.musictriggers.common.ServerChannelData;
import mods.thecomputerizer.musictriggers.common.SoundHandler;
import mods.thecomputerizer.musictriggers.config.ConfigRegistry;
import net.darkhax.gamestages.GameStageHelper;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screen.WinGameScreen;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.passive.TameableEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.network.play.client.CClientStatusPacket;
import net.minecraft.potion.Effect;
import net.minecraft.stats.Stat;
import net.minecraft.stats.Stats;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Difficulty;
import net.minecraft.world.LightType;
import net.minecraft.world.World;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.registries.ForgeRegistries;
import org.orecruncher.lib.WorldUtils;
import sereneseasons.api.season.ISeasonState;
import sereneseasons.api.season.Season;
import sereneseasons.api.season.SeasonHelper;

import java.util.*;

import static mods.thecomputerizer.musictriggers.MusicTriggers.stringBreaker;

public class MusicPicker {
    public final Minecraft mc;
    public PlayerEntity player;
    public World world;
    private final Channel channel;
    private final SoundHandler handler;
    private final Info info;

    public final HashMap<String, Integer> triggerPersistence = new HashMap<>();
    public final HashMap<String, Integer> startMap = new HashMap<>();
    public final HashMap<String, Boolean> boolMap = new HashMap<>();
    public final HashMap<Integer, Boolean> victory = new HashMap<>();
    public boolean setPVP = false;
    public int pvpVictoryID = 0;

    public final HashMap<String, List<String>> dynamicSongs = new HashMap<>();
    public final HashMap<String, Integer> dynamicPriorities = new HashMap<>();
    public final HashMap<String, Integer> dynamicFadeIn = new HashMap<>();
    public final HashMap<String, Integer> dynamicFadeOut = new HashMap<>();
    public final HashMap<String, String> dynamicDelay = new HashMap<>();
    public final List<String> savePlayable = new ArrayList<>();
    public final List<String> titleCardEvents = new ArrayList<>();
    public final List<String> timeSwitch = new ArrayList<>();

    public static final List<String> effectList = new ArrayList<>();

    public int curFadeIn = 0;
    public int curFadeOut = 0;
    public String curDelay = "0";
    public String crashHelper;

    public MusicPicker(Channel channel, SoundHandler handler) {
        this.mc = Minecraft.getInstance();
        this.channel = channel;
        this.handler = handler;
        this.info = new Info(channel.getChannelName());
    }

    public Info getInfo() {
        return this.info;
    }

    public Packeted querySongList() {
        Packeted packet = new Packeted();
        this.player = Minecraft.getInstance().player;
        if(this.handler.TriggerIdentifierMap.isEmpty()) {
            this.getInfo().updateSongList(new ArrayList<>());
            this.info.runToggles();
            return packet;
        }
        if(this.player == null) {
            if (this.handler.TriggerIdentifierMap.get("menu") != null) {
                this.getInfo().updatePlayableTriggers(Collections.singletonList("menu"));
                this.getInfo().updateActiveTriggers(Collections.singletonList("menu"));
                this.getInfo().updateSongList(this.handler.TriggerIdentifierMap.get("menu").get("_"));
            }
        } else {
            this.world = this.player.level;
            packet.setMenuSongs(allMenuSongs());
            List<String> activeSongs = comboChecker(priorityHandler(playableEvents(packet)));
            this.getInfo().updatePlayableTriggers(savePlayable);
            for (String event : timeSwitch) {
                if (!this.getInfo().getActiveTriggers().contains(event) && triggerPersistence.get(event) > 0) triggerPersistence.put(event, 0);
            }
            timeSwitch.clear();
            if (activeSongs != null && !activeSongs.isEmpty()) {
                dynamicSongs.clear();
                dynamicPriorities.clear();
                dynamicFadeIn.clear();
                dynamicFadeOut.clear();
                this.getInfo().updateSongList(activeSongs);
                this.info.runToggles();
                return packet;
            }
            dynamicSongs.clear();
            dynamicPriorities.clear();
            dynamicFadeIn.clear();
            dynamicFadeOut.clear();
            if (this.handler.TriggerIdentifierMap.get("generic") != null) {
                this.getInfo().updatePlayableTriggers(Collections.singletonList("generic"));
                this.getInfo().updateActiveTriggers(Collections.singletonList("generic"));
                curDelay = this.handler.TriggerInfoMap.get("generic")[4];
                if (curDelay.matches("0")) curDelay = this.channel.getMainConfig().universalDelay;
                curFadeIn = MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("generic")[1]);
                if (curFadeIn == 0) curFadeIn = this.channel.getMainConfig().universalFadeIn;
                curFadeOut = MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("generic")[35]);
                if (curFadeOut == 0) curFadeOut = this.channel.getMainConfig().universalFadeOut;
                this.getInfo().updateSongList(this.handler.TriggerIdentifierMap.get("generic").get("_"));
            }
        }
        this.info.runToggles();
        return packet;
    }

    @SuppressWarnings("rawtypes")
    public List<String> comboChecker(String st) {
        if (st == null) {
            return null;
        }
        List<String> playableSongs = new ArrayList<>();
        boolean skip = false;
        for (String s : dynamicSongs.get(st)) {
            for (Map.Entry<String, List<String>> stringListEntry : this.handler.antiSongs.entrySet()) {
                String checkThis = ((Map.Entry) stringListEntry).getKey().toString();
                if (s.startsWith("#") && s.replaceAll("#","").matches(checkThis)) {
                    skip = true;
                }
            }
            if(!skip) {
                for (Map.Entry<String, List<String>> stringListEntry : this.handler.songCombos.entrySet()) {
                    String checkThis = ((Map.Entry) stringListEntry).getKey().toString();
                    if (s.startsWith("@") && s.replaceAll("@", "").matches(checkThis)) {
                        if (new HashSet<>(this.info.getPlayableTriggers()).containsAll(this.handler.songCombos.get(s.replaceAll("@", ""))) && this.handler.TriggerInfoMap.keySet().containsAll(this.handler.instantiatedCombos.get(this.handler.songCombos.get(s.replaceAll("@", ""))))) {
                            playableSongs.add(s.substring(1));
                            if (!this.getInfo().getActiveTriggers().contains(st)) {
                                this.getInfo().updateActiveTriggers(this.handler.songCombos.get(s.replaceAll("@", "")));
                            }
                        }
                    }
                }
            }
        }
        if (playableSongs.isEmpty() && !skip) {
            for (String s : dynamicSongs.get(st)) {
                if (!s.startsWith("@")) {
                    playableSongs.add(s);
                    if (!this.getInfo().getActiveTriggers().contains(st)) {
                        this.getInfo().getActiveTriggers().add(st);
                    }
                }
            }
        }
        if (playableSongs.isEmpty()) {
            this.info.getPlayableTriggers().remove(st);
            if (this.info.getPlayableTriggers().isEmpty()) return null;
            playableSongs = comboChecker(priorityHandler(this.getInfo().getPlayableTriggers()));
        }
        return playableSongs;
    }

    public String priorityHandler(List<String> sta) {
        if (sta == null) return null;
        int highest = Integer.MIN_VALUE;
        String trueHighest = "";
        for (String list : sta) {
            if (dynamicPriorities.get(list) > highest && !dynamicSongs.get(list).isEmpty()) {
                highest = dynamicPriorities.get(list);
                trueHighest = list;
            }
        }
        while (dynamicSongs.get(trueHighest) == null) {
            sta.remove(trueHighest);
            if (sta.isEmpty()) {
                return null;
            }
            for (String list : sta) {
                if (dynamicPriorities.get(list) > highest) {
                    highest = dynamicPriorities.get(list);
                    trueHighest = list;
                }
            }
        }
        if (!dynamicFadeIn.isEmpty()) {
            if (dynamicFadeIn.get(trueHighest) != null) curFadeIn = dynamicFadeIn.get(trueHighest);
            else curFadeIn = 0;
            if(curFadeIn==0) curFadeIn = this.channel.getMainConfig().universalFadeIn;
        }
        if (!dynamicFadeOut.isEmpty()) {
            if (dynamicFadeOut.get(trueHighest) != null) curFadeOut = dynamicFadeOut.get(trueHighest);
            else curFadeOut = 0;
            if(curFadeOut==0) curFadeOut = this.channel.getMainConfig().universalFadeOut;
        }
        if (!dynamicDelay.isEmpty()) {
            if (dynamicDelay.get(trueHighest) != null) curDelay = dynamicDelay.get(trueHighest);
            else curDelay = "0";
            if(curDelay.matches("0")) curDelay = this.channel.getMainConfig().universalDelay;
        }
        return trueHighest;
    }

    public List<String> playableEvents(Packeted packet) {
        crashHelper = "";
        List<String> events = new ArrayList<>();
        this.info.updateActiveTriggers(new ArrayList<>());
        try {
            double time = (double) world.dayTime() / 24000.0;
            if (time > 1) {
                time = time - (long) time;
            }
            if (this.handler.TriggerIdentifierMap.get("time") != null) {
                crashHelper = "time";
                for (String identifier : this.handler.TriggerIdentifierMap.get("time").keySet()) {
                    crashHelper = "time-" + identifier;
                    String selectedStartTime = this.handler.TriggerInfoMap.get("time-" + identifier)[8];
                    String selectedEndTime = this.handler.TriggerInfoMap.get("time-" + identifier)[29];
                    double transformedTimeMin;
                    double transformedTimeMax;
                    if (selectedStartTime.matches("day")) {
                        transformedTimeMin = 0d;
                        transformedTimeMax = 0.54166666666d;
                    } else if (selectedStartTime.matches("night")) {
                        transformedTimeMin = 0.54166666666d;
                        transformedTimeMax = 1d;
                    } else if (selectedStartTime.matches("sunset")) {
                        transformedTimeMin = 0.5d;
                        transformedTimeMax = 0.54166666666d;
                    } else if (selectedStartTime.matches("sunrise")) {
                        transformedTimeMin = 0.95833333333d;
                        transformedTimeMax = 1d;
                    } else {
                        double doubleStart = Double.parseDouble(selectedStartTime);
                        double doubleEnd = Double.parseDouble(selectedEndTime);
                        if (doubleEnd == -1) {
                            if (doubleStart <= 21d) doubleEnd = doubleStart + 3d;
                            else doubleEnd = doubleStart - 21d;
                        }
                        transformedTimeMin = doubleStart / 24d;
                        transformedTimeMax = doubleEnd / 24d;
                    }
                    boolean pass;
                    if (transformedTimeMin < transformedTimeMax)
                        pass = time >= transformedTimeMin && time < transformedTimeMax;
                    else pass = time >= transformedTimeMin || time < transformedTimeMax;
                    if (pass) {
                        this.boolMap.put("time-" + identifier, true);
                        this.startMap.putIfAbsent("time-" + identifier,0);
                        if (MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("time-" + identifier)[21]) == 0) {
                            if (!events.contains("time-" + identifier)) {
                                events.add("time-" + identifier);
                                dynamicSongs.put("time-" + identifier, this.handler.TriggerIdentifierMap.get("time").get(identifier));
                                dynamicPriorities.put("time-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("time-" + identifier)[0]));
                                dynamicFadeIn.put("time-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("time-" + identifier)[1]));
                                dynamicFadeOut.put("time-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("time-" + identifier)[35]));
                                dynamicDelay.put("time-" + identifier, this.handler.TriggerInfoMap.get("time-" + identifier)[4]);
                                triggerPersistence.put("time-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("time-" + identifier)[3]));
                                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("time-" + identifier)[33]))
                                    timeSwitch.add("time-" + identifier);
                            }
                        } else if (MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("time-" + identifier)[21]) == world.getMoonPhase() + 1) {
                            if (!events.contains("time-" + identifier)) {
                                events.add("time-" + identifier);
                                dynamicSongs.put("time-" + identifier, this.handler.TriggerIdentifierMap.get("time").get(identifier));
                                dynamicPriorities.put("time-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("time-" + identifier)[0]));
                                dynamicFadeIn.put("time-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("time-" + identifier)[1]));
                                dynamicFadeOut.put("time-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("time-" + identifier)[35]));
                                dynamicDelay.put("time-" + identifier, this.handler.TriggerInfoMap.get("time-" + identifier)[4]);
                                triggerPersistence.put("time-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("time-" + identifier)[3]));
                                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("time-" + identifier)[33]))
                                    timeSwitch.add("time-" + identifier);
                            }
                        }
                    } else if (triggerPersistence.get("time-" + identifier) != null && triggerPersistence.get("time-" + identifier) > 0) {
                        if (!events.contains("time-" + identifier)) {
                            events.add("time-" + identifier);
                            dynamicSongs.put("time-" + identifier, this.handler.TriggerIdentifierMap.get("time").get(identifier));
                            dynamicPriorities.put("time-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("time-" + identifier)[0]));
                            dynamicFadeIn.put("time-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("time-" + identifier)[1]));
                            dynamicFadeOut.put("time-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("time-" + identifier)[35]));
                            dynamicDelay.put("time-" + identifier, this.handler.TriggerInfoMap.get("time-" + identifier)[4]);
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("time-" + identifier)[33]))
                                timeSwitch.add("time-" + identifier);
                        }
                    }
                }
            }
            if (this.handler.TriggerIdentifierMap.get("light") != null) {
                crashHelper = "light";
                for (String identifier : this.handler.TriggerIdentifierMap.get("light").keySet()) {
                    crashHelper = "light-" + identifier;
                    if (averageLight(roundedPos(player), Boolean.parseBoolean(this.handler.TriggerInfoMap.get("light-" + identifier)[20])) <= MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("light-" + identifier)[2])) {
                        this.boolMap.put("light-" + identifier, true);
                        this.startMap.putIfAbsent("light-" + identifier, 0);
                        if (this.boolMap.get("light-" + identifier) && this.startMap.get("light-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("light-" + identifier)[8])) {
                            if (!events.contains("light-" + identifier)) {
                                events.add("light-" + identifier);
                                dynamicSongs.put("light-" + identifier, this.handler.TriggerIdentifierMap.get("light").get(identifier));
                                dynamicPriorities.put("light-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("light-" + identifier)[0]));
                                dynamicFadeIn.put("light-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("light-" + identifier)[1]));
                                dynamicFadeOut.put("light-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("light-" + identifier)[35]));
                                dynamicDelay.put("light-" + identifier, this.handler.TriggerInfoMap.get("light-" + identifier)[4]);
                                triggerPersistence.put("light-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("light-" + identifier)[3]));
                                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("light-" + identifier)[33]))
                                    timeSwitch.add("light-" + identifier);
                            }
                        }
                    } else if (triggerPersistence.get("light-" + identifier) != null && triggerPersistence.get("light-" + identifier) > 0) {
                        if (!events.contains("light-" + identifier)) {
                            events.add("light-" + identifier);
                            dynamicSongs.put("light-" + identifier, this.handler.TriggerIdentifierMap.get("light").get(identifier));
                            dynamicPriorities.put("light-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("light-" + identifier)[0]));
                            dynamicFadeIn.put("light-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("light-" + identifier)[1]));
                            dynamicFadeOut.put("light-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("light-" + identifier)[35]));
                            dynamicDelay.put("light-" + identifier, this.handler.TriggerInfoMap.get("light-" + identifier)[4]);
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("light-" + identifier)[33]))
                                timeSwitch.add("light-" + identifier);
                        }
                    } else {
                        this.boolMap.put("light-" + identifier, false);
                        this.startMap.put("light-" + identifier, 0);
                    }
                }
            }
            if (this.handler.TriggerIdentifierMap.get("height") != null) {
                crashHelper = "height";
                for (String identifier : this.handler.TriggerIdentifierMap.get("height").keySet()) {
                    crashHelper = "height-" + identifier;
                    boolean pass;
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("height-" + identifier)[28])) {
                        pass = player.getY() < MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("height-" + identifier)[2]) && checkForSky();
                    } else
                        pass = player.getY() > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("height-" + identifier)[2]);
                    if (pass) {
                        this.boolMap.put("height-" + identifier, true);
                        this.startMap.putIfAbsent("height-" + identifier,0);
                        if (this.boolMap.get("height-" + identifier) && this.startMap.get("height-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("height-" + identifier)[8])) {
                            if (!events.contains("height-" + identifier)) {
                                events.add("height-" + identifier);
                                dynamicSongs.put("height-" + identifier, this.handler.TriggerIdentifierMap.get("height").get(identifier));
                                dynamicPriorities.put("height-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("height-" + identifier)[0]));
                                dynamicFadeIn.put("height-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("height-" + identifier)[1]));
                                dynamicFadeOut.put("height-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("height-" + identifier)[35]));
                                dynamicDelay.put("height-" + identifier, this.handler.TriggerInfoMap.get("height-" + identifier)[4]);
                                triggerPersistence.put("height-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("height-" + identifier)[3]));
                                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("height-" + identifier)[33]))
                                    timeSwitch.add("height-" + identifier);
                            }
                        }
                    } else if (triggerPersistence.get("height-" + identifier) != null && triggerPersistence.get("height-" + identifier) > 0) {
                        if (!events.contains("height-" + identifier)) {
                            events.add("height-" + identifier);
                            dynamicSongs.put("height-" + identifier, this.handler.TriggerIdentifierMap.get("height").get(identifier));
                            dynamicPriorities.put("height-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("height-" + identifier)[0]));
                            dynamicFadeIn.put("height-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("height-" + identifier)[1]));
                            dynamicFadeOut.put("height-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("height-" + identifier)[35]));
                            dynamicDelay.put("height-" + identifier, this.handler.TriggerInfoMap.get("height-" + identifier)[4]);
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("height-" + identifier)[33]))
                                timeSwitch.add("height-" + identifier);
                        }
                    } else {
                        this.boolMap.put("height-" + identifier, false);
                        this.startMap.put("height-" + identifier, 0);
                    }
                }
            }
            if (this.handler.TriggerIdentifierMap.get("elytra") != null && player.getFallFlyingTicks() > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("elytra")[8])) {
                crashHelper = "elytra";
                events.add("elytra");
                dynamicSongs.put("elytra", this.handler.TriggerIdentifierMap.get("elytra").get("_"));
                dynamicPriorities.put("elytra", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("elytra")[0]));
                dynamicFadeIn.put("elytra", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("elytra")[1]));
                dynamicFadeOut.put("elytra", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("elytra")[35]));
                dynamicDelay.put("elytra", this.handler.TriggerInfoMap.get("elytra")[4]);
                triggerPersistence.put("elytra", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("elytra")[3]));
                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("elytra")[33])) timeSwitch.add("elytra");
            } else if (triggerPersistence.get("elytra") != null && triggerPersistence.get("elytra") > 0) {
                crashHelper = "elytra";
                events.add("elytra");
                dynamicSongs.put("elytra", this.handler.TriggerIdentifierMap.get("elytra").get("_"));
                dynamicPriorities.put("elytra", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("elytra")[0]));
                dynamicFadeIn.put("elytra", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("elytra")[1]));
                dynamicFadeOut.put("elytra", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("elytra")[35]));
                dynamicDelay.put("elytra", this.handler.TriggerInfoMap.get("elytra")[4]);
                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("elytra")[33])) timeSwitch.add("elytra");
            }
            if (player.fishing != null && player.fishing.isInWaterOrBubble()) {
                this.boolMap.put("fishing", true);
                this.startMap.putIfAbsent("fishing", 0);
                if (this.boolMap.get("fishing") && this.startMap.get("fishing") > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("fishing")[8])) {
                    crashHelper = "fishing";
                    events.add("fishing");
                    dynamicSongs.put("fishing", this.handler.TriggerIdentifierMap.get("fishing").get("_"));
                    dynamicPriorities.put("fishing", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("fishing")[0]));
                    dynamicFadeIn.put("fishing", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("fishing")[1]));
                    dynamicFadeOut.put("fishing", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("fishing")[35]));
                    dynamicDelay.put("fishing", this.handler.TriggerInfoMap.get("fishing")[4]);
                    triggerPersistence.put("fishing", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("fishing")[3]));
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("fishing")[33]))
                        timeSwitch.add("fishing");
                }
            } else if (triggerPersistence.get("fishing") != null && triggerPersistence.get("fishing") > 0) {
                crashHelper = "fishing";
                events.add("fishing");
                dynamicSongs.put("fishing", this.handler.TriggerIdentifierMap.get("fishing").get("_"));
                dynamicPriorities.put("fishing", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("fishing")[0]));
                dynamicFadeIn.put("fishing", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("fishing")[1]));
                dynamicFadeOut.put("fishing", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("fishing")[35]));
                dynamicDelay.put("fishing", this.handler.TriggerInfoMap.get("fishing")[4]);
                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("fishing")[33])) timeSwitch.add("fishing");
            } else this.boolMap.put("fishing", false);
            if (world.isRaining() && this.handler.TriggerIdentifierMap.get("raining") != null) {
                this.boolMap.put("raining", true);
                this.startMap.putIfAbsent("raining", 0);
                if (this.boolMap.get("raining") && this.startMap.get("raining") > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("raining")[8])) {
                    crashHelper = "raining";
                    events.add("raining");
                    dynamicSongs.put("raining", this.handler.TriggerIdentifierMap.get("raining").get("_"));
                    dynamicPriorities.put("raining", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("raining")[0]));
                    dynamicFadeIn.put("raining", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("raining")[1]));
                    dynamicFadeOut.put("raining", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("raining")[35]));
                    dynamicDelay.put("raining", this.handler.TriggerInfoMap.get("raining")[4]);
                    triggerPersistence.put("raining", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("raining")[3]));
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("raining")[33])) timeSwitch.add("raining");
                }
            } else if (triggerPersistence.get("raining") != null && triggerPersistence.get("raining") > 0) {
                crashHelper = "raining";
                events.add("raining");
                dynamicSongs.put("raining", this.handler.TriggerIdentifierMap.get("raining").get("_"));
                dynamicPriorities.put("raining", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("raining")[0]));
                dynamicFadeIn.put("raining", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("raining")[1]));
                dynamicFadeOut.put("raining", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("raining")[35]));
                dynamicDelay.put("raining", this.handler.TriggerInfoMap.get("raining")[4]);
                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("raining")[33])) timeSwitch.add("raining");
            } else {
                this.boolMap.put("raining", false);
                this.startMap.put("raining", 0);
            }
            if (this.handler.TriggerIdentifierMap.get("snowing") != null) {
                if (world.isRaining()) packet.addSnowTrigger(new ServerChannelData.Snow());
                if (this.handler.TriggerIdentifierMap.get("snowing") != null && world.isRaining() && this.channel.getSyncStatus().isSnowTriggerActive()) {
                    this.boolMap.put("snowing", true);
                    this.startMap.putIfAbsent("snowing", 0);
                    if (this.boolMap.get("snowing") && this.startMap.get("snowing") > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("snowing")[8])) {
                        crashHelper = "snowing";
                        events.add("snowing");
                        dynamicSongs.put("snowing", this.handler.TriggerIdentifierMap.get("snowing").get("_"));
                        dynamicPriorities.put("snowing", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("snowing")[0]));
                        dynamicFadeIn.put("snowing", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("snowing")[1]));
                        dynamicFadeOut.put("snowing", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("snowing")[35]));
                        dynamicDelay.put("snowing", this.handler.TriggerInfoMap.get("snowing")[4]);
                        triggerPersistence.put("snowing", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("snowing")[3]));
                        if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("snowing")[33]))
                            timeSwitch.add("snowing");
                    }
                } else if (triggerPersistence.get("snowing") != null && triggerPersistence.get("snowing") > 0) {
                    crashHelper = "snowing";
                    events.add("snowing");
                    dynamicSongs.put("snowing", this.handler.TriggerIdentifierMap.get("snowing").get("_"));
                    dynamicPriorities.put("snowing", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("snowing")[0]));
                    dynamicFadeIn.put("snowing", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("snowing")[1]));
                    dynamicFadeOut.put("snowing", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("snowing")[35]));
                    dynamicDelay.put("snowing", this.handler.TriggerInfoMap.get("snowing")[4]);
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("snowing")[33])) timeSwitch.add("snowing");
                } else {
                    this.boolMap.put("snowing", false);
                    this.startMap.put("snowing", 0);
                }
            }
            if (world.isThundering() && this.handler.TriggerIdentifierMap.get("storming") != null) {
                this.boolMap.put("storming", true);
                this.startMap.putIfAbsent("storming", 0);
                if (this.boolMap.get("storming") && this.startMap.get("storming") > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("storming")[8])) {
                    crashHelper = "storming";
                    events.add("storming");
                    dynamicSongs.put("storming", this.handler.TriggerIdentifierMap.get("storming").get("_"));
                    dynamicPriorities.put("storming", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("storming")[0]));
                    dynamicFadeIn.put("storming", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("storming")[1]));
                    dynamicFadeOut.put("storming", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("storming")[35]));
                    dynamicDelay.put("storming", this.handler.TriggerInfoMap.get("storming")[4]);
                    triggerPersistence.put("storming", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("storming")[3]));
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("storming")[33]))
                        timeSwitch.add("storming");
                }
            } else if (triggerPersistence.get("storming") != null && triggerPersistence.get("storming") > 0) {
                crashHelper = "storming";
                events.add("storming");
                dynamicSongs.put("storming", this.handler.TriggerIdentifierMap.get("storming").get("_"));
                dynamicPriorities.put("storming", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("storming")[0]));
                dynamicFadeIn.put("storming", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("storming")[1]));
                dynamicFadeOut.put("storming", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("storming")[35]));
                dynamicDelay.put("storming", this.handler.TriggerInfoMap.get("storming")[4]);
                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("storming")[33])) timeSwitch.add("storming");
            } else {
                this.boolMap.put("storming", false);
                this.startMap.put("storming", 0);
            }
            if (this.handler.TriggerIdentifierMap.get("lowhp") != null && player.getHealth() < player.getMaxHealth() * (MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("lowhp")[2]) / 100F)) {
                this.boolMap.put("lowhp", true);
                this.startMap.putIfAbsent("lowhp", 0);
                if (this.boolMap.get("lowhp") && this.startMap.get("lowhp") > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("lowhp")[8])) {
                    crashHelper = "lowhp";
                    events.add("lowhp");
                    dynamicSongs.put("lowhp", this.handler.TriggerIdentifierMap.get("lowhp").get("_"));
                    dynamicPriorities.put("lowhp", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("lowhp")[0]));
                    dynamicFadeIn.put("lowhp", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("lowhp")[1]));
                    dynamicFadeOut.put("lowhp", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("lowhp")[35]));
                    dynamicDelay.put("lowhp", this.handler.TriggerInfoMap.get("lowhp")[4]);
                    triggerPersistence.put("lowhp", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("lowhp")[3]));
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("lowhp")[33])) timeSwitch.add("lowhp");
                }
            } else if (triggerPersistence.get("lowhp") != null && triggerPersistence.get("lowhp") > 0) {
                crashHelper = "lowhp";
                events.add("lowhp");
                dynamicSongs.put("lowhp", this.handler.TriggerIdentifierMap.get("lowhp").get("_"));
                dynamicPriorities.put("lowhp", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("lowhp")[0]));
                dynamicFadeIn.put("lowhp", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("lowhp")[1]));
                dynamicFadeOut.put("lowhp", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("lowhp")[35]));
                dynamicDelay.put("lowhp", this.handler.TriggerInfoMap.get("lowhp")[4]);
                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("lowhp")[33])) timeSwitch.add("lowhp");
            } else {
                this.boolMap.put("lowhp", false);
                this.startMap.put("lowhp", 0);
            }
            if (this.handler.TriggerIdentifierMap.get("dead") != null && (player.getHealth() <= 0f || player.isDeadOrDying())) {
                this.boolMap.put("dead", true);
                this.startMap.putIfAbsent("dead", 0);
                if (this.boolMap.get("dead") && this.startMap.get("dead") > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("dead")[8])) {
                    crashHelper = "dead";
                    events.add("dead");
                    dynamicSongs.put("dead", this.handler.TriggerIdentifierMap.get("dead").get("_"));
                    dynamicPriorities.put("dead", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("dead")[0]));
                    dynamicFadeIn.put("dead", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("dead")[1]));
                    dynamicFadeOut.put("dead", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("dead")[35]));
                    dynamicDelay.put("dead", this.handler.TriggerInfoMap.get("dead")[4]);
                    for (Map.Entry<Integer, Boolean> integerListEntry : victory.entrySet()) {
                        int key = integerListEntry.getKey();
                        victory.put(key, false);
                    }
                    triggerPersistence.put("dead", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("dead")[3]));
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("dead")[33])) timeSwitch.add("dead");
                }
            } else if (triggerPersistence.get("dead") != null && triggerPersistence.get("dead") > 0) {
                crashHelper = "dead";
                events.add("dead");
                dynamicSongs.put("dead", this.handler.TriggerIdentifierMap.get("dead").get("_"));
                dynamicPriorities.put("dead", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("dead")[0]));
                dynamicFadeIn.put("dead", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("dead")[1]));
                dynamicFadeOut.put("dead", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("dead")[35]));
                dynamicDelay.put("dead", this.handler.TriggerInfoMap.get("dead")[4]);
                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("dead")[33])) timeSwitch.add("dead");
            } else {
                this.boolMap.put("dead", false);
                this.startMap.put("dead", 0);
            }
            if (player.isSpectator() && this.handler.TriggerIdentifierMap.get("spectator") != null) {
                this.boolMap.put("spectator", true);
                this.startMap.putIfAbsent("spectator", 0);
                if (this.boolMap.get("spectator") && this.startMap.get("spectator") > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("spectator")[8])) {
                    crashHelper = "spectator";
                    events.add("spectator");
                    dynamicSongs.put("spectator", this.handler.TriggerIdentifierMap.get("spectator").get("_"));
                    dynamicPriorities.put("spectator", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("spectator")[0]));
                    dynamicFadeIn.put("spectator", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("spectator")[1]));
                    dynamicFadeOut.put("spectator", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("spectator")[35]));
                    dynamicDelay.put("spectator", this.handler.TriggerInfoMap.get("spectator")[4]);
                    triggerPersistence.put("spectator", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("spectator")[3]));
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("spectator")[33]))
                        timeSwitch.add("spectator");
                }
            } else if (triggerPersistence.get("spectator") != null && triggerPersistence.get("spectator") > 0) {
                crashHelper = "spectator";
                events.add("spectator");
                dynamicSongs.put("spectator", this.handler.TriggerIdentifierMap.get("spectator").get("_"));
                dynamicPriorities.put("spectator", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("spectator")[0]));
                dynamicFadeIn.put("spectator", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("spectator")[1]));
                dynamicFadeOut.put("spectator", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("spectator")[35]));
                dynamicDelay.put("spectator", this.handler.TriggerInfoMap.get("spectator")[4]);
                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("spectator")[33])) timeSwitch.add("spectator");
            } else {
                this.boolMap.put("spectator", false);
                this.startMap.put("spectator", 0);
            }
            if (player.isCreative() && this.handler.TriggerIdentifierMap.get("creative") != null) {
                this.boolMap.put("creative", true);
                this.startMap.putIfAbsent("creative", 0);
                if (this.boolMap.get("creative") && this.startMap.get("creative") > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("creative")[8])) {
                    crashHelper = "creative";
                    events.add("creative");
                    dynamicSongs.put("creative", this.handler.TriggerIdentifierMap.get("creative").get("_"));
                    dynamicPriorities.put("creative", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("creative")[0]));
                    dynamicFadeIn.put("creative", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("creative")[1]));
                    dynamicFadeOut.put("creative", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("creative")[35]));
                    dynamicDelay.put("creative", this.handler.TriggerInfoMap.get("creative")[4]);
                    triggerPersistence.put("creative", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("creative")[3]));
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("creative")[33]))
                        timeSwitch.add("creative");
                }
            } else if (triggerPersistence.get("creative") != null && triggerPersistence.get("creative") > 0) {
                crashHelper = "creative";
                events.add("creative");
                dynamicSongs.put("creative", this.handler.TriggerIdentifierMap.get("creative").get("_"));
                dynamicPriorities.put("creative", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("creative")[0]));
                dynamicFadeIn.put("creative", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("creative")[1]));
                dynamicFadeOut.put("creative", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("creative")[35]));
                dynamicDelay.put("creative", this.handler.TriggerInfoMap.get("creative")[4]);
                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("creative")[33])) timeSwitch.add("creative");
            } else {
                this.boolMap.put("creative", false);
                this.startMap.put("creative", 0);
            }
            if (this.handler.TriggerIdentifierMap.get("riding") != null) {
                crashHelper = "riding";
                for (String identifier : this.handler.TriggerIdentifierMap.get("riding").keySet()) {
                    crashHelper = "riding-" + identifier;
                    if (checkRiding(this.handler.TriggerInfoMap.get("riding-" + identifier)[9])) {
                        this.boolMap.put("riding-" + identifier, true);
                        this.startMap.putIfAbsent("riding-" + identifier, 0);
                        if (this.boolMap.get("riding-" + identifier) && this.startMap.get("riding-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("riding-" + identifier)[8])) {
                            if (!events.contains("riding-" + identifier)) {
                                events.add("riding-" + identifier);
                                dynamicSongs.put("riding-" + identifier, this.handler.TriggerIdentifierMap.get("riding").get(identifier));
                                dynamicPriorities.put("riding-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("riding-" + identifier)[0]));
                                dynamicFadeIn.put("riding-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("riding-" + identifier)[1]));
                                dynamicFadeOut.put("riding-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("riding-" + identifier)[35]));
                                dynamicDelay.put("riding-" + identifier, this.handler.TriggerInfoMap.get("riding-" + identifier)[4]);
                                triggerPersistence.put("riding-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("riding-" + identifier)[3]));
                                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("riding-" + identifier)[33]))
                                    timeSwitch.add("riding-" + identifier);
                            }
                        }
                    } else if (triggerPersistence.get("riding-" + identifier) != null && triggerPersistence.get("riding-" + identifier) > 0) {
                        if (!events.contains("riding-" + identifier)) {
                            events.add("riding-" + identifier);
                            dynamicSongs.put("riding-" + identifier, this.handler.TriggerIdentifierMap.get("riding").get(identifier));
                            dynamicPriorities.put("riding-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("riding-" + identifier)[0]));
                            dynamicFadeIn.put("riding-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("riding-" + identifier)[1]));
                            dynamicFadeOut.put("riding-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("riding-" + identifier)[35]));
                            dynamicDelay.put("riding-" + identifier, this.handler.TriggerInfoMap.get("riding-" + identifier)[4]);
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("riding-" + identifier)[33]))
                                timeSwitch.add("riding-" + identifier);
                        }
                    } else {
                        this.boolMap.put("riding-" + identifier, false);
                        this.startMap.put("riding-" + identifier, 0);
                    }
                }
            }
            if (this.handler.TriggerIdentifierMap.get("underwater") != null && (world.getBlockState(roundedPos(player)).getMaterial() == Material.WATER || world.getBlockState(roundedPos(player)).getMaterial() == Material.WATER_PLANT || world.getBlockState(roundedPos(player)).getMaterial() == Material.REPLACEABLE_WATER_PLANT) && (world.getBlockState(roundedPos(player).above()).getMaterial() == Material.WATER || world.getBlockState(roundedPos(player).above()).getMaterial() == Material.WATER_PLANT || world.getBlockState(roundedPos(player).above()).getMaterial() == Material.REPLACEABLE_WATER_PLANT)) {
                this.boolMap.put("underwater", true);
                this.startMap.putIfAbsent("underwater", 0);
                if (this.startMap.get("underwater") > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("underwater")[8])) {
                    crashHelper = "underwater";
                    events.add("underwater");
                    dynamicSongs.put("underwater", this.handler.TriggerIdentifierMap.get("underwater").get("_"));
                    dynamicPriorities.put("underwater", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("underwater")[0]));
                    dynamicFadeIn.put("underwater", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("underwater")[1]));
                    dynamicFadeOut.put("underwater", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("underwater")[35]));
                    dynamicDelay.put("underwater", this.handler.TriggerInfoMap.get("underwater")[4]);
                    triggerPersistence.put("underwater", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("underwater")[3]));
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("underwater")[33]))
                        timeSwitch.add("underwater");
                }
            } else if (triggerPersistence.get("underwater") != null && triggerPersistence.get("underwater") > 0) {
                crashHelper = "underwater";
                events.add("underwater");
                dynamicSongs.put("underwater", this.handler.TriggerIdentifierMap.get("underwater").get("_"));
                dynamicPriorities.put("underwater", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("underwater")[0]));
                dynamicFadeIn.put("underwater", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("underwater")[1]));
                dynamicFadeOut.put("underwater", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("underwater")[35]));
                dynamicDelay.put("underwater", this.handler.TriggerInfoMap.get("underwater")[4]);
                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("underwater")[33]))
                    timeSwitch.add("underwater");
            } else {
                this.boolMap.put("underwater", false);
                this.startMap.put("underwater", 0);
            }
            for (LivingEntity ent : world.getEntitiesOfClass(LivingEntity.class, new AxisAlignedBB(player.getX() - 16, player.getY() - 8, player.getZ() - 16, player.getX() + 16, player.getY() + 8, player.getZ() + 16))) {
                if ((ent instanceof TameableEntity && ent.serializeNBT() != null && ent.serializeNBT().getString("Owner").matches(player.getStringUUID())) && this.handler.TriggerIdentifierMap.get("pet") != null) {
                    crashHelper = "pet";
                    events.add("pet");
                    dynamicSongs.put("pet", this.handler.TriggerIdentifierMap.get("pet").get("_"));
                    dynamicPriorities.put("pet", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("pet")[0]));
                    dynamicFadeIn.put("pet", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("pet")[1]));
                    dynamicFadeOut.put("pet", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("pet")[35]));
                    dynamicDelay.put("pet", this.handler.TriggerInfoMap.get("pet")[4]);
                    triggerPersistence.put("pet", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("pet")[3]));
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("pet")[33])) timeSwitch.add("pet");
                    break;
                }
            }
            if (triggerPersistence.get("pet") != null && triggerPersistence.get("pet") > 0) {
                crashHelper = "pet";
                events.add("pet");
                dynamicSongs.put("pet", this.handler.TriggerIdentifierMap.get("pet").get("_"));
                dynamicPriorities.put("pet", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("pet")[0]));
                dynamicFadeIn.put("pet", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("pet")[1]));
                dynamicFadeOut.put("pet", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("pet")[35]));
                dynamicDelay.put("pet", this.handler.TriggerInfoMap.get("pet")[4]);
                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("pet")[33])) timeSwitch.add("pet");
            }
            if (this.handler.TriggerIdentifierMap.get("drowning") != null && player.getAirSupply() < MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("drowning")[2])) {
                this.boolMap.put("drowning", true);
                this.startMap.putIfAbsent("drowning", 0);
                if (this.startMap.get("drowning") > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("drowning")[8])) {
                    crashHelper = "drowning";
                    events.add("drowning");
                    dynamicSongs.put("drowning", this.handler.TriggerIdentifierMap.get("drowning").get("_"));
                    dynamicPriorities.put("drowning", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("drowning")[0]));
                    dynamicFadeIn.put("drowning", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("drowning")[1]));
                    dynamicFadeOut.put("drowning", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("drowning")[35]));
                    dynamicDelay.put("drowning", this.handler.TriggerInfoMap.get("drowning")[4]);
                    triggerPersistence.put("drowning", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("drowning")[3]));
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("drowning")[33]))
                        timeSwitch.add("drowning");
                }
            } else if (triggerPersistence.get("drowning") != null && triggerPersistence.get("drowning") > 0) {
                crashHelper = "drowning";
                events.add("drowning");
                dynamicSongs.put("drowning", this.handler.TriggerIdentifierMap.get("drowning").get("_"));
                dynamicPriorities.put("drowning", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("drowning")[0]));
                dynamicFadeIn.put("drowning", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("drowning")[1]));
                dynamicFadeOut.put("drowning", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("drowning")[35]));
                dynamicDelay.put("drowning", this.handler.TriggerInfoMap.get("drowning")[4]);
                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("drowning")[33])) timeSwitch.add("drowning");
            } else {
                this.boolMap.put("drowning", false);
                this.startMap.put("drowning", 0);
            }
            if (this.handler.TriggerIdentifierMap.get("pvp") != null && setPVP) {
                this.boolMap.put("pvp", true);
                this.startMap.putIfAbsent("pvp", 0);
                if (this.startMap.get("pvp") > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("pvp")[8])) {
                    crashHelper = "pvp";
                    events.add("pvp");
                    dynamicSongs.put("pvp", this.handler.TriggerIdentifierMap.get("pvp").get("_"));
                    dynamicPriorities.put("pvp", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("pvp")[0]));
                    dynamicFadeIn.put("pvp", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("pvp")[1]));
                    dynamicFadeOut.put("pvp", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("pvp")[35]));
                    dynamicDelay.put("pvp", this.handler.TriggerInfoMap.get("pvp")[4]);
                    triggerPersistence.put("pvp", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("pvp")[3]));
                    triggerPersistence.put("pvp-victory_timeout", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("pvp")[22]));
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("pvp")[33])) timeSwitch.add("pvp");
                    setPVP = false;
                    pvpVictoryID = MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("pvp")[17]);
                    victory.putIfAbsent(pvpVictoryID, false);
                }
            } else if (triggerPersistence.get("pvp") != null && triggerPersistence.get("pvp") > 0) {
                crashHelper = "pvp";
                events.add("pvp");
                dynamicSongs.put("pvp", this.handler.TriggerIdentifierMap.get("pvp").get("_"));
                dynamicPriorities.put("pvp", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("pvp")[0]));
                dynamicFadeIn.put("pvp", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("pvp")[1]));
                dynamicFadeOut.put("pvp", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("pvp")[35]));
                dynamicDelay.put("pvp", this.handler.TriggerInfoMap.get("pvp")[4]);
                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("pvp")[33])) timeSwitch.add("pvp");
            } else {
                this.boolMap.put("pvp", false);
                this.startMap.put("pvp", 0);
            }
            if (triggerPersistence.get("pvp") != null && EventsClient.PVPTracker != null && triggerPersistence.get("victory_timeout") <= 0) {
                EventsClient.PVPTracker = null;
            }
            if (EventsClient.PVPTracker != null && EventsClient.PVPTracker.isDeadOrDying()) {
                victory.put(pvpVictoryID, true);
                EventsClient.PVPTracker = null;
            }
            if (this.handler.TriggerIdentifierMap.get("home") != null) {
                crashHelper = "home";
                packet.addHomeTrigger(new ServerChannelData.Home(MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("home")[11])));
                if (this.channel.getSyncStatus().isHomeTriggerActive()) {
                    this.boolMap.put("home", true);
                    this.startMap.putIfAbsent("home", 0);
                    if (this.startMap.get("home") > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("home")[8])) {
                        events.add("home");
                        dynamicSongs.put("home", this.handler.TriggerIdentifierMap.get("home").get("_"));
                        dynamicPriorities.put("home", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("home")[0]));
                        dynamicFadeIn.put("home", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("home")[1]));
                        dynamicFadeOut.put("home", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("home")[35]));
                        dynamicDelay.put("home", this.handler.TriggerInfoMap.get("home")[4]);
                        triggerPersistence.put("home", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("home")[3]));
                        if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("home")[33])) timeSwitch.add("home");
                    }
                } else if (triggerPersistence.get("home") != null && triggerPersistence.get("home") > 0) {
                    crashHelper = "home";
                    events.add("home");
                    dynamicSongs.put("home", this.handler.TriggerIdentifierMap.get("home").get("_"));
                    dynamicPriorities.put("home", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("home")[0]));
                    dynamicFadeIn.put("home", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("home")[1]));
                    dynamicFadeOut.put("home", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("home")[35]));
                    dynamicDelay.put("home", this.handler.TriggerInfoMap.get("home")[4]);
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("home")[33])) timeSwitch.add("home");
                } else {
                    this.boolMap.put("home", false);
                    this.startMap.put("home", 0);
                }
            }
            if (this.handler.TriggerIdentifierMap.get("dimension") != null) {
                crashHelper = "dimension";
                for (String identifier : this.handler.TriggerIdentifierMap.get("dimension").keySet()) {
                    crashHelper = "dimension-" + identifier;
                    if (checkResourceList(player.level.dimension().location().toString(), this.handler.TriggerInfoMap.get("dimension-" + identifier)[9], false)) {
                        this.boolMap.put("dimension-" + identifier, true);
                        this.startMap.putIfAbsent("dimension-" + identifier, 0);
                        if (this.boolMap.get("dimension-" + identifier) && this.startMap.get("dimension-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("dimension-" + identifier)[8])) {
                            if (!events.contains("dimension-" + identifier)) {
                                events.add("dimension-" + identifier);
                                dynamicSongs.put("dimension-" + identifier, this.handler.TriggerIdentifierMap.get("dimension").get(identifier));
                                dynamicPriorities.put("dimension-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("dimension-" + identifier)[0]));
                                dynamicFadeIn.put("dimension-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("dimension-" + identifier)[1]));
                                dynamicFadeOut.put("dimension-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("dimension-" + identifier)[35]));
                                dynamicDelay.put("dimension-" + identifier, this.handler.TriggerInfoMap.get("dimension-" + identifier)[4]);
                                triggerPersistence.put("dimension-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("dimension-" + identifier)[3]));
                                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("dimension-" + identifier)[33]))
                                    timeSwitch.add("dimension-" + identifier);
                            }
                        }
                    } else if (triggerPersistence.get("dimension-" + identifier) != null && triggerPersistence.get("dimension-" + identifier) > 0) {
                        if (!events.contains("dimension-" + identifier)) {
                            events.add("dimension-" + identifier);
                            dynamicSongs.put("dimension-" + identifier, this.handler.TriggerIdentifierMap.get("dimension").get(identifier));
                            dynamicPriorities.put("dimension-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("dimension-" + identifier)[0]));
                            dynamicFadeIn.put("dimension-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("dimension-" + identifier)[1]));
                            dynamicFadeOut.put("dimension-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("dimension-" + identifier)[35]));
                            dynamicDelay.put("dimension-" + identifier, this.handler.TriggerInfoMap.get("dimension-" + identifier)[4]);
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("dimension-" + identifier)[33]))
                                timeSwitch.add("dimension-" + identifier);
                        }
                    } else {
                        this.boolMap.put("dimension-" + identifier, false);
                        this.startMap.put("dimension-" + identifier, 0);
                    }
                }
            }
            if (this.handler.TriggerIdentifierMap.get("biome") != null) {
                crashHelper = "biome";
                for (String identifier : this.handler.TriggerIdentifierMap.get("biome").keySet()) {
                    crashHelper = "biome-" + identifier;
                    packet.addBiomeTrigger(new ServerChannelData.Biome("biome-" + identifier, this.handler.TriggerInfoMap.get("biome-" + identifier)[9],
                            this.handler.TriggerInfoMap.get("biome-" + identifier)[23], this.handler.TriggerInfoMap.get("biome-" + identifier)[24],
                            Float.parseFloat(this.handler.TriggerInfoMap.get("biome-" + identifier)[25]), Boolean.parseBoolean(this.handler.TriggerInfoMap.get("biome-" + identifier)[26]),
                            Float.parseFloat(this.handler.TriggerInfoMap.get("biome-" + identifier)[30]), Boolean.parseBoolean(this.handler.TriggerInfoMap.get("biome-" + identifier)[31])));
                    if (this.channel.getSyncStatus().isBiomeTriggerActive("biome-" + identifier)) {
                        this.boolMap.put("biome-" + identifier, true);
                        this.startMap.putIfAbsent("biome-" + identifier,0);
                        if (this.boolMap.get("biome-" + identifier) && this.startMap.get("biome-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("biome-" + identifier)[8])) {
                            if (!events.contains("biome-" + identifier)) {
                                events.add("biome-" + identifier);
                                dynamicSongs.put("biome-" + identifier, this.handler.TriggerIdentifierMap.get("biome").get(identifier));
                                dynamicPriorities.put("biome-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("biome-" + identifier)[0]));
                                dynamicFadeIn.put("biome-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("biome-" + identifier)[1]));
                                dynamicFadeOut.put("biome-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("biome-" + identifier)[35]));
                                dynamicDelay.put("biome-" + identifier, this.handler.TriggerInfoMap.get("biome-" + identifier)[4]);
                                triggerPersistence.put("biome-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("biome-" + identifier)[3]));
                                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("biome-" + identifier)[33]))
                                    timeSwitch.add("biome-" + identifier);
                            }
                        }
                    } else if (triggerPersistence.get("biome-" + identifier) != null && triggerPersistence.get("biome-" + identifier) > 0) {
                        if (!events.contains("biome-" + identifier)) {
                            events.add("biome-" + identifier);
                            dynamicSongs.put("biome-" + identifier, this.handler.TriggerIdentifierMap.get("biome").get(identifier));
                            dynamicPriorities.put("biome-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("biome-" + identifier)[0]));
                            dynamicFadeIn.put("biome-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("biome-" + identifier)[1]));
                            dynamicFadeOut.put("biome-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("biome-" + identifier)[35]));
                            dynamicDelay.put("biome-" + identifier, this.handler.TriggerInfoMap.get("biome-" + identifier)[4]);
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("biome-" + identifier)[33]))
                                timeSwitch.add("biome-" + identifier);
                        }
                    } else {
                        this.boolMap.put("biome-" + identifier, false);
                        this.startMap.put("biome-" + identifier, 0);
                    }
                }
            }
            if (!ConfigRegistry.clientSideOnly && this.handler.TriggerIdentifierMap.get("structure") != null) {
                crashHelper = "structure";
                for (String identifier : this.handler.TriggerIdentifierMap.get("structure").keySet()) {
                    crashHelper = "structure-" + identifier;
                    packet.addStructureTrigger(new ServerChannelData.Structure("structure-" + identifier, this.handler.TriggerInfoMap.get("structure-" + identifier)[9]));
                    if (this.channel.getSyncStatus().isStructureTriggerActive("structure-" + identifier)) {
                        this.boolMap.put("structure-" + identifier, true);
                        this.startMap.putIfAbsent("structure-" + identifier,0);
                        if (this.boolMap.get("structure-" + identifier) && this.startMap.get("structure-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("structure-" + identifier)[8])) {
                            if (!events.contains("structure-" + identifier)) {
                                events.add("structure-" + identifier);
                                dynamicSongs.put("structure-" + identifier, this.handler.TriggerIdentifierMap.get("structure").get(identifier));
                                dynamicPriorities.put("structure-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("structure-" + identifier)[0]));
                                dynamicFadeIn.put("structure-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("structure-" + identifier)[1]));
                                dynamicFadeOut.put("structure-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("structure-" + identifier)[35]));
                                dynamicDelay.put("structure-" + identifier, this.handler.TriggerInfoMap.get("structure-" + identifier)[4]);
                                triggerPersistence.put("structure-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("structure-" + identifier)[3]));
                                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("structure-" + identifier)[33]))
                                    timeSwitch.add("structure-" + identifier);
                            }
                        }
                    } else if (triggerPersistence.get("structure-" + identifier) != null && triggerPersistence.get("structure-" + identifier) > 0) {
                        if (!events.contains("structure-" + identifier)) {
                            events.add("structure-" + identifier);
                            dynamicSongs.put("structure-" + identifier, this.handler.TriggerIdentifierMap.get("structure").get(identifier));
                            dynamicPriorities.put("structure-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("structure-" + identifier)[0]));
                            dynamicFadeIn.put("structure-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("structure-" + identifier)[1]));
                            dynamicFadeOut.put("structure-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("structure-" + identifier)[35]));
                            dynamicDelay.put("structure-" + identifier, this.handler.TriggerInfoMap.get("structure-" + identifier)[4]);
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("structure-" + identifier)[33]))
                                timeSwitch.add("structure-" + identifier);
                        }
                    } else {
                        this.boolMap.put("structure-" + identifier, false);
                        this.startMap.put("structure-" + identifier, 0);
                    }
                }
            }
            if (this.handler.TriggerIdentifierMap.get("mob") != null && !ConfigRegistry.clientSideOnly) {
                crashHelper = "mob";
                for (String identifier : this.handler.TriggerIdentifierMap.get("mob").keySet()) {
                    crashHelper = "mob-" + identifier;
                    packet.addMobTrigger(new ServerChannelData.Mob("mob-" + identifier, this.handler.TriggerInfoMap.get("mob-" + identifier)[9],
                            MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("mob-" + identifier)[11]), Boolean.parseBoolean(this.handler.TriggerInfoMap.get("mob-" + identifier)[12]),
                            MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("mob-" + identifier)[13]), MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("mob-" + identifier)[14]),
                            MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("mob-" + identifier)[15]), Boolean.parseBoolean(this.handler.TriggerInfoMap.get("mob-" + identifier)[16]),
                            MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("mob-" + identifier)[17]), this.handler.TriggerInfoMap.get("mob-" + identifier)[18],
                            MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("mob-" + identifier)[2]), MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("mob-" + identifier)[22]),
                            this.handler.TriggerInfoMap.get("mob-" + identifier)[27], this.handler.TriggerInfoMap.get("mob-" + identifier)[36]));
                    if (this.channel.getSyncStatus().isMobTriggerActive("mob-" + identifier)) {
                        this.boolMap.put("mob-" + identifier, true);
                        this.startMap.putIfAbsent("mob-" + identifier, 0);
                        if (this.boolMap.get("mob-" + identifier) && this.startMap.get("mob-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("mob-" + identifier)[8])) {
                            if (!events.contains("mob-" + identifier)) {
                                events.add("mob-" + identifier);
                                dynamicSongs.put("mob-" + identifier, this.handler.TriggerIdentifierMap.get("mob").get(identifier));
                                dynamicPriorities.put("mob-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("mob-" + identifier)[0]));
                                dynamicFadeIn.put("mob-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("mob-" + identifier)[1]));
                                dynamicFadeOut.put("mob-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("mob-" + identifier)[35]));
                                dynamicDelay.put("mob-" + identifier, this.handler.TriggerInfoMap.get("mob-" + identifier)[4]);
                                triggerPersistence.put("mob-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("mob-" + identifier)[3]));
                                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("mob-" + identifier)[33]))
                                    timeSwitch.add("mob-" + identifier);
                            }
                        }
                    } else if (triggerPersistence.get("mob-" + identifier) != null && triggerPersistence.get("mob-" + identifier) > 0) {
                        if (!events.contains("mob-" + identifier)) {
                            events.add("mob-" + identifier);
                            dynamicSongs.put("mob-" + identifier, this.handler.TriggerIdentifierMap.get("mob").get(identifier));
                            dynamicPriorities.put("mob-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("mob-" + identifier)[0]));
                            dynamicFadeIn.put("mob-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("mob-" + identifier)[1]));
                            dynamicFadeOut.put("mob-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("mob-" + identifier)[35]));
                            dynamicDelay.put("mob-" + identifier, this.handler.TriggerInfoMap.get("mob-" + identifier)[4]);
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("mob-" + identifier)[33]))
                                timeSwitch.add("mob-" + identifier);
                        }
                    } else {
                        this.boolMap.put("mob-" + identifier, false);
                        this.startMap.put("mob-" + identifier, 0);
                    }
                    victory.put(MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("mob-" + identifier)[17]), this.channel.getSyncStatus().getVictoryStatusForMobTrigger("mob-" + identifier));
                }
            }
            if (this.handler.TriggerIdentifierMap.get("zones") != null) {
                crashHelper = "zones";
                for (String identifier : this.handler.TriggerIdentifierMap.get("zones").keySet()) {
                    crashHelper = "zones-" + identifier;
                    String[] broken = stringBreaker(this.handler.TriggerInfoMap.get("zones-" + identifier)[7], ",");
                    BlockPos bp = player.blockPosition();
                    int x1 = MusicTriggers.randomInt(broken[0]);
                    int y1 = MusicTriggers.randomInt(broken[1]);
                    int z1 = MusicTriggers.randomInt(broken[2]);
                    int x2 = MusicTriggers.randomInt(broken[3]);
                    int y2 = MusicTriggers.randomInt(broken[4]);
                    int z2 = MusicTriggers.randomInt(broken[5]);
                    if (bp.getX() > x1 && bp.getX() < x2 && bp.getY() > y1 && bp.getY() < y2 && bp.getZ() > z1 && bp.getZ() < z2) {
                        this.boolMap.put("zones-" + identifier, true);
                        this.startMap.putIfAbsent("zones-" + identifier,0);
                        if (this.boolMap.get("zones-" + identifier) && this.startMap.get("zones-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("zones-" + identifier)[8])) {
                            if (!events.contains("zones-" + identifier)) {
                                events.add("zones-" + identifier);
                                dynamicSongs.put("zones-" + identifier, this.handler.TriggerIdentifierMap.get("zones").get(identifier));
                                dynamicPriorities.put("zones-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("zones-" + identifier)[0]));
                                dynamicFadeIn.put("zones-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("zones-" + identifier)[1]));
                                dynamicFadeOut.put("zones-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("zones-" + identifier)[35]));
                                dynamicDelay.put("zones-" + identifier, this.handler.TriggerInfoMap.get("zones-" + identifier)[4]);
                                triggerPersistence.put("zones-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("zones-" + identifier)[3]));
                                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("zones-" + identifier)[33]))
                                    timeSwitch.add("zones-" + identifier);
                            }
                        }
                    } else if (triggerPersistence.get("zones-" + identifier) != null && triggerPersistence.get("zones-" + identifier) > 0) {
                        if (!events.contains("zones-" + identifier)) {
                            events.add("zones-" + identifier);
                            dynamicSongs.put("zones-" + identifier, this.handler.TriggerIdentifierMap.get("zones").get(identifier));
                            dynamicPriorities.put("zones-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("zones-" + identifier)[0]));
                            dynamicFadeIn.put("zones-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("zones-" + identifier)[1]));
                            dynamicFadeOut.put("zones-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("zones-" + identifier)[35]));
                            dynamicDelay.put("zones-" + identifier, this.handler.TriggerInfoMap.get("zones-" + identifier)[4]);
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("zones-" + identifier)[33]))
                                timeSwitch.add("zones-" + identifier);
                        }
                    } else {
                        this.boolMap.put("zones-" + identifier, false);
                        this.startMap.put("zones-" + identifier, 0);
                    }
                }
            }
            if (this.handler.TriggerIdentifierMap.get("effect") != null) {
                crashHelper = "effect";
                for (String identifier : this.handler.TriggerIdentifierMap.get("effect").keySet()) {
                    crashHelper = "effect-" + identifier;
                    effectList.clear();
                    for (Effect p : player.getActiveEffectsMap().keySet()) {
                        if (p.getRegistryName() != null) effectList.add(p.getRegistryName().toString());
                        if (checkResourceList(p.getRegistryName().toString(), this.handler.TriggerInfoMap.get("effect-" + identifier)[9], false)) {
                            this.boolMap.put("effect-" + identifier, true);
                            this.startMap.putIfAbsent("effect-" + identifier, 0);
                            if (this.boolMap.get("effect-" + identifier) && this.startMap.get("effect-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("effect-" + identifier)[8])) {
                                if (!events.contains("effect-" + identifier)) {
                                    events.add("effect-" + identifier);
                                    dynamicSongs.put("effect-" + identifier, this.handler.TriggerIdentifierMap.get("effect").get(identifier));
                                    dynamicPriorities.put("effect-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("effect-" + identifier)[0]));
                                    dynamicFadeIn.put("effect-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("effect-" + identifier)[1]));
                                    dynamicFadeOut.put("effect-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("effect-" + identifier)[35]));
                                    dynamicDelay.put("effect-" + identifier, this.handler.TriggerInfoMap.get("effect-" + identifier)[4]);
                                    triggerPersistence.put("effect-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("effect-" + identifier)[3]));
                                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("effect-" + identifier)[33]))
                                        timeSwitch.add("effect-" + identifier);
                                }
                            }
                        } else if (triggerPersistence.get("effect-" + identifier) != null && triggerPersistence.get("effect-" + identifier) > 0) {
                            if (!events.contains("effect-" + identifier)) {
                                events.add("effect-" + identifier);
                                dynamicSongs.put("effect-" + identifier, this.handler.TriggerIdentifierMap.get("effect").get(identifier));
                                dynamicPriorities.put("effect-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("effect-" + identifier)[0]));
                                dynamicFadeIn.put("effect-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("effect-" + identifier)[1]));
                                dynamicFadeOut.put("effect-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("effect-" + identifier)[35]));
                                dynamicDelay.put("effect-" + identifier, this.handler.TriggerInfoMap.get("effect-" + identifier)[4]);
                                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("effect-" + identifier)[33]))
                                    timeSwitch.add("effect-" + identifier);
                            }
                        } else {
                            this.boolMap.put("effect-" + identifier, false);
                            this.startMap.put("effect-" + identifier, 0);
                        }
                    }
                }
            }
            if (this.handler.TriggerIdentifierMap.get("victory") != null) {
                crashHelper = "victory";
                for (String identifier : this.handler.TriggerIdentifierMap.get("victory").keySet()) {
                    crashHelper = "victory-" + identifier;
                    int id = MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("victory-" + identifier)[17]);
                    if (victory.get(id) != null && victory.get(id)) {
                        this.boolMap.put("victory-" + identifier, true);
                        this.startMap.putIfAbsent("victory-" + identifier, 0);
                        if (this.boolMap.get("victory-" + identifier) && this.startMap.get("victory-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("victory-" + identifier)[8])) {
                            if (!events.contains("victory-" + identifier)) {
                                events.add("victory-" + identifier);
                                dynamicSongs.put("victory-" + identifier, this.handler.TriggerIdentifierMap.get("victory").get(identifier));
                                dynamicPriorities.put("victory-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("victory-" + identifier)[0]));
                                dynamicFadeIn.put("victory-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("victory-" + identifier)[1]));
                                dynamicFadeOut.put("victory-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("victory-" + identifier)[35]));
                                dynamicDelay.put("victory-" + identifier, this.handler.TriggerInfoMap.get("victory-" + identifier)[4]);
                                triggerPersistence.put("victory-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("victory-" + identifier)[3]));
                                victory.put(MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("victory-" + identifier)[17]), false);
                                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("victory-" + identifier)[33]))
                                    timeSwitch.add("victory-" + identifier);
                            }
                        }
                    } else if (triggerPersistence.get("victory-" + identifier) != null && triggerPersistence.get("victory-" + identifier) > 0) {
                        if (!events.contains("victory-" + identifier)) {
                            events.add("victory-" + identifier);
                            dynamicSongs.put("victory-" + identifier, this.handler.TriggerIdentifierMap.get("victory").get(identifier));
                            dynamicPriorities.put("victory-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("victory-" + identifier)[0]));
                            dynamicFadeIn.put("victory-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("victory-" + identifier)[1]));
                            dynamicFadeOut.put("victory-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("victory-" + identifier)[35]));
                            dynamicDelay.put("victory-" + identifier, this.handler.TriggerInfoMap.get("victory-" + identifier)[4]);
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("victory-" + identifier)[33]))
                                timeSwitch.add("victory-" + identifier);
                        }
                    } else {
                        this.boolMap.put("victory-" + identifier, false);
                        this.startMap.put("victory-" + identifier, 0);
                    }
                }
            }
            if (this.handler.TriggerIdentifierMap.get("gui") != null) {
                crashHelper = "gui";
                for (String identifier : this.handler.TriggerIdentifierMap.get("gui").keySet()) {
                    crashHelper = "gui-" + identifier;
                    if (mc.screen != null && checkResourceList(mc.screen.getClass().getName(), this.handler.TriggerInfoMap.get("gui-" + identifier)[9], false)) {
                        this.boolMap.put("gui-" + identifier, true);
                        this.startMap.putIfAbsent("gui-" + identifier, 0);
                        if (this.boolMap.get("gui-" + identifier) && this.startMap.get("gui-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gui-" + identifier)[8])) {
                            if (!events.contains("gui-" + identifier)) {
                                events.add("gui-" + identifier);
                                dynamicSongs.put("gui-" + identifier, this.handler.TriggerIdentifierMap.get("gui").get(identifier));
                                dynamicPriorities.put("gui-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gui-" + identifier)[0]));
                                dynamicFadeIn.put("gui-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gui-" + identifier)[1]));
                                dynamicFadeOut.put("gui-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gui-" + identifier)[35]));
                                dynamicDelay.put("gui-" + identifier, this.handler.TriggerInfoMap.get("gui-" + identifier)[4]);
                                triggerPersistence.put("gui-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gui-" + identifier)[3]));
                                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("gui-" + identifier)[33]))
                                    timeSwitch.add("gui-" + identifier);
                            }
                        }
                    } else if (this.handler.TriggerInfoMap.get("gui-" + identifier)[9].matches("CREDITS") && mc.screen instanceof WinGameScreen) {
                        if (!events.contains("gui-" + identifier)) {
                            events.add("gui-" + identifier);
                            dynamicSongs.put("gui-" + identifier, this.handler.TriggerIdentifierMap.get("gui").get(identifier));
                            dynamicPriorities.put("gui-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gui-" + identifier)[0]));
                            dynamicFadeIn.put("gui-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gui-" + identifier)[1]));
                            dynamicFadeOut.put("gui-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gui-" + identifier)[35]));
                            dynamicDelay.put("gui-" + identifier, this.handler.TriggerInfoMap.get("gui-" + identifier)[4]);
                            triggerPersistence.put("gui-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gui-" + identifier)[3]));
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("gui-" + identifier)[33]))
                                timeSwitch.add("gui-" + identifier);
                        }
                    } else if (triggerPersistence.get("gui-" + identifier) != null && triggerPersistence.get("gui-" + identifier) > 0) {
                        if (!events.contains("gui-" + identifier)) {
                            events.add("gui-" + identifier);
                            dynamicSongs.put("gui-" + identifier, this.handler.TriggerIdentifierMap.get("gui").get(identifier));
                            dynamicPriorities.put("gui-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gui-" + identifier)[0]));
                            dynamicFadeIn.put("gui-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gui-" + identifier)[1]));
                            dynamicFadeOut.put("gui-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gui-" + identifier)[35]));
                            dynamicDelay.put("gui-" + identifier, this.handler.TriggerInfoMap.get("gui-" + identifier)[4]);
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("gui-" + identifier)[33]))
                                timeSwitch.add("gui-" + identifier);
                        }
                    } else {
                        this.boolMap.put("gui-" + identifier, false);
                        this.startMap.put("gui-" + identifier, 0);
                    }
                }
            }
            if (this.handler.TriggerIdentifierMap.get("difficulty") != null) {
                crashHelper = "difficulty";
                for (String identifier : this.handler.TriggerIdentifierMap.get("difficulty").keySet()) {
                    crashHelper = "difficulty-" + identifier;
                    int diffID = MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[2]);
                    if (diffID == 4 && world.getLevelData().isHardcore()) {
                        if (!events.contains("difficulty-" + identifier)) {
                            events.add("difficulty-" + identifier);
                            dynamicSongs.put("difficulty-" + identifier, this.handler.TriggerIdentifierMap.get("difficulty").get(identifier));
                            dynamicPriorities.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[0]));
                            dynamicFadeIn.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[1]));
                            dynamicFadeOut.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[35]));
                            dynamicDelay.put("difficulty-" + identifier, this.handler.TriggerInfoMap.get("difficulty-" + identifier)[4]);
                            triggerPersistence.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[3]));
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[33]))
                                timeSwitch.add("difficulty-" + identifier);
                        }
                    } else if (diffID == 3 && world.getDifficulty() == Difficulty.HARD) {
                        if (!events.contains("difficulty-" + identifier)) {
                            events.add("difficulty-" + identifier);
                            dynamicSongs.put("difficulty-" + identifier, this.handler.TriggerIdentifierMap.get("difficulty").get(identifier));
                            dynamicPriorities.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[0]));
                            dynamicFadeIn.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[1]));
                            dynamicFadeOut.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[35]));
                            dynamicDelay.put("difficulty-" + identifier, this.handler.TriggerInfoMap.get("difficulty-" + identifier)[4]);
                            triggerPersistence.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[3]));
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[33]))
                                timeSwitch.add("difficulty-" + identifier);
                        }
                    } else if (diffID == 2 && world.getDifficulty() == Difficulty.NORMAL) {
                        if (!events.contains("difficulty-" + identifier)) {
                            events.add("difficulty-" + identifier);
                            dynamicSongs.put("difficulty-" + identifier, this.handler.TriggerIdentifierMap.get("difficulty").get(identifier));
                            dynamicPriorities.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[0]));
                            dynamicFadeIn.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[1]));
                            dynamicFadeOut.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[35]));
                            dynamicDelay.put("difficulty-" + identifier, this.handler.TriggerInfoMap.get("difficulty-" + identifier)[4]);
                            triggerPersistence.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[3]));
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[33]))
                                timeSwitch.add("difficulty-" + identifier);
                        }
                    } else if (diffID == 1 && world.getDifficulty() == Difficulty.EASY) {
                        if (!events.contains("difficulty-" + identifier)) {
                            events.add("difficulty-" + identifier);
                            dynamicSongs.put("difficulty-" + identifier, this.handler.TriggerIdentifierMap.get("difficulty").get(identifier));
                            dynamicPriorities.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[0]));
                            dynamicFadeIn.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[1]));
                            dynamicFadeOut.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[35]));
                            dynamicDelay.put("difficulty-" + identifier, this.handler.TriggerInfoMap.get("difficulty-" + identifier)[4]);
                            triggerPersistence.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[3]));
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[33]))
                                timeSwitch.add("difficulty-" + identifier);
                        }
                    } else if (diffID == 0 && world.getDifficulty() == Difficulty.PEACEFUL) {
                        if (!events.contains("difficulty-" + identifier)) {
                            events.add("difficulty-" + identifier);
                            dynamicSongs.put("difficulty-" + identifier, this.handler.TriggerIdentifierMap.get("difficulty").get(identifier));
                            dynamicPriorities.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[0]));
                            dynamicFadeIn.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[1]));
                            dynamicFadeOut.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[35]));
                            dynamicDelay.put("difficulty-" + identifier, this.handler.TriggerInfoMap.get("difficulty-" + identifier)[4]);
                            triggerPersistence.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[3]));
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[33]))
                                timeSwitch.add("difficulty-" + identifier);
                        }
                    } else if (triggerPersistence.get("difficulty-" + identifier) != null && triggerPersistence.get("difficulty-" + identifier) > 0) {
                        if (!events.contains("difficulty-" + identifier)) {
                            events.add("difficulty-" + identifier);
                            dynamicSongs.put("difficulty-" + identifier, this.handler.TriggerIdentifierMap.get("difficulty").get(identifier));
                            dynamicPriorities.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[0]));
                            dynamicFadeIn.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[1]));
                            dynamicFadeOut.put("difficulty-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[35]));
                            dynamicDelay.put("difficulty-" + identifier, this.handler.TriggerInfoMap.get("difficulty-" + identifier)[4]);
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("difficulty-" + identifier)[33]))
                                timeSwitch.add("difficulty-" + identifier);
                        }
                    }
                }
            }
            if (this.handler.TriggerIdentifierMap.get("advancement") != null) {
                crashHelper = "advancement";
                for (String identifier : this.handler.TriggerIdentifierMap.get("advancement").keySet()) {
                    crashHelper = "advancement-" + identifier;
                    if (EventsClient.advancement && (EventsClient.lastAdvancement.contains(this.handler.TriggerInfoMap.get("advancement-" + identifier)[5]) || this.handler.TriggerInfoMap.get("advancement-" + identifier)[5].matches("YouWillNeverGuessThis"))) {
                        this.boolMap.put("advancement-" + identifier, true);
                        this.startMap.putIfAbsent("advancement-" + identifier,0);
                        if (this.boolMap.get("advancement-" + identifier) && this.startMap.get("advancement-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("advancement-" + identifier)[8])) {
                            EventsClient.advancement = false;
                            if (!events.contains("advancement-" + identifier)) {
                                events.add("advancement-" + identifier);
                                dynamicSongs.put("advancement-" + identifier, this.handler.TriggerIdentifierMap.get("advancement").get(identifier));
                                dynamicPriorities.put("advancement-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("advancement-" + identifier)[0]));
                                dynamicFadeIn.put("advancement-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("advancement-" + identifier)[1]));
                                dynamicFadeOut.put("advancement-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("advancement-" + identifier)[35]));
                                dynamicDelay.put("advancement-" + identifier, this.handler.TriggerInfoMap.get("advancement-" + identifier)[4]);
                                triggerPersistence.put("advancement-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("advancement-" + identifier)[3]));
                                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("advancement-" + identifier)[33]))
                                    timeSwitch.add("advancement-" + identifier);
                            }
                        }
                    } else if (triggerPersistence.get("advancement-" + identifier) != null && triggerPersistence.get("advancement-" + identifier) > 0) {
                        if (!events.contains("advancement-" + identifier)) {
                            events.add("advancement-" + identifier);
                            dynamicSongs.put("advancement-" + identifier, this.handler.TriggerIdentifierMap.get("advancement").get(identifier));
                            dynamicPriorities.put("advancement-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("advancement-" + identifier)[0]));
                            dynamicFadeIn.put("advancement-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("advancement-" + identifier)[1]));
                            dynamicFadeOut.put("advancement-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("advancement-" + identifier)[35]));
                            dynamicDelay.put("advancement-" + identifier, this.handler.TriggerInfoMap.get("advancement-" + identifier)[4]);
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("advancement-" + identifier)[33]))
                                timeSwitch.add("advancement-" + identifier);
                        }
                    } else {
                        this.boolMap.put("advancement-" + identifier, false);
                        this.startMap.put("advancement-" + identifier, 0);
                    }
                }
            }
            if (!ConfigRegistry.clientSideOnly && this.handler.TriggerIdentifierMap.get("raid") != null) {
                crashHelper = "raid";
                for (String identifier : this.handler.TriggerIdentifierMap.get("raid").keySet()) {
                    crashHelper = "raid-" + identifier;
                    packet.addRaidTrigger(new ServerChannelData.Raid("raid-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("raid-" + identifier)[2])));
                    if (this.channel.getSyncStatus().isRaidTriggerActive("raid-" + identifier)) {
                        this.boolMap.put("raid-" + identifier, true);
                        this.startMap.putIfAbsent("raid-" + identifier, 0);
                        if (this.boolMap.get("raid-" + identifier) && this.startMap.get("raid-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("raid-" + identifier)[8])) {
                            if (!events.contains("raid-" + identifier)) {
                                events.add("raid-" + identifier);
                                dynamicSongs.put("raid-" + identifier, this.handler.TriggerIdentifierMap.get("raid").get(identifier));
                                dynamicPriorities.put("raid-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("raid-" + identifier)[0]));
                                dynamicFadeIn.put("raid-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("raid-" + identifier)[1]));
                                dynamicFadeOut.put("raid-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("raid-" + identifier)[35]));
                                dynamicDelay.put("raid-" + identifier, this.handler.TriggerInfoMap.get("raid-" + identifier)[4]);
                                triggerPersistence.put("raid-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("raid-" + identifier)[3]));
                                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("raid-" + identifier)[33]))
                                    timeSwitch.add("raid-" + identifier);
                            }
                        }
                    } else if (triggerPersistence.get("raid-" + identifier) != null && triggerPersistence.get("raid-" + identifier) > 0) {
                        if (!events.contains("raid-" + identifier)) {
                            events.add("raid-" + identifier);
                            dynamicSongs.put("raid-" + identifier, this.handler.TriggerIdentifierMap.get("raid").get(identifier));
                            dynamicPriorities.put("raid-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("raid-" + identifier)[0]));
                            dynamicFadeIn.put("raid-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("raid-" + identifier)[1]));
                            dynamicFadeOut.put("raid-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("raid-" + identifier)[35]));
                            dynamicDelay.put("raid-" + identifier, this.handler.TriggerInfoMap.get("raid-" + identifier)[4]);
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("raid-" + identifier)[33]))
                                timeSwitch.add("raid-" + identifier);
                        }
                    } else {
                        this.boolMap.put("raid-" + identifier, false);
                        this.startMap.put("advancement-" + identifier, 0);
                    }
                }
            }
            if (this.handler.TriggerIdentifierMap.get("statistic") != null) {
                crashHelper = "statistic";
                for (String identifier : this.handler.TriggerIdentifierMap.get("statistic").keySet()) {
                    crashHelper = "statistic-" + identifier;
                    if (checkStat(this.handler.TriggerInfoMap.get("statistic-" + identifier)[9], MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("statistic-" + identifier)[2]))) {
                        this.boolMap.put("statistic-" + identifier, true);
                        this.startMap.putIfAbsent("statistic-" + identifier, 0);
                        if (this.boolMap.get("statistic-" + identifier) && this.startMap.get("statistic-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("statistic-" + identifier)[8])) {
                            if (!events.contains("statistic-" + identifier)) {
                                events.add("statistic-" + identifier);
                                dynamicSongs.put("statistic-" + identifier, this.handler.TriggerIdentifierMap.get("statistic").get(identifier));
                                dynamicPriorities.put("statistic-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("statistic-" + identifier)[0]));
                                dynamicFadeIn.put("statistic-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("statistic-" + identifier)[1]));
                                dynamicFadeOut.put("statistic-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("statistic-" + identifier)[35]));
                                dynamicDelay.put("statistic-" + identifier, this.handler.TriggerInfoMap.get("statistic-" + identifier)[4]);
                                triggerPersistence.put("statistic-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("statistic-" + identifier)[3]));
                                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("statistic-" + identifier)[33]))
                                    timeSwitch.add("statistic-" + identifier);
                            }
                        }
                    } else if (triggerPersistence.get("statistic-" + identifier) != null && triggerPersistence.get("statistic-" + identifier) > 0) {
                        if (!events.contains("statistic-" + identifier)) {
                            events.add("statistic-" + identifier);
                            dynamicSongs.put("statistic-" + identifier, this.handler.TriggerIdentifierMap.get("statistic").get(identifier));
                            dynamicPriorities.put("statistic-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("statistic-" + identifier)[0]));
                            dynamicFadeIn.put("statistic-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("statistic-" + identifier)[1]));
                            dynamicFadeOut.put("statistic-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("statistic-" + identifier)[35]));
                            dynamicDelay.put("statistic-" + identifier, this.handler.TriggerInfoMap.get("statistic-" + identifier)[4]);
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("statistic-" + identifier)[33]))
                                timeSwitch.add("statistic-" + identifier);
                        }
                    } else {
                        this.boolMap.put("statistic-" + identifier, false);
                        this.startMap.put("statistic-" + identifier, 0);
                    }
                }
            }
            if (this.handler.TriggerIdentifierMap.get("command") != null) {
                crashHelper = "command";
                for (String identifier : this.handler.TriggerIdentifierMap.get("command").keySet()) {
                    crashHelper = "command-" + identifier;
                    if (EventsClient.commandMap.containsKey(identifier) && EventsClient.commandMap.get(identifier)) {
                        this.boolMap.put("command-" + identifier, true);
                        this.startMap.putIfAbsent("command-" + identifier, 0);
                        if (this.boolMap.get("command-" + identifier) && this.startMap.get("command-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("command-" + identifier)[8])) {
                            if (!events.contains("command-" + identifier)) {
                                EventsClient.commandMap.put(identifier, false);
                                events.add("command-" + identifier);
                                dynamicSongs.put("command-" + identifier, this.handler.TriggerIdentifierMap.get("command").get(identifier));
                                dynamicPriorities.put("command-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("command-" + identifier)[0]));
                                dynamicFadeIn.put("command-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("command-" + identifier)[1]));
                                dynamicFadeOut.put("command-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("command-" + identifier)[35]));
                                dynamicDelay.put("command-" + identifier, this.handler.TriggerInfoMap.get("command-" + identifier)[4]);
                                triggerPersistence.put("command-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("command-" + identifier)[3]));
                                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("command-" + identifier)[33]))
                                    timeSwitch.add("command-" + identifier);
                            }
                        }
                    } else if (triggerPersistence.get("command-" + identifier) != null && triggerPersistence.get("command-" + identifier) > 0) {
                        if (!events.contains("command-" + identifier)) {
                            events.add("command-" + identifier);
                            dynamicSongs.put("command-" + identifier, this.handler.TriggerIdentifierMap.get("command").get(identifier));
                            dynamicPriorities.put("command-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("command-" + identifier)[0]));
                            dynamicFadeIn.put("command-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("command-" + identifier)[1]));
                            dynamicFadeOut.put("command-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("command-" + identifier)[35]));
                            dynamicDelay.put("command-" + identifier, this.handler.TriggerInfoMap.get("command-" + identifier)[4]);
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("command-" + identifier)[33]))
                                timeSwitch.add("command-" + identifier);
                        }
                    } else {
                        this.boolMap.put("command-" + identifier, false);
                        this.startMap.put("command-" + identifier, 0);
                    }
                }
            }
            List<String> stages = gamestages();
            if (!stages.isEmpty()) events.addAll(stages);
            if (bloodmoon()) events.add("bloodmoon");
            if (harvestmoon()) events.add("harvestmoon");
            if (bluemoon()) events.add("bluemoon");
            List<String> moon = moon();
            if (!moon.isEmpty()) events.addAll(moon);
            String dynamicrain = dynamicrain(events);
            if (dynamicrain != null && !events.contains(dynamicrain)) events.add(dynamicrain);
            if (acidrain()) events.add("acidrain");
            if (blizzard()) events.add("blizzard");
            if (cloudy()) events.add("cloudy");
            if (lightrain()) events.add("lightrain");
            List<String> seasons = seasons();
            if (!seasons.isEmpty()) events.addAll(seasons);
            events.removeIf(trigger -> !this.channel.getToggleStatusForTrigger(trigger));
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException("There was a problem with your " + crashHelper + " trigger! The error was " + e.getMessage() + " and was caught on line " + e.getStackTrace()[0].getLineNumber() + " in the class " + e.getStackTrace()[0]);
        }
        this.info.updatePlayableTriggers(events);
        savePlayable.clear();
        savePlayable.addAll(events);
        return events;
    }

    private List<String> gamestages() {
        List<String> events = new ArrayList<>();
        if (ModList.get().isLoaded("gamestages") && this.handler.TriggerIdentifierMap.get("gamestage")!=null) {
            crashHelper = "gamestages";
            for (String identifier : this.handler.TriggerIdentifierMap.get("gamestage").keySet()) {
                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[9])) {
                    if (GameStageHelper.hasStage(player, this.handler.TriggerInfoMap.get("gamestage-" + identifier)[19])) {
                        this.boolMap.put("gamestage-" + identifier,true);
					    this.startMap.putIfAbsent("gamestage-" + identifier,0);
                        if (this.boolMap.get("gamestage-" + identifier) && this.startMap.get("gamestage-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[8])) {
                            if (!events.contains("gamestage-" + identifier)) {
                                events.add("gamestage-" + identifier);
                                dynamicSongs.put("gamestage-" + identifier, this.handler.TriggerIdentifierMap.get("gamestage").get(identifier));
                                dynamicPriorities.put("gamestage-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[0]));
                                dynamicFadeIn.put("gamestage-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[1]));
                                dynamicFadeOut.put("gamestage-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[35]));
                                dynamicDelay.put("gamestage-" + identifier, this.handler.TriggerInfoMap.get("gamestage-" + identifier)[4]);
                                triggerPersistence.put("gamestage-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[3]));
                                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[33]))
                                    timeSwitch.add("gamestage-" + identifier);
                            }
                        }
                    } else if (triggerPersistence.get("gamestage-" + identifier) != null && triggerPersistence.get("gamestage-" + identifier) > 0) {
                        if(!events.contains("gamestage-" + identifier)) {
                            events.add("gamestage-" + identifier);
                            dynamicSongs.put("gamestage-" + identifier, this.handler.TriggerIdentifierMap.get("gamestage").get(identifier));
                            dynamicPriorities.put("gamestage-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[0]));
                            dynamicFadeIn.put("gamestage-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[1]));
                            dynamicFadeOut.put("gamestage-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[35]));
                            dynamicDelay.put("gamestage-" + identifier, this.handler.TriggerInfoMap.get("gamestage-" + identifier)[4]);
                            if(Boolean.parseBoolean(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[33])) timeSwitch.add("gamestage-" + identifier);
                        }
                    } else {
					this.boolMap.put("gamestage-" + identifier,false);
					this.startMap.put("gamestage-" + identifier,0);
				}
                } else {
                    if (!GameStageHelper.hasStage(player, this.handler.TriggerInfoMap.get("gamestage-" + identifier)[19])) {
                        this.boolMap.put("gamestage-" + identifier,true);
					    this.startMap.putIfAbsent("gamestage-" + identifier,0);
                        if (this.boolMap.get("gamestage-" + identifier) && this.startMap.get("gamestage-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[8])) {
                            if (!events.contains("gamestage-" + identifier)) {
                                events.add("gamestage-" + identifier);
                                dynamicSongs.put("gamestage-" + identifier, this.handler.TriggerIdentifierMap.get("gamestage").get(identifier));
                                dynamicPriorities.put("gamestage-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[0]));
                                dynamicFadeIn.put("gamestage-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[1]));
                                dynamicFadeOut.put("gamestage-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[35]));
                                dynamicDelay.put("gamestage-" + identifier, this.handler.TriggerInfoMap.get("gamestage-" + identifier)[4]);
                                triggerPersistence.put("gamestage-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[3]));
                                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[33]))
                                    timeSwitch.add("gamestage-" + identifier);
                            }
                        }
                    } else if (triggerPersistence.get("gamestage-" + identifier) != null && triggerPersistence.get("gamestage-" + identifier) > 0) {
                        if(!events.contains("gamestage-" + identifier)) {
                            events.add("gamestage-" + identifier);
                            dynamicSongs.put("gamestage-" + identifier, this.handler.TriggerIdentifierMap.get("gamestage").get(identifier));
                            dynamicPriorities.put("gamestage-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[0]));
                            dynamicFadeIn.put("gamestage-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[1]));
                            dynamicFadeOut.put("gamestage-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[35]));
                            dynamicDelay.put("gamestage-" + identifier, this.handler.TriggerInfoMap.get("gamestage-" + identifier)[4]);
                            if(Boolean.parseBoolean(this.handler.TriggerInfoMap.get("gamestage-" + identifier)[33])) timeSwitch.add("gamestage-" + identifier);
                        }
                    } else {
					this.boolMap.put("gamestage-" + identifier,false);
					this.startMap.put("gamestage-" + identifier,0);
				}
                }
            }
        }
        return events;
    }

    private boolean bloodmoon() {
        if (ModList.get().isLoaded("enhancedcelestials") && this.handler.TriggerIdentifierMap.get("bloodmoon")!=null) {
            crashHelper = "bloodmoon";
            LunarContext lunarContext = ((EnhancedCelestialsWorldData) world).getLunarContext();
            if (lunarContext != null && lunarContext.getCurrentEvent() instanceof BloodMoon) {
                this.boolMap.put("bloodmoon",true);
                this.startMap.putIfAbsent("bloodmoon" ,0);
                if (this.boolMap.get("bloodmoon") && this.startMap.get("bloodmoon") > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("bloodmoon")[8])) {
                    dynamicSongs.put("bloodmoon", this.handler.TriggerIdentifierMap.get("bloodmoon").get("_"));
                    dynamicPriorities.put("bloodmoon", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("bloodmoon")[0]));
                    dynamicFadeIn.put("bloodmoon", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("bloodmoon")[1]));
                    dynamicFadeOut.put("bloodmoon", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("bloodmoon")[35]));
                    dynamicDelay.put("bloodmoon", this.handler.TriggerInfoMap.get("bloodmoon")[4]);
                    triggerPersistence.put("bloodmoon", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("bloodmoon")[3]));
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("bloodmoon")[33]))
                        timeSwitch.add("bloodmoon");
                    return true;
                }
            } else if (triggerPersistence.get("bloodmoon") != null && triggerPersistence.get("bloodmoon") > 0) {
                dynamicSongs.put("bloodmoon", this.handler.TriggerIdentifierMap.get("bloodmoon").get("_"));
                dynamicPriorities.put("bloodmoon", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("bloodmoon")[0]));
                dynamicFadeIn.put("bloodmoon", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("bloodmoon")[1]));
                dynamicFadeOut.put("bloodmoon", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("bloodmoon")[35]));
                dynamicDelay.put("bloodmoon", this.handler.TriggerInfoMap.get("bloodmoon")[4]);
                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("bloodmoon")[33])) timeSwitch.add("bloodmoon");
                return true;
            } else {
				this.boolMap.put("bloodmoon",false);
				this.startMap.put("bloodmoon",0);
			}
        }
        return false;
    }

    private boolean harvestmoon() {
        if (ModList.get().isLoaded("enhancedcelestials") && this.handler.TriggerIdentifierMap.get("harvestmoon")!=null) {
            crashHelper = "harvestmoon";
            LunarContext lunarContext = ((EnhancedCelestialsWorldData) world).getLunarContext();
            if (lunarContext != null && lunarContext.getCurrentEvent() instanceof HarvestMoon) {
                this.boolMap.put("harvestmoon",true);
                this.startMap.putIfAbsent("harvestmoon" ,0);
                if (this.boolMap.get("harvestmoon") && this.startMap.get("harvestmoon") > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("harvestmoon")[8])) {
                    dynamicSongs.put("harvestmoon", this.handler.TriggerIdentifierMap.get("harvestmoon").get("_"));
                    dynamicPriorities.put("harvestmoon", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("harvestmoon")[0]));
                    dynamicFadeIn.put("harvestmoon", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("harvestmoon")[1]));
                    dynamicFadeOut.put("harvestmoon", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("harvestmoon")[35]));
                    dynamicDelay.put("harvestmoon", this.handler.TriggerInfoMap.get("harvestmoon")[4]);
                    triggerPersistence.put("harvestmoon", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("harvestmoon")[3]));
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("harvestmoon")[33]))
                        timeSwitch.add("harvestmoon");
                    return true;
                }
            } else if (triggerPersistence.get("harvestmoon") != null && triggerPersistence.get("harvestmoon") > 0) {
                dynamicSongs.put("harvestmoon", this.handler.TriggerIdentifierMap.get("harvestmoon").get("_"));
                dynamicPriorities.put("harvestmoon", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("harvestmoon")[0]));
                dynamicFadeIn.put("harvestmoon", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("harvestmoon")[1]));
                dynamicFadeOut.put("harvestmoon", MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("harvestmoon")[35]));
                dynamicDelay.put("harvestmoon", this.handler.TriggerInfoMap.get("harvestmoon")[4]);
                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("harvestmoon")[33]))
                    timeSwitch.add("harvestmoon");
                return true;
            } else {
				this.boolMap.put("harvestmoon",false);
				this.startMap.put("harvestmoon",0);
			}
        }
        return false;
    }

    private boolean bluemoon() {
        if (ModList.get().isLoaded("enhancedcelestials") && this.handler.TriggerIdentifierMap.get("bluemoon")!=null) {
            crashHelper = "bluemoon";
            LunarContext lunarContext = ((EnhancedCelestialsWorldData) world).getLunarContext();
            if (lunarContext != null && lunarContext.getCurrentEvent() instanceof BlueMoon) {
                this.boolMap.put("bluemoon",true);
                this.startMap.putIfAbsent("bluemoon" ,0);
                if (this.boolMap.get("bluemoon") && this.startMap.get("bluemoon") > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("bluemoon")[8])) {
                    dynamicSongs.put("bluemoon", this.handler.TriggerIdentifierMap.get("bluemoon").get("_"));
                    dynamicPriorities.put("bluemoon", Integer.parseInt(this.handler.TriggerInfoMap.get("bluemoon")[0]));
                    dynamicFadeIn.put("bluemoon", Integer.parseInt(this.handler.TriggerInfoMap.get("bluemoon")[1]));
                    dynamicFadeOut.put("bluemoon", Integer.parseInt(this.handler.TriggerInfoMap.get("bluemoon")[35]));
                    dynamicDelay.put("bluemoon", this.handler.TriggerInfoMap.get("bluemoon")[4]);
                    triggerPersistence.put("bluemoon", Integer.parseInt(this.handler.TriggerInfoMap.get("bluemoon")[3]));
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("bluemoon")[33]))
                        timeSwitch.add("bluemoon");
                    return true;
                }
            }
            else if (triggerPersistence.get("bluemoon")!=null && triggerPersistence.get("bluemoon") > 0) {
                dynamicSongs.put("bluemoon", this.handler.TriggerIdentifierMap.get("bluemoon").get("_"));
                dynamicPriorities.put("bluemoon", Integer.parseInt(this.handler.TriggerInfoMap.get("bluemoon")[0]));
                dynamicFadeIn.put("bluemoon", Integer.parseInt(this.handler.TriggerInfoMap.get("bluemoon")[1]));
                dynamicFadeOut.put("bluemoon", Integer.parseInt(this.handler.TriggerInfoMap.get("bluemoon")[35]));
                dynamicDelay.put("bluemoon", this.handler.TriggerInfoMap.get("bluemoon")[4]);
                if(Boolean.parseBoolean(this.handler.TriggerInfoMap.get("bluemoon")[33])) timeSwitch.add("bluemoon");
                return true;
            } else {
                this.boolMap.put("bluemoon",false);
                this.startMap.put("bluemoon",0);
            }
        }
        return false;
    }

    private List<String> moon() {
        List<String> events = new ArrayList<>();
        if (ModList.get().isLoaded("enhancedcelestials") && this.handler.TriggerIdentifierMap.get("moon")!=null) {
            crashHelper = "moon";
            LunarContext lunarContext = ((EnhancedCelestialsWorldData) world).getLunarContext();
            if(this.handler.TriggerIdentifierMap.get("moon")!=null && lunarContext != null && lunarContext.getCurrentEvent() instanceof Moon) {
                for (String identifier : this.handler.TriggerIdentifierMap.get("moon").keySet()) {
                    crashHelper = "moon-"+identifier;
                    if ((lunarContext.getCurrentEvent().getKey().contains(this.handler.TriggerInfoMap.get("moon-"+identifier)[9]))) {
                        this.boolMap.put("moon-" + identifier,true);
					this.startMap.putIfAbsent("moon-" + identifier,0);
                        if (this.boolMap.get("moon-" + identifier) && this.startMap.get("moon-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("moon-" + identifier)[8])) {
                            if (!events.contains("moon-" + identifier)) {
                                events.add("moon-" + identifier);
                                dynamicSongs.put("moon-" + identifier, this.handler.TriggerIdentifierMap.get("moon").get(identifier));
                                dynamicPriorities.put("moon-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("moon-" + identifier)[0]));
                                dynamicFadeIn.put("moon-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("moon-" + identifier)[1]));
                                dynamicFadeOut.put("moon-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("moon-" + identifier)[35]));
                                dynamicDelay.put("moon-" + identifier, this.handler.TriggerInfoMap.get("moon-" + identifier)[4]);
                                triggerPersistence.put("moon-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("moon-" + identifier)[3]));
                                if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("moon-" + identifier)[33]))
                                    timeSwitch.add("moon-" + identifier);
                            }
                        }
                    } else if (triggerPersistence.get("moon-"+identifier) != null && triggerPersistence.get("moon-"+identifier) > 0) {
                        if (!events.contains("moon-" + identifier)) {
                            events.add("moon-" + identifier);
                            dynamicSongs.put("moon-" + identifier, this.handler.TriggerIdentifierMap.get("moon").get(identifier));
                            dynamicPriorities.put("moon-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("moon-" + identifier)[0]));
                            dynamicFadeIn.put("moon-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("moon-" + identifier)[1]));
                            dynamicFadeOut.put("moon-" + identifier, Integer.parseInt(this.handler.TriggerInfoMap.get("moon-" + identifier)[35]));
                            dynamicDelay.put("moon-" + identifier, this.handler.TriggerInfoMap.get("moon-" + identifier)[4]);
                            if(Boolean.parseBoolean(this.handler.TriggerInfoMap.get("moon-" + identifier)[33])) timeSwitch.add("moon-" + identifier);
                        }
                    }  else {
                        this.boolMap.put("moon-" + identifier,false);
                        this.startMap.put("moon-" + identifier,0);
                    }
                }
            }
        }
        return events;
    }

    private String dynamicrain(List<String> events) {
        if (ModList.get().isLoaded("dsurround") && this.handler.TriggerIdentifierMap.get("rainintensity")!=null) {
            crashHelper = "rainintensity";
            for (String identifier : this.handler.TriggerIdentifierMap.get("rainintensity").keySet()) {
                crashHelper = "rainintensity-" + identifier;
                if (WorldUtils.getRainStrength(world, 1F) > Float.parseFloat(this.handler.TriggerInfoMap.get("rainintensity-" + identifier)[2]) / 100F) {
                    this.boolMap.put("rainintensity-" + identifier,true);
					this.startMap.putIfAbsent("rainintensity-" + identifier,0);
                    if (this.boolMap.get("rainintensity-" + identifier) && this.startMap.get("rainintensity-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("rainintensity-" + identifier)[8])) {
                        if (!events.contains("rainintensity-" + identifier)) {
                            dynamicSongs.put("rainintensity-" + identifier, this.handler.TriggerIdentifierMap.get("rainintensity").get(identifier));
                            dynamicPriorities.put("rainintensity-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("rainintensity-" + identifier)[0]));
                            dynamicFadeIn.put("rainintensity-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("rainintensity-" + identifier)[1]));
                            dynamicFadeOut.put("rainintensity-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("rainintensity-" + identifier)[35]));
                            dynamicDelay.put("rainintensity-" + identifier, this.handler.TriggerInfoMap.get("rainintensity-" + identifier)[4]);
                            triggerPersistence.put("rainintensity-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("rainintensity" + identifier)[3]));
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("rainintensity-" + identifier)[33]))
                                timeSwitch.add("rainintensity-" + identifier);
                            return "rainintensity-" + identifier;
                        }
                    }
                } else if (triggerPersistence.get("rainintensity-" + identifier) != null && triggerPersistence.get("rainintensity-" + identifier) > 0) {
                    if(!events.contains("rainintensity-" + identifier)) {
                        dynamicSongs.put("rainintensity-" + identifier, this.handler.TriggerIdentifierMap.get("rainintensity").get(identifier));
                        dynamicPriorities.put("rainintensity-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("rainintensity-" + identifier)[0]));
                        dynamicFadeIn.put("rainintensity-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("rainintensity-" + identifier)[1]));
                        dynamicFadeOut.put("rainintensity-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("rainintensity-" + identifier)[35]));
                        dynamicDelay.put("rainintensity-" + identifier, this.handler.TriggerInfoMap.get("rainintensity-" + identifier)[4]);
                        if(Boolean.parseBoolean(this.handler.TriggerInfoMap.get("rainintensity-" + identifier)[33])) timeSwitch.add("rainintensity-" + identifier);
                        return "rainintensity-" + identifier;
                    }
                } else {
					this.boolMap.put("rainintensity-" + identifier,false);
					this.startMap.put("rainintensity-" + identifier,0);
				}
            }
        }
        return null;
    }

    private boolean acidrain() {
        if (ModList.get().isLoaded("betterweather") && this.handler.TriggerIdentifierMap.get("acidrain")!=null) {
            crashHelper = "acidrain";
            BetterWeatherWorldData weatherdata = (BetterWeatherWorldData) world;
            if (weatherdata.getWeatherEventContext() != null && weatherdata.getWeatherEventContext().getCurrentEvent() instanceof AcidRain) {
                this.boolMap.put("acidrain",true);
                this.startMap.putIfAbsent("acidrain" ,0);
                if (this.boolMap.get("acidrain") && this.startMap.get("acidrain") > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("acidrain")[8])) {
                    dynamicSongs.put("acidrain", this.handler.TriggerIdentifierMap.get("acidrain").get("_"));
                    dynamicPriorities.put("acidrain", Integer.parseInt(this.handler.TriggerInfoMap.get("acidrain")[0]));
                    dynamicFadeIn.put("acidrain", Integer.parseInt(this.handler.TriggerInfoMap.get("acidrain")[1]));
                    dynamicFadeOut.put("acidrain", Integer.parseInt(this.handler.TriggerInfoMap.get("acidrain")[35]));
                    dynamicDelay.put("acidrain", this.handler.TriggerInfoMap.get("acidrain")[4]);
                    triggerPersistence.put("acidrain", Integer.parseInt(this.handler.TriggerInfoMap.get("acidrain")[3]));
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("acidrain")[33]))
                        timeSwitch.add("acidrain");
                    return true;
                }
            }
            else if (triggerPersistence.get("acidrain")!=null && triggerPersistence.get("acidrain") > 0) {
                dynamicSongs.put("acidrain", this.handler.TriggerIdentifierMap.get("acidrain").get("_"));
                dynamicPriorities.put("acidrain", Integer.parseInt(this.handler.TriggerInfoMap.get("acidrain")[0]));
                dynamicFadeIn.put("acidrain", Integer.parseInt(this.handler.TriggerInfoMap.get("acidrain")[1]));
                dynamicFadeOut.put("acidrain", Integer.parseInt(this.handler.TriggerInfoMap.get("acidrain")[35]));
                dynamicDelay.put("acidrain", this.handler.TriggerInfoMap.get("acidrain")[4]);
                if(Boolean.parseBoolean(this.handler.TriggerInfoMap.get("acidrain")[33])) timeSwitch.add("acidrain");
                return true;
            } else {
                this.boolMap.put("acidrain",false);
                this.startMap.put("acidrain",0);
            }
        }
        return false;
    }

    private boolean blizzard() {
        if (ModList.get().isLoaded("betterweather") && this.handler.TriggerIdentifierMap.get("blizzard")!=null) {
            crashHelper = "blizzard";
            BetterWeatherWorldData weatherdata = (BetterWeatherWorldData) world;
            if (weatherdata.getWeatherEventContext() != null && weatherdata.getWeatherEventContext().getCurrentEvent() instanceof Blizzard) {
                this.boolMap.put("blizzard",true);
                this.startMap.putIfAbsent("blizzard" ,0);
                if (this.boolMap.get("blizzard") && this.startMap.get("blizzard") > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("blizzard")[8])) {
                    dynamicSongs.put("blizzard", this.handler.TriggerIdentifierMap.get("blizzard").get("_"));
                    dynamicPriorities.put("blizzard", Integer.parseInt(this.handler.TriggerInfoMap.get("blizzard")[0]));
                    dynamicFadeIn.put("blizzard", Integer.parseInt(this.handler.TriggerInfoMap.get("blizzard")[1]));
                    dynamicFadeOut.put("blizzard", Integer.parseInt(this.handler.TriggerInfoMap.get("blizzard")[35]));
                    dynamicDelay.put("blizzard", this.handler.TriggerInfoMap.get("blizzard")[4]);
                    triggerPersistence.put("blizzard", Integer.parseInt(this.handler.TriggerInfoMap.get("blizzard")[3]));
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("blizzard")[33]))
                        timeSwitch.add("blizzard");
                    return true;
                }
            }
            else if (triggerPersistence.get("blizzard")!=null && triggerPersistence.get("blizzard") > 0) {
                dynamicSongs.put("blizzard", this.handler.TriggerIdentifierMap.get("blizzard").get("_"));
                dynamicPriorities.put("blizzard", Integer.parseInt(this.handler.TriggerInfoMap.get("blizzard")[0]));
                dynamicFadeIn.put("blizzard", Integer.parseInt(this.handler.TriggerInfoMap.get("blizzard")[1]));
                dynamicFadeOut.put("blizzard", Integer.parseInt(this.handler.TriggerInfoMap.get("blizzard")[35]));
                dynamicDelay.put("blizzard", this.handler.TriggerInfoMap.get("blizzard")[4]);
                if(Boolean.parseBoolean(this.handler.TriggerInfoMap.get("blizzard")[33])) timeSwitch.add("blizzard");
                return true;
            } else {
                this.boolMap.put("blizzard",false);
                this.startMap.put("blizzard",0);
            }
        }
        return false;
    }

    private boolean cloudy() {
        if (ModList.get().isLoaded("betterweather") && this.handler.TriggerIdentifierMap.get("cloudy")!=null) {
            crashHelper = "cloudy";
            BetterWeatherWorldData weatherdata = (BetterWeatherWorldData) world;
            if (weatherdata.getWeatherEventContext() != null && weatherdata.getWeatherEventContext().getCurrentEvent() instanceof Cloudy) {
                this.boolMap.put("cloudy",true);
                this.startMap.putIfAbsent("cloudy" ,0);
                if (this.boolMap.get("cloudy") && this.startMap.get("cloudy") > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("cloudy")[8])) {
                    dynamicSongs.put("cloudy", this.handler.TriggerIdentifierMap.get("cloudy").get("_"));
                    dynamicPriorities.put("cloudy", Integer.parseInt(this.handler.TriggerInfoMap.get("cloudy")[0]));
                    dynamicFadeIn.put("cloudy", Integer.parseInt(this.handler.TriggerInfoMap.get("cloudy")[1]));
                    dynamicFadeOut.put("cloudy", Integer.parseInt(this.handler.TriggerInfoMap.get("cloudy")[35]));
                    dynamicDelay.put("cloudy", this.handler.TriggerInfoMap.get("cloudy")[4]);
                    triggerPersistence.put("cloudy", Integer.parseInt(this.handler.TriggerInfoMap.get("cloudy")[3]));
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("cloudy")[33])) timeSwitch.add("cloudy");
                    return true;
                }
            }
            else if (triggerPersistence.get("cloudy")!=null && triggerPersistence.get("cloudy") > 0) {
                dynamicSongs.put("cloudy", this.handler.TriggerIdentifierMap.get("cloudy").get("_"));
                dynamicPriorities.put("cloudy", Integer.parseInt(this.handler.TriggerInfoMap.get("cloudy")[0]));
                dynamicFadeIn.put("cloudy", Integer.parseInt(this.handler.TriggerInfoMap.get("cloudy")[1]));
                dynamicFadeOut.put("cloudy", Integer.parseInt(this.handler.TriggerInfoMap.get("cloudy")[35]));
                dynamicDelay.put("cloudy", this.handler.TriggerInfoMap.get("cloudy")[4]);
                if(Boolean.parseBoolean(this.handler.TriggerInfoMap.get("cloudy")[33])) timeSwitch.add("cloudy");
                return true;
            } else {
                this.boolMap.put("cloudy",false);
                this.startMap.put("cloudy",0);
            }
        }
        return false;
    }

    private boolean lightrain() {
        if (ModList.get().isLoaded("betterweather") && this.handler.TriggerIdentifierMap.get("lightrain")!=null) {
            crashHelper = "lightrain";
            BetterWeatherWorldData weatherdata = (BetterWeatherWorldData) world;
            if (weatherdata.getWeatherEventContext() != null && weatherdata.getWeatherEventContext().getCurrentEvent() instanceof Rain && !(weatherdata.getWeatherEventContext().getCurrentEvent() instanceof AcidRain)) {
                this.boolMap.put("lightrain",true);
                this.startMap.putIfAbsent("lightrain" ,0);
                if (this.boolMap.get("lightrain") && this.startMap.get("lightrain") > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("lightrain")[8])) {
                    dynamicSongs.put("lightrain", this.handler.TriggerIdentifierMap.get("lightrain").get("_"));
                    dynamicPriorities.put("lightrain", Integer.parseInt(this.handler.TriggerInfoMap.get("lightrain")[0]));
                    dynamicFadeIn.put("lightrain", Integer.parseInt(this.handler.TriggerInfoMap.get("lightrain")[1]));
                    dynamicFadeOut.put("lightrain", Integer.parseInt(this.handler.TriggerInfoMap.get("lightrain")[35]));
                    dynamicDelay.put("lightrain", this.handler.TriggerInfoMap.get("lightrain")[4]);
                    triggerPersistence.put("lightrain", Integer.parseInt(this.handler.TriggerInfoMap.get("lightrain")[3]));
                    if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("lightrain")[33]))
                        timeSwitch.add("lightrain");
                    return true;
                }
            }
            else if (triggerPersistence.get("lightrain")!=null && triggerPersistence.get("lightrain") > 0) {
                dynamicSongs.put("lightrain", this.handler.TriggerIdentifierMap.get("lightrain").get("_"));
                dynamicPriorities.put("lightrain", Integer.parseInt(this.handler.TriggerInfoMap.get("lightrain")[0]));
                dynamicFadeIn.put("lightrain", Integer.parseInt(this.handler.TriggerInfoMap.get("lightrain")[1]));
                dynamicFadeOut.put("lightrain", Integer.parseInt(this.handler.TriggerInfoMap.get("lightrain")[35]));
                dynamicDelay.put("lightrain", this.handler.TriggerInfoMap.get("lightrain")[4]);
                if(Boolean.parseBoolean(this.handler.TriggerInfoMap.get("lightrain")[33])) timeSwitch.add("lightrain");
                return true;
            } else {
                this.boolMap.put("lightrain",false);
                this.startMap.put("lightrain",0);
            }
        }
        return false;
    }

    private List<String> seasons() {
        List<String> tempList = new ArrayList<>();
        if (ModList.get().isLoaded("sereneseasons") && this.handler.TriggerIdentifierMap.get("season")!=null) {
            crashHelper = "season";
            for (String identifier : this.handler.TriggerIdentifierMap.get("season").keySet()) {
                crashHelper = "season-" + identifier;
                int seasonID = MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[2]);
                ISeasonState curSeason = SeasonHelper.getSeasonState(world);
                if (seasonID == 0 && curSeason.getSeason() == Season.SPRING) {
                    this.boolMap.put("season-" + identifier,true);
					this.startMap.putIfAbsent("season-" + identifier,0);
                    if (this.boolMap.get("season-" + identifier) && this.startMap.get("season-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[8])) {
                        if (!tempList.contains("season-" + identifier)) {
                            tempList.add("season-" + identifier);
                            dynamicSongs.put("season-" + identifier, this.handler.TriggerIdentifierMap.get("season").get(identifier));
                            dynamicPriorities.put("season-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[0]));
                            dynamicFadeIn.put("season-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[1]));
                            dynamicFadeOut.put("season-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[35]));
                            dynamicDelay.put("season-" + identifier, this.handler.TriggerInfoMap.get("season-" + identifier)[4]);
                            triggerPersistence.put("season-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[3]));
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("season-" + identifier)[33]))
                                timeSwitch.add("season-" + identifier);
                        }
                    }
                } else if (seasonID == 1 && curSeason.getSeason() == Season.SUMMER) {
                    this.boolMap.put("season-" + identifier,true);
					this.startMap.putIfAbsent("season-" + identifier,0);
                    if (this.boolMap.get("season-" + identifier) && this.startMap.get("season-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[8])) {
                        if (!tempList.contains("season-" + identifier)) {
                            tempList.add("season-" + identifier);
                            dynamicSongs.put("season-" + identifier, this.handler.TriggerIdentifierMap.get("season").get(identifier));
                            dynamicPriorities.put("season-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[0]));
                            dynamicFadeIn.put("season-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[1]));
                            dynamicFadeOut.put("season-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[35]));
                            dynamicDelay.put("season-" + identifier, this.handler.TriggerInfoMap.get("season-" + identifier)[4]);
                            triggerPersistence.put("season-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[3]));
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("season-" + identifier)[33]))
                                timeSwitch.add("season-" + identifier);
                        }
                    }
                } else if (seasonID == 2 && curSeason.getSeason() == Season.AUTUMN) {
                    this.boolMap.put("season-" + identifier,true);
					this.startMap.putIfAbsent("season-" + identifier,0);
                    if (this.boolMap.get("season-" + identifier) && this.startMap.get("season-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[8])) {
                        if (!tempList.contains("season-" + identifier)) {
                            tempList.add("season-" + identifier);
                            dynamicSongs.put("season-" + identifier, this.handler.TriggerIdentifierMap.get("season").get(identifier));
                            dynamicPriorities.put("season-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[0]));
                            dynamicFadeIn.put("season-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[1]));
                            dynamicFadeOut.put("season-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[35]));
                            dynamicDelay.put("season-" + identifier, this.handler.TriggerInfoMap.get("season-" + identifier)[4]);
                            triggerPersistence.put("season-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[3]));
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("season-" + identifier)[33]))
                                timeSwitch.add("season-" + identifier);
                        }
                    }
                } else if (seasonID == 3 && curSeason.getSeason() == Season.WINTER) {
                    this.boolMap.put("season-" + identifier,true);
					this.startMap.putIfAbsent("season-" + identifier,0);
                    if (this.boolMap.get("season-" + identifier) && this.startMap.get("season-" + identifier) > MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[8])) {
                        if (!tempList.contains("season-" + identifier)) {
                            tempList.add("season-" + identifier);
                            dynamicSongs.put("season-" + identifier, this.handler.TriggerIdentifierMap.get("season").get(identifier));
                            dynamicPriorities.put("season-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[0]));
                            dynamicFadeIn.put("season-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[1]));
                            dynamicFadeOut.put("season-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[35]));
                            dynamicDelay.put("season-" + identifier, this.handler.TriggerInfoMap.get("season-" + identifier)[4]);
                            triggerPersistence.put("season-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[3]));
                            if (Boolean.parseBoolean(this.handler.TriggerInfoMap.get("season-" + identifier)[33]))
                                timeSwitch.add("season-" + identifier);
                        }
                    }
                } else if (triggerPersistence.get("season-" + identifier) != null && triggerPersistence.get("season-" + identifier) > 0) {
                    if (!tempList.contains("season-" + identifier)) {
                        tempList.add("season-" + identifier);
                        dynamicSongs.put("season-" + identifier, this.handler.TriggerIdentifierMap.get("season").get(identifier));
                        dynamicPriorities.put("season-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[0]));
                        dynamicFadeIn.put("season-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[1]));
                        dynamicFadeOut.put("season-" + identifier, MusicTriggers.randomInt(this.handler.TriggerInfoMap.get("season-" + identifier)[35]));
                        dynamicDelay.put("season-" + identifier, this.handler.TriggerInfoMap.get("season-" + identifier)[4]);
                        if(Boolean.parseBoolean(this.handler.TriggerInfoMap.get("season-" + identifier)[33])) timeSwitch.add("season-" + identifier);
                    }
                } else {
					this.boolMap.put("season-" + identifier,false);
					this.startMap.put("season-" + identifier,0);
				}
            }
        }
        return tempList;
    }

    public static BlockPos roundedPos(PlayerEntity p) {
        return new BlockPos((Math.round(p.blockPosition().getX() * 2) / 2.0), (Math.round(p.blockPosition().getY() * 2) / 2.0), (Math.round(p.blockPosition().getZ() * 2) / 2.0));
    }

    public double averageLight(BlockPos p, boolean b) {
        return b ? world.getRawBrightness(p, 0) : world.getBrightness(LightType.BLOCK, p);
    }

    public boolean checkResourceList(String type, String resourceList, boolean match) {
        for(String resource : stringBreaker(resourceList,";")) {
            if(match && type.matches(resource)) return true;
            else if(!match && type.contains(resource)) return true;
        }
        return false;
    }

    public boolean checkStatResourceList(String type, String resourceList, String stat) {
        for(String resource : stringBreaker(resourceList,";")) {
            if(resource.contains(stat) && type.contains(resource.substring(stat.length()+1))) return true;
        }
        return false;
    }

    public boolean checkRiding(String resource) {
        if(player!=null) {
            if (!player.isPassenger() || player.getControllingPassenger() == null) return false;
            else if (resource.matches("minecraft")) return true;
            else if (checkResourceList(Objects.requireNonNull(player.getControllingPassenger()).getName().getString(), resource, true))
                return true;
            else if (ForgeRegistries.ENTITIES.getKey(player.getControllingPassenger().getType()) == null) return false;
            return checkResourceList(Objects.requireNonNull(ForgeRegistries.ENTITIES.getKey(player.getControllingPassenger().getType())).toString(), resource, false);
        } return false;
    }

    public boolean checkStat(String statName, int level) {
        if(mc.player!=null && mc.getConnection()!=null) {
            Objects.requireNonNull(mc.getConnection()).send(new CClientStatusPacket(CClientStatusPacket.State.REQUEST_STATS));
            for (Stat<ResourceLocation> stat : Stats.CUSTOM) {
                if (checkResourceList(stat.getValue().toString(), statName, false) && mc.player.getStats().getValue(stat) > level)
                    return true;
            }
            if (statName.contains("mined")) {
                for (Stat<Block> stat : Stats.BLOCK_MINED) {
                    if (stat.getValue().getRegistryName() != null && checkStatResourceList(stat.getValue().getRegistryName().toString(), statName, "mined") && mc.player.getStats().getValue(Stats.BLOCK_MINED.get(stat.getValue())) > level)
                        return true;
                }
            }
            if (statName.contains("crafted")) {
                for (Stat<Item> stat : Stats.ITEM_CRAFTED) {
                    if (stat.getValue().getRegistryName() != null && checkStatResourceList(stat.getValue().getRegistryName().toString(), statName, "crafted") && mc.player.getStats().getValue(Stats.ITEM_CRAFTED.get(stat.getValue())) > level)
                        return true;
                }
            }
            if (statName.contains("used")) {
                for (Stat<Item> stat : Stats.ITEM_USED) {
                    if (stat.getValue().getRegistryName() != null && checkStatResourceList(stat.getValue().getRegistryName().toString(), statName, "used") && mc.player.getStats().getValue(Stats.ITEM_USED.get(stat.getValue())) > level)
                        return true;
                }
            }
            if (statName.contains("broken")) {
                for (Stat<Item> stat : Stats.ITEM_BROKEN) {
                    if (stat.getValue().getRegistryName() != null && checkStatResourceList(stat.getValue().getRegistryName().toString(), statName, "broken") && mc.player.getStats().getValue(Stats.ITEM_BROKEN.get(stat.getValue())) > level)
                        return true;
                }
            }
            if (statName.contains("picked_up")) {
                for (Stat<Item> stat : Stats.ITEM_PICKED_UP) {
                    if (stat.getValue().getRegistryName() != null && checkStatResourceList(stat.getValue().getRegistryName().toString(), statName, "picked_up") && mc.player.getStats().getValue(Stats.ITEM_PICKED_UP.get(stat.getValue())) > level)
                        return true;
                }
            }
            if (statName.contains("dropped")) {
                for (Stat<Item> stat : Stats.ITEM_DROPPED) {
                    if (stat.getValue().getRegistryName() != null && checkStatResourceList(stat.getValue().getRegistryName().toString(), statName, "dropped") && mc.player.getStats().getValue(Stats.ITEM_DROPPED.get(stat.getValue())) > level)
                        return true;
                }
            }
            if (statName.contains("killed")) {
                for (Stat<EntityType<?>> stat : Stats.ENTITY_KILLED) {
                    if (stat.getValue().getRegistryName() != null && checkStatResourceList(stat.getValue().getRegistryName().toString(), statName, "killed") && mc.player.getStats().getValue(Stats.ENTITY_KILLED.get(stat.getValue())) > level)
                        return true;
                }
            }
            if (statName.contains("killed_by")) {
                for (Stat<EntityType<?>> stat : Stats.ENTITY_KILLED_BY) {
                    if (stat.getValue().getRegistryName() != null && checkStatResourceList(stat.getValue().getRegistryName().toString(), statName, "killed_by") && mc.player.getStats().getValue(Stats.ENTITY_KILLED_BY.get(stat.getValue())) > level)
                        return true;
                }
            }
        }
        return false;
    }

    public boolean checkForSky() {
        BlockPos pp = roundedPos(player);
        if(!world.canSeeSky(pp)) return true;
        if(player.isInWater()) {
            BlockPos pos = new BlockPos(pp.getX(), world.getFluidState(pp).getHeight(world, pp), pp.getZ());
            return !world.canSeeSky(pos);
        } return false;
    }

    public List<String> allMenuSongs() {
        List<String> ret = new ArrayList<>();
        if (this.handler.TriggerIdentifierMap.get("menu") != null) {
            for (String song : this.handler.TriggerIdentifierMap.get("menu").get("_")) ret.add(ChannelManager.getSongHolder(this.channel).get(song));
        }
        return ret;
    }

    public void clearListsAndMaps() {
        this.triggerPersistence.clear();
        this.victory.clear();
        this.dynamicSongs.clear();
        this.dynamicPriorities.clear();
        this.dynamicFadeIn.clear();
        this.dynamicFadeOut.clear();
        this.dynamicDelay.clear();
        this.savePlayable.clear();
        this.timeSwitch.clear();
        this.startMap.clear();
        this.boolMap.clear();
        this.info.clear();
    }

    public static final class Info {
        private final String channel;
        private final Cloner cloner;
        private final List<String> currentSongList;
        private final List<String> previousSongList;
        private final List<String> playableTriggers;
        private final List<String> toggledPlayableTriggers;
        private final List<String> activeTriggers;
        private final List<String> toggledActiveTriggers;
        public Info(String channel) {
            this.channel = channel;
            this.cloner = new Cloner();
            this.currentSongList = new ArrayList<>();
            this.previousSongList = new ArrayList<>();
            this.playableTriggers = new ArrayList<>();
            this.toggledPlayableTriggers = new ArrayList<>();
            this.activeTriggers = new ArrayList<>();
            this.toggledActiveTriggers = new ArrayList<>();
        }

        public List<String> getCurrentSongList() {
            return this.currentSongList;
        }

        public boolean songListChanged() {
            return !this.currentSongList.equals(this.previousSongList);
        }

        public void updateSongList(List<String> newSongs) {
            this.previousSongList.clear();
            this.previousSongList.addAll(this.cloner.deepClone(this.currentSongList));
            this.currentSongList.clear();
            this.currentSongList.addAll(newSongs);
        }

        public List<String> getPlayableTriggers() {
            return this.playableTriggers;
        }

        public void updatePlayableTriggers(List<String> newTriggers) {
            this.playableTriggers.clear();
            this.playableTriggers.addAll(newTriggers);
        }

        public List<String> getActiveTriggers() {
            return this.activeTriggers;
        }

        public void updateActiveTriggers(List<String> activeTriggers) {
            this.activeTriggers.clear();
            this.activeTriggers.addAll(activeTriggers);
        }

        public void runToggles() {
            runPlayableToggle();
            runActiveToggle();
        }

        private void runPlayableToggle() {
            this.toggledPlayableTriggers.removeIf(trigger -> !this.playableTriggers.contains(trigger));
            List<String> toggleList = new ArrayList<>();
            for(String trigger : this.playableTriggers) if(!this.toggledPlayableTriggers.contains(trigger)) {
                toggleList.add(trigger);
                this.toggledPlayableTriggers.add(trigger);
            }
            if(!toggleList.isEmpty()) ChannelManager.getChannel(this.channel).runToggle(2,toggleList);
        }

        private void runActiveToggle() {
            this.toggledActiveTriggers.removeIf(trigger -> !this.activeTriggers.contains(trigger));
            List<String> toggleList = new ArrayList<>();
            for(String trigger : this.activeTriggers) {
                MusicTriggers.logger.info("Checking active trigger to toggle: "+trigger);
                if(!this.toggledActiveTriggers.contains(trigger)) {
                    toggleList.add(trigger);
                    this.toggledActiveTriggers.add(trigger);
                }
            }
            if(!toggleList.isEmpty()) ChannelManager.getChannel(this.channel).runToggle(3,toggleList);
        }

        public void clearSongLists() {
            this.currentSongList.clear();
            this.previousSongList.clear();

        }

        public void clear() {
            clearSongLists();
            this.playableTriggers.clear();
            this.toggledPlayableTriggers.clear();
            this.activeTriggers.clear();
            this.toggledActiveTriggers.clear();
        }
    }

    public static final class Packeted {

        private final List<ServerChannelData.Snow> snowTriggers;
        private final List<ServerChannelData.Home> homeTriggers;
        private final List<ServerChannelData.Biome> biomeTriggers;
        private final List<ServerChannelData.Structure> structureTriggers;
        private final List<ServerChannelData.Mob> mobTriggers;
        private final List<ServerChannelData.Raid> raidTriggers;
        private final List<String> menuSongs;

        public Packeted() {
            this.snowTriggers = new ArrayList<>();
            this.homeTriggers = new ArrayList<>();
            this.biomeTriggers = new ArrayList<>();
            this.structureTriggers = new ArrayList<>();
            this.mobTriggers = new ArrayList<>();
            this.raidTriggers = new ArrayList<>();
            this.menuSongs = new ArrayList<>();
        }

        public void setMenuSongs(List<String> songs) {
            this.menuSongs.clear();
            this.menuSongs.addAll(songs);
        }

        public List<String> getMenuSongs() {
            return this.menuSongs;
        }

        public List<ServerChannelData.Snow> getSnowTriggers() {
            return this.snowTriggers;
        }

        public void addSnowTrigger(ServerChannelData.Snow snow) {
            this.snowTriggers.add(snow);
        }

        public List<ServerChannelData.Home> getHomeTriggers() {
            return this.homeTriggers;
        }

        public void addHomeTrigger(ServerChannelData.Home home) {
            this.homeTriggers.add(home);
        }

        public List<ServerChannelData.Biome> getBiomeTriggers() {
            return this.biomeTriggers;
        }

        public void addBiomeTrigger(ServerChannelData.Biome biome) {
            this.biomeTriggers.add(biome);
        }

        public List<ServerChannelData.Structure> getStructureTriggers() {
            return this.structureTriggers;
        }

        public void addStructureTrigger(ServerChannelData.Structure structure) {
            this.structureTriggers.add(structure);
        }

        public List<ServerChannelData.Mob> getMobTriggers() {
            return this.mobTriggers;
        }

        public void addMobTrigger(ServerChannelData.Mob mob) {
            this.mobTriggers.add(mob);
        }

        public List<ServerChannelData.Raid> getRaidTriggers() {
            return this.raidTriggers;
        }

        public void addRaidTrigger(ServerChannelData.Raid raid) {
            this.raidTriggers.add(raid);
        }
    }
}