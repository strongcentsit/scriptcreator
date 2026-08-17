package promo.model;

public enum PromoStep {
    PRE_VALIDATE(1, "Pre Validate"),
    PROMOTE(2, "Promote"),
    CANDIDATE_EXTERNAL_CODE_GEN(3, "Candidate / External Code Generation"),
    POST_VALIDATE(4, "Post Validate"),
    PROMOTE_RELATED_ENTITIES(5, "Promote Related Entities");

    private final int id;
    private final String stepName;

    PromoStep(int id, String stepName) {
        this.id = id;
        this.stepName = stepName;
    }

    public int getId() { return id; }
    public String getStepName() { return stepName; }
}