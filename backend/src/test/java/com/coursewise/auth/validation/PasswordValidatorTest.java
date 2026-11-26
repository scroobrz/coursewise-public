package com.coursewise.auth.validation;

import com.coursewise.TestParameters;
import com.coursewise.config.AppConfigProperties;
import com.coursewise.config.AppConfigResponse;
import com.coursewise.config.AppConfigResponse.ValidationProperties;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

@ExtendWith(MockitoExtension.class)
class PasswordValidatorTest {

    private PasswordValidator validator;

    @Mock
    private ConstraintValidatorContext context;
    
    @Mock
    private ConstraintViolationBuilder builder;

    @BeforeEach
    void setUp() {
        AppConfigProperties config = new AppConfigProperties(
                true, "http://localhost:4200", "noreply@coursewise.com",
                new AppConfigResponse(
                        30, 60, 15,
                        new ValidationProperties(
                                "^[sS]\\d{7}@ed\\.ac\\.uk$", 8, 128,
                                "^(?=.*[A-Z])(?=.*[^a-zA-Z]).+$",
                                "Must contain at least one uppercase letter and one non-alphabetic character"
                        )
                )
        );
        validator = new PasswordValidator(config);
    }

    private void mockViolationBuilder() {
        when(context.buildConstraintViolationWithTemplate(anyString())).thenReturn(builder);
        when(builder.addConstraintViolation()).thenReturn(context);
    }

    @Nested
    class ValidPasswords {

        @Test
        void valid_password_isTrue() {
            assertThat(validator.isValid("Password123!", context)).isTrue();
            verify(context, never()).buildConstraintViolationWithTemplate(anyString());
        }
        
        @Test
        void valid_password_with_number_no_symbol() {
            assertThat(validator.isValid("Password123", context)).isTrue();
        }

        @Test
        void valid_password_with_symbol_no_number() {
            assertThat(validator.isValid("Password!!!", context)).isTrue();
        }
    }

    @Nested
    class InvalidPasswords {

        @BeforeEach
        void setupContext() {
            mockViolationBuilder();
        }

        static Stream<String> nonBlankInvalidPasswords() {
            // skip null and empty emails from the TestParameters source as they aren't 
            // addressed by this annotation
            return TestParameters.invalidPasswords().filter(password -> !password.isBlank());
        }

        @ParameterizedTest
        @MethodSource("nonBlankInvalidPasswords")
        void constraintBreaking_passwords_are_invalid(String invalidPassword){
            assertThat(validator.isValid(invalidPassword, context)).isFalse();
        }
    }
}
