package net.vulkanmod.render.engine;

import com.google.common.collect.Sets;
import com.mojang.blaze3d.opengl.Uniform;
import com.mojang.blaze3d.opengl.Uniform.Sampler;
import com.mojang.blaze3d.opengl.Uniform.Ubo;
import com.mojang.blaze3d.opengl.Uniform.Utb;
import com.mojang.blaze3d.pipeline.RenderPipeline.UniformDescription;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.vulkanmod.vulkan.shader.Pipeline;
import net.vulkanmod.vulkan.shader.descriptor.ImageDescriptor;
import net.vulkanmod.vulkan.shader.descriptor.UBO;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

public class EGlProgram {
   private static final Logger LOGGER = LogUtils.getLogger();
   public static Set<String> BUILT_IN_UNIFORMS = Sets.newHashSet(new String[]{"Projection", "Lighting", "Fog", "Globals"});
   public static EGlProgram INVALID_PROGRAM = new EGlProgram(-1, "invalid");
   private final Map<String, Uniform> uniformsByName = new HashMap<>();
   private final int programId;
   private final String debugLabel;

   public EGlProgram(int i, String string) {
      this.programId = i;
      this.debugLabel = string;
   }

   public void setupUniforms(Pipeline pipeline, List<UniformDescription> uniformDescriptions, List<String> samplers) {
      int i = 0;
      int j = 0;

      for (UniformDescription uniformDescription : uniformDescriptions) {
         String name = uniformDescription.name();

         Uniform uniform = (Uniform)(switch (uniformDescription.type()) {
            case UNIFORM_BUFFER -> {
               UBO ubo = pipeline.getUBO(name);
               if (ubo == null) {
                  yield null;
               } else {
                  int binding = ubo.binding;
                  yield new Ubo(binding);
               }
            }
            case TEXEL_BUFFER -> {
               int binding = i++;
               yield new Utb(binding, 0, Objects.requireNonNull(uniformDescription.textureFormat()));
            }
            default -> throw new MatchException(null, null);
         });
         this.uniformsByName.put(name, uniform);
      }

      for (String samplerName : samplers) {
         ImageDescriptor imageDescriptor = pipeline.getImageDescriptor(samplerName);
         if (imageDescriptor != null) {
            int binding = imageDescriptor.getBinding();
            int imageIdx = imageDescriptor.imageIdx;
            this.uniformsByName.put(samplerName, new Sampler(binding, imageIdx));
         }
      }
   }

   @Nullable
   public Uniform getUniform(String string) {
      RenderSystem.assertOnRenderThread();
      return this.uniformsByName.get(string);
   }

   public int getProgramId() {
      return this.programId;
   }

   @Override
   public String toString() {
      return this.debugLabel;
   }

   public String getDebugLabel() {
      return this.debugLabel;
   }

   public Map<String, Uniform> getUniforms() {
      return this.uniformsByName;
   }
}
