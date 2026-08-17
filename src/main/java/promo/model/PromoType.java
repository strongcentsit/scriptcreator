package promo.model;

public enum PromoType {
    ACCOM(2, "ACCOM", "Accom contract"),
    ACCOM_SUPPLIERS(16, "ACCOM_SUPPLIERS", "Accommodation Suppliers"),
    CONTENT_PROFILE(42, "CONTENT_PROFILE", "Content Profile"),
    ACCOM_PRODUCT_CATALOGUE(43, "ACCOM_PRODUCT_CATALOGUE", "Accom Product Catalogue");

    private final int id;
    private final String code;
    private final String name;

    PromoType(int id, String code, String name) {
        this.id = id;
        this.code = code;
        this.name = name;
    }

    public int getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
}