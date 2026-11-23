package com.example.draedonskin;

import cn.blockforge.generated.modid2324exomechi.DialogueRules;
import cn.blockforge.generated.modid2324exomechi.GeneratedMod;
import cn.blockforge.generated.modid2324exomechi.HologramEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Introduccion de Draedon que se muestra ANTES de que aparezcan los iconos flotantes.
 *
 * Se dispara cuando aparece el holograma de Draedon (kind == 0). Cada frase es una pagina
 * con el mismo efecto de maquina de escribir y la misma duracion que usan los dialogos
 * originales (DialogueRules.duration). Mientras corre, DraedonSkinRenderer oculta los iconos.
 *
 * Las frases estan en assets/draedon_skin/lang/*.json (claves intro.draedon_skin.1, .2, ...).
 */
@Mod.EventBusSubscriber(modid = DraedonSkinMod.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class IntroOverlay {

    /** Cuantas frases tiene la introduccion. Debe coincidir con las claves del archivo de idioma. */
    private static final int PAGES = 7;
    /** El holograma de Draedon empieza a aparecer a los 20 ticks de vida. */
    private static final int START_AGE = 20;
    /** Si llegas cuando el holograma ya tiene mas ticks que esto, no se reproduce (te lo perdiste). */
    private static final int LATE_LIMIT = 45;
    /** Color del texto (el mismo celeste del rotulo del holograma). */
    private static final int TEXT_COLOR = 0xA7E8FF;

    private static final Set<UUID> SEEN = new HashSet<>();
    private static ClientLevel level;
    private static boolean running;
    private static String[] texts = new String[0];
    private static int pageIndex;
    private static int age;
    private static int duration;
    private static SimpleSoundInstance sound;
    private static final float TIME_SCALE = 0.6F;

    private IntroOverlay() {
    }

    /** true mientras la introduccion esta en marcha; los iconos flotantes esperan. */
    public static boolean isRunning() {
        return running;
    }

    private static void stopSound(Minecraft mc) {
        if (sound != null) {
            mc.getSoundManager().stop(sound);
            sound = null;
        }
    }

    private static void reset(Minecraft mc) {
        stopSound(mc);
        running = false;
        pageIndex = 0;
        age = 0;
    }

    private static void begin(Minecraft mc) {
        texts = new String[PAGES];
        for (int i = 0; i < PAGES; i++) {
            texts[i] = Component.translatable("intro.draedon_skin." + (i + 1)).getString();
        }
        pageIndex = -1;
        running = true;
        next(mc);
    }

    private static void next(Minecraft mc) {
        stopSound(mc);
        pageIndex++;
        if (pageIndex >= texts.length) {
            running = false;
            return;
        }
        age = 0;
        String text = texts[pageIndex];
        duration = Math.max(40, Math.round(
                DialogueRules.duration(text.codePointCount(0, text.length())) * TIME_SCALE));
        if (!mc.options.hideGui) {
            sound = SimpleSoundInstance.forUI(GeneratedMod.TEXT_REVEAL.get(), 1.0F, 0.7F);
            mc.getSoundManager().play(sound);
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            reset(mc);
            level = null;
            return;
        }
        if (mc.level != level) {
            reset(mc);
            SEEN.clear();
            level = mc.level;
        }
        if (!mc.player.isAlive() || mc.player.isSpectator()) {
            reset(mc);
            return;
        }
        if (mc.isPaused()) {
            return;
        }

        if (running) {
            if (++age >= duration) {
                next(mc);
            }
            return;
        }

        // Buscar un holograma de Draedon (kind 0) recien aparecido cerca de ti
        if (SEEN.size() > 64) {
            SEEN.clear();
        }
        for (HologramEntity h : mc.level.getEntitiesOfClass(
                HologramEntity.class, mc.player.getBoundingBox().inflate(32.0), e -> e.kind() == 0)) {
            if (h.age() < START_AGE || !SEEN.add(h.getUUID())) {
                continue;
            }
            if (h.age() <= LATE_LIMIT) {
                begin(mc);
                break;
            }
        }
    }

    @SubscribeEvent
    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (!running || pageIndex < 0 || pageIndex >= texts.length || mc.player == null || mc.options.hideGui) {
            return;
        }
        GuiGraphics g = event.getGuiGraphics();
        Font font = mc.font;

        float time = age + event.getPartialTick();
        float alpha = Math.min(Mth.clamp(time / 8.0F, 0.0F, 1.0F), Mth.clamp((duration - time) / 20.0F, 0.0F, 1.0F));
        int a = (int) (alpha * 255.0F);
        if (a <= 8) {
            return; // con alfa muy bajo Minecraft dibujaria el texto opaco
        }
        int color = (a << 24) | TEXT_COLOR;
        int shown = (int) Math.max(0.0F, (time / TIME_SCALE - 6.0F) / 1.15F); // efecto maquina de escribir

        int maxWidth = Math.min(g.guiWidth() - 40, 420);
        List<FormattedText> lines = font.getSplitter().splitLines(texts[pageIndex], maxWidth, Style.EMPTY);
        int gap = 14;
        int bottom = g.guiHeight() - 68; // por encima de la barra de objetos
        int y = bottom - (lines.size() - 1) * gap - 9;

        for (FormattedText line : lines) {
            String s = line.getString();
            int length = s.codePointCount(0, s.length());
            int take = Math.min(shown, length);
            if (take > 0) {
                String part = s.substring(0, s.offsetByCodePoints(0, take));
                int x = (g.guiWidth() - font.width(s)) / 2; // posicion fija para que no "baile" al escribir
                g.drawString(font, part, x, y, color, true);
            }
            shown -= length;
            y += gap;
        }
    }
}
