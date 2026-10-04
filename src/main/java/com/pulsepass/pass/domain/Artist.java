package com.pulsepass.pass.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
        name = "artists",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_artist_stage_name", columnNames = "stage_name")
        }
)
public class Artist {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "stage_name", nullable = false, length = 75)
    private String stageName;

    @Column(nullable = false, length = 100)
    private String country;

    @Column(nullable = false, length = 100)
    private String genre;

    @Column(nullable = false)
    private Boolean active = true;

    protected Artist() {
    }

    public Artist(String stageName, String country, String genre, Boolean active) {
        this.stageName = stageName;
        this.country = country;
        this.genre = genre;
        this.active = active;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Artist artist)) return false;
        return stageName != null && stageName.equals(artist.stageName);
    }

    @Override
    public int hashCode() {
        return stageName != null ? stageName.hashCode() : 0;
    }
}
