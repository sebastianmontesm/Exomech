package com.example.draedonskin;

import cn.blockforge.generated.modid2324exomechi.HologramEntity;
import cn.blockforge.generated.modid2324exomechi.client.HologramRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.logging.LogUtils;
import com.mojang.math.Axis;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.slf4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Envuelve el HologramRenderer original (columna de luz, linea de escaneo, textos e iconos
 * de seleccion) y dibuja encima un modelo de jugador sentado con tu skin.
 */
public final class DraedonSkinRenderer extends EntityRenderer<HologramEntity> {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final ResourceLocation SKIN =
            new ResourceLocation(DraedonSkinMod.MODID, "textures/entity/draedon_skin.png");

    /** true si la skin es de brazos finos (Alex). La tuya es de brazos anchos (Steve). */
    private static final boolean SLIM = false;

    // =====================================================================
    //  VALORES QUE PUEDES CAMBIAR PARA COLOCAR EL JUGADOR
    //  (tras cambiar cualquiera hay que reiniciar runClient)
    // =====================================================================

    /** Tamano del jugador. 1.0 = normal. Mas grande = mas alto y mas ancho. */
    private static final float SCALE = 3.0F;

    /**
     * ALTURA de la cadera sobre el suelo del holograma (en bloques).
     * El cojin de la silla esta a 1.125.
     *   - Jugador hundido / por debajo de la silla  -> SUBE este valor.
     *   - Jugador flotando por encima del asiento   -> BAJALO.
     * Los hombros y la cabeza se calculan solos a partir de este valor y de SCALE.
     */
    private static final float HIP_Y = 2.5F;

    /** Desplazamiento adelante/atras. Positivo = hacia el respaldo, negativo = hacia delante. */
    private static final float BACK_Z = 0.3F;

    /** Inclinacion de los brazos en radianes: 0 = colgando, -1.57 = rectos hacia delante. */
    private static final float ARM_PITCH = -0.9F;

    private static final float HEAD_SCALE = 0.6F;

    // =====================================================================

    // --- Efecto holograma: los mismos valores que usa el renderer original ---
    private static final int FULL_BRIGHT = 15728880;          // sin sombras, brilla siempre
    private static final float TINT_R = 0.6F, TINT_G = 0.92F, TINT_B = 1.0F; // filtro azulado
    private static final float MAX_ALPHA = 0.78F;             // translucidez
    private static final float FADE_START = 20.0F;            // ticks hasta empezar a aparecer
    private static final float FADE_TIME = 20.0F;             // ticks que tarda en aparecer

    // Los hombros estan 12 px (0.75 bloques a escala 1) por encima de la cadera
    private static final float SHOULDER_Y = HIP_Y + 0.75F * SCALE;

    private final HologramRenderer original;
    private final PlayerModel<AbstractClientPlayer> model;

