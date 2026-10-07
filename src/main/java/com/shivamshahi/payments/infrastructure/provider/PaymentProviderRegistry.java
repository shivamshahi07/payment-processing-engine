package com.shivamshahi.payments.infrastructure.provider;

import com.shivamshahi.payments.application.port.PaymentProvider;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Registry/Factory for discovering and selecting PaymentProviders.
 */
public class PaymentProviderRegistry {
    private final Map<String, PaymentProvider> providers = new ConcurrentHashMap<>();

    public void registerProvider(PaymentProvider provider) {
        Objects.requireNonNull(provider, "Provider cannot be null");
        providers.put(provider.getName().toUpperCase(), provider);
    }

    public Optional<PaymentProvider> getProvider(String name) {
        if (name == null) return Optional.empty();
        return Optional.ofNullable(providers.get(name.trim().toUpperCase()));
    }

    public PaymentProvider getRequiredProvider(String name) {
        return getProvider(name).orElseThrow(() ->
                new IllegalArgumentException("No payment provider registered with name: " + name));
    }
}
