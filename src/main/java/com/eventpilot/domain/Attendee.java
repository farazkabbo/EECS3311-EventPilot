package com.eventpilot.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** A person invited to the event, with role, dietary needs and RSVP status. */
public class Attendee {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public enum Role { GUEST, SPEAKER, VIP, STAFF }

    public enum Rsvp { INVITED, ACCEPTED, TENTATIVE, DECLINED }

    private String id;
    private String name;
    private String email;
    private Role role;
    private List<String> dietary = new ArrayList<>();
    private String accessibility;
    private Rsvp rsvp;

    private Attendee() {
    }

    public Attendee(String id, String name, String email, Role role, List<String> dietary,
                    String accessibility, Rsvp rsvp) {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (email == null || !EMAIL.matcher(email.trim()).matches()) {
            throw new IllegalArgumentException("invalid e-mail: " + email);
        }
        this.id = id;
        this.name = name.trim();
        this.email = email.trim().toLowerCase(Locale.ROOT);
        this.role = role == null ? Role.GUEST : role;
        this.dietary = normalizeDiets(dietary);
        this.accessibility = accessibility == null ? "" : accessibility.trim();
        this.rsvp = rsvp == null ? Rsvp.INVITED : rsvp;
    }

    public static List<String> normalizeDiets(List<String> diets) {
        List<String> result = new ArrayList<>();
        if (diets == null) {
            return result;
        }
        for (String d : diets) {
            if (d == null) {
                continue;
            }
            String n = d.trim().toLowerCase(Locale.ROOT);
            if (!n.isEmpty() && !n.equals("none") && !result.contains(n)) {
                result.add(n);
            }
        }
        return result;
    }

    public void updateRsvp(Rsvp newStatus) {
        if (newStatus == null) {
            throw new IllegalArgumentException("RSVP status is required");
        }
        this.rsvp = newStatus;
    }

    public boolean isConfirmed() {
        return rsvp == Rsvp.ACCEPTED;
    }

    void setId(String id) {
        this.id = id;
    }

    public String id() { return id; }
    public String name() { return name; }
    public String email() { return email; }
    public Role role() { return role; }
    public List<String> dietary() { return List.copyOf(dietary); }
    public String accessibility() { return accessibility; }
    public Rsvp rsvp() { return rsvp; }

    @Override
    public String toString() {
        return id + " " + name + " <" + email + "> " + role + " " + rsvp
                + (dietary.isEmpty() ? "" : " diet=" + dietary);
    }
}
