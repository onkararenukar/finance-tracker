package com.financetracker.categorization.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "categories")
@Getter
@Setter
@NoArgsConstructor
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(name = "is_seeded", nullable = false)
    private boolean seeded;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    public Category(String name, boolean seeded) {
        this.name = name;
        this.seeded = seeded;
    }
}
