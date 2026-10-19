package com.coursewise.auth.validation;

import com.coursewise.config.AppConfigProperties;
import com.coursewise.config.AppConfigResponse.ValidationProperties;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Spring-managed {@link ConstraintValidator} for {@link ValidPassword}.
 *
 * <p>Enforces all password rules from {@code coursewise.config.validation.*}:
 * <ol>
 *   <li>Minimum length ({@code password-min-length}).</li>
 *   <li>Maximum length ({@code password-max-length}).</li>
 *   <li>Complexity regex ({@code password-regex}), e.g. requiring at least one
 *       uppercase letter and one non-alphabetic character.</li>
 * </ol>
 *
 * <p>When multiple constraints are violated, all violation messages are reported.
 * Null values are accepted (defer to {@code @NotBlank} for null/empty checks to
 * avoid duplicate error messages).
 *
 * <p>The complexity regex is compiled once at construction rather than on every
 * {@link #isValid} call, since this bean is a singleton and the config value is
 * fixed for its lifetime.
 */
@Component
public class PasswordValidator implements ConstraintValidator<ValidPassword, String> {

    private final ValidationProperties rules;
    private final Pattern passwordPattern;

    public PasswordValidator(AppConfigProperties config) {
        this.rules = config.shared().validation();
        this.passwordPattern = Pattern.compile(rules.passwordRegex());
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            // Defer to @NotBlank — don't double-report
            return true;
        }

        boolean valid = true;

        // Disable default constraint violation so we can add specific messages
        context.disableDefaultConstraintViolation();

        if (value.length() < rules.passwordMinLength()) {
            context.buildConstraintViolationWithTemplate(
                    "Password must be at least " + rules.passwordMinLength() + " characters")
                    .addConstraintViolation();
            valid = false;
        }

        if (value.length() > rules.passwordMaxLength()) {
            context.buildConstraintViolationWithTemplate(
                    "Password must be at most " + rules.passwordMaxLength() + " characters")
                    .addConstraintViolation();
            valid = false;
        }

        if (!passwordPattern.matcher(value).matches()) {
            context.buildConstraintViolationWithTemplate(rules.passwordRegexDescription())
                    .addConstraintViolation();
            valid = false;
        }

        return valid;
    }
}
