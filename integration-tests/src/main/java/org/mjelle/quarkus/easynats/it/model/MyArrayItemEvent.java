package org.mjelle.quarkus.easynats.it.model;

import java.util.Objects;

public class MyArrayItemEvent {

  private String data;

  public MyArrayItemEvent() {}

  public MyArrayItemEvent(String data) {
    this.data = data;
  }

  public String getData() {
    return data;
  }

  public void setData(String data) {
    this.data = data;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MyArrayItemEvent that = (MyArrayItemEvent) o;
    return Objects.equals(data, that.data);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(data);
  }
}
