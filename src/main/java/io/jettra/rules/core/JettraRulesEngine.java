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
            field.setAccessible(true);
            String label = getFieldLabel(field, messages);
            Object value = null;
            try {
                value = field.get(obj);
            } catch (Exception e) {}

            // 1. @Rules
            if (field.isAnnotationPresent(Rules.class)) {
                Rules rule = field.getAnnotation(Rules.class);
                results.add(validateField(obj, field, rule, messages));
            }

            // 2. @NotNull
            if (field.isAnnotationPresent(NotNull.class)) {
                NotNull notNullAnno = field.getAnnotation(NotNull.class);
                boolean invalid = (value == null);
                if (!invalid && value instanceof String s) {
                    invalid = s.trim().isEmpty();
                }
                if (invalid) {
                    String msg = notNullAnno.message();
                    if (msg == null || msg.isEmpty() || msg.startsWith("{")) {
                        msg = "El campo '" + label + "' no puede estar vacío";
                    } else if (messages != null && messages.containsKey(msg)) {
                        msg = messages.getProperty(msg);
                    }
                    results.add(new RuleResult(false, msg, field.getName()));
                }
            }

            // 3. @Min
            if (field.isAnnotationPresent(Min.class)) {
                Min min = field.getAnnotation(Min.class);
                if (value instanceof Number n) {
                    if (n.doubleValue() < min.value()) {
                        String msg = min.message();
                        if (msg == null || msg.isEmpty() || msg.contains("{value}") || msg.startsWith("{")) {
                            long minVal = min.value();
                            msg = "El campo '" + label + "' debe ser mayor o igual a " + minVal;
                        } else if (messages != null && messages.containsKey(msg)) {
                            msg = messages.getProperty(msg);
                        }
                        results.add(new RuleResult(false, msg, field.getName()));
                    }
                }
            }

            // 4. @Max
            if (field.isAnnotationPresent(io.jettra.rules.validations.Max.class)) {
                io.jettra.rules.validations.Max max = field.getAnnotation(io.jettra.rules.validations.Max.class);
                if (value instanceof Number n) {
                    if (n.doubleValue() > max.value()) {
                        String msg = max.message();
                        if (msg == null || msg.isEmpty() || msg.contains("{value}") || msg.startsWith("{")) {
                            msg = "El campo '" + label + "' debe ser menor o igual a " + max.value();
                        } else if (messages != null && messages.containsKey(msg)) {
                            msg = messages.getProperty(msg);
                        }
                        results.add(new RuleResult(false, msg, field.getName()));
                    }
                }
            }

            // 5. @Size
            if (field.isAnnotationPresent(io.jettra.rules.validations.Size.class)) {
                io.jettra.rules.validations.Size size = field.getAnnotation(io.jettra.rules.validations.Size.class);
                if (value instanceof String s) {
                    if (s.length() < size.min() || s.length() > size.max()) {
                        String msg = "El campo '" + label + "' debe tener entre " + size.min() + " y " + size.max() + " caracteres";
                        results.add(new RuleResult(false, msg, field.getName()));
                    }
                }
            }
        }
        return results;
    }

    private static String getFieldLabel(Field field, Properties messages) {
        String label = null;
        for (java.lang.annotation.Annotation ann : field.getAnnotations()) {
            if (ann.annotationType().getSimpleName().equals("PropertiesLabel")) {
                try {
                    String propKey = (String) ann.annotationType().getMethod("value").invoke(ann);
                    if (messages != null && propKey != null && messages.containsKey(propKey)) {
                        label = messages.getProperty(propKey);
                    }
                    if (label == null || label.trim().isEmpty()) {
                        label = (String) ann.annotationType().getMethod("label").invoke(ann);
                    }
                } catch (Exception e) {}
                break;
            }
        }
        if (label == null || label.trim().isEmpty()) {
            if (messages != null && messages.containsKey(field.getName())) {
                label = messages.getProperty(field.getName());
            } else {
                label = field.getName();
            }
        }
        return label;
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
