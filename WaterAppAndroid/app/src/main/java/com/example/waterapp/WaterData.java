package com.example.waterapp;

import java.util.Map;

public class WaterData {
    private Map<String, String> rowData;
    private String meterId;

    public WaterData(Map<String, String> rowData, String meterId) {
        this.rowData = rowData;
        this.meterId = meterId;
    }

    public Map<String, String> getRowData() {
        return rowData;
    }

    public String getMeterId() {
        return meterId;
    }
}
