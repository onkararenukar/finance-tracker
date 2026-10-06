package com.financetracker.categorization.repository;

import com.financetracker.categorization.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /**
     * Case-insensitive lookup - this is the core of "does the LLM's
     * suggestion match an existing category" in
     * {@code CategorizationService}. Matches the case-insensitive unique
     * index defined in {@code V1__create_categories_table.sql}.
     */
    @Query("SELECT c FROM Category c WHERE LOWER(c.name) = LOWER(:name)")
    Optional<Category> findByNameIgnoreCase(@Param("name") String name);

    /** All categories, ordered alphabetically - powers the "existing categories" list shown to the LLM prompt and the dashboard's picker. */
    List<Category> findAllByOrderByNameAsc();
}
