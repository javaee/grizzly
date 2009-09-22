package com.sun.grizzly.config.dom;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.Documented;
import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import javax.validation.Constraint;
import javax.validation.ConstraintPayload;

@Retention(RUNTIME)
@Target({FIELD, METHOD})
@Documented
@Constraint(validatedBy = NetworkAddressValidator.class)
public @interface NetworkAddress {
    String message() default "must be a valid network address";

    Class<?>[] groups() default {};

    Class<? extends ConstraintPayload>[] payload() default {};

}
