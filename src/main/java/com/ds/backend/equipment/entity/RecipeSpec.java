package com.ds.backend.equipment.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "recipe_specs")
public class RecipeSpec {
    @Id
    @Column(name = "recipe_id")
    private String recipeId;

    @Column(name = "target_value")
    private Double targetValue;

    private Double usl;
    private Double lsl;

    @Column(name = "lcl_yield")
    private Double lclYield;

    @Column(name = "ideal_cycle_ms")
    private Integer idealCycleMs;

    public String getRecipeId() {
        return recipeId;
    }

    public Double getTargetValue() {
        return targetValue;
    }

    public Double getUsl() {
        return usl;
    }

    public Double getLsl() {
        return lsl;
    }

    public Double getLclYield() {
        return lclYield;
    }

    public Integer getIdealCycleMs() {
        return idealCycleMs;
    }
}
