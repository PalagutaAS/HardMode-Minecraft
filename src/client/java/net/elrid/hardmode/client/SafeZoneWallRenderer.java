package net.elrid.hardmode.client;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import net.elrid.hardmode.HardMode;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.StagedVertexBuffer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;

import java.util.Optional;
import java.util.OptionalDouble;


/**
 * Рендерит визуальную стену по периметру безопасной зоны.
 * Использует LevelRenderEvents (новый API 26.2) и BufferBuilder.
 */
public class SafeZoneWallRenderer {

    /** Текстура стены. Можно использовать ванильную forcefield.png или свою. */
    private static final Identifier WALL_TEXTURE =
            //Identifier.fromNamespaceAndPath(HardMode.MOD_ID, "textures/misc/zone_wall.png");
            Identifier.withDefaultNamespace("textures/misc/forcefield.png");
            //Identifier.withDefaultNamespace("textures/misc/glint.png");

    /** Цвет стены (RGBA, 0-255). */
    private static final float COLOR_R = 0.10F; // почти нет красного
    private static final float COLOR_G = 3.00F; // максимум зелёного
    private static final float COLOR_B = 0.20F; // чуть синего для изумрудного оттенка
    private static final float COLOR_A = 0.850F; // полная альфа

    /** Высота стены относительно центра зоны. */
    private static final float WALL_BOTTOM = -24.0F;
    private static final float WALL_TOP    = 40.0F;
    /** Длина одного тайла текстуры по горизонтали (в блоках). */
    private static final float TILE_LENGTH = 0.050F;
    /** Скорость вертикального скролла (доля UV за тик). */
    private static final float SCROLL_SPEED = 0.01F;

    /** Дистанция от игрока до стены, дальше которой стена не видна. */
    private static final float FADE_FAR  = 80.0F;
    /** Дистанция, ближе которой стена полностью непрозрачна. */
    private static final float FADE_NEAR = 30.0F;

    /** Высота плавного затухания верхнего края стены (в блоках). */
    private static final float FADE_TOP_HEIGHT = 10.0F;

    /** Количество полос, на которые разбита зона затухания. Больше — плавнее. */
    private static final int TOP_FADE_STEPS = 6;

    /** Кастомный RenderPipeline для полупрозрачной текстурированной стены. */
    private static final RenderPipeline WALL_PIPELINE = RenderPipelines.register(
            RenderPipeline.builder(RenderPipelines.GUI_TEXTURED_SNIPPET)
                    .withLocation(Identifier.fromNamespaceAndPath(HardMode.MOD_ID, "pipeline/zone_wall_additive"))
                    .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT)) // ← перекрываем блендинг снипета
                    .withDepthStencilState(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false))
                    .build()
    );

    private static final StagedVertexBuffer STAGED_BUFFER = new StagedVertexBuffer(
            () -> HardMode.MOD_ID + " zone wall",
            RenderType.SMALL_BUFFER_SIZE
    );

    private static final ByteBufferBuilder ALLOCATOR =
            new ByteBufferBuilder(1536); // достаточно для ~300 квадов


    private SafeZoneWallRenderer() {}

    public static void register() {
        LevelRenderEvents.AFTER_TRANSLUCENT_TERRAIN.register(SafeZoneWallRenderer::render);
        HardMode.LOGGER.info("SafeZoneWallRenderer зарегистрирован");
    }

    private static void render(LevelRenderContext context) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        if (!mc.level.dimension().equals(Level.OVERWORLD)) return;
        if (!ClientZoneState.isValid()) return;

        Vec3 camera = context.levelState().cameraRenderState.pos;
        double cx = ClientZoneState.x() - camera.x;
        double cy = mc.player.getY() - camera.y;   // якорь по Y — игрок
        double cz = ClientZoneState.z() - camera.z;
        double radius = ClientZoneState.radius();

