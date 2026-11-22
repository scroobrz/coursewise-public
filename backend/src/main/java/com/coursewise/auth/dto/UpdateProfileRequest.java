package com.coursewise.auth.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Pattern(regexp = ".*\\S.*", message = "First name must not be empty or consist only of whitespace")
        @Size(max = 50, message = "First name must be less than 50 characters") 
        String firstName,
        
        @Pattern(regexp = ".*\\S.*", message = "Last name must not be empty or consist only of whitespace")
        @Size(max = 50, message = "Last name must be less than 50 characters") 
        String lastName
) {}
