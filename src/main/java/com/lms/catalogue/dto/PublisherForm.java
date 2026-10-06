package com.lms.catalogue.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/** Create/edit form for {@link com.lms.common.domain.Publisher}. */
@Getter
@Setter
public class PublisherForm {

    @NotBlank(message = "Publisher name is required")
    @Size(max = 150, message = "Publisher name must be at most 150 characters")
    private String publisherName;

    /** CK_Publisher_WebsiteFormat: NULL or starts with http(s)://. */
    @Pattern(regexp = "^$|^https?://.+", message = "Website must start with http:// or https://")
    private String website;
}