// Жёсткий отсев: игрок настолько далеко, что стены вообще не видно
        double distToCenterSq = cx * cx + cz * cz;
        double hardFar = radius + FADE_FAR + 200.0;
        if (distToCenterSq > hardFar * hardFar) return;

        double px = mc.player.getX() - ClientZoneState.x(); // локально по X
        double pz = mc.player.getZ() - ClientZoneState.z(); // локально по Z

        double distSq = cx * cx + cz * cz;
        double far = radius + 400.0;
        if (distSq > far * far) return;

        PoseStack poseStack = context.poseStack();
        poseStack.pushPose();
        poseStack.translate(cx, cy, cz);

        VertexFormat formatBinding = WALL_PIPELINE.getVertexFormatBinding(0);
        PrimitiveTopology primitive = WALL_PIPELINE.getPrimitiveTopology();
        StagedVertexBuffer.Draw draw = STAGED_BUFFER.appendDraw(
                formatBinding,
                primitive,
                primitive == PrimitiveTopology.QUADS ? RenderSystem.getProjectionType().vertexSorting() : null
        );

        long gameTime = mc.level.getGameTime();
        VertexConsumer builder = STAGED_BUFFER.getVertexBuilder(draw);
        buildCylinder(poseStack.last().pose(), radius, gameTime, builder, px, pz);

        STAGED_BUFFER.upload();

        StagedVertexBuffer.ExecuteInfo info = STAGED_BUFFER.getExecuteInfo(draw);
        if (info != null) {
            draw(mc, info, WALL_PIPELINE);
        }
        STAGED_BUFFER.endFrame();

        poseStack.popPose();
    }

    /**
     * Строит цилиндр (внутреннюю и внешнюю стороны) из QUADS.
     */
    private static void buildCylinder(Matrix4fc matrix, double radius, long gameTime,
                                      VertexConsumer buffer, double px, double pz) {

        int segments = Math.max(64, (int) (radius * 3.0));
        float uScale = (float) (2.0 * Math.PI * radius / TILE_LENGTH);

        float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        float scrollV = ((gameTime + partialTick) * SCROLL_SPEED) % 1.0F;

        // Верхняя граница «плотной» части, нижняя граница «плотной» части.
        float bodyBottom = WALL_BOTTOM + FADE_TOP_HEIGHT;
        float bodyTop    = WALL_TOP    - FADE_TOP_HEIGHT;
        float wallHeight = WALL_TOP - WALL_BOTTOM;

        for (int i = 0; i < segments; i++) {
            double a0 = i * (Math.PI * 2.0) / segments;
            double a1 = (i + 1) * (Math.PI * 2.0) / segments;

            float x0 = (float) (Math.cos(a0) * radius);
            float z0 = (float) (Math.sin(a0) * radius);
            float x1 = (float) (Math.cos(a1) * radius);
            float z1 = (float) (Math.sin(a1) * radius);

            float a0Hor = alphaFor(x0 - px, z0 - pz);
            float a1Hor = alphaFor(x1 - px, z1 - pz);
            if (a0Hor <= 0.001F && a1Hor <= 0.001F) continue;

            float u0 = (float) i / segments * uScale;
            float u1 = (float) (i + 1) / segments * uScale;

            // === Тело (без вертикального затухания) ===
            {
                float vLow  = scrollV + (WALL_TOP - bodyBottom) / wallHeight;
                float vHigh = scrollV + (WALL_TOP - bodyTop)    / wallHeight;
                addWallQuad(buffer, matrix,
                        x0, z0, x1, z1,
                        bodyBottom, bodyTop,
                        u0, u1, vLow, vHigh,
                        a0Hor, a0Hor, a1Hor, a1Hor);
            }

            // === Нижняя зона затухания ===
            for (int h = 0; h < TOP_FADE_STEPS; h++) {
                float t0 = h       / (float) TOP_FADE_STEPS;
                float t1 = (h + 1) / (float) TOP_FADE_STEPS;

                float yLow  = WALL_BOTTOM + FADE_TOP_HEIGHT * t0;
                float yHigh = WALL_BOTTOM + FADE_TOP_HEIGHT * t1;

                // t=0 — самый низ (прозрачно), t=1 — стык с телом (плотно)
                float vFadeLow  = smoothstep(t0);
                float vFadeHigh = smoothstep(t1);

                float a0Low  = a0Hor * vFadeLow;
                float a0High = a0Hor * vFadeHigh;
                float a1Low  = a1Hor * vFadeLow;
                float a1High = a1Hor * vFadeHigh;

                float vLow  = scrollV + (WALL_TOP - yLow)  / wallHeight;
                float vHigh = scrollV + (WALL_TOP - yHigh) / wallHeight;

                addWallQuad(buffer, matrix,
                        x0, z0, x1, z1,
                        yLow, yHigh,
                        u0, u1, vLow, vHigh,
                        a0Low, a0High, a1Low, a1High);
            }

            // === Верхняя зона затухания ===
            for (int h = 0; h < TOP_FADE_STEPS; h++) {
                float t0 = h       / (float) TOP_FADE_STEPS;
                float t1 = (h + 1) / (float) TOP_FADE_STEPS;

                float yLow  = bodyTop + FADE_TOP_HEIGHT * t0;
                float yHigh = bodyTop + FADE_TOP_HEIGHT * t1;

                float vFadeLow  = smoothstep(1.0F - t0);
                float vFadeHigh = smoothstep(1.0F - t1);

                float a0Low  = a0Hor * vFadeLow;
                float a0High = a0Hor * vFadeHigh;
                float a1Low  = a1Hor * vFadeLow;
                float a1High = a1Hor * vFadeHigh;

                float vLow  = scrollV + (WALL_TOP - yLow)  / wallHeight;
                float vHigh = scrollV + (WALL_TOP - yHigh) / wallHeight;

                addWallQuad(buffer, matrix,
                        x0, z0, x1, z1,
                        yLow, yHigh,
                        u0, u1, vLow, vHigh,
                        a0Low, a0High, a1Low, a1High);
            }
        }
    }

    /** Альфа для точки стены на расстоянии dist от игрока (в горизонтальной плоскости). */
    private static float alphaFor(double dx, double dz) {
        double dist = Math.sqrt(dx * dx + dz * dz);
        if (dist >= FADE_FAR)  return 0.0F;
        if (dist <= FADE_NEAR) return COLOR_A;
        float t = (float) ((FADE_FAR - dist) / (FADE_FAR - FADE_NEAR));
        t = t * t * (3.0F - 2.0F * t); // smoothstep
        return COLOR_A * t;
    }

    private static void addWallQuad(VertexConsumer buffer, Matrix4fc matrix,
                                    float x0, float z0, float x1, float z1,
                                    float yLow, float yHigh,
                                    float u0, float u1,
                                    float vLow, float vHigh,
                                    float a0Low, float a0High,
                                    float a1Low, float a1High) {

        // Внутренняя сторона
        buffer.addVertex(matrix, x0, yLow,  z0).setColor(COLOR_R, COLOR_G, COLOR_B, a0Low ).setUv(u0, vLow);
        buffer.addVertex(matrix, x1, yLow,  z1).setColor(COLOR_R, COLOR_G, COLOR_B, a1Low ).setUv(u1, vLow);
        buffer.addVertex(matrix, x1, yHigh, z1).setColor(COLOR_R, COLOR_G, COLOR_B, a1High).setUv(u1, vHigh);
        buffer.addVertex(matrix, x0, yHigh, z0).setColor(COLOR_R, COLOR_G, COLOR_B, a0High).setUv(u0, vHigh);

        // Внешняя сторона
        buffer.addVertex(matrix, x1, yLow,  z1).setColor(COLOR_R, COLOR_G, COLOR_B, a1Low ).setUv(u1, vLow);
        buffer.addVertex(matrix, x0, yLow,  z0).setColor(COLOR_R, COLOR_G, COLOR_B, a0Low ).setUv(u0, vLow);
        buffer.addVertex(matrix, x0, yHigh, z0).setColor(COLOR_R, COLOR_G, COLOR_B, a0High).setUv(u0, vHigh);
        buffer.addVertex(matrix, x1, yHigh, z1).setColor(COLOR_R, COLOR_G, COLOR_B, a1High).setUv(u1, vHigh);
    }

    /** Классический smoothstep: 0 → 0, 1 → 1, с плавными краями. */
    private static float smoothstep(float t) {
        if (t <= 0.0F) return 0.0F;
        if (t >= 1.0F) return 1.0F;
        return t * t * (3.0F - 2.0F * t);
    }

    /**
     * Загружает построенный буфер в GPU и выполняет отрисовку.
     */
    private static void draw(Minecraft client, StagedVertexBuffer.ExecuteInfo info, RenderPipeline pipeline) {
        GpuBufferSlice dynamicTransforms = RenderSystem.getDynamicUniforms()
                .writeTransform(
                        RenderSystem.getModelViewMatrixCopy(), // ★ используем копию
                        new Vector4f(1f, 1f, 1f, 1f),
                        new Vector3f(),
                        new Matrix4f()
                );

        var mainTarget = client.gameRenderer.mainRenderTarget();
        try (RenderPass renderPass = RenderSystem.getDevice()
                .createCommandEncoder()
                .createRenderPass(
                        () -> HardMode.MOD_ID + " zone wall render",
                        mainTarget.getColorTextureView(),
                        Optional.empty(),
                        mainTarget.getDepthTextureView(),
                        OptionalDouble.empty())) {

            renderPass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(renderPass);
            renderPass.setUniform("DynamicTransforms", dynamicTransforms);

            var texture = Minecraft.getInstance().getTextureManager().getTexture(WALL_TEXTURE);
            renderPass.bindTexture("Sampler0", texture.getTextureView(), texture.getSampler());

            renderPass.setVertexBuffer(0, info.vertexBuffer().slice());
            renderPass.setIndexBuffer(info.indexBuffer(), info.indexType());
            renderPass.drawIndexed(info.indexCount(), 1, info.firstIndex(), info.baseVertex(), 0);
        }
    }

    /** Вызывать при выключении мода. */
    public static void close() {
        STAGED_BUFFER.close();
    }
}