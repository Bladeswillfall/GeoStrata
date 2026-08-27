package com.geostrata.geology;

/**
 * Pure diagnostic response that combines geological province tuning with coarse terrain morphology.
 * All outputs are normalized potentials; this class does not define physical fold wavelengths,
 * fault displacement or runtime block ownership.
 */
public final class StructuralDeformationResponse {
    private StructuralDeformationResponse() {
    }

    public static Result evaluate(
            GeologyProvinceSampler.Sample provinceSample,
            ProvinceDeformationProfiles.Snapshot profiles,
            TerrainMorphologySample terrain
    ) {
        if (provinceSample == null || profiles == null || terrain == null) {
            throw new IllegalArgumentException("blended deformation response inputs must not be null");
        }
        if (!profiles.loaded()) {
            throw new IllegalStateException("GeoStrata province deformation profiles have not been loaded yet");
        }

        Result primary = evaluate(
                profiles.normalization(),
                profiles.profileFor(provinceSample.province()),
                terrain
        );
        Result neighbor = evaluate(
                profiles.normalization(),
                profiles.profileFor(provinceSample.neighborProvince()),
                terrain
        );
        return blend(primary, neighbor, provinceSample.interiorBlend(profiles.blendWidthBlocks()));
    }

    public static Result evaluate(
            ProvinceDeformationProfiles.Normalization normalization,
            ProvinceDeformationProfiles.Profile profile,
            TerrainMorphologySample terrain
    ) {
        if (normalization == null || profile == null || terrain == null) {
            throw new IllegalArgumentException("deformation response inputs must not be null");
        }

        double reliefSignal = unit(terrain.relief() / normalization.reliefScaleBlocks());
        double slopeSignal = unit(terrain.slopeMagnitude() / normalization.slopeScale());
        double ridgeSignal = unit(Math.max(0.0, terrain.prominence()) / normalization.ridgeProminenceScaleBlocks());
        double terrainSignal = unit(
                reliefSignal * normalization.reliefWeight()
                        + slopeSignal * normalization.slopeWeight()
                        + ridgeSignal * normalization.ridgeWeight()
        );
        double intensity = unit(profile.baselineIntensity() + profile.terrainCoupling() * terrainSignal);

        return new Result(
                reliefSignal,
                slopeSignal,
                ridgeSignal,
                terrainSignal,
                intensity,
                intensity * profile.dipPotential(),
                intensity * profile.foldPotential(),
                intensity * profile.faultPotential()
        );
    }

    static Result blend(Result primary, Result neighbor, double interiorBlend) {
        if (primary == null || neighbor == null || !Double.isFinite(interiorBlend)) {
            throw new IllegalArgumentException("deformation blend inputs must be finite and non-null");
        }
        double clamped = unit(interiorBlend);
        double primaryShare = 0.5 + 0.5 * clamped;
        return new Result(
                mix(primary.reliefSignal(), neighbor.reliefSignal(), primaryShare),
                mix(primary.slopeSignal(), neighbor.slopeSignal(), primaryShare),
                mix(primary.ridgeSignal(), neighbor.ridgeSignal(), primaryShare),
                mix(primary.terrainSignal(), neighbor.terrainSignal(), primaryShare),
                mix(primary.intensity(), neighbor.intensity(), primaryShare),
                mix(primary.dipPotential(), neighbor.dipPotential(), primaryShare),
                mix(primary.foldPotential(), neighbor.foldPotential(), primaryShare),
                mix(primary.faultPotential(), neighbor.faultPotential(), primaryShare)
        );
    }

    private static double mix(double primary, double neighbor, double primaryShare) {
        return primary * primaryShare + neighbor * (1.0 - primaryShare);
    }

    private static double unit(double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("deformation response value must be finite");
        }
        return Math.max(0.0, Math.min(1.0, value));
    }

    public record Result(
            double reliefSignal,
            double slopeSignal,
            double ridgeSignal,
            double terrainSignal,
            double intensity,
            double dipPotential,
            double foldPotential,
            double faultPotential
    ) {
    }
}
