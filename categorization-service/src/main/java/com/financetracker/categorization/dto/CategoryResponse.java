package com.financetracker.categorization.dto;

import com.financetracker.categorization.entity.Category;

public record CategoryResponse(Long id, String name) {
    public static CategoryResponse from(Category category) {
        return new CategoryResponse(category.getId(), category.getName());
    }
}
