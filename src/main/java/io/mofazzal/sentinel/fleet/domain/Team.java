package io.mofazzal.sentinel.fleet.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "team")
public class Team {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "contact_channel", nullable = false, length = 200)
    private String contactChannel;

    protected Team() {
    }

    public Team(String name, String contactChannel) {
        this.name = requireText(name, "name");
        this.contactChannel = requireText(contactChannel, "contactChannel");
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getContactChannel() {
        return contactChannel;
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        return other instanceof Team team && id != null && Objects.equals(id, team.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
