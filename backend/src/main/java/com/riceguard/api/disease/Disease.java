package com.riceguard.api.disease;

import java.util.List;

public record Disease(
        String id,
        int version,
        String name,
        String scientificName,
        String description,
        String part,        // LEAF | STEM | GRAIN
        String cause,       // FUNGUS | BACTERIA | VIRUS
        boolean common,
        List<String> symptoms,
        List<String> conditions,
        List<String> prevention,
        List<String> treatment,
        String imageUrl,
        String imagePublicId,
        boolean hidden,
        boolean adminEdited) {}
