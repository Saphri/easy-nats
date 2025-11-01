package org.mjelle.quarkus.easynats.it;

import jakarta.enterprise.context.ApplicationScoped;
import org.mjelle.quarkus.easynats.NatsSubscriber;
import org.mjelle.quarkus.easynats.it.model.MyArrayItemEvent;

@ApplicationScoped
public class ArrayPayloadSubscriber {

    private volatile MyArrayItemEvent[] lastMessage;

    @NatsSubscriber(subject = "test.array")
    public void onMessage(MyArrayItemEvent[] event) {
        this.lastMessage = event;
    }

    public MyArrayItemEvent[] getLastMessage() {
        return lastMessage;
    }
}
