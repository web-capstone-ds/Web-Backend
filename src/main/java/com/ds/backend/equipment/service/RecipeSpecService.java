package com.ds.backend.equipment.service;

import com.ds.backend.equipment.entity.RecipeSpec;
import com.ds.backend.equipment.repository.RecipeSpecRepository;
import org.springframework.stereotype.Service;

@Service
public class RecipeSpecService {
    private final RecipeSpecRepository repository;

    public RecipeSpecService(RecipeSpecRepository repository) {
        this.repository = repository;
    }

    public SpecValues getSpec(String recipeId) {
        return repository.findById(recipeId)
                .map(spec -> new SpecValues(spec.getRecipeId(), valueOrDefault(spec.getTargetValue(), 12.00),
                        valueOrDefault(spec.getUsl(), 12.04), valueOrDefault(spec.getLsl(), 11.96),
                        valueOrDefault(spec.getLclYield(), 95.0)))
                .orElseGet(() -> new SpecValues(recipeId, 12.00, 12.04, 11.96, 95.0));
    }

    private double valueOrDefault(Double value, double defaultValue) {
        return value == null ? defaultValue : value;
    }

    public record SpecValues(String recipeId, double target, double usl, double lsl, double lclYield) {}
}
