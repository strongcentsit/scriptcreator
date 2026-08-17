package com.strongcentsit.scriptcreator.promo.model;

public enum PromoSubStep {
    PROMOTE_CONTENT_PROFILE(5, 1, "Promote Content Profile"),
    PROMOTE_ACCOM_PRODUCT_CATALOGUE(5, 2, "Promote Accom Product Catalogue");

    private final int parentStepId;
    private final int subStepId;
    private final String name;

    PromoSubStep(int parentStepId, int subStepId, String name) {
        this.parentStepId = parentStepId;
        this.subStepId = subStepId;
        this.name = name;
    }

    public int getParentStepId() { return parentStepId; }
    public int getSubStepId() { return subStepId; }
    public String getName() { return name; }
}