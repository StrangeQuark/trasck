package com.strangequark.trasck.config;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component("runtimeSecurityProfile")
public class RuntimeSecurityProfile {

    private static final Set<String> PRODUCTION_LIKE_PROFILES = Set.of("prod", "production", "staging", "hosted");

    private final Environment environment;

    public RuntimeSecurityProfile(Environment environment) {
        this.environment = environment;
    }

    public boolean isProductionLike() {
        return Arrays.stream(environment.getActiveProfiles())
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet())
                .stream()
                .anyMatch(PRODUCTION_LIKE_PROFILES::contains);
    }

    public boolean isNonProductionLike() {
        return !isProductionLike();
    }
}
