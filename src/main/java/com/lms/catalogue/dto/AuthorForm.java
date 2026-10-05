package com.lms.catalogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Create/edit form for {@link com.lms.common.domain.Author}. */
@Getter
@Setter
public class AuthorForm {

    @NotBlank(message = "Author name is required")
    @Size(max = 150, message = "Author name must be at most 150 characters")
    private String authorName;

    private String biography;
}
