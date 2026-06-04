package dev.blocktracker.shader;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class IrisSourceTransformer {
    private static final Pattern VERSION = Pattern.compile("(?m)^\\s*#version\\s+\\d+.*$");
    private static final Pattern FRAG_DATA = Pattern.compile("gl_FragData\\s*\\[\\s*(\\d+)\\s*]");
    private static final Pattern UNIFORM = Pattern.compile("(?m)^\\s*uniform\\s+([A-Za-z0-9_]+)\\s+([^;]+);\\s*$");
    private static final Pattern IO_DECL = Pattern.compile("^(\\s*)(flat\\s+)?(in|out|attribute|varying)\\s+([A-Za-z0-9_]+)\\s+([^;]+);(.*)$");

    private IrisSourceTransformer() {
    }

    public static String toVulkanGlsl(String source, Stage stage) {
        if (source == null || source.isBlank()) {
            return source;
        }

        String transformed = VERSION.matcher(source).replaceFirst("#version 450");
        if (!transformed.startsWith("#version")) {
            transformed = "#version 450\n" + transformed;
        }

        transformed = renameReservedTextureUniform(transformed);
        transformed = transformed
                .replace("texture2DLod(", "textureLod(")
                .replace("texture3DLod(", "textureLod(")
                .replace("textureCubeLod(", "textureLod(")
                .replace("shadow2DLod(", "textureLod(")
                .replace("texture2D(", "texture(")
                .replace("texture3D(", "texture(")
                .replace("textureCube(", "texture(")
                .replace("shadow2D(", "texture(");

        transformed = rewriteUniforms(transformed);
        transformed = rewriteQualifiedOpaqueUniformBindings(transformed);
        transformed = rewriteColorwheelCompatibility(transformed);
        transformed = rewriteDistantHorizonsCompatibility(transformed, stage);
        transformed = rewriteKnownIrisAttributes(transformed, stage);
        transformed = rewriteIoDeclarations(transformed, stage);
        transformed = injectCompatibilityHeader(transformed, stage);

        if (stage == Stage.FRAGMENT) {
            transformed = rewriteFragmentOutputs(transformed);
        }

        transformed = rewriteIoDeclarations(transformed, stage);
        return transformed;
    }

    private static String renameReservedTextureUniform(String source) {
        if (!source.matches("(?s).*uniform\\s+[^;]*\\btexture\\b[^;]*;.*")) {
            return source;
        }
        return Pattern.compile("\\btexture\\b(?!\\s*\\()").matcher(source).replaceAll("veyra_TextureSampler");
    }

    private static String rewriteUniforms(String source) {
        Matcher matcher = UNIFORM.matcher(source);
        StringBuilder out = new StringBuilder();
        StringBuilder block = new StringBuilder();
        List<String> seenUniformNames = new ArrayList<>();
        int samplerBinding = 1;

        while (matcher.find()) {
            String type = matcher.group(1);
            String declaration = matcher.group(2).trim();
            List<String> names = uniformNames(declaration);
            boolean duplicate = !names.isEmpty() && names.stream().allMatch(seenUniformNames::contains);
            String uniqueDeclaration = uniqueUniformDeclaration(declaration, seenUniformNames);

            if (duplicate || uniqueDeclaration.isBlank()) {
                matcher.appendReplacement(out, "");
            } else if (isOpaqueUniform(type)) {
                StringBuilder replacement = new StringBuilder();
                for (String part : splitDeclarations(uniqueDeclaration)) {
                    if (!part.isBlank()) {
                        replacement.append("layout(binding = ").append(samplerBinding++).append(") uniform ")
                                .append(type).append(' ').append(part.trim()).append(";\n");
                    }
                }
                matcher.appendReplacement(out, Matcher.quoteReplacement(replacement.toString().stripTrailing()));
            } else {
                block.append("    ").append(type).append(' ').append(uniqueDeclaration).append(";\n");
                matcher.appendReplacement(out, "");
            }
        }
        matcher.appendTail(out);

        if (!block.isEmpty()) {
            String ubo = "layout(binding = 0) uniform VeyraIrisUniforms {\n" + block + "};\n";
            return insertAfterVersion(out.toString(), ubo);
        }

        return out.toString();
    }

    private static List<String> uniformNames(String declaration) {
        List<String> names = new ArrayList<>();
        for (String part : declaration.split(",")) {
            String name = uniformName(part);
            if (!name.isBlank()) {
                names.add(name);
            }
        }
        return names;
    }

    private static String uniqueUniformDeclaration(String declaration, List<String> seenUniformNames) {
        List<String> uniqueParts = new ArrayList<>();
        for (String part : declaration.split(",")) {
            String name = uniformName(part);
            if (!name.isBlank() && !seenUniformNames.contains(name)) {
                seenUniformNames.add(name);
                uniqueParts.add(part.trim());
            }
        }
        return String.join(", ", uniqueParts);
    }

    private static String uniformName(String declarationPart) {
        String name = declarationPart.trim();
        int equals = name.indexOf('=');
        if (equals >= 0) {
            name = name.substring(0, equals).trim();
        }
        int bracket = name.indexOf('[');
        if (bracket >= 0) {
            name = name.substring(0, bracket).trim();
        }
        String[] tokens = name.split("\\s+");
        if (tokens.length == 0) {
            return "";
        }
        return tokens[tokens.length - 1];
    }

    private static boolean isOpaqueUniform(String type) {
        return type.contains("sampler") || type.contains("image") || type.startsWith("atomic_uint");
    }

    private static String rewriteQualifiedOpaqueUniformBindings(String source) {
        String[] lines = source.split("\\n", -1);
        StringBuilder out = new StringBuilder(source.length() + 256);
        int binding = nextDescriptorBinding(source);
        for (String line : lines) {
            String trimmed = line.trim();
            boolean opaque = trimmed.contains(" uniform ") || trimmed.startsWith("uniform ");
            opaque = opaque && (trimmed.contains("sampler") || trimmed.contains("image") || trimmed.contains("atomic_uint"));
            if (opaque && !trimmed.contains("binding") && trimmed.endsWith(";")) {
                int indentEnd = 0;
                while (indentEnd < line.length() && Character.isWhitespace(line.charAt(indentEnd))) {
                    indentEnd++;
                }
                String indent = line.substring(0, indentEnd);
                String declaration = line.substring(indentEnd);
                int uniformIndex = declaration.indexOf("uniform");
                int semicolon = declaration.lastIndexOf(';');
                String prefix = declaration.substring(0, uniformIndex + "uniform".length()).trim();
                String rest = declaration.substring(uniformIndex + "uniform".length(), semicolon).trim();
                int typeEnd = rest.indexOf(' ');
                if (typeEnd > 0) {
                    String type = rest.substring(0, typeEnd).trim();
                    String variables = rest.substring(typeEnd + 1).trim();
                    for (String variable : splitDeclarations(variables)) {
                        out.append(indent)
                                .append("layout(binding = ").append(binding++).append(") ")
                                .append(prefix).append(' ').append(type).append(' ').append(variable.trim()).append(';')
                                .append('\n');
                    }
                } else {
                    out.append(indent).append("layout(binding = ").append(binding++).append(") ").append(declaration).append('\n');
                }
            } else {
                out.append(line).append('\n');
            }
        }
        return out.toString();
    }

    private static int nextDescriptorBinding(String source) {
        Matcher matcher = Pattern.compile("layout\\s*\\(\\s*binding\\s*=\\s*(\\d+)\\s*\\)").matcher(source);
        int max = -1;
        while (matcher.find()) {
            max = Math.max(max, Integer.parseInt(matcher.group(1)));
        }
        return max + 1;
    }

    private static String rewriteColorwheelCompatibility(String source) {
        if (!source.contains("clrwl_computeFragment(")) {
            return source;
        }

        String transformed = source
                .replace("float ao;", "float clrwlAo;")
                .replace("clrwl_computeFragment(albedoTexture, albedo, lmCoordColorwheel, ao, overlayColor);", "clrwl_computeFragment(albedoTexture, albedo, lmCoordColorwheel, clrwlAo, overlayColor);");

        if (!transformed.contains("void clrwl_computeFragment")) {
            String stub = """

// Veyra stub for Colorwheel/Iris extension hook when the provider mod is absent.
void clrwl_computeFragment(vec4 albedoTexture, inout vec4 albedo, out vec2 lmCoordColorwheel, out float ao, out vec4 overlayColor) {
    lmCoordColorwheel = vec2(1.0);
    ao = 1.0;
    overlayColor = vec4(0.0);
}
""";
            int main = transformed.indexOf("void main");
            transformed = main > 0 ? transformed.substring(0, main) + stub + transformed.substring(main) : insertAfterVersion(transformed, stub);
        }
        return transformed;
    }

    private static String rewriteDistantHorizonsCompatibility(String source, Stage stage) {
        String transformed = source;
        if (stage == Stage.VERTEX && transformed.contains("dhMaterialId")) {
            String header = """

// Veyra stub for Distant Horizons vertex metadata when DH is absent.
const int dhMaterialId = 0;
const int DH_BLOCK_LEAVES = 1;
const int DH_BLOCK_ILLUMINATED = 2;
const int DH_BLOCK_WATER = 3;
""";
            transformed = insertAfterVersion(transformed, header);
        }
        return transformed;
    }

    private static String rewriteKnownIrisAttributes(String source, Stage stage) {
        if (stage != Stage.VERTEX) {
            return source;
        }
        return source
                .replaceAll("(?m)^\\s*(?:in|attribute)\\s+vec4\\s+mc_Entity\\s*;\\s*$", "const vec4 mc_Entity = vec4(0.0);")
                .replaceAll("(?m)^\\s*(?:in|attribute)\\s+vec4\\s+mc_midTexCoord\\s*;\\s*$", "const vec4 mc_midTexCoord = vec4(0.5, 0.5, 0.0, 1.0);")
                .replaceAll("(?m)^\\s*(?:in|attribute)\\s+vec4\\s+at_tangent\\s*;\\s*$", "const vec4 at_tangent = vec4(1.0, 0.0, 0.0, 1.0);")
                .replaceAll("(?m)^\\s*(?:in|attribute)\\s+vec3\\s+at_midBlock\\s*;\\s*$", "const vec3 at_midBlock = vec3(0.0);")
                .replaceAll("(?m)^\\s*(?:in|attribute)\\s+float\\s+at_velocity\\s*;\\s*$", "const float at_velocity = 0.0;");
    }

    private static String rewriteIoDeclarations(String source, Stage stage) {
        String[] lines = source.split("\\n", -1);
        StringBuilder out = new StringBuilder(source.length() + 512);
        int inLocation = stage == Stage.VERTEX ? 4 : 0;
        int outLocation = 0;

        for (String line : lines) {
            IoDeclaration io = parseIoDeclaration(line, stage);
            if (io != null && !line.contains("layout(")) {
                for (int i = 0; i < io.variables.size(); i++) {
                    String variable = io.variables.get(i);
                    int location = io.direction.equals("in") ? inLocation++ : outLocation++;
                    out.append(io.indent)
                            .append("layout(location = ").append(location).append(") ")
                            .append(io.flat)
                            .append(io.direction).append(' ')
                            .append(io.type).append(' ')
                            .append(variable.trim()).append(';');
                    if (i == io.variables.size() - 1) {
                        out.append(io.trailing);
                    }
                    out.append('\n');
                }
            } else {
                out.append(line).append('\n');
            }
        }

        return out.toString();
    }

    private static IoDeclaration parseIoDeclaration(String line, Stage stage) {
        int indentEnd = 0;
        while (indentEnd < line.length() && Character.isWhitespace(line.charAt(indentEnd))) {
            indentEnd++;
        }
        String indent = line.substring(0, indentEnd);
        String rest = line.substring(indentEnd);
        String flat = "";
        if (rest.startsWith("flat ")) {
            flat = "flat ";
            rest = rest.substring("flat ".length());
        }

        String qualifier;
        if (rest.startsWith("in ")) {
            qualifier = "in";
            rest = rest.substring(3);
        } else if (rest.startsWith("out ")) {
            qualifier = "out";
            rest = rest.substring(4);
        } else if (rest.startsWith("attribute ")) {
            qualifier = "attribute";
            rest = rest.substring(10);
        } else if (rest.startsWith("varying ")) {
            qualifier = "varying";
            rest = rest.substring(8);
        } else {
            return null;
        }

        int semicolon = rest.indexOf(';');
        if (semicolon < 0) {
            return null;
        }

        String beforeSemicolon = rest.substring(0, semicolon).trim();
        int typeEnd = beforeSemicolon.indexOf(' ');
        if (typeEnd < 0) {
            return null;
        }
        String type = beforeSemicolon.substring(0, typeEnd).trim();
        String variablesSource = beforeSemicolon.substring(typeEnd + 1).trim();
        if (variablesSource.isBlank()) {
            return null;
        }

        String direction = qualifier;
        if (qualifier.equals("attribute")) {
            direction = "in";
        } else if (qualifier.equals("varying")) {
            direction = stage == Stage.VERTEX ? "out" : "in";
        }

        return new IoDeclaration(indent, flat, direction, type, splitDeclarations(variablesSource), rest.substring(semicolon + 1));
    }

    private static List<String> splitDeclarations(String source) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        int bracketDepth = 0;
        int parenDepth = 0;
        for (int i = 0; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '[') bracketDepth++;
            else if (c == ']') bracketDepth = Math.max(0, bracketDepth - 1);
            else if (c == '(') parenDepth++;
            else if (c == ')') parenDepth = Math.max(0, parenDepth - 1);
            else if (c == ',' && bracketDepth == 0 && parenDepth == 0) {
                parts.add(source.substring(start, i).trim());
                start = i + 1;
            }
        }
        parts.add(source.substring(start).trim());
        return parts;
    }

    private record IoDeclaration(String indent, String flat, String direction, String type, List<String> variables, String trailing) {}

    private static String injectCompatibilityHeader(String source, Stage stage) {
        String header = switch (stage) {
            case VERTEX -> """

// Veyra Iris/OpenGL compatibility inputs for Vulkan compile bring-up.
layout(location = 0) in vec3 veyra_Position;
layout(location = 1) in vec4 veyra_Color;
layout(location = 2) in vec3 veyra_Normal;
layout(location = 3) in vec2 veyra_UV0;
const vec2 veyra_UV1 = vec2(1.0);
const vec2 veyra_UV2 = vec2(1.0);
const mat4 veyra_IdentityMat4 = mat4(1.0);
const mat3 veyra_IdentityMat3 = mat3(1.0);
const mat4 veyra_TextureMatrix[8] = mat4[8](mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0), mat4(1.0));
#define gl_Vertex vec4(veyra_Position, 1.0)
#define gl_Color veyra_Color
#define gl_Normal veyra_Normal
#define gl_MultiTexCoord0 vec4(veyra_UV0, 0.0, 1.0)
#define gl_MultiTexCoord1 vec4(veyra_UV1, 0.0, 1.0)
#define gl_MultiTexCoord2 vec4(veyra_UV2, 0.0, 1.0)
#define gl_TextureMatrix veyra_TextureMatrix
#define gl_ModelViewMatrix veyra_IdentityMat4
#define gl_ProjectionMatrix veyra_IdentityMat4
#define gl_ModelViewProjectionMatrix veyra_IdentityMat4
#define gl_NormalMatrix veyra_IdentityMat3
#define ftransform() (veyra_IdentityMat4 * gl_Vertex)
""";
            case FRAGMENT -> "";
            case COMPUTE -> "";
        };
        return header.isEmpty() ? source : insertAfterVersion(source, header);
    }

    private static String insertAfterVersion(String source, String insertion) {
        int newline = source.indexOf('\n');
        if (newline < 0) {
            return source + '\n' + insertion;
        }
        return source.substring(0, newline + 1) + insertion + source.substring(newline + 1);
    }

    private static String rewriteFragmentOutputs(String source) {
        String transformed = source;
        StringBuilder declarations = new StringBuilder();

        Matcher matcher = FRAG_DATA.matcher(source);
        boolean[] used = new boolean[16];
        while (matcher.find()) {
            int index = Integer.parseInt(matcher.group(1));
            if (index >= 0 && index < used.length) {
                used[index] = true;
            }
        }

        for (int i = 0; i < used.length; i++) {
            if (used[i]) {
                declarations.append("layout(location = ").append(i).append(") out vec4 veyra_FragData").append(i).append(";\n");
            }
        }

        transformed = FRAG_DATA.matcher(transformed).replaceAll(match -> "veyra_FragData" + match.group(1));

        if (transformed.contains("gl_FragColor")) {
            declarations.append("layout(location = 0) out vec4 veyra_FragColor;\n");
            transformed = transformed.replace("gl_FragColor", "veyra_FragColor");
        }

        if (!declarations.isEmpty()) {
            int insert = transformed.indexOf("void main");
            if (insert > 0) {
                transformed = transformed.substring(0, insert) + declarations + "\n" + transformed.substring(insert);
            }
        }

        return transformed;
    }

    public enum Stage {
        VERTEX,
        FRAGMENT,
        COMPUTE
    }
}
