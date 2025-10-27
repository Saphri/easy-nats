package org.mjelle.quarkus.easynats.it.model;

import io.quarkus.runtime.annotations.RegisterForReflection;

@RegisterForReflection
public record OrderData(String orderId, String customerId, double totalPrice) {
}
