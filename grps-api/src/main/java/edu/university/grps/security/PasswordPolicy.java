package edu.university.grps.security;

import edu.university.grps.common.ApiException;

/** SEC-02: at least 10 characters with both letters and numbers. */
public final class PasswordPolicy {

    private PasswordPolicy() {}

    public static void check(String password) {
        if (password == null || password.length() < 10
                || !password.chars().anyMatch(Character::isLetter)
                || !password.chars().anyMatch(Character::isDigit)) {
            throw ApiException.validation("Password must be at least 10 characters and include letters and numbers.");
        }
    }
}
