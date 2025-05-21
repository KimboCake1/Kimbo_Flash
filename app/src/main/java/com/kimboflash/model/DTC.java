package com.kimboflash.model;

import java.util.Date;

public class DTC {
    private String code, description, status;
    private Date timestamp;

    public DTC() { }

    public DTC(String code, String description, Date timestamp) {
        this.code = code;
        this.description = description;
        this.timestamp = timestamp;
        this.status = "active";
    }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Date getTimestamp() { return timestamp; }
    public void setTimestamp(Date timestamp) { this.timestamp = timestamp; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
