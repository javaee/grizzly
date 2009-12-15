package com.sun.grizzly.config.dom;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Retention(RUNTIME)
@Target({FIELD, METHOD})
@Documented
@Constraint(validatedBy = NetworkAddressValidator.class)
public @interface NetworkAddress {
    String message() default "must be a valid network address";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

}
