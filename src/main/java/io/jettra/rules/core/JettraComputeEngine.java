package io.jettra.rules.core;

import io.jettra.rules.annotations.Compute;
import io.jettra.rules.enums.OperationType;
import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class JettraComputeEngine {

    /**
     * Executes all computations defined by @Compute annotations in the object.
     * @param obj The object to process.
     */
    public static void compute(Object obj) {
        if (obj == null) return;

        Class<?> clazz = obj.getClass();
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(Compute.class)) {
                Compute compute = field.getAnnotation(Compute.class);
                executeCompute(obj, field, compute);
            }
        }
    }

    private static void executeCompute(Object obj, Field targetField, Compute anno) {
        try {
            double result = 0;
            
            if (!anno.expression().isEmpty()) {
                result = evaluateExpression(obj, anno.expression());
            } else if (anno.operation() != OperationType.NONE) {
                result = evaluateOperation(obj, anno.operation(), anno.fields(), 0);
            } else {
                return;
            }

            targetField.setAccessible(true);
            if (targetField.getType() == Double.class || targetField.getType() == double.class) {
                targetField.set(obj, result);
            } else if (targetField.getType() == Integer.class || targetField.getType() == int.class) {
                targetField.set(obj, (int) result);
            } else if (targetField.getType() == Long.class || targetField.getType() == long.class) {
                targetField.set(obj, (long) result);
            } else if (targetField.getType() == String.class) {
                targetField.set(obj, String.valueOf(result));
            }
        } catch (Exception e) {
            System.err.println("Error in @Compute for field " + targetField.getName() + ": " + e.getMessage());
        }
    }

    private static double evaluateOperation(Object obj, OperationType type, String[] fields, double before) {
        double result = 0;
        double[] vals = new double[fields.length];
        for (int i = 0; i < fields.length; i++) {
            vals[i] = resolveValue(obj, fields[i], before);
        }

        switch (type) {
            case SUM:
                for (double v : vals) result += v;
                break;
            case SUBTRACTION:
                if (vals.length > 0) {
                    result = vals[0];
                    for (int i = 1; i < vals.length; i++) result -= vals[i];
                }
                break;
            case MULT:
                result = 1;
                for (double v : vals) result *= v;
                break;
            case DIV:
                if (vals.length > 0) {
                    result = vals[0];
                    for (int i = 1; i < vals.length; i++) {
                        if (vals[i] != 0) result /= vals[i];
                    }
                }
                break;
            case AVERAGE:
                if (vals.length > 0) {
                    double sum = 0;
                    for (double v : vals) sum += v;
                    result = sum / vals.length;
                }
                break;
            case ABS:
                if (vals.length > 0) result = Math.abs(vals[0]);
                break;
            case ROUND:
                if (vals.length > 0) result = Math.round(vals[0]);
                break;
            case CEIL:
                if (vals.length > 0) result = Math.ceil(vals[0]);
                break;
            case FLOOR:
                if (vals.length > 0) result = Math.floor(vals[0]);
                break;
            case PERCENTAGE:
                if (vals.length >= 2) result = (vals[0] * vals[1]) / 100.0;
                break;
            case TAX:
                // Assume vals[0] is subtotal, vals[1] is tax rate (e.g., 7.0)
                if (vals.length >= 2) result = vals[0] * (vals[1] / 100.0);
                break;
            case INTEREST:
                // Simple interest: P * r * t
                if (vals.length >= 3) result = vals[0] * (vals[1] / 100.0) * vals[2];
                break;
            case DISCOUNT:
                // vals[0] is price, vals[1] is discount rate
                if (vals.length >= 2) result = vals[0] * (vals[1] / 100.0);
                break;
            case NET_VALUE:
                // price - discount
                if (vals.length >= 2) result = vals[0] - vals[1];
                break;
            case MAX:
                result = Double.MIN_VALUE;
                for (double v : vals) result = Math.max(result, v);
                break;
            case MIN:
                result = Double.MAX_VALUE;
                for (double v : vals) result = Math.min(result, v);
                break;
            default:
                result = before;
        }
        return result;
    }

    private static double resolveValue(Object obj, String fieldName, double before) {
        if ("RESULT.BEFORE".equalsIgnoreCase(fieldName) || "BEFORE".equalsIgnoreCase(fieldName)) {
            return before;
        }
        try {
            if (fieldName.matches("-?\\d+(\\.\\d+)?")) {
                return Double.parseDouble(fieldName);
            }
            Field field = obj.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            Object val = field.get(obj);
            if (val instanceof Number) return ((Number) val).doubleValue();
            if (val instanceof String) return Double.parseDouble((String) val);
        } catch (Exception e) {}
        return 0;
    }

    /**
     * Basic parser for the expression DSL.
     * Supports: OP(f1, f2).APPLY(IF(cond).THEN(OP).ELSE(OP))
     */
    private static double evaluateExpression(Object obj, String expr) {
        // This is a simplified implementation of the requested DSL
        // In a real scenario, we would use a proper lexer/parser
        
        double currentResult = 0;
        
        // Example: SUBTRACTION(saldo, descuento)
        Pattern p = Pattern.compile("(\\w+)\\(([^\\)]+)\\)");
        Matcher m = p.matcher(expr);
        
        if (m.find()) {
            String opName = m.group(1);
            String[] fields = m.group(2).split(",");
            for(int i=0; i<fields.length; i++) fields[i] = fields[i].trim();
            
            OperationType type = OperationType.valueOf(opName.toUpperCase());
            currentResult = evaluateOperation(obj, type, fields, 0);
            
            // Check for .APPLY(...)
            String remaining = expr.substring(m.end());
            if (remaining.contains(".APPLY")) {
                currentResult = evaluateApply(obj, remaining, currentResult);
            }
        }
        
        return currentResult;
    }

    private static double evaluateApply(Object obj, String applyExpr, double before) {
        // Handle IF(f1, OP, v1).THEN(...).ELSE(...)
        if (applyExpr.contains("IF")) {
            // Simplified IF logic
            Pattern p = Pattern.compile("IF\\(([^,]+),([^,]+),([^\\)]+)\\)\\.THEN\\(([^\\)]+)\\)\\.ELSE\\(([^\\)]+)\\)");
            Matcher m = p.matcher(applyExpr);
            if (m.find()) {
                String field = m.group(1).trim();
                String op = m.group(2).trim();
                double val = resolveValue(obj, m.group(3).trim(), before);
                
                boolean condition = false;
                double fieldVal = resolveValue(obj, field, before);
                
                if ("LESS".equalsIgnoreCase(op)) condition = fieldVal < val;
                else if ("GREATER".equalsIgnoreCase(op)) condition = fieldVal > val;
                else if ("EQUALS".equalsIgnoreCase(op)) condition = fieldVal == val;
                
                String action = condition ? m.group(4) : m.group(5);
                return evaluateExpression(obj, action.replace("BEFORE", String.valueOf(before)));
            }
        }
        return before;
    }
}
