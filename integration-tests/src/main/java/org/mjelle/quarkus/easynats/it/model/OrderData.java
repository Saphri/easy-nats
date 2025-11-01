package org.mjelle.quarkus.easynats.it.model;

public record OrderData(String orderId, String customerId, double totalPrice) {
}
