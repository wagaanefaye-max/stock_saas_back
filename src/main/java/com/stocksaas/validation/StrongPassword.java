package com.stocksaas.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Contrainte de validation pour une politique de mot de passe fort.
 * Règles : au moins 8 caractères, une majuscule, une minuscule, un chiffre, un caractère spécial.
 */
@Documented
@Constraint(validatedBy = StrongPasswordValidator.class)
@Target({ ElementType.FIELD, ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface StrongPassword {

    String message() default "Le mot de passe doit respecter les règles suivantes :\n"
            + "- Au moins 8 caractères\n"
            + "- Une lettre majuscule\n"
            + "- Une lettre minuscule\n"
            + "- Un chiffre\n"
            + "- Un caractère spécial (@ $ ! % * ? & . # - _ = + etc.)";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
