package io.jettra.rules.core;

import io.jettra.rules.annotations.Rules;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.regex.Pattern;
import io.jettra.rules.validations.Min;
import io.jettra.rules.validations.NotNull;

public class JettraRulesEngine {

    public static List<RuleResult> validate(Object obj) {
        return validate(obj, null);
    }

    /**
     * Validates all fields of an object using provided properties for message localization.
     * @param obj The object to validate.
     * @param messages Properties object containing localized labels.
     * @return A list of validation results.
     */
    public static List<RuleResult> validate(Object obj, Properties messages) {
        List<RuleResult> results = new ArrayList<>();
        if (obj == null) return results;

        Class<?> clazz = obj.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Rules.class)) {
                Rules rule = field.getAnnotation(Rules.class);
                results.add(validateField(obj, field, rule, messages));
            }
            if (field.isAnnotationPresent(Min.class)) {
                Min min = field.getAnnotation(Min.class);
                field.setAccessible(true);
                try {
                    Object value = field.get(obj);
                    if (value instanceof Number n) {
                        if (n.doubleValue() < min.value()) {
                            String message = min.message().replace("{value}", String.valueOf(min.value()));
                            if (messages != null && messages.containsKey(message)) {
                                message = messages.getProperty(message);
                            }
                            results.add(new RuleResult(false, message, field.getName()));
                        }
                    }
                } catch (Exception e) {}
            }
        }
        return results;
    }

    private static RuleResult validateField(Object obj, Field field, Rules rule, Properties messages) {
        try {
            field.setAccessible(true);
            Object value = field.get(obj);
            String operator = rule.apply().toLowerCase();
            String than = rule.than();
            String customMessage = rule.message();

            Object compareTo = resolveValue(obj, than);
            boolean valid = checkRule(value, operator, compareTo);

            String message = "";
            if (!valid) {
                if (customMessage.isEmpty()) {
                    message = generateDefaultMessage(field.getName(), operator, than);
                } else {
                    // Try to resolve as property key
                    if (messages != null && messages.containsKey(customMessage)) {
                        message = messages.getProperty(customMessage);
                    } else {
                        message = customMessage;
                    }
                }
            }
            return new RuleResult(valid, message, field.getName());
        } catch (Exception e) {
            return new RuleResult(false, "Error evaluating rule: " + e.getMessage(), field.getName());
        }
    }

    private static Object resolveValue(Object obj, String than) {
        if (than == null || than.isEmpty()) return null;
        try {
            Field field = obj.getClass().getDeclaredField(than);
            field.setAccessible(true);
            return field.get(obj);
        } catch (NoSuchFieldException e) {
            // Assume it's a literal value
            return than;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean checkRule(Object value, String operator, Object compareTo) {
        if (value == null) return true; // Let @NotNull handle nulls if needed

        switch (operator) {
            case "equals":
                return value.equals(compareTo);
            case "notequals":
                return !value.equals(compareTo);
            case "greater":
                return compare(value, compareTo) > 0;
            case "greaterorequals":
                return compare(value, compareTo) >= 0;
            case "less":
                return compare(value, compareTo) < 0;
            case "lessorequals":
                return compare(value, compareTo) <= 0;
            case "contains":
                return value.toString().contains(compareTo.toString());
            case "notcontains":
                return !value.toString().contains(compareTo.toString());
            case "startswith":
                return value.toString().startsWith(compareTo.toString());
            case "endswith":
                return value.toString().endsWith(compareTo.toString());
            case "regex":
                return Pattern.compile(compareTo.toString()).matcher(value.toString()).matches();
            default:
                return true;
        }
    }

    private static int compare(Object v1, Object v2) {
        if (v1 instanceof Number n1 && v2 instanceof Number n2) {
            return Double.compare(n1.doubleValue(), n2.doubleValue());
        }
        if (v1 instanceof Number n1 && v2 instanceof String s2) {
            try {
                return Double.compare(n1.doubleValue(), Double.parseDouble(s2));
            } catch (Exception e) { return 0; }
        }
        if (v1 instanceof Comparable c1 && v2.getClass().isInstance(v1)) {
            return c1.compareTo(v2);
        }
        return v1.toString().compareTo(v2.toString());
    }

    private static String generateDefaultMessage(String field, String operator, String than) {
        return "Field '" + field + "' must be " + operator + " than " + than;
    }
}
