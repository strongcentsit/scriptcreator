package com.strongcentsit.scriptcreator.promo;

import com.strongcentsit.scriptcreator.promo.model.PromoStep;
import com.strongcentsit.scriptcreator.promo.model.PromoSubStep;
import com.strongcentsit.scriptcreator.promo.model.PromoType;

import java.util.*;

public class PromoConfigRegistry {

    public static class StepConfig {
        private final List<PromoStep> steps;
        private final List<PromoSubStep> subSteps;

        public StepConfig(List<PromoStep> steps, List<PromoSubStep> subSteps) {
            this.steps = steps;
            this.subSteps = subSteps;
        }

        public List<PromoStep> getSteps() { return steps; }
        public List<PromoSubStep> getSubSteps() { return subSteps; }
    }

    private static final Map<PromoType, StepConfig> REGISTRY = new HashMap<>();

    static {
        // Configure steps and sub-steps for ACCOM_SUPPLIERS (Type 16)
        REGISTRY.put(
                PromoType.ACCOM_SUPPLIERS,
                new StepConfig(
                        Arrays.asList(
                                PromoStep.PRE_VALIDATE,
                                PromoStep.PROMOTE,
                                PromoStep.POST_VALIDATE,
                                PromoStep.PROMOTE_RELATED_ENTITIES
                        ),
                        Arrays.asList(
                                PromoSubStep.PROMOTE_CONTENT_PROFILE,
                                PromoSubStep.PROMOTE_ACCOM_PRODUCT_CATALOGUE
                        )
                )
        );

        // Additional PromoType configurations can be added here
    }

    public static StepConfig getConfig(PromoType promoType) {
        return REGISTRY.getOrDefault(
                promoType,
                new StepConfig(Collections.emptyList(), Collections.emptyList())
        );
    }
}