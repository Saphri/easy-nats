package org.mjelle.quarkus.easynats.it;

import jakarta.enterprise.context.ApplicationScoped;

import org.mjelle.quarkus.easynats.NatsSubscriber;
import org.mjelle.quarkus.easynats.it.model.MyArrayItemEventList;

@ApplicationScoped
public class ArrayPayloadSubscriber {

  private volatile MyArrayItemEventList lastMessage;

  @NatsSubscriber(subject = "test.array")
  public void onMessage(MyArrayItemEventList event) {
    this.lastMessage = event;
  }

  public MyArrayItemEventList getLastMessage() {
    return lastMessage;
  }
}
