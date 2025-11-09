package org.mjelle.quarkus.easynats.it.model;

import java.util.Arrays;

public class MyArrayItemEventList {

  private MyArrayItemEvent[] items;

  public MyArrayItemEventList() {}

  public MyArrayItemEventList(MyArrayItemEvent[] items) {
    this.items = items;
  }

  public MyArrayItemEvent[] getItems() {
    return items;
  }

  public void setItems(MyArrayItemEvent[] items) {
    this.items = items;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    MyArrayItemEventList that = (MyArrayItemEventList) o;
    return Arrays.equals(items, that.items);
  }

  @Override
  public int hashCode() {
    return Arrays.hashCode(items);
  }
}
