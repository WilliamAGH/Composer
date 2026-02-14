package com.composerai.api.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Constraint(validatedBy = AiCommandValidator.class)
public @interface AiCommandValid {
    String message() default "Unsupported AI command";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
