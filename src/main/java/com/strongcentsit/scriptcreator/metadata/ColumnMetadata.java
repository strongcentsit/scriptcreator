package com.strongcentsit.scriptcreator.metadata;

public class ColumnMetadata {
    private String columnName;
    private String dataType;
    private int dataLength;
    private Integer dataPrecision;
    private Integer dataScale;
    private boolean nullable;

    public ColumnMetadata(String columnName, String dataType, int dataLength, Integer dataPrecision, Integer dataScale, boolean nullable) {
        this.columnName = columnName;
        this.dataType = dataType;
        this.dataLength = dataLength;
        this.dataPrecision = dataPrecision;
        this.dataScale = dataScale;
        this.nullable = nullable;
    }

    public String getColumnName() { return columnName; }
    public String getDataType() { return dataType; }
    public int getDataLength() { return dataLength; }
    public Integer getDataPrecision() { return dataPrecision; }
    public Integer getDataScale() { return dataScale; }
    public boolean isNullable() { return nullable; }
}