package mods.thecomputerizer.musictriggers.client;

import com.mojang.blaze3d.systems.RenderSystem;
import mods.thecomputerizer.musictriggers.MusicTriggersCommon;
import mods.thecomputerizer.musictriggers.client.gui.GuiMain;
import mods.thecomputerizer.musictriggers.client.gui.GuiTriggerInfo;
import mods.thecomputerizer.musictriggers.config.configDebug;
import mods.thecomputerizer.musictriggers.config.configObject;
import mods.thecomputerizer.musictriggers.config.configTitleCards;
import net.minecraft.advancement.Advancement;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.sound.SoundInstance;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.LightType;

import java.util.ArrayList;
import java.util.List;

public class eventsClient {

    public static Identifier IMAGE_CARD = null;
    public static int curImageIndex;
    public static float fadeCount = 1000;
    public static float startDelayCount = 0;
    public static Boolean activated = false;
    public static int timer = 0;
    public static int GuiCounter = 0;
    public static int reloadCounter = 0;
    public static boolean ismoving;
    public static List<Identifier> pngs = new ArrayList<>();
    public static int movingcounter = 0;
    public static String lastAdvancement;
    public static boolean advancement;
    public static PlayerEntity PVPTracker;
    public static boolean renderDebug = true;
    public static boolean zone = false;
    public static boolean firstPass = false;
    public static GuiTriggerInfo parentScreen = null;
    public static int x1 = 0;
    public static int y1 = 0;
    public static int z1 = 0;
    public static int x2 = 0;
    public static int y2 = 0;
    public static int z2 = 0;

    public static SoundInstance playSound(SoundInstance sound) {
        if (sound!=null) {
            if ((MusicPlayer.curMusic != null || MusicPlayer.curTrackList == null || MusicPlayer.curTrackList.isEmpty()) && sound.getId().getNamespace().matches(MusicTriggersCommon.MODID) && (((MusicPlayer.curMusic!=null && MinecraftClient.getInstance().getSoundManager().isPlaying(MusicPlayer.curMusic)) && sound.getId() != MusicPlayer.fromRecord.getId()) || MusicPlayer.playing)) {
                return new PositionedSoundInstance(sound.getId(), SoundCategory.MUSIC, Float.MIN_VALUE*1000, 1F, false, 0, SoundInstance.AttenuationType.LINEAR, 0,0,0, false);
            }
            for (String s : configDebug.blockedmods) {
                if (sound.getId().getNamespace().contains(s) && sound.getCategory() == SoundCategory.MUSIC) {
                    if (!(MusicPlayer.curMusic == null && configDebug.SilenceIsBad)) {
                        return new PositionedSoundInstance(sound.getId(), SoundCategory.MUSIC, Float.MIN_VALUE*1000, 1F, false, 0, SoundInstance.AttenuationType.LINEAR, 0,0,0, false);
                    }
                }
            }
            if (sound.getId().getNamespace().contains("minecraft") && sound.getCategory() == SoundCategory.MUSIC) {
                if (!(MusicPlayer.curMusic == null && configDebug.SilenceIsBad)) {
                    return new PositionedSoundInstance(sound.getId(), SoundCategory.MUSIC, Float.MIN_VALUE*1000, 1F, false, 0, SoundInstance.AttenuationType.LINEAR, 0,0,0, false);
                }
            }
        }
        return sound;
    }

    public static void onDamage(LivingEntity e, DamageSource d) {
        if(e instanceof PlayerEntity && d.getAttacker() instanceof PlayerEntity) {
            if (e == MusicPicker.player) {
                PVPTracker = (PlayerEntity) d.getAttacker();
                MusicPicker.setPVP = true;
            }
            else if(d.getAttacker() == MusicPicker.player) {
                PVPTracker = (PlayerEntity)e;
                MusicPicker.setPVP = true;
            }
        }
    }

    public static void onAdvancement(Advancement a) {
        lastAdvancement = a.getId().toString();
        advancement = true;
    }

    public static void onCustomTick() {
        if(configTitleCards.imagecards.get(curImageIndex)!=null) {
            if (timer > configTitleCards.imagecards.get(curImageIndex).getTime()) {
                activated = false;
                timer = 0;
                ismoving = false;
                movingcounter = 0;
            }
            if (ismoving) {
                if (timer % configTitleCards.imagecards.get(curImageIndex).getDelay() == 0) {
                    movingcounter++;
                    if (movingcounter >= pngs.size()) {
                        movingcounter = 0;
                    }
                }
                IMAGE_CARD = pngs.get(movingcounter);
            }
            if (activated) {
                timer++;
                startDelayCount++;
                if (startDelayCount > 0) {
                    if (fadeCount > 1) {
                        fadeCount -= configTitleCards.imagecards.get(curImageIndex).getFadeIn();
                        if (fadeCount < 1) {
                            fadeCount = 1;
                        }
                    }
                }
            } else {
                if (fadeCount < 1000) {
                    fadeCount += configTitleCards.imagecards.get(curImageIndex).getFadeOut();
                    if (fadeCount > 1000) {
                        fadeCount = 1000;
                        ismoving = false;
                    }
                }
                startDelayCount = 0;
            }
        }
    }

    public static void imageCards(MatrixStack matrix) {
        MinecraftClient mc = MinecraftClient.getInstance();
        PlayerEntity player = mc.player;
        if (player != null && configTitleCards.imagecards.get(curImageIndex) != null) {
            int x = mc.getWindow().getWidth();
            int y = mc.getWindow().getHeight();
            if (fadeCount != 1000 && IMAGE_CARD != null) {
                matrix.push();

                float opacity = (int) (17 - (fadeCount / 80));
                opacity = (opacity * 1.15f) / 15;

                int sizeX = configTitleCards.imageDimensions.get(IMAGE_CARD).getWidth();
                int sizeY = configTitleCards.imageDimensions.get(IMAGE_CARD).getHeight();

                float scaleY = (0.25f * (configTitleCards.imagecards.get(curImageIndex).getScaleY() / 100f));
                float scaleX = (0.25f * (configTitleCards.imagecards.get(curImageIndex).getScaleX() / 100f));
                matrix.scale(scaleX, scaleY, 1f);

                float posY = ((y * 2f) / ((4f * (configTitleCards.imagecards.get(curImageIndex).getScaleY() / 100f)) * 3f)) + configTitleCards.imagecards.get(curImageIndex).getVertical();
                float posX = ((x * 2f) / ((configTitleCards.imagecards.get(curImageIndex).getScaleX() / 100f) * 3f)) - (sizeX / 2f) + configTitleCards.imagecards.get(curImageIndex).getHorizontal();

                MusicTriggersCommon.logger.info(IMAGE_CARD + " X: " + x + " Y: " + y + " PosX: " + posX + " PosY: " + posY);

                RenderSystem.setShaderColor(1F, 1F, 1F, Math.max(0, Math.min(0.95f, opacity)));
                RenderSystem.setShaderTexture(0, IMAGE_CARD);
                DrawableHelper.drawTexture(matrix, (int) posX, (int) posY, 0F, 0F, sizeX, sizeY, sizeX, sizeY);
                matrix.pop();
            }
        }
    }


    public static void onKeyInput() {
        if(MinecraftClient.getInstance().player!=null) {
            BlockPos pos = MusicPicker.roundedPos(MinecraftClient.getInstance().player);
            if(!zone) MinecraftClient.getInstance().setScreen(new GuiMain(configObject.createFromCurrent()));
            else if(!firstPass) {
                x1 = pos.getX();
                y1 = pos.getY();
                z1 = pos.getZ();
                firstPass = true;
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ambient(SoundEvents.BLOCK_ANVIL_LAND));
            } else {
                x2 = pos.getX();
                y2 = pos.getY();
                z2 = pos.getZ();
                firstPass = false;
                zone = false;
                MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.ambient(SoundEvents.BLOCK_ANVIL_BREAK));
                String compiledZoneCoords = x1+","+y1+","+z1+","+x2+","+y2+","+z2;
                parentScreen.holder.editTriggerInfoParameter(parentScreen.songCode, parentScreen.trigger, parentScreen.scrollingSongs.index, compiledZoneCoords);
                MinecraftClient.getInstance().setScreen(parentScreen);
            }
        }
    }

    public static void onTick() {
        if(!renderDebug) MinecraftClient.getInstance().options.hudHidden = true;
        else MinecraftClient.getInstance().options.hudHidden = false;
        if(reloadCounter>0) {
            reloadCounter-=1;
            if(reloadCounter==1) {
                reload.readAndReload();
                Text msg = Text.of("\u00A7a\u00A7oFinished!");
                MusicPicker.player.sendMessage(msg,false);
                IMAGE_CARD = null;
                fadeCount = 1000;
                timer = 0;
                activated = false;
                ismoving = false;
                MusicPlayer.cards = true;
                MusicPlayer.reloading = false;
            }
        }
    }

    public static void debugInfo(MatrixStack matrix) {
        if (configDebug.ShowDebugInfo && renderDebug) {
            List<String> left = new ArrayList<>();
            if (MusicPlayer.curTrack != null) {
                left.add("Music Triggers Current song: "+MusicPlayer.curTrackHolder);
            }
            if (!configDebug.ShowJustCurSong) {
                if (MusicPicker.playableList != null && !MusicPicker.playableList.isEmpty()) {
                    StringBuilder s = new StringBuilder();
                    for (String ev : MusicPicker.playableList) {
                        s.append(" ").append(ev);
                    }
                    left.add("Music Triggers Playable Triggers:"+s);
                }
                StringBuilder sm = new StringBuilder();
                sm.append("minecraft");
                for (String ev : configDebug.blockedmods) {
                    sm.append(" ").append(ev);
                }
                left.add("Music Triggers Current Blocked Mods: "+sm);
                if (MusicPicker.player != null && MusicPicker.world != null) {
                    if (fromServer.curStruct != null) {
                        left.add("Music Triggers Current Structure: "+fromServer.curStruct);
                    }
                    left.add("Music Triggers Current Biome: "+fromServer.curBiome);
                    left.add("Music Triggers Current Dimension: "+MusicPicker.player.world.getDimension().getEffects());
                    left.add("Music Triggers Current Total Light: "+MusicPicker.world.getLightLevel(MusicPicker.roundedPos(MusicPicker.player)));
                    left.add("Music Triggers Current Block Light: "+MusicPicker.world.getLightLevel(LightType.BLOCK, MusicPicker.roundedPos(MusicPicker.player)));
                    if (MusicPicker.effectList != null && !MusicPicker.effectList.isEmpty()) {
                        StringBuilder se = new StringBuilder();
                        for (String ev : MusicPicker.effectList) {
                            se.append(" ").append(ev);
                        }
                        left.add("Music Triggers Current Effect List:"+se);
                    }
                    if (getLivingFromEntity(MinecraftClient.getInstance().targetedEntity) != null) {
                        left.add("Music Triggers Current Entity Name: "+getLivingFromEntity(MinecraftClient.getInstance().targetedEntity).getName().getString());
                    }
                    if (MusicPicker.mc.currentScreen != null) {
                        left.add("Music Triggers current GUI: "+MusicPicker.mc.currentScreen);
                    }
                }
            }
            int top = 2;
            for (String msg : left)
            {
                MinecraftClient.getInstance().inGameHud.fill(matrix, 1, top - 1, 2 + MinecraftClient.getInstance().textRenderer.getWidth(msg) + 1, top + MinecraftClient.getInstance().textRenderer.fontHeight - 1, -1873784752);
                MinecraftClient.getInstance().textRenderer.draw(matrix, msg, 2, top, 14737632);
                top += MinecraftClient.getInstance().textRenderer.fontHeight;
            }
        }
    }

    private static LivingEntity getLivingFromEntity(Entity e) {
        if (e instanceof LivingEntity) {
            return (LivingEntity) e;
        } else return null;
    }
}
