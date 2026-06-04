package dev.blocktracker.shader;

import java.util.List;

public enum IrisProgramId {
    SHADOW("shadow"),
    SHADOWCOMP("shadowcomp"),
    GBuffersBasic("gbuffers_basic"),
    GBuffersTextured("gbuffers_textured"),
    GBuffersTerrain("gbuffers_terrain"),
    GBuffersBlock("gbuffers_block"),
    GBuffersEntities("gbuffers_entities"),
    GBuffersHand("gbuffers_hand"),
    GBuffersWater("gbuffers_water"),
    GBuffersWeather("gbuffers_weather"),
    Deferred("deferred"),
    Deferred1("deferred1"),
    Composite("composite"),
    Composite1("composite1"),
    Composite2("composite2"),
    Composite3("composite3"),
    Composite4("composite4"),
    Composite5("composite5"),
    Composite6("composite6"),
    Composite7("composite7"),
    Composite8("composite8"),
    Composite9("composite9"),
    Composite10("composite10"),
    Composite11("composite11"),
    Composite12("composite12"),
    Composite13("composite13"),
    Composite14("composite14"),
    Composite15("composite15"),
    FINAL("final");

    public static final List<IrisProgramId> ORDERED = List.of(values());

    private final String irisName;

    IrisProgramId(String irisName) {
        this.irisName = irisName;
    }

    public String irisName() {
        return irisName;
    }
}
