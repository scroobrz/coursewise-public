package com.coursewise.auth.validation;

import com.coursewise.config.AppConfigProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Spring-managed {@link ConstraintValidator} for {@link ValidEmail}.
 *
 * <p>Validates that the email:
 * <ol>
 *   <li>Is not null or blank.</li>
 *   <li>Matches the {@code coursewise.config.validation.email-regex} pattern
 *       loaded from {@code application.yml} at runtime.</li>
 * </ol>
 *
 * <p>Being Spring-managed (via {@code @Component}) allows this validator to
 * inject {@link AppConfigProperties} — something a plain {@code @Pattern}
 * annotation cannot do, as annotation values must be compile-time constants.
 * The regex is compiled once at construction rather than on every {@link #isValid}
 * call, since this bean is a singleton and the config value is fixed for its lifetime.
 */
@Component
public class EmailValidator implements ConstraintValidator<ValidEmail, String> {

    private final Pattern emailPattern;

    public EmailValidator(AppConfigProperties config) {
        this.emailPattern = Pattern.compile(config.shared().validation().emailRegex());
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            // Defer to @NotBlank for null/empty — don't double-report
            return true;
        }
        return emailPattern.matcher(value).matches();
    }
}
