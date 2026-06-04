package net.vulkanmod.vulkan.shader;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import dev.blocktracker.shader.IrisProgramSource;
import net.minecraft.client.Minecraft;
import net.vulkanmod.render.shader.PipelineConfigs;
import net.vulkanmod.render.shader.ShaderLoadUtil;
import net.vulkanmod.render.vertex.CustomVertexFormat;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import net.vulkanmod.vulkan.shader.layout.Uniform;
import net.vulkanmod.vulkan.util.MappedBuffer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.joml.Matrix4f;

import java.util.regex.PatternSyntaxException;
import net.vulkanmod.vulkan.Renderer;
import net.vulkanmod.vulkan.VRenderSystem;
import net.vulkanmod.vulkan.framebuffer.Framebuffer;
import net.vulkanmod.vulkan.framebuffer.RenderPass;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.texture.VTextureSelector;
import net.vulkanmod.vulkan.texture.VulkanImage;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Same-package bridge for VulkanMod's package-private GraphicsPipeline
 * constructor. This is deliberately small: it turns already-transformed Iris
 * sources into Vulkan shader modules/pipeline layouts after VulkanMod has
 * created its device, without drawing them yet.
 */
public final class VeyraIrisPipelineBridge {
    private static final Logger LOGGER = LogManager.getLogger("VeyraIrisPipelineBridge");
    private static final Pattern DESCRIPTOR_BINDING = Pattern.compile("layout\\s*\\(\\s*binding\\s*=\\s*(\\d+)\\s*\\)\\s*(?:readonly\\s+|writeonly\\s+|coherent\\s+|volatile\\s+|restrict\\s+)*uniform\\s+([A-Za-z0-9_]+)");
    private static final Pattern UBO_BLOCK = Pattern.compile("layout\\s*\\(\\s*binding\\s*=\\s*0\\s*\\)\\s*uniform\\s+VeyraIrisUniforms\\s*\\{([^}]*)};", Pattern.DOTALL);
    private static final Pattern UBO_FIELD = Pattern.compile("\\b(float|int|vec2|vec3|vec4|ivec2|ivec3|mat3|mat4)\\s+([^;]+);");
    private static final List<GraphicsPipeline> GRAPHICS_PIPELINES = new ArrayList<>();
    private static final Map<String, GraphicsPipeline> GRAPHICS_BY_PROGRAM = new java.util.HashMap<>();
    private static final Map<String, IrisProgramSource> PROGRAMS_BY_NAME = new java.util.HashMap<>();
    private static final Map<String, GraphicsPipeline> SERIAL_MRT_PIPELINES = new java.util.HashMap<>();
    private static final Map<String, GraphicsPipeline> SCENE_COPY_PIPELINES = new java.util.HashMap<>();
    private static final Map<RenderPass, GraphicsPipeline> TERRAIN_GBUFFER_PIPELINES = new java.util.IdentityHashMap<>();
    private static final Map<String, GraphicsPipeline> TERRAIN_SOLAS_GBUFFER_PIPELINES = new java.util.HashMap<>();
    private static final Map<String, GraphicsPipeline> TERRAIN_WATER_AUX_PIPELINES = new java.util.HashMap<>();
    private static final Map<RenderPass, GraphicsPipeline> TERRAIN_SHADOW_PIPELINES = new java.util.IdentityHashMap<>();
    private static final Map<RenderPass, GraphicsPipeline> TERRAIN_SHADOW_COLOR_PIPELINES = new java.util.IdentityHashMap<>();
    private static GraphicsPipeline sceneCopyPipeline;
    private static GraphicsPipeline screenSourceCopyPipeline;
    private static Framebuffer screenSourceFramebuffer;
    private static RenderPass screenSourceRenderPass;
    private static GraphicsPipeline screenFinalPipeline;
    private static RenderPass screenFinalRenderPass;
    private static Framebuffer screenFinalFramebuffer;
    private static String screenFinalMode;
    private static VulkanImage solasNoiseTexture;
    private static VulkanImage solasMilkyWayTexture;

    private VeyraIrisPipelineBridge() {
    }

    public static BuildResult rebuildShaderModules(List<IrisProgramSource> programs) {
        return rebuildGraphicsPipelines(programs, Map.of());
    }

