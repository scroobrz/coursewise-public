package com.coursewise.auth.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Pattern(regexp = ".*\\S.*", message = "First name must not be empty or consist only of whitespace")
        @Size(max = 255, message = "First name must be less than 255 characters") 
        String firstName,
        
        @Pattern(regexp = ".*\\S.*", message = "Last name must not be empty or consist only of whitespace")
        @Size(max = 255, message = "Last name must be less than 255 characters") 
        String lastName
) {}
