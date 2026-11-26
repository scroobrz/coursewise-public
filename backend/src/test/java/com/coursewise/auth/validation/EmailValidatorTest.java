package com.coursewise.auth.validation;

import com.coursewise.config.AppConfigProperties;
import com.coursewise.config.AppConfigResponse;
import com.coursewise.config.AppConfigResponse.ValidationProperties;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(MockitoExtension.class)
class EmailValidatorTest {

    private EmailValidator validator;

    @Mock
    private ConstraintValidatorContext context;

    @BeforeEach
    void setUp() {
        AppConfigProperties config = new AppConfigProperties(
                true, "http://localhost:4200", "noreply@coursewise.com",
                new AppConfigResponse(
                        30, 60, 15,
                        new ValidationProperties(
                                "^[sS]\\d{7}@ed\\.ac\\.uk$", 8, 128,
                                "^(?=.*[A-Z])(?=.*[^a-zA-Z]).+$",
                                "Must contain at least one uppercase letter"
                        )
                )
        );
        validator = new EmailValidator(config);
    }

    @Nested
    class ValidEmails {

        @Test
        void lowercase_s_prefix_isValid() {
            assertThat(validator.isValid("s1234567@ed.ac.uk", context)).isTrue();
        }

        @Test
        void uppercase_S_prefix_isValid() {
            assertThat(validator.isValid("S1234567@ed.ac.uk", context)).isTrue();
        }
    }

    @Nested
    class InvalidEmails {

        @ParameterizedTest 
        @MethodSource("com.coursewise.TestParameters#invalidEmails")
        void constraintBreaking_emails_are_invalid(String invalidEmail){
            if(invalidEmail.isBlank()){
                // skip null and empty emails from the TestParameters 
                // source as they aren't addressed by this annotation
                return;
            }
            assertThat(validator.isValid(invalidEmail, context)).isFalse();
        }
    }
}
