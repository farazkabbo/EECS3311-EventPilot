package com.eventpilot.infra;

import com.eventpilot.domain.Attendee;
import com.eventpilot.domain.DomainException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Reads attendees from CSV (F02). Header: {@code name,email,role,dietary} with
 * optional {@code accessibility} and {@code rsvp} columns. Dietary needs are
 * separated by ';'. Each row is validated on its own: bad rows are reported
 * with their line number and the rest are still imported.
 */
public class AttendeeCsvImporter {

    public static final String EXPECTED_HEADER = "name,email,role,dietary[,accessibility,rsvp]";

    public record RowError(int line, String reason) {
        @Override
        public String toString() {
            return "line " + line + ": " + reason;
        }
    }

    public record ImportReport(List<Attendee> imported, List<RowError> rejected, List<String> duplicates) {
        public String summary() {
            return imported.size() + " imported, " + rejected.size() + " rejected, " + duplicates.size() + " duplicate(s)";
        }
    }

    public ImportReport parse(Path file, Set<String> existingEmails) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new DomainException("File not found: " + file);
        }
        List<String> lines;
        try {
            lines = Files.readAllLines(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new DomainException("Cannot read " + file + ": " + e.getMessage());
        }
        return parse(lines, existingEmails);
    }

    public ImportReport parse(List<String> lines, Set<String> existingEmails) {
        if (lines.isEmpty()) {
            throw new DomainException("The CSV file is empty. Expected header: " + EXPECTED_HEADER);
        }
        List<String> header = Arrays.stream(splitCsv(stripBom(lines.get(0))))
                .map(h -> h.trim().toLowerCase(Locale.ROOT)).toList();
        int iName = header.indexOf("name");
        int iEmail = header.indexOf("email");
        int iRole = header.indexOf("role");
        int iDiet = header.indexOf("dietary");
        int iAccess = header.indexOf("accessibility");
        int iRsvp = header.indexOf("rsvp");
        if (iName < 0 || iEmail < 0 || iRole < 0 || iDiet < 0) {
            throw new DomainException("Wrong CSV header \"" + lines.get(0) + "\". Expected: " + EXPECTED_HEADER);
        }

        List<Attendee> imported = new ArrayList<>();
        List<RowError> rejected = new ArrayList<>();
        List<String> duplicates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        existingEmails.forEach(e -> seen.add(e.toLowerCase(Locale.ROOT)));

        for (int i = 1; i < lines.size(); i++) {
            int lineNo = i + 1;
            String line = lines.get(i);
            if (line.isBlank()) {
                continue;
            }
            String[] cols = splitCsv(line);
            try {
                String email = col(cols, iEmail);
                if (seen.contains(email.trim().toLowerCase(Locale.ROOT))) {
                    duplicates.add("line " + lineNo + ": " + email.trim());
                    continue;
                }
                Attendee attendee = new Attendee(null, col(cols, iName), email, parseRole(col(cols, iRole)),
                        Arrays.asList(col(cols, iDiet).split(";")), col(cols, iAccess), parseRsvp(col(cols, iRsvp)));
                seen.add(attendee.email());
                imported.add(attendee);
            } catch (IllegalArgumentException e) {
                rejected.add(new RowError(lineNo, e.getMessage()));
            }
        }
        return new ImportReport(imported, rejected, duplicates);
    }

    private static Attendee.Role parseRole(String text) {
        if (text.isBlank()) {
            return Attendee.Role.GUEST;
        }
        try {
            return Attendee.Role.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown role \"" + text + "\" (use guest, speaker, vip or staff)");
        }
    }

    private static Attendee.Rsvp parseRsvp(String text) {
        if (text.isBlank()) {
            return Attendee.Rsvp.INVITED;
        }
        try {
            return Attendee.Rsvp.valueOf(text.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("unknown RSVP \"" + text + "\"");
        }
    }

    private static String col(String[] cols, int index) {
        return index >= 0 && index < cols.length ? cols[index].trim() : "";
    }

    private static String stripBom(String s) {
        return s.startsWith("﻿") ? s.substring(1) : s;
    }

    /** Splits one CSV line, honouring double quotes. */
    static String[] splitCsv(String line) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        boolean quoted = false;
        for (int i = 0; i < line.length(); i++) {
            char ch = line.charAt(i);
            if (ch == '"') {
                if (quoted && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                    cur.append('"');
                    i++;
                } else {
                    quoted = !quoted;
                }
            } else if (ch == ',' && !quoted) {
                out.add(cur.toString());
                cur.setLength(0);
            } else {
                cur.append(ch);
            }
        }
        out.add(cur.toString());
        return out.toArray(new String[0]);
    }
}
