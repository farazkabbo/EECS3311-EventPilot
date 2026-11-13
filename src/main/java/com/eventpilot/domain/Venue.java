package com.eventpilot.domain;

import java.util.ArrayList;
import java.util.List;

/** A venue from the venue catalog. Shared by events (aggregation), never edited by them. */
public class Venue {

    private String id;
    private String name;
    private String city;
    private String district;
    private String address;
    private int capacity;
    private double pricePerDay;
    private boolean accessible;
    private double rating;
    private List<String> features = new ArrayList<>();
    private List<Room> rooms = new ArrayList<>();

    private Venue() {
    }

    public Venue(String id, String name, String city, String district, String address, int capacity,
                 double pricePerDay, boolean accessible, double rating, List<String> features, List<Room> rooms) {
        this.id = id;
        this.name = name;
        this.city = city;
        this.district = district;
        this.address = address;
        this.capacity = capacity;
        this.pricePerDay = pricePerDay;
        this.accessible = accessible;
        this.rating = rating;
        this.features = new ArrayList<>(features == null ? List.of() : features);
        this.rooms = new ArrayList<>(rooms == null ? List.of() : rooms);
    }

    public String id() { return id; }
    public String name() { return name; }
    public String city() { return city; }
    public String district() { return district; }
    public String address() { return address; }
    public int capacity() { return capacity; }
    public double pricePerDay() { return pricePerDay; }
    public boolean accessible() { return accessible; }
    public double rating() { return rating; }
    public List<String> features() { return List.copyOf(features); }
    public List<Room> rooms() { return List.copyOf(rooms); }

    @Override
    public String toString() {
        return String.format("%s %s (%s, %s) cap %d, $%.0f/day, %s, rating %.1f", id, name, city, district,
                capacity, pricePerDay, accessible ? "accessible" : "not accessible", rating);
    }
}