    public static BuildResult rebuildGraphicsPipelines(List<IrisProgramSource> programs, Map<String, RenderPass> renderPasses) {
        cleanUp();

        int attempted = 0;
        int created = 0;
        int actualPipelines = 0;
        List<String> failures = new ArrayList<>();

        for (IrisProgramSource program : programs) {
            if (!program.hasVertex() || !program.hasFragment()) {
                continue;
            }

            attempted++;
            try {
                PROGRAMS_BY_NAME.put(program.name(), program);
                Pipeline.Builder builder = new Pipeline.Builder(vertexFormatFor(program.name()), "veyra_iris/" + program.name());
                builder.addUBO(createVeyraUbo(program));
                addContiguousImageDescriptors(builder, program);
                builder.renderPass = renderPasses.get(program.name());
                builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, program.vertexSource());
                builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, program.fragmentSource());
                GraphicsPipeline pipeline = builder.createGraphicsPipeline();
                GRAPHICS_PIPELINES.add(pipeline);
                GRAPHICS_BY_PROGRAM.put(program.name(), pipeline);
                created++;
                if (builder.renderPass != null) {
                    actualPipelines++;
                }
            } catch (Throwable throwable) {
                String message = throwable.getMessage();
                if (message == null) {
                    message = throwable.getClass().getSimpleName();
                }
                failures.add(program.name() + " :: " + message.replace('\n', ' '));
                LOGGER.warn("Failed creating Iris Vulkan shader modules for {}", program.name(), throwable);
            }
        }

        return new BuildResult(attempted, created, actualPipelines, failures);
    }

    private static VertexFormat vertexFormatFor(String programName) {
        return isFullscreenProgram(programName) ? DefaultVertexFormat.EMPTY : DefaultVertexFormat.POSITION_TEX_COLOR_NORMAL;
    }

    private static boolean isFullscreenProgram(String programName) {
        String local = programName;
        int slash = local.lastIndexOf('/');
        if (slash >= 0) {
            local = local.substring(slash + 1);
        }
        return local.startsWith("deferred") || local.startsWith("composite") || local.equals("final");
    }

    private static UBO createVeyraUbo(IrisProgramSource program) {
        return createVeyraUbo(program, 0);
    }

    private static UBO createVeyraUbo(IrisProgramSource program, int binding) {
        List<Uniform.Info> infos = new ArrayList<>();
        collectUboFields(program.vertexSource(), infos);
        collectUboFields(program.fragmentSource(), infos);
        collectUboFields(program.computeSource(), infos);
        if (infos.isEmpty()) {
            return new UBO("VeyraIrisUniforms", binding, 63, 4096, List.of());
        }
        return new UBO("VeyraIrisUniforms", binding, 63, 4096, infos);
    }

    private static void collectUboFields(String source, List<Uniform.Info> infos) {
        if (source == null) {
            return;
        }
        Matcher blockMatcher = UBO_BLOCK.matcher(source);
        while (blockMatcher.find()) {
            Matcher fieldMatcher = UBO_FIELD.matcher(blockMatcher.group(1));
            while (fieldMatcher.find()) {
                String type = fieldMatcher.group(1);
                for (String rawName : fieldMatcher.group(2).split(",")) {
                    String name = rawName.trim();
                    if (name.isBlank()) continue;
                    int bracket = name.indexOf('[');
                    if (bracket >= 0) name = name.substring(0, bracket).trim();
                    String finalName = name;
                    boolean exists = infos.stream().anyMatch(info -> info.name.equals(finalName));
                    if (exists) continue;
                    Uniform.Info info = Uniform.createUniformInfo(type, finalName);
                    info.setBufferSupplier(() -> uniformValue(type, finalName));
                    infos.add(info);
                }
            }
        }
    }

    private static MappedBuffer uniformValue(String type, String name) {
        int bytes = switch (type) {
            case "vec2", "ivec2" -> 8;
            case "vec3", "vec4", "ivec3" -> 16;
            case "mat3" -> 48;
            case "mat4" -> 64;
            default -> 4;
        };
        MappedBuffer buffer = new MappedBuffer(bytes);
        if (type.equals("int") || type.startsWith("ivec")) {
            int value = switch (name) {
                case "worldTime" -> (int) (worldTimeTicks() % 24000L);
                case "worldDay" -> (int) (worldTimeTicks() / 24000L);
                case "moonPhase" -> (int) ((worldTimeTicks() / 24000L) & 7L);
                case "frameCounter" -> (int) ((System.currentTimeMillis() / 50L) & 0x7fffffffL);
                case "framemod8" -> (int) ((System.currentTimeMillis() / 50L) & 7L);
                case "dhRenderDistance", "vxRenderDistance" -> Minecraft.getInstance().options == null ? 12 : Minecraft.getInstance().options.renderDistance().get();
                case "isEyeInWater" -> eyeInWaterState();
                case "entityId", "blockEntityId", "currentRenderedItemId", "heldItemId" -> 0;
                case "renderStage" -> 0;
                default -> 0;
            };
            for (int i = 0; i < bytes; i += 4) buffer.putInt(i, value);
            if (name.equals("eyeBrightnessSmooth") && bytes >= 8) {
                buffer.putInt(0, 240);
                buffer.putInt(4, 240);
            }
            if (name.equals("atlasSize") && bytes >= 8) {
                buffer.putInt(0, 1024);
                buffer.putInt(4, 1024);
            }
            return buffer;
        }
        if (type.equals("mat4")) {
            return switch (name) {
                case "gbufferProjection", "dhProjection", "vxProj", "gbufferPreviousProjection" -> VRenderSystem.getProjectionMatrix();
                case "gbufferModelView", "dhModelView", "gbufferPreviousModelView" -> VRenderSystem.getModelViewMatrix();
                case "gbufferProjectionInverse", "dhProjectionInverse", "vxProjInv" -> inverseMapped(VRenderSystem.getProjectionMatrix());
                case "gbufferModelViewInverse", "vxModelViewInv" -> inverseMapped(VRenderSystem.getModelViewMatrix());
                case "shadowProjection" -> shadowProjectionBuffer(false);
                case "shadowProjectionInverse" -> shadowProjectionBuffer(true);
                case "shadowModelView" -> shadowModelViewBuffer(false);
                case "shadowModelViewInverse" -> shadowModelViewBuffer(true);
                default -> identityMat4();
            };
        }
        if (type.equals("mat3")) {
            for (int i = 0; i < 12; i++) buffer.putFloat(i * 4, (i == 0 || i == 4 || i == 8) ? 1.0f : 0.0f);
            return buffer;
        }
        if (name.equals("cameraPosition") || name.equals("previousCameraPosition")) {
            return cameraPositionBuffer();
        }
        if (name.equals("sunPosition")) {
            return sunOrMoonPosition(true);
        }
        if (name.equals("moonPosition")) {
            return sunOrMoonPosition(false);
        }
        if (name.equals("upPosition") || name.equals("relativeEyePosition")) {
            return vec3Buffer(0.0f, 1.0f, 0.0f);
        }
        if (name.equals("endFlashPosition")) {
            return sunOrMoonPosition(true);
        }
        if (name.equals("entityColor")) {
            return vec4Buffer(1.0f, 1.0f, 1.0f, 1.0f);
        }
        if (name.equals("lightningBoltPosition")) {
            return vec4Buffer(0.0f, 0.0f, 0.0f, 0.0f);
        }
        if (name.equals("fogColor")) {
            return VRenderSystem.getShaderFogColor();
        }
        if (name.equals("skyColor")) {
            return VRenderSystem.getShaderFogColor();
        }
        float first = switch (name) {
            case "viewWidth" -> Minecraft.getInstance().getWindow() == null ? 1920.0f : Minecraft.getInstance().getWindow().getWidth();
            case "viewHeight" -> Minecraft.getInstance().getWindow() == null ? 1080.0f : Minecraft.getInstance().getWindow().getHeight();
            case "aspectRatio" -> {
                var window = Minecraft.getInstance().getWindow();
                yield window == null ? 16.0f / 9.0f : (float) window.getWidth() / Math.max(1.0f, (float) window.getHeight());
            }
            case "frameTimeCounter" -> (System.currentTimeMillis() % 10_000_000L) / 1000.0f;
            case "framemod8" -> (float) ((System.currentTimeMillis() / 50L) & 7L);
            case "timeAngle" -> (worldTimeTicks() % 24000L) / 24000.0f;
            case "rainStrength", "wetness" -> rainStrength();
            case "timeBrightness" -> timeBrightness();
            case "near", "dhNearPlane" -> 0.05f;
            case "far", "dhFarPlane" -> Minecraft.getInstance().options == null ? 1024.0f : Minecraft.getInstance().options.renderDistance().get() * 16.0f;
            case "cloudHeight" -> 192.0f;
            case "isDesert" -> biomeFlag("desert", "badlands", "savanna");
            case "isSwamp" -> biomeFlag("swamp", "mangrove_swamp");
            case "isSnowy" -> biomeFlag("snow", "frozen", "ice", "jagged_peaks", "grove");
            case "isJungle" -> biomeFlag("jungle", "bamboo");
            case "isMushroom" -> biomeFlag("mushroom");
            case "isLushCaves" -> biomeFlag("lush_caves");
            case "isDeepDark" -> biomeFlag("deep_dark");
            case "isCherryGrove" -> biomeFlag("cherry_grove");
            case "isPaleGarden" -> biomeFlag("pale_garden");
            case "endFlashIntensity" -> activeDimensionPrefix().equals("world1/") ? 1.0f : 0.0f;
            case "darknessFactor", "blindFactor", "shadowFade", "nightVision", "centerDepthSmooth" -> 0.0f;
            case "eyeAltitude" -> {
                Minecraft client = Minecraft.getInstance();
                yield client.player == null ? 64.0f : (float) client.player.getY();
            }
            default -> 0.0f;
        };
        buffer.putFloat(0, first);
        return buffer;
    }

    private static MappedBuffer shadowProjectionBuffer(boolean inverse) {
        MappedBuffer buffer = new MappedBuffer(64);
        Matrix4f matrix = shadowProjectionMatrix();
        if (inverse) matrix.invert();
        matrix.get(buffer.buffer.asFloatBuffer());
        return buffer;
    }

    public static Matrix4f shadowProjectionMatrix() {
        int renderDistance = Minecraft.getInstance().options == null ? 12 : Minecraft.getInstance().options.renderDistance().get();
        float radius = Math.max(96.0f, Math.min(384.0f, renderDistance * 18.0f));
        return new Matrix4f().ortho(-radius, radius, -radius, radius, 0.1f, radius * 4.0f);
    }

    private static MappedBuffer shadowModelViewBuffer(boolean inverse) {
        MappedBuffer buffer = new MappedBuffer(64);
        Matrix4f matrix = shadowModelViewMatrix();
        if (inverse) matrix.invert();
        matrix.get(buffer.buffer.asFloatBuffer());
        return buffer;
    }

    public static Matrix4f shadowModelViewMatrix() {
        MappedBuffer camera = cameraPositionBuffer();
        float sx = sunOrMoonComponent(0);
        float sy = sunOrMoonComponent(1);
        float sz = sunOrMoonComponent(2);
        org.joml.Vector3f lightDir = new org.joml.Vector3f(sx, sy, sz);
        if (lightDir.lengthSquared() < 0.001f) lightDir.set(0.25f, 0.75f, 0.25f);
        lightDir.normalize();
        float radius = 256.0f;
        org.joml.Vector3f eye = new org.joml.Vector3f(lightDir).mul(radius);
        org.joml.Vector3f center = new org.joml.Vector3f(0.0f, 0.0f, 0.0f);
        org.joml.Vector3f up = Math.abs(lightDir.y) > 0.9f ? new org.joml.Vector3f(0, 0, 1) : new org.joml.Vector3f(0, 1, 0);
        Matrix4f matrix = new Matrix4f().lookAt(eye, center, up);
        matrix.translate(-camera.buffer.getFloat(0), -camera.buffer.getFloat(4), -camera.buffer.getFloat(8));
        return matrix;
    }

    private static float sunOrMoonComponent(int component) {
        double angle = ((worldTimeTicks() % 24000L) / 24000.0) * Math.PI * 2.0 - Math.PI / 2.0;
        return switch (component) {
            case 0 -> (float) Math.cos(angle);
            case 1 -> (float) Math.sin(angle);
            default -> (float) Math.sin(angle * 0.73) * 0.35f;
        };
    }

    private static MappedBuffer identityMat4() {
        MappedBuffer buffer = new MappedBuffer(64);
        for (int i = 0; i < 16; i++) buffer.putFloat(i * 4, (i % 5) == 0 ? 1.0f : 0.0f);
        return buffer;
    }

    private static MappedBuffer inverseMapped(MappedBuffer source) {
        try {
            MappedBuffer buffer = new MappedBuffer(64);
            Matrix4f matrix = new Matrix4f(source.buffer.asFloatBuffer());
            matrix.invert().get(buffer.buffer.asFloatBuffer());
            return buffer;
        } catch (Throwable throwable) {
            return identityMat4();
        }
    }

    private static MappedBuffer vec3Buffer(float x, float y, float z) {
        MappedBuffer buffer = new MappedBuffer(16);
        buffer.putFloat(0, x);
        buffer.putFloat(4, y);
        buffer.putFloat(8, z);
        buffer.putFloat(12, 0.0f);
        return buffer;
    }

    private static MappedBuffer vec4Buffer(float x, float y, float z, float w) {
        MappedBuffer buffer = new MappedBuffer(16);
        buffer.putFloat(0, x);
        buffer.putFloat(4, y);
        buffer.putFloat(8, z);
        buffer.putFloat(12, w);
        return buffer;
    }

    private static int eyeInWaterState() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.gameRenderer != null && client.gameRenderer.getMainCamera() != null) {
                String fluid = String.valueOf(client.gameRenderer.getMainCamera().getFluidInCamera());
                if (fluid.contains("WATER")) return 1;
                if (fluid.contains("LAVA")) return 2;
                if (fluid.contains("POWDER_SNOW")) return 3;
            }
        } catch (Throwable ignored) {
        }
        return 0;
    }

    private static long worldTimeTicks() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.level != null) {
                return client.level.getOverworldClockTime();
            }
        } catch (Throwable ignored) {
        }
        return (System.currentTimeMillis() / 50L) % 24000L;
    }

    private static float rainStrength() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.level != null) {
                return client.level.getRainLevel(1.0f);
            }
        } catch (Throwable ignored) {
        }
        return 0.0f;
    }

    private static float timeBrightness() {
        double angle = ((worldTimeTicks() % 24000L) / 24000.0) * Math.PI * 2.0;
        return (float) Math.max(0.0, Math.sin(angle - Math.PI / 2.0) * 0.5 + 0.5);
    }

    private static float biomeFlag(String... needles) {
        String key = currentBiomeKey();
        for (String needle : needles) {
            if (key.contains(needle)) return 1.0f;
        }
        return 0.0f;
    }

    private static String currentBiomeKey() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.level != null && client.player != null) {
                return client.level.getBiome(client.player.blockPosition())
                        .unwrapKey()
                        .map(resourceKey -> resourceKey.identifier().toString())
                        .orElse("");
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static MappedBuffer sunOrMoonPosition(boolean sun) {
        double angle = ((worldTimeTicks() % 24000L) / 24000.0) * Math.PI * 2.0 - Math.PI / 2.0;
        if (!sun) {
            angle += Math.PI;
        }
        float x = (float) (Math.cos(angle) * 100.0);
        float y = (float) (Math.sin(angle) * 100.0);
        return vec3Buffer(x, y, 0.0f);
    }

    private static MappedBuffer cameraPositionBuffer() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.gameRenderer != null && client.gameRenderer.getMainCamera() != null) {
                var position = client.gameRenderer.getMainCamera().position();
                return vec3Buffer((float) position.x(), (float) position.y(), (float) position.z());
            }
        } catch (Throwable ignored) {
        }
        return vec3Buffer(0.0f, 64.0f, 0.0f);
    }

    private static void addContiguousImageDescriptors(Pipeline.Builder builder, IrisProgramSource program) {
        int maxBinding = 0;
        boolean[] storage = new boolean[64];
        boolean[] sampler = new boolean[64];
        String[] names = new String[64];
        for (String source : new String[]{program.vertexSource(), program.fragmentSource(), program.computeSource()}) {
            if (source == null) {
                continue;
            }
            Matcher matcher = DESCRIPTOR_BINDING.matcher(source);
            while (matcher.find()) {
                int binding = Integer.parseInt(matcher.group(1));
                if (binding == 0 || binding >= storage.length) {
                    continue;
                }
                maxBinding = Math.max(maxBinding, binding);
                String type = matcher.group(2);
                String variableName = descriptorNameAfter(source, matcher.end());
                if (type.contains("sampler")) {
                    sampler[binding] = true;
                } else if (type.contains("image")) {
                    storage[binding] = true;
                }
                if (!variableName.isBlank()) {
                    names[binding] = variableName;
                }
            }
        }

        for (int binding = 1; binding <= maxBinding; binding++) {
            int descriptorType = storage[binding] && !sampler[binding] ? 3 : 1;
            String qualifier = storage[binding] && !sampler[binding] ? "image2D" : "sampler2D";
            String name = names[binding] == null ? "veyra_binding_" + binding : names[binding];
            builder.addImageDescriptor(new ImageDescriptor(binding, qualifier, name, textureIndexFor(name), descriptorType));
        }
    }

    private static String descriptorNameAfter(String source, int offset) {
        int semicolon = source.indexOf(';', offset);
        if (semicolon < 0) {
            return "";
        }
        String declaration = source.substring(offset, semicolon).trim();
        int space = declaration.lastIndexOf(' ');
        String name = (space >= 0 ? declaration.substring(space + 1) : declaration).trim();
        int bracket = name.indexOf('[');
        if (bracket >= 0) {
            name = name.substring(0, bracket).trim();
        }
        return name;
    }

    private static int textureIndexFor(String name) {
        if (name.startsWith("colortex")) {
            return parseTrailingIndex(name, 0);
        }
        if (name.equals("gaux1")) return 1;
        if (name.equals("gaux2")) return 2;
        if (name.equals("gaux3")) return 3;
        if (name.equals("gaux4")) return 4;
        if (name.equals("gcolor")) return 0;
        if (name.equals("gdepth") || name.equals("gnormal")) return 3;
        if (name.equals("normals")) return 3;
        if (name.equals("specular")) return 1;
        if (name.equals("floodfillSampler") || name.equals("voxelSampler")) return 5;
        if (name.equals("composite")) return 3;
        if (name.equals("dhDepthTex0") || name.equals("dhDepthTex") || name.equals("vxDepthTexOpaque")) return 8;
        if (name.equals("dhDepthTex1") || name.equals("vxDepthTexTrans")) return 9;
        if (name.startsWith("depthtex")) {
            return Math.min(10, 8 + parseTrailingIndex(name, 0));
        }
        if (name.equals("noisetex")) return 6;
        if (name.startsWith("shadowcolor")) return 4;
        if (name.startsWith("shadowtex")) {
            return 11;
        }
        if (name.equals("tex") || name.equals("texture") || name.equals("veyra_TextureSampler")) return 0;
        if (name.equals("lightmap")) return 2;
        return 0;
    }

    private static int parseTrailingIndex(String value, int fallback) {
        int i = value.length() - 1;
        while (i >= 0 && Character.isDigit(value.charAt(i))) {
            i--;
        }
        if (i == value.length() - 1) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.substring(i + 1));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    public static ExecutionResult executeFullscreenPasses(List<dev.blocktracker.shader.IrisRenderPlan.Pass> passes) {
        Renderer renderer = Renderer.getInstance();
        int executed = 0;
        int max = Integer.getInteger("veyra.iris.executeGraph.maxPasses", 64);
        List<String> names = new ArrayList<>();
        if (drawEnabled() && copyMainSceneToScreenSource(renderer)) {
            names.add("scene/copy_screen_source");
        }
        if (drawEnabled()) {
            List<String> bootstrapTargets = Boolean.getBoolean("veyra.iris.disableTerrainCapture")
                    ? List.of("colortex0", "colortex1", "colortex2", "colortex3", "colortex4", "colortex5", "colortex7")
                    : List.of("colortex2", "colortex4", "colortex5", "colortex7");
            for (String target : bootstrapTargets) {
                if (copyMainSceneToColorTarget(renderer, target)) {
                    names.add("scene/copy_" + target);
                }
            }
        }
        if (bindMainDepthAsIrisDepth(renderer)) {
            names.add("scene/bind_depthtex0_1_2");
        }
        if (bindSolasCustomTextures()) {
            names.add("scene/bind_solas_custom_textures");
        }
        if (bindShadowFallback()) {
            names.add("scene/bind_shadow_fallback");
        }
        String activeDimensionPrefix = activeDimensionPrefix();
        for (dev.blocktracker.shader.IrisRenderPlan.Pass pass : passes) {
            if (!isFullscreenKind(pass.kind()) || !isActiveDimensionPass(pass.name(), activeDimensionPrefix)) {
                continue;
            }
            GraphicsPipeline pipeline = GRAPHICS_BY_PROGRAM.get(pass.name());
            if (pipeline == null || pass.drawBuffers().isEmpty()) {
                continue;
            }
            for (int drawBufferIndex = 0; drawBufferIndex < pass.drawBuffers().size(); drawBufferIndex++) {
                String targetName = "colortex" + pass.drawBuffers().get(drawBufferIndex);
                RenderPass renderPass = dev.blocktracker.shader.VeyraIrisTargetManager.colorPass(targetName);
                var framebuffer = dev.blocktracker.shader.VeyraIrisTargetManager.colorTarget(targetName);
                if (renderPass == null || framebuffer == null) {
                    continue;
                }
                try {
                    GraphicsPipeline passPipeline = pass.drawBuffers().size() == 1
                            ? pipeline
                            : serialMrtPipeline(pass, drawBufferIndex, renderPass);
                    if (passPipeline == null) {
                        continue;
                    }
                    if (renderer.beginRenderPass(renderPass, framebuffer)) {
                        if (drawEnabled()) {
                            bindProgramSpecificTextures(pass.name());
                            renderer.bindGraphicsPipeline(passPipeline);
                            renderer.uploadAndBindUBOs(passPipeline);
                            vkCmdDrawFullscreenTriangle();
                        }
                        renderer.endRenderPass();
                        dev.blocktracker.shader.VeyraIrisTargetManager.bindSelectorSlots();
                        bindSolasCustomTextures();
                        bindShadowFallback();
                        names.add(pass.name() + "->" + targetName);
                    }
                } catch (Throwable throwable) {
                    LOGGER.warn("Failed executing Iris fullscreen pass {} target {}", pass.name(), targetName, throwable);
                }
            }
            executed++;
            if (executed >= max) {
                return new ExecutionResult(executed, names);
            }
        }
        if (drawEnabled() && !"false".equalsIgnoreCase(System.getProperty("veyra.iris.presentToScreen"))) {
            if (drawFinalToMainFramebuffer(renderer)) {
                names.add("screen/final");
            }
        }
        return new ExecutionResult(executed, names);
    }

    public static GraphicsPipeline terrainGbufferPipeline(RenderPass renderPass) {
        return TERRAIN_GBUFFER_PIPELINES.computeIfAbsent(renderPass, pass -> {
            Pipeline.Builder builder = new Pipeline.Builder(CustomVertexFormat.COMPRESSED_TERRAIN, "veyra_iris/terrain_gbuffer_colortex0");
            builder.applyConfig(PipelineConfigs.TERRAIN);
            builder.renderPass = pass;
            builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, TERRAIN_GBUFFER_VERTEX_SOURCE);
            builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, TERRAIN_GBUFFER_FRAGMENT_SOURCE);
            GraphicsPipeline pipeline = builder.createGraphicsPipeline();
            for (UBO buffer : pipeline.getBuffers()) {
                buffer.setUseGlobalBuffer(true);
            }
            LOGGER.info("Created VulkanMod-compatible Iris terrain gbuffers pipeline for colortex0");
            return pipeline;
        });
    }

    public static GraphicsPipeline terrainSolasGbufferPipeline(RenderPass renderPass, int drawBufferIndex) {
        return terrainSolasProgramPipeline(renderPass, "gbuffers_terrain", drawBufferIndex);
    }

    public static GraphicsPipeline waterSolasGbufferPipeline(RenderPass renderPass, int drawBufferIndex) {
        // Solas water currently compiles to SPIR-V but MoltenVK rejects the
        // fragment at graphics-pipeline creation with VulkanMod's compressed
        // terrain adapter. Keep this opt-in while the adapter is specialized;
        // otherwise one failed translucent layer retries every frame.
        if (!Boolean.getBoolean("veyra.iris.experimentalSolasWater")) {
            return null;
        }
        return terrainSolasProgramPipeline(renderPass, "gbuffers_water", drawBufferIndex);
    }

    private static GraphicsPipeline terrainSolasProgramPipeline(RenderPass renderPass, String localProgramName, int drawBufferIndex) {
        IrisProgramSource program = PROGRAMS_BY_NAME.get(activeProgramName(localProgramName));
        if (program == null || program.fragmentSource() == null) {
            return null;
        }
        String key = System.identityHashCode(renderPass) + ":" + localProgramName + ":" + drawBufferIndex + ":" + program.name();
        return TERRAIN_SOLAS_GBUFFER_PIPELINES.computeIfAbsent(key, ignored -> {
            Pipeline.Builder builder = new Pipeline.Builder(CustomVertexFormat.COMPRESSED_TERRAIN, "veyra_iris/solas_terrain_" + drawBufferIndex);
            builder.applyConfig(PipelineConfigs.TERRAIN);
            builder.addUBO(createVeyraUbo(program, 5));
            builder.addImageDescriptor(new ImageDescriptor(6, "sampler2D", "noisetex", 6, 1));
            builder.addImageDescriptor(new ImageDescriptor(7, "sampler2D", "specular", 1, 1));
            builder.addImageDescriptor(new ImageDescriptor(8, "sampler2D", "normals", 3, 1));
            builder.addImageDescriptor(new ImageDescriptor(9, "sampler3D", "floodfillSampler", 5, 1));
            builder.addImageDescriptor(new ImageDescriptor(10, "sampler3D", "floodfillSamplerCopy", 5, 1));
            builder.addImageDescriptor(new ImageDescriptor(11, "sampler2D", "shadowtex0", 11, 1));
            builder.addImageDescriptor(new ImageDescriptor(12, "sampler2D", "shadowtex1", 11, 1));
            builder.addImageDescriptor(new ImageDescriptor(13, "sampler2D", "shadowcolor0", 4, 1));
            builder.renderPass = renderPass;
            builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, TERRAIN_SOLAS_VERTEX_SOURCE);
            builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, solasTerrainFragment(program.fragmentSource(), drawBufferIndex));
            GraphicsPipeline pipeline = builder.createGraphicsPipeline();
            for (UBO buffer : pipeline.getBuffers()) buffer.setUseGlobalBuffer(true);
            LOGGER.info("Created real Solas {} pipeline {} drawbuffer {}", localProgramName, program.name(), drawBufferIndex);
            return pipeline;
        });
    }

    private static String solasTerrainFragment(String source, int drawBufferIndex) {
        String shifted = source
                .replace("layout(binding = 0) uniform VeyraIrisUniforms", "layout(binding = 5) uniform VeyraIrisUniforms")
                .replace("layout(binding = 1) uniform sampler2D tex", "layout(binding = 3) uniform sampler2D tex")
                .replace("layout(binding = 2) uniform sampler2D noisetex", "layout(binding = 6) uniform sampler2D noisetex")
                .replace("layout(binding = 3) uniform sampler2D specular", "layout(binding = 7) uniform sampler2D specular")
                .replace("layout(binding = 4) uniform sampler2D normals", "layout(binding = 8) uniform sampler2D normals")
                .replace("layout(binding = 5) uniform sampler3D floodfillSampler", "layout(binding = 9) uniform sampler3D floodfillSampler")
                .replace("layout(binding = 6) uniform sampler3D floodfillSamplerCopy", "layout(binding = 10) uniform sampler3D floodfillSamplerCopy")
                .replace("layout(binding = 8) uniform sampler2D shadowtex0", "layout(binding = 11) uniform sampler2D shadowtex0")
                .replace("layout(binding = 9) uniform sampler2D shadowtex1", "layout(binding = 12) uniform sampler2D shadowtex1")
                .replace("layout(binding = 10) uniform sampler2D shadowcolor0", "layout(binding = 13) uniform sampler2D shadowcolor0");
        return fragmentForSingleDrawBuffer(shifted, drawBufferIndex);
    }

    public static GraphicsPipeline terrainShadowPipeline(RenderPass renderPass) {
        return TERRAIN_SHADOW_PIPELINES.computeIfAbsent(renderPass, pass -> {
            Pipeline.Builder builder = new Pipeline.Builder(CustomVertexFormat.COMPRESSED_TERRAIN, "veyra_iris/terrain_shadow_depth");
            builder.applyConfig(PipelineConfigs.TERRAIN);
            builder.renderPass = pass;
            builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, TERRAIN_GBUFFER_VERTEX_SOURCE);
            builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, TERRAIN_SHADOW_FRAGMENT_SOURCE);
            GraphicsPipeline pipeline = builder.createGraphicsPipeline();
            for (UBO buffer : pipeline.getBuffers()) buffer.setUseGlobalBuffer(true);
            LOGGER.info("Created VulkanMod-compatible Iris terrain shadow depth pipeline");
            return pipeline;
        });
    }

    public static GraphicsPipeline terrainShadowColorPipeline(RenderPass renderPass) {
        return TERRAIN_SHADOW_COLOR_PIPELINES.computeIfAbsent(renderPass, pass -> {
            Pipeline.Builder builder = new Pipeline.Builder(CustomVertexFormat.COMPRESSED_TERRAIN, "veyra_iris/terrain_shadow_color");
            builder.applyConfig(PipelineConfigs.TERRAIN);
            builder.renderPass = pass;
            builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, TERRAIN_GBUFFER_VERTEX_SOURCE);
            builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, TERRAIN_SHADOW_COLOR_FRAGMENT_SOURCE);
            GraphicsPipeline pipeline = builder.createGraphicsPipeline();
            for (UBO buffer : pipeline.getBuffers()) buffer.setUseGlobalBuffer(true);
            LOGGER.info("Created VulkanMod-compatible Iris terrain shadow color pipeline");
            return pipeline;
        });
    }

    public static GraphicsPipeline terrainWaterAuxPipeline(RenderPass renderPass, int target) {
        String key = System.identityHashCode(renderPass) + ":" + target;
        return TERRAIN_WATER_AUX_PIPELINES.computeIfAbsent(key, ignored -> {
            Pipeline.Builder builder = new Pipeline.Builder(CustomVertexFormat.COMPRESSED_TERRAIN, "veyra_iris/terrain_water_aux_colortex" + target);
            builder.applyConfig(PipelineConfigs.TERRAIN);
            builder.renderPass = renderPass;
            builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, TERRAIN_GBUFFER_VERTEX_SOURCE);
            builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, target == 3 ? TERRAIN_WATER_NORMAL_FRAGMENT_SOURCE : TERRAIN_WATER_AUX_FRAGMENT_SOURCE);
            GraphicsPipeline pipeline = builder.createGraphicsPipeline();
            for (UBO buffer : pipeline.getBuffers()) buffer.setUseGlobalBuffer(true);
            LOGGER.info("Created VulkanMod-compatible Iris translucent/water aux pipeline for colortex{}", target);
            return pipeline;
        });
    }

    private static GraphicsPipeline serialMrtPipeline(dev.blocktracker.shader.IrisRenderPlan.Pass pass, int drawBufferIndex, RenderPass renderPass) {
        String targetName = "colortex" + pass.drawBuffers().get(drawBufferIndex);
        String key = pass.name() + "#" + targetName;
        GraphicsPipeline existing = SERIAL_MRT_PIPELINES.get(key);
        if (existing != null) {
            return existing;
        }
        IrisProgramSource program = PROGRAMS_BY_NAME.get(pass.name());
        if (program == null) {
            return null;
        }
        Pipeline.Builder builder = new Pipeline.Builder(vertexFormatFor(program.name()), "veyra_iris/serial_mrt/" + pass.name() + "_" + targetName);
        builder.addUBO(createVeyraUbo(program));
        addContiguousImageDescriptors(builder, program);
        builder.renderPass = renderPass;
        builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, program.vertexSource());
        builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, fragmentForSingleDrawBuffer(program.fragmentSource(), drawBufferIndex));
        GraphicsPipeline created = builder.createGraphicsPipeline();
        SERIAL_MRT_PIPELINES.put(key, created);
        GRAPHICS_PIPELINES.add(created);
        LOGGER.info("Created serialized Iris MRT pipeline {} output location {} -> {}", pass.name(), drawBufferIndex, targetName);
        return created;
    }

    private static String fragmentForSingleDrawBuffer(String source, int selectedLocation) {
        if (source == null) {
            return null;
        }
        Pattern outputPattern = Pattern.compile("layout\\s*\\(\\s*location\\s*=\\s*(\\d+)\\s*\\)\\s*out\\s+([^;]+?)\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*;");
        Matcher matcher = outputPattern.matcher(source);
        StringBuffer out = new StringBuffer();
        while (matcher.find()) {
            int location = Integer.parseInt(matcher.group(1));
            String type = matcher.group(2).trim();
            String name = matcher.group(3).trim();
            String replacement = location == selectedLocation
                    ? "layout(location = 0) out " + type + " " + name + ";"
                    : type + " " + name + ";";
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return out.toString();
    }

    private static boolean drawEnabled() {
        return !"false".equalsIgnoreCase(System.getProperty("veyra.iris.executeGraph.draw"));
    }

    private static boolean bindMainDepthAsIrisDepth(Renderer renderer) {
        try {
            Framebuffer mainFramebuffer = renderer.getMainPass() == null ? null : renderer.getMainPass().getMainFramebuffer();
            if (mainFramebuffer == null || mainFramebuffer.getDepthAttachment() == null) {
                return false;
            }
            transitionImageToShaderRead(mainFramebuffer.getDepthAttachment());
            VTextureSelector.bindTexture(8, mainFramebuffer.getDepthAttachment());
            VTextureSelector.bindTexture(9, mainFramebuffer.getDepthAttachment());
            VTextureSelector.bindTexture(10, mainFramebuffer.getDepthAttachment());
            return true;
        } catch (Throwable throwable) {
            LOGGER.warn("Failed binding main depth as Iris depthtex", throwable);
            return false;
        }
    }

    private static void transitionImageToShaderRead(Object vulkanImage) throws ReflectiveOperationException {
        Class<?> memoryStackClass = Class.forName("org.lwjgl.system.MemoryStack");
        Object stack = memoryStackClass.getMethod("stackPush").invoke(null);
        try {
            Class<?> vkCommandBufferClass = Class.forName("org.lwjgl.vulkan.VkCommandBuffer");
            Object commandBuffer = Renderer.class.getMethod("getCommandBuffer").invoke(null);
            vulkanImage.getClass()
                    .getMethod("transitionImageLayout", memoryStackClass, vkCommandBufferClass, int.class)
                    .invoke(vulkanImage, stack, commandBuffer, 5);
        } finally {
            if (stack instanceof AutoCloseable closeable) {
                try {
                    closeable.close();
                } catch (Exception ignored) {
                }
            }
        }
    }

    private static boolean bindProgramSpecificTextures(String programName) {
        try {
            IrisProgramSource program = PROGRAMS_BY_NAME.get(programName);
            String source = program == null ? "" : String.valueOf(program.fragmentSource()) + '\n' + String.valueOf(program.vertexSource()) + '\n' + String.valueOf(program.computeSource());
            if (source.contains("shadowcolor0")) {
                Framebuffer shadowColor = dev.blocktracker.shader.VeyraIrisTargetManager.colorTarget("shadowcolor0");
                if (shadowColor != null && shadowColor.getColorAttachment() != null) {
                    transitionImageToShaderRead(shadowColor.getColorAttachment());
                    VTextureSelector.bindTexture(4, shadowColor.getColorAttachment());
                } else {
                    VTextureSelector.bindTexture(4, VTextureSelector.getWhiteTexture());
                }
            }
            return true;
        } catch (Throwable throwable) {
            LOGGER.warn("Failed binding Iris program-specific textures for {}", programName, throwable);
            return false;
        }
    }

    private static boolean bindShadowFallback() {
        try {
            Framebuffer shadow = dev.blocktracker.shader.VeyraIrisTargetManager.depthTarget("shadowtex0");
            if (shadow != null && shadow.getDepthAttachment() != null) {
                transitionImageToShaderRead(shadow.getDepthAttachment());
                VTextureSelector.bindTexture(11, shadow.getDepthAttachment());
            } else {
                VTextureSelector.bindTexture(11, VTextureSelector.getWhiteTexture());
            }
            return true;
        } catch (Throwable throwable) {
            LOGGER.warn("Failed binding Iris shadow fallback", throwable);
            return false;
        }
    }

    private static boolean bindSolasCustomTextures() {
        try {
            ensureSolasCustomTextures();
            if (solasNoiseTexture != null) {
                transitionImageToShaderRead(solasNoiseTexture);
                VTextureSelector.bindTexture(6, solasNoiseTexture);
            }
            if (solasMilkyWayTexture != null) {
                transitionImageToShaderRead(solasMilkyWayTexture);
                // Solas declares texture.deferred.depthtex2=tex/milkyWay.png.
                // Bind it to the Iris depthtex2 descriptor slot for deferred/final sampling.
                VTextureSelector.bindTexture(10, solasMilkyWayTexture);
            }
            return solasNoiseTexture != null || solasMilkyWayTexture != null;
        } catch (Throwable throwable) {
            LOGGER.warn("Failed binding Solas custom textures", throwable);
            return false;
        }
    }

    private static void ensureSolasCustomTextures() throws Exception {
        Path shaders = Minecraft.getInstance().gameDirectory.toPath()
                .resolve("shaderpacks")
                .resolve("Solas Shader V3.6")
                .resolve("shaders")
                .resolve("tex");
        if (solasNoiseTexture == null) {
            solasNoiseTexture = loadTexture(shaders.resolve("noise.png"), "Solas noise");
        }
        if (solasMilkyWayTexture == null) {
            solasMilkyWayTexture = loadTexture(shaders.resolve("milkyWay.png"), "Solas milkyWay");
        }
    }

    private static VulkanImage loadTexture(Path path, String name) throws Exception {
        if (!Files.exists(path)) {
            LOGGER.warn("Missing Solas custom texture {} at {}", name, path);
            return null;
        }
        BufferedImage image = ImageIO.read(path.toFile());
        if (image == null) {
            return null;
        }
        int width = image.getWidth();
        int height = image.getHeight();
        ByteBuffer pixels = ByteBuffer.allocateDirect(width * height * 4);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int argb = image.getRGB(x, y);
                pixels.put((byte) ((argb >> 16) & 0xFF));
                pixels.put((byte) ((argb >> 8) & 0xFF));
                pixels.put((byte) (argb & 0xFF));
                pixels.put((byte) ((argb >> 24) & 0xFF));
            }
        }
        pixels.flip();
        VulkanImage texture = VulkanImage.builder(width, height)
                .setName(name)
                .setFormat(VulkanImage.DefaultFormat)
                .setUsage(6)
                .setLinearFiltering(true)
                .setClamp(true)
                .createVulkanImage();
        texture.uploadSubTextureAsync(0, width, height, 0, 0, 0, 0, width, pixels);
        LOGGER.info("Loaded {} custom texture {}x{} from {}", name, width, height, path);
        return texture;
    }

    private static boolean copyMainSceneToColorTarget(Renderer renderer, String targetName) {
        Framebuffer target = dev.blocktracker.shader.VeyraIrisTargetManager.colorTarget(targetName);
        RenderPass renderPass = dev.blocktracker.shader.VeyraIrisTargetManager.colorPass(targetName);
        if (target == null || renderPass == null) {
            return false;
        }
        try {
            bindMainSceneAsColortex0(renderer);
            if (targetName.equals("colortex3")) {
                bindMainDepthAsIrisDepth(renderer);
            }
            GraphicsPipeline pipeline = SCENE_COPY_PIPELINES.get(targetName);
            if (pipeline == null) {
                pipeline = targetName.equals("colortex3")
                        ? createDepthNormalPipeline("veyra_iris/scene_gbuffer_normal_colortex3", renderPass)
                        : createCopyPipeline("veyra_iris/scene_copy_" + targetName, renderPass, 0, SCENE_COPY_FRAGMENT_SOURCE);
                SCENE_COPY_PIPELINES.put(targetName, pipeline);
            }
            if (renderer.beginRenderPass(renderPass, target)) {
                renderer.bindGraphicsPipeline(pipeline);
                renderer.uploadAndBindUBOs(pipeline);
                vkCmdDrawFullscreenTriangle();
                renderer.endRenderPass();
                dev.blocktracker.shader.VeyraIrisTargetManager.bindSelectorSlots();
                return true;
            }
        } catch (Throwable throwable) {
            LOGGER.warn("Failed copying main scene into Iris {}", targetName, throwable);
        }
        return false;
    }

    private static boolean copyMainSceneToColortex0(Renderer renderer) {
        Framebuffer target = dev.blocktracker.shader.VeyraIrisTargetManager.colorTarget("colortex0");
        RenderPass renderPass = dev.blocktracker.shader.VeyraIrisTargetManager.colorPass("colortex0");
        if (target == null || renderPass == null) {
            return false;
        }
        try {
            bindMainSceneAsColortex0(renderer);
            if (sceneCopyPipeline == null) {
                sceneCopyPipeline = createCopyPipeline("veyra_iris/scene_copy_colortex0", renderPass, 0, SCENE_COPY_FRAGMENT_SOURCE);
            }
            if (renderer.beginRenderPass(renderPass, target)) {
                renderer.bindGraphicsPipeline(sceneCopyPipeline);
                renderer.uploadAndBindUBOs(sceneCopyPipeline);
                vkCmdDrawFullscreenTriangle();
                renderer.endRenderPass();
                dev.blocktracker.shader.VeyraIrisTargetManager.bindSelectorSlots();
                return true;
            }
        } catch (Throwable throwable) {
            LOGGER.warn("Failed copying main scene into Iris colortex0", throwable);
        }
        return false;
    }

    private static boolean copyMainSceneToScreenSource(Renderer renderer) {
        Framebuffer mainFramebuffer = renderer.getMainPass() == null ? null : renderer.getMainPass().getMainFramebuffer();
        if (mainFramebuffer == null) {
            return false;
        }
        try {
            if (screenSourceFramebuffer == null || screenSourceFramebuffer.getWidth() != mainFramebuffer.getWidth() || screenSourceFramebuffer.getHeight() != mainFramebuffer.getHeight()) {
                cleanScreenSource();
                screenSourceFramebuffer = Framebuffer.builder(mainFramebuffer.getWidth(), mainFramebuffer.getHeight(), 1, false).setLinearFiltering(true).build();
                screenSourceRenderPass = RenderPass.builder(screenSourceFramebuffer).build();
                screenSourceCopyPipeline = createCopyPipeline("veyra_iris/scene_copy_screen_source", screenSourceRenderPass, 0, SCENE_COPY_FRAGMENT_SOURCE);
            }
            bindMainSceneAsColortex0(renderer);
            if (renderer.beginRenderPass(screenSourceRenderPass, screenSourceFramebuffer)) {
                renderer.bindGraphicsPipeline(screenSourceCopyPipeline);
                renderer.uploadAndBindUBOs(screenSourceCopyPipeline);
                vkCmdDrawFullscreenTriangle();
                renderer.endRenderPass();
                // Slot 5 is reserved for Solas' floodfill/voxel scene samplers.
                // Slot 6 remains noisetex and is rebound by bindSolasCustomTextures().
                VTextureSelector.bindTexture(5, screenSourceFramebuffer.getColorAttachment());
                return true;
            }
        } catch (Throwable throwable) {
            LOGGER.warn("Failed copying main scene into screen source", throwable);
        }
        return false;
    }

    private static GraphicsPipeline createCopyPipeline(String name, RenderPass renderPass, int textureIndex, String fragmentSource) {
        Pipeline.Builder builder = new Pipeline.Builder(DefaultVertexFormat.EMPTY, name);
        builder.addUBO(new UBO("VeyraIrisUniforms", 0, 63, 4096, List.of()));
        builder.addImageDescriptor(new ImageDescriptor(1, "sampler2D", "tex", textureIndex, 1));
        builder.renderPass = renderPass;
        builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, FULLSCREEN_VERTEX_SOURCE);
        builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, fragmentSource);
        return builder.createGraphicsPipeline();
    }

    private static GraphicsPipeline createDepthNormalPipeline(String name, RenderPass renderPass) {
        Pipeline.Builder builder = new Pipeline.Builder(DefaultVertexFormat.EMPTY, name);
        builder.addUBO(new UBO("VeyraIrisUniforms", 0, 63, 4096, List.of(
                uniformInfo("float", "viewWidth"),
                uniformInfo("float", "viewHeight")
        )));
        builder.addImageDescriptor(new ImageDescriptor(1, "sampler2D", "depthtex0", 8, 1));
        builder.renderPass = renderPass;
        builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, FULLSCREEN_VERTEX_SOURCE);
        builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, DEPTH_NORMAL_FRAGMENT_SOURCE);
        return builder.createGraphicsPipeline();
    }

    private static Uniform.Info uniformInfo(String type, String name) {
        Uniform.Info info = Uniform.createUniformInfo(type, name);
        info.setBufferSupplier(() -> uniformValue(type, name));
        return info;
    }

    private static void bindMainSceneAsColortex0(Renderer renderer) {
        try {
            if (renderer.getMainPass() != null && renderer.getMainPass().getMainFramebuffer() != null) {
                renderer.getMainPass().bindAsTexture();
                if (renderer.getMainPass().getMainFramebuffer().getColorAttachment() != null) {
                    VTextureSelector.bindTexture(0, renderer.getMainPass().getMainFramebuffer().getColorAttachment());
                }
            }
        } catch (Throwable throwable) {
            LOGGER.warn("Failed binding main scene as Iris colortex0", throwable);
        }
    }

    private static boolean drawFinalToMainFramebuffer(Renderer renderer) {
        String activeFinalName = activeProgramName("final");
        IrisProgramSource finalProgram = PROGRAMS_BY_NAME.get(activeFinalName);
        Framebuffer mainFramebuffer = renderer.getMainPass() == null ? null : renderer.getMainPass().getMainFramebuffer();
        if (mainFramebuffer == null) {
            return false;
        }
        boolean useSolasFinal = !"false".equalsIgnoreCase(System.getProperty("veyra.iris.presentShaderFinal", "true"));
        String mode = useSolasFinal ? "solas_" + activeFinalName.replace('/', '_') : "fallback";
        try {
            if (screenFinalPipeline == null || screenFinalFramebuffer != mainFramebuffer || !mode.equals(screenFinalMode)) {
                if (screenFinalPipeline != null) {
                    screenFinalPipeline.cleanUp();
                }
                if (screenFinalRenderPass != null) {
                    screenFinalRenderPass.cleanUp();
                }
                screenFinalFramebuffer = mainFramebuffer;
                screenFinalMode = mode;
                screenFinalRenderPass = RenderPass.builder(mainFramebuffer).build();
                Pipeline.Builder builder = new Pipeline.Builder(DefaultVertexFormat.EMPTY, "veyra_iris/screen_" + mode);
                builder.addUBO(useSolasFinal && finalProgram != null ? createVeyraUbo(finalProgram) : new UBO("VeyraIrisUniforms", 0, 63, 4096, List.of()));
                builder.renderPass = screenFinalRenderPass;
                if (useSolasFinal && finalProgram != null) {
                    addContiguousImageDescriptors(builder, finalProgram);
                    builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, finalProgram.vertexSource());
                    builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, finalProgram.fragmentSource());
                } else {
                    builder.addImageDescriptor(new ImageDescriptor(1, "sampler2D", "tex", 6, 1));
                    builder.setShaderSrc(SPIRVUtils.ShaderKind.VERTEX_SHADER, FULLSCREEN_VERTEX_SOURCE);
                    builder.setShaderSrc(SPIRVUtils.ShaderKind.FRAGMENT_SHADER, FALLBACK_PRESENT_FRAGMENT_SOURCE);
                }
                screenFinalPipeline = builder.createGraphicsPipeline();
            }
            String presentSource = System.getProperty("veyra.iris.presentSource", "colortex0");
            Framebuffer finalTarget = dev.blocktracker.shader.VeyraIrisTargetManager.colorTarget(presentSource);
            VTextureSelector.bindTexture(6, finalTarget == null || finalTarget.getColorAttachment() == null
                    ? (screenSourceFramebuffer == null ? null : screenSourceFramebuffer.getColorAttachment())
                    : finalTarget.getColorAttachment());
            if (renderer.beginRenderPass(screenFinalRenderPass, mainFramebuffer)) {
                renderer.bindGraphicsPipeline(screenFinalPipeline);
                renderer.uploadAndBindUBOs(screenFinalPipeline);
                vkCmdDrawFullscreenTriangle();
                renderer.endRenderPass();
                return true;
            }
        } catch (Throwable throwable) {
            LOGGER.warn("Failed drawing Iris final pass to main framebuffer", throwable);
        }
        return false;
    }

    private static String activeDimensionPrefix() {
        try {
            Minecraft client = Minecraft.getInstance();
            if (client.level != null) {
                String dimension = client.level.dimension().toString();
                if (dimension.contains("the_end")) return "world1/";
                if (dimension.contains("the_nether")) return "world-1/";
            }
        } catch (Throwable ignored) {
        }
        return "";
    }

    private static String activeProgramName(String localName) {
        String prefix = activeDimensionPrefix();
        String dimensionName = prefix + localName;
        return PROGRAMS_BY_NAME.containsKey(dimensionName) ? dimensionName : localName;
    }

    private static boolean isActiveDimensionPass(String programName, String activePrefix) {
        boolean isDimensionOverride = programName.startsWith("world1/") || programName.startsWith("world-1/") || programName.startsWith("world0/");
        if (activePrefix.isEmpty()) {
            return !isDimensionOverride;
        }
        String local = programName.contains("/") ? programName.substring(programName.lastIndexOf('/') + 1) : programName;
        String activeName = activeProgramName(local);
        return programName.equals(activeName);
    }

    private static void vkCmdDrawFullscreenTriangle() throws ReflectiveOperationException {
        Class<?> vkCommandBufferClass = Class.forName("org.lwjgl.vulkan.VkCommandBuffer");
        Object commandBuffer = Renderer.class.getMethod("getCommandBuffer").invoke(null);
        Class.forName("org.lwjgl.vulkan.VK10")
                .getMethod("vkCmdDraw", vkCommandBufferClass, int.class, int.class, int.class, int.class)
                .invoke(null, commandBuffer, 3, 1, 0, 0);
    }

    private static boolean isFullscreenKind(String kind) {
        return kind.equals("deferred") || kind.equals("composite") || kind.equals("final");
    }

    public static void cleanUp() {
        GRAPHICS_BY_PROGRAM.clear();
        PROGRAMS_BY_NAME.clear();
        SERIAL_MRT_PIPELINES.clear();
        TERRAIN_GBUFFER_PIPELINES.forEach((renderPass, pipeline) -> {
            try {
                pipeline.cleanUp();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed cleaning Iris terrain gbuffers pipeline", throwable);
            }
        });
        TERRAIN_GBUFFER_PIPELINES.clear();
        TERRAIN_SOLAS_GBUFFER_PIPELINES.forEach((key, pipeline) -> {
            try {
                pipeline.cleanUp();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed cleaning real Solas terrain pipeline {}", key, throwable);
            }
        });
        TERRAIN_SOLAS_GBUFFER_PIPELINES.clear();
        TERRAIN_SHADOW_PIPELINES.forEach((renderPass, pipeline) -> {
            try {
                pipeline.cleanUp();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed cleaning Iris terrain shadow pipeline", throwable);
            }
        });
        TERRAIN_SHADOW_PIPELINES.clear();
        TERRAIN_SHADOW_COLOR_PIPELINES.forEach((renderPass, pipeline) -> {
            try {
                pipeline.cleanUp();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed cleaning Iris terrain shadow color pipeline", throwable);
            }
        });
        TERRAIN_SHADOW_COLOR_PIPELINES.clear();
        TERRAIN_WATER_AUX_PIPELINES.forEach((key, pipeline) -> {
            try {
                pipeline.cleanUp();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed cleaning Iris water aux pipeline {}", key, throwable);
            }
        });
        TERRAIN_WATER_AUX_PIPELINES.clear();
        SCENE_COPY_PIPELINES.forEach((name, pipeline) -> {
            try {
                pipeline.cleanUp();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed cleaning Iris scene copy pipeline {}", name, throwable);
            }
        });
        SCENE_COPY_PIPELINES.clear();
        if (sceneCopyPipeline != null) {
            try {
                sceneCopyPipeline.cleanUp();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed cleaning Iris scene copy pipeline", throwable);
            }
            sceneCopyPipeline = null;
        }
        cleanScreenSource();
        if (screenFinalPipeline != null) {
            try {
                screenFinalPipeline.cleanUp();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed cleaning Iris screen final pipeline", throwable);
            }
            screenFinalPipeline = null;
        }
        if (screenFinalRenderPass != null) {
            try {
                screenFinalRenderPass.cleanUp();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed cleaning Iris screen final render pass", throwable);
            }
            screenFinalRenderPass = null;
        }
        if (solasNoiseTexture != null) {
            try {
                solasNoiseTexture.free();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed cleaning Solas noise texture", throwable);
            }
            solasNoiseTexture = null;
        }
        if (solasMilkyWayTexture != null) {
            try {
                solasMilkyWayTexture.free();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed cleaning Solas milkyWay texture", throwable);
            }
            solasMilkyWayTexture = null;
        }
        screenFinalFramebuffer = null;
        screenFinalMode = null;
        for (GraphicsPipeline pipeline : List.copyOf(GRAPHICS_PIPELINES)) {
            try {
                pipeline.cleanUp();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed cleaning Iris pipeline {}", pipeline.name, throwable);
            }
        }
        GRAPHICS_PIPELINES.clear();
    }

    private static final String FULLSCREEN_VERTEX_SOURCE = """
#version 450
layout(location = 0) out vec2 texCoord;
const vec4 pos[3] = vec4[3](vec4(-1.0, -1.0, 0.0, 1.0), vec4(3.0, -1.0, 0.0, 1.0), vec4(-1.0, 3.0, 0.0, 1.0));
const vec2 uv[3] = vec2[3](vec2(0.0, 0.0), vec2(2.0, 0.0), vec2(0.0, 2.0));
void main() { texCoord = uv[gl_VertexIndex]; gl_Position = pos[gl_VertexIndex]; }
""";

    private static void cleanScreenSource() {
        if (screenSourceCopyPipeline != null) {
            try {
                screenSourceCopyPipeline.cleanUp();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed cleaning Iris screen source copy pipeline", throwable);
            }
            screenSourceCopyPipeline = null;
        }
        if (screenSourceRenderPass != null) {
            try {
                screenSourceRenderPass.cleanUp();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed cleaning Iris screen source render pass", throwable);
            }
            screenSourceRenderPass = null;
        }
        if (screenSourceFramebuffer != null) {
            try {
                screenSourceFramebuffer.cleanUp();
            } catch (Throwable throwable) {
                LOGGER.warn("Failed cleaning Iris screen source framebuffer", throwable);
            }
            screenSourceFramebuffer = null;
        }
    }

    private static final String SCENE_COPY_FRAGMENT_SOURCE = """
#version 450
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
layout(binding = 1) uniform sampler2D tex;
void main() { fragColor = texture(tex, texCoord); }
""";

    private static final String TERRAIN_SOLAS_VERTEX_SOURCE = """
#version 450
layout (binding = 0) uniform UniformBufferObject { mat4 MVP; int CurrentTime; };
layout (binding = 2) uniform UBO2 { ivec4 SectionOffsets[128]; vec4 SectionFadeFactors[128]; };
layout (push_constant) uniform pushConstant { vec3 ModelOffset; };
layout (binding = 4) uniform sampler2D Sampler2;
layout (location = 0) in ivec4 Position;
layout (location = 1) in uvec2 UV0;
layout (location = 2) in uint PackedColor;
layout(location = 0) out vec4 color;
layout(location = 1) out vec3 normal;
layout(location = 2) out vec3 binormal;
layout(location = 3) out vec3 tangent;
layout(location = 4) out vec2 texCoord;
layout(location = 5) out vec2 lmCoord;
layout(location = 6) flat out int mat;
layout(location = 7) out float dist;
layout(location = 8) flat out vec2 absMidCoordPos;
layout(location = 9) out vec2 signMidCoordPos;
layout(location = 10) out vec3 viewVector;
layout(location = 11) out vec4 vTexCoord;
layout(location = 12) out vec4 vTexCoordAM;
const float UV_INV = 1.0 / 32768.0;
const vec3 POSITION_INV = vec3(1.0 / 2048.0);
vec4 sample_lightmap2(sampler2D lightMap, uint uv) {
    const ivec2 lm = ivec2(bitfieldExtract(uv, 4, 4), bitfieldExtract(uv, 12, 4));
    return texelFetch(lightMap, lm, 0);
}
vec3 getVertexPosition() {
    const int encOffset = SectionOffsets[gl_InstanceIndex >> 2][gl_InstanceIndex & 3];
    const vec3 baseOffset = bitfieldExtract(ivec3(encOffset) >> ivec3(0, 16, 8), 0, 8);
    return fma(Position.xyz, POSITION_INV, ModelOffset + baseOffset);
}
void main() {
    vec3 pos = getVertexPosition();
    gl_Position = MVP * vec4(pos, 1.0);
    color = unpackUnorm4x8(PackedColor) * sample_lightmap2(Sampler2, Position.a);
    texCoord = UV0 * UV_INV;
    lmCoord = vec2(bitfieldExtract(Position.a, 4, 4), bitfieldExtract(Position.a, 12, 4)) / 15.0;
    normal = vec3(0.0, 1.0, 0.0);
    tangent = vec3(1.0, 0.0, 0.0);
    binormal = vec3(0.0, 0.0, 1.0);
    mat = 1;
    dist = length(pos);
    absMidCoordPos = vec2(0.5);
    signMidCoordPos = vec2(0.0);
    viewVector = normalize(max(length(pos), 0.0001) * vec3(0.0, 0.0, -1.0));
    vTexCoord = vec4(texCoord, 0.0, 1.0);
    vTexCoordAM = vec4(texCoord, 0.0, 1.0);
}
""";

    private static final String TERRAIN_GBUFFER_VERTEX_SOURCE = """
#version 450
layout (binding = 0) uniform UniformBufferObject { mat4 MVP; int CurrentTime; };
layout (binding = 2) uniform UBO2 { ivec4 SectionOffsets[128]; vec4 SectionFadeFactors[128]; };
layout (push_constant) uniform pushConstant { vec3 ModelOffset; };
layout (binding = 4) uniform sampler2D Sampler2;
layout (location = 0) in ivec4 Position;
layout (location = 1) in uvec2 UV0;
layout (location = 2) in uint PackedColor;
layout (location = 0) out vec4 vertexColor;
layout (location = 1) out vec2 texCoord0;
layout (location = 2) out vec3 localViewPos;
layout (location = 3) out flat float fadeFactor;
const float UV_INV = 1.0 / 32768.0;
const vec3 POSITION_INV = vec3(1.0 / 2048.0);
vec4 sample_lightmap2(sampler2D lightMap, uint uv) {
    const ivec2 lm = ivec2(bitfieldExtract(uv, 4, 4), bitfieldExtract(uv, 12, 4));
    return texelFetch(lightMap, lm, 0);
}
vec3 getVertexPosition() {
    const int encOffset = SectionOffsets[gl_InstanceIndex >> 2][gl_InstanceIndex & 3];
    const vec3 baseOffset = bitfieldExtract(ivec3(encOffset) >> ivec3(0, 16, 8), 0, 8);
    return fma(Position.xyz, POSITION_INV, ModelOffset + baseOffset);
}
void main() {
    vec3 pos = getVertexPosition();
    localViewPos = pos;
    gl_Position = MVP * vec4(pos, 1.0);
    vertexColor = unpackUnorm4x8(PackedColor) * sample_lightmap2(Sampler2, Position.a);
    texCoord0 = UV0 * UV_INV;
    fadeFactor = SectionFadeFactors[gl_InstanceIndex >> 2][gl_InstanceIndex & 3].x;
}
""";

    private static final String TERRAIN_GBUFFER_FRAGMENT_SOURCE = """
#version 450
layout(binding = 3) uniform sampler2D Sampler0;
layout(binding = 1) uniform UBO {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
    float AlphaCutout;
    ivec2 TextureSize;
    vec2 TexelSize;
    int UseRgss;
};
layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 texCoord0;
layout(location = 2) in vec3 localViewPos;
layout(location = 3) in flat float fadeFactor;
layout(location = 0) out vec4 fragColor;
void main() {
    vec4 albedo = texture(Sampler0, texCoord0) * vertexColor;
    if (albedo.a < AlphaCutout) discard;
    fragColor = albedo;
}
""";

    private static final String TERRAIN_SHADOW_FRAGMENT_SOURCE = """
#version 450
layout(binding = 3) uniform sampler2D Sampler0;
layout(binding = 1) uniform UBO {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
    float AlphaCutout;
    ivec2 TextureSize;
    vec2 TexelSize;
    int UseRgss;
};
layout(location = 1) in vec2 texCoord0;
layout(location = 2) in vec3 localViewPos;
void main() {
    if (texture(Sampler0, texCoord0).a < AlphaCutout) discard;
}
""";

    private static final String TERRAIN_SHADOW_COLOR_FRAGMENT_SOURCE = """
#version 450
layout(binding = 3) uniform sampler2D Sampler0;
layout(binding = 1) uniform UBO {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
    float AlphaCutout;
    ivec2 TextureSize;
    vec2 TexelSize;
    int UseRgss;
};
layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 texCoord0;
layout(location = 2) in vec3 localViewPos;
layout(location = 0) out vec4 fragColor;
void main() {
    vec4 albedo = texture(Sampler0, texCoord0) * vertexColor;
    if (albedo.a < AlphaCutout) discard;
    fragColor = vec4(albedo.rgb, 1.0);
}
""";

    private static final String TERRAIN_WATER_AUX_FRAGMENT_SOURCE = """
#version 450
layout(binding = 3) uniform sampler2D Sampler0;
layout(binding = 1) uniform UBO {
    vec4 FogColor;
    float FogEnvironmentalStart;
    float FogEnvironmentalEnd;
    float FogRenderDistanceStart;
    float FogRenderDistanceEnd;
    float FogSkyEnd;
    float FogCloudsEnd;
    float AlphaCutout;
    ivec2 TextureSize;
    vec2 TexelSize;
    int UseRgss;
};
layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 texCoord0;
layout(location = 2) in vec3 localViewPos;
layout(location = 0) out vec4 fragColor;
void main() {
    vec4 albedo = texture(Sampler0, texCoord0) * vertexColor;
    if (albedo.a < 0.01) discard;
    float water = clamp(1.0 - albedo.a, 0.0, 1.0);
    float refraction = max(water, 0.15);
    fragColor = vec4(refraction * water, water * 0.4 + 0.4, 1.0, 1.0);
}
""";

    private static final String TERRAIN_WATER_NORMAL_FRAGMENT_SOURCE = """
#version 450
layout(binding = 3) uniform sampler2D Sampler0;
layout(location = 0) in vec4 vertexColor;
layout(location = 1) in vec2 texCoord0;
layout(location = 2) in vec3 localViewPos;
layout(location = 0) out vec4 fragColor;
vec2 encodeNormal(vec3 normal) {
    float f = sqrt(max(normal.z * 8.0 + 8.0, 0.0001));
    return normal.xy / f + 0.5;
}
vec3 faceNormal() {
    vec3 dx = dFdx(localViewPos);
    vec3 dy = dFdy(localViewPos);
    vec3 n = normalize(cross(dx, dy));
    if (!gl_FrontFacing) n = -n;
    return n;
}
void main() {
    vec4 albedo = texture(Sampler0, texCoord0) * vertexColor;
    if (albedo.a < 0.01) discard;
    // Solas colortex3 layout: encoded normal, skylight-ish term, smoothness/material term.
    fragColor = vec4(encodeNormal(faceNormal()), 0.5, 0.35);
}
""";

    private static final String DEPTH_NORMAL_FRAGMENT_SOURCE = """
#version 450
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
layout(binding = 0) uniform VeyraIrisUniforms {
    float viewWidth;
    float viewHeight;
};
layout(binding = 1) uniform sampler2D depthtex0;

vec2 encodeNormal(vec3 normal) {
    float f = sqrt(max(normal.z * 8.0 + 8.0, 0.0001));
    return normal.xy / f + 0.5;
}

void main() {
    vec2 texel = 1.0 / vec2(max(viewWidth, 1.0), max(viewHeight, 1.0));
    float d  = texture(depthtex0, texCoord).r;
    float dx = texture(depthtex0, texCoord + vec2(texel.x, 0.0)).r - texture(depthtex0, texCoord - vec2(texel.x, 0.0)).r;
    float dy = texture(depthtex0, texCoord + vec2(0.0, texel.y)).r - texture(depthtex0, texCoord - vec2(0.0, texel.y)).r;
    vec3 n = normalize(vec3(-dx * 96.0, -dy * 96.0, 1.0));
    vec2 enc = encodeNormal(n);
    float light = clamp(1.0 - d, 0.0, 1.0) * 0.5;
    fragColor = vec4(enc, light, 0.15);
}
""";

    private static final String FALLBACK_PRESENT_FRAGMENT_SOURCE = """
#version 450
layout(location = 0) in vec2 texCoord;
layout(location = 0) out vec4 fragColor;
layout(binding = 1) uniform sampler2D tex;
void main() {
    fragColor = texture(tex, texCoord);
}
""";



    public record BuildResult(int attempted, int created, int actualGraphicsPipelines, List<String> failures) {
    }

    public record ExecutionResult(int executed, List<String> passNames) {
    }
}
