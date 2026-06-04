package dev.blocktracker.shader;

public record IrisProgramSource(
        String name,
        String vertexSource,
        String fragmentSource,
        String computeSource,
        boolean hasVertex,
        boolean hasFragment,
        boolean hasCompute
) {
    public boolean present() {
        return hasVertex || hasFragment || hasCompute;
    }
}
