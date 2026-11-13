package com.eventpilot.domain;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** The catering plan: meals aligned with agenda breaks, menus and portions. */
public class CateringPlan {

    public record MenuItem(String name, List<String> suitableFor) {
        public MenuItem {
            suitableFor = Attendee.normalizeDiets(suitableFor);
        }
    }

    public record Meal(String name, LocalDateTime time, List<MenuItem> menu, int portions, double costPerPortion) {
        public Meal {
            menu = menu == null ? List.of() : List.copyOf(menu);
        }

        public double cost() {
            return portions * costPerPortion;
        }
    }

    private int headcount;
    private List<Meal> meals = new ArrayList<>();
    private String notes = "";

    private CateringPlan() {
    }

    public CateringPlan(int headcount, List<Meal> meals, String notes) {
        this.headcount = headcount;
        this.meals = new ArrayList<>(meals == null ? List.of() : meals);
        this.notes = notes == null ? "" : notes;
    }

    public int headcount() { return headcount; }
    public List<Meal> meals() { return List.copyOf(meals); }
    public String notes() { return notes; }

    public double estimatedCost() {
        return meals.stream().mapToDouble(Meal::cost).sum();
    }

    /** All dietary needs that at least one menu item in every meal covers. */
    public Set<String> coveredDiets() {
        Set<String> covered = null;
        for (Meal meal : meals) {
            Set<String> inMeal = new LinkedHashSet<>();
            meal.menu().forEach(m -> inMeal.addAll(m.suitableFor()));
            if (covered == null) {
                covered = inMeal;
            } else {
                covered.retainAll(inMeal);
            }
        }
        return covered == null ? Set.of() : covered;
    }
}
