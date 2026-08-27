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
