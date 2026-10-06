package com.lms.catalogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Create/edit form for {@link com.lms.common.domain.Category}. */
@Getter
@Setter
public class CategoryForm {

    @NotBlank(message = "Category name is required")
    @Size(max = 100, message = "Category name must be at most 100 characters")
    private String categoryName;

    @Size(max = 500, message = "Description must be at most 500 characters")
    private String description;
}
