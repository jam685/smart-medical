package com.hospital.smartmedical.common.enums;

public enum Role {
    PATIENT, DOCTOR, ADMIN;

    public static Role of(String name) {
        if (name == null) return null;
        try { return Role.valueOf(name.toUpperCase()); }
        catch (IllegalArgumentException e) { return null; }
    }
}
