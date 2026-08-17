package promo.model;

public class ExcelItem {
    private final String code;
    private final String name;
    private boolean existsInTarget;

    public ExcelItem(String code, String name) {
        this.code = code;
        this.name = name;
        this.existsInTarget = false;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isExistsInTarget() { return existsInTarget; }
    public void setExistsInTarget(boolean existsInTarget) { this.existsInTarget = existsInTarget; }
}