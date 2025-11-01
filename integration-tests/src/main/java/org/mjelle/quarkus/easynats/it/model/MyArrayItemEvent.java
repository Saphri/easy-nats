package org.mjelle.quarkus.easynats.it.model;

public class MyArrayItemEvent {

    private String data;

    public MyArrayItemEvent() {
    }

    public MyArrayItemEvent(String data) {
        this.data = data;
    }

    public String getData() {
        return data;
    }

    public void setData(String data) {
        this.data = data;
    }
}
