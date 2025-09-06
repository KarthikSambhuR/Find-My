package dev.sambhu.findmyfamily.models;

import java.io.Serializable;

public class PhoneNumber implements Serializable {
    private String number;
    private String alias;

    public PhoneNumber() {}

    public PhoneNumber(String number, String alias) {
        this.number = number;
        this.alias = alias;
    }

    public String getNumber() {
        return number;
    }

    public void setNumber(String number) {
        this.number = number;
    }

    public String getAlias() {
        return alias;
    }

    public void setAlias(String alias) {
        this.alias = alias;
    }
}