    public DraedonSkinRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0.0F;
        this.original = new HologramRenderer(ctx);
        removeDraedonBody(original);
        this.model = new PlayerModel<>(ctx.bakeLayer(SLIM ? ModelLayers.PLAYER_SLIM : ModelLayers.PLAYER), SLIM);
        this.model.setAllVisible(true);
    }

    /**
     * El renderer original carga TODOS los huesos de draedon_projection.json (silla, cuerpo, cabeza, brazos).
     * Aqui se eliminan todos menos la silla, asi no hace falta ningun resource pack.
     */
    private static void removeDraedonBody(HologramRenderer renderer) {
        try {
            Field field = HologramRenderer.class.getDeclaredField("bones");
            field.setAccessible(true);
            List<?> bones = (List<?>) field.get(renderer);
            bones.removeIf(bone -> {
                try {
                    Method name = bone.getClass().getDeclaredMethod("name");
                    name.setAccessible(true);
                    return !"chair".equals(name.invoke(bone));
                } catch (ReflectiveOperationException ex) {
                    return false;
                }
            });
        } catch (ReflectiveOperationException | RuntimeException ex) {
            LOGGER.error("No se pudo quitar el cuerpo de Draedon del renderer original", ex);
        }
    }

    @Override
    public ResourceLocation getTextureLocation(HologramEntity entity) {
        return SKIN;
    }

    @Override
    public boolean shouldRender(HologramEntity e, Frustum frustum, double x, double y, double z) {
        return original.shouldRender(e, frustum, x, y, z);
    }

    @Override
    public void render(HologramEntity e, float yaw, float partial, PoseStack pose,
                       MultiBufferSource buffers, int light) {
        // 1) NUEVO: los iconos flotantes esperan a que termine la introducción
        if (e.kind() != 0 && IntroOverlay.isRunning()) {
            return;
        }
        // Columna, linea de escaneo, texto, iconos de seleccion, silla...
        original.render(e, yaw, partial, pose, buffers, light);

        if (e.kind() != 0) {
            return; // kind != 0 son los iconos de seleccion de mechs, no Draedon
        }
        float age = e.age() + partial;
        if (age < FADE_START) {
            return;
        }
        float time = (float) (e.level().getGameTime() % 24000L) + partial;
        float alpha = Mth.clamp((age - FADE_START) / FADE_TIME, 0.0F, 1.0F) * MAX_ALPHA;
        alpha *= 0.94F + 0.06F * Mth.sin(time * 0.8F); // parpadeo leve de holograma

        pose.pushPose();
        // Mismo vaiven y misma rotacion que usa el original para Draedon
        pose.translate(0.0, 0.06 * Mth.sin(time * 0.045F), 0.0);
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.lerp(partial, e.yRotO, e.getYRot())));
        // Igual que LivingEntityRenderer: el modelo de jugador tiene Y hacia abajo
        pose.translate(0.0F, SHOULDER_Y, BACK_Z);
        pose.scale(-SCALE, -SCALE, SCALE);

        poseSeated(time);

        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(SKIN));
        model.renderToBuffer(pose, vc, FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                TINT_R, TINT_G, TINT_B, alpha);
        pose.popPose();
    }

    /** Postura sentada (la misma que usa vanilla al montar) + el balanceo de cabeza del original. */
    private void poseSeated(float time) {
        float sway = Mth.sin(time * 0.04F);

        model.head.xRot = 0.0F;
        model.head.yRot = sway * 5.0F * Mth.DEG_TO_RAD;
        model.head.zRot = 0.0F;

        model.body.xRot = 0.0F;
        model.body.yRot = 0.0F;
        model.body.zRot = 0.0F;

        // Brazos hacia delante, apoyados en los reposabrazos
        model.rightArm.xRot = ARM_PITCH + sway * 2.0F * Mth.DEG_TO_RAD;
        model.rightArm.yRot = 0.0F;
        model.rightArm.zRot = 0.0F;
        model.leftArm.xRot = ARM_PITCH;
        model.leftArm.yRot = 0.0F;
        model.leftArm.zRot = 0.0F;

        // Piernas sentadas (valores de vanilla al montar)
        model.rightLeg.xRot = -1.4137167F;
        model.rightLeg.yRot = 0.31415927F;
        model.rightLeg.zRot = 0.07853982F;
        model.leftLeg.xRot = -1.4137167F;
        model.leftLeg.yRot = -0.31415927F;
        model.leftLeg.zRot = -0.07853982F;

        // Las capas externas de la skin (chaqueta, mangas, pantalones, sombrero) siguen a su parte base
        model.hat.copyFrom(model.head);
        model.jacket.copyFrom(model.body);
        model.leftSleeve.copyFrom(model.leftArm);
        model.rightSleeve.copyFrom(model.rightArm);
        model.leftPants.copyFrom(model.leftLeg);
        model.rightPants.copyFrom(model.rightLeg);

        model.head.xScale = model.head.yScale = model.head.zScale = HEAD_SCALE;
        model.hat.xScale = model.hat.yScale = model.hat.zScale = HEAD_SCALE;
    }
}
