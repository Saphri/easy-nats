package org.mjelle.quarkus.easynats.deployment.build;

import java.util.List;

import org.mjelle.quarkus.easynats.runtime.metadata.SubscriberMetadata;

import io.quarkus.builder.item.SimpleBuildItem;

/** Build item that aggregates all subscriber metadata for passing to the runtime recorder. */
public final class SubscribersCollectionBuildItem extends SimpleBuildItem {

  private final List<SubscriberMetadata> subscribers;

  public SubscribersCollectionBuildItem(List<SubscriberMetadata> subscribers) {
    this.subscribers = List.copyOf(subscribers);
  }

  public List<SubscriberMetadata> getSubscribers() {
    return subscribers;
  }
}
