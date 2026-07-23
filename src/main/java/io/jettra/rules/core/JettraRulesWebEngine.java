package io.jettra.rules.core;

import io.jettra.rules.annotations.Compute;
import io.jettra.rules.annotations.Rules;
import io.jettra.rules.enums.OperationType;
import io.jettra.rules.validations.*;

import java.lang.reflect.Field;
import java.util.HashMap;
import java.util.Map;

/**
 * Engine responsible for generating client-side (web browser) validation and computation logic
 * from JettraRules annotations on model classes.
 */
public class JettraRulesWebEngine {

    /**
     * Returns HTML5 validation attributes for a specific field based on annotations.
     */
    public static Map<String, String> getHtmlAttributes(Class<?> modelClass, String fieldName) {
        Map<String, String> attrs = new HashMap<>();
        if (modelClass == null || fieldName == null) return attrs;
        try {
            Field field = modelClass.getDeclaredField(fieldName);
            return getHtmlAttributes(field);
        } catch (NoSuchFieldException e) {
            return attrs;
        }
    }

    /**
     * Returns HTML5 validation attributes for a Field.
     */
    public static Map<String, String> getHtmlAttributes(Field field) {
        Map<String, String> attrs = new HashMap<>();
        if (field == null) return attrs;

        if (field.isAnnotationPresent(NotNull.class) ||
            field.isAnnotationPresent(NotEmpty.class) ||
            field.isAnnotationPresent(NotBlank.class)) {
            attrs.put("required", "true");
        }

        if (field.isAnnotationPresent(Email.class)) {
            attrs.put("type", "email");
        }

        if (field.isAnnotationPresent(Min.class)) {
            attrs.put("min", String.valueOf(field.getAnnotation(Min.class).value()));
        }

        if (field.isAnnotationPresent(Max.class)) {
            attrs.put("max", String.valueOf(field.getAnnotation(Max.class).value()));
        }

        if (field.isAnnotationPresent(DecimalMin.class)) {
            attrs.put("min", field.getAnnotation(DecimalMin.class).value());
        }

        if (field.isAnnotationPresent(DecimalMax.class)) {
            attrs.put("max", field.getAnnotation(DecimalMax.class).value());
        }

        if (field.isAnnotationPresent(Positive.class)) {
            attrs.put("min", "0.00001");
        }

        if (field.isAnnotationPresent(PositiveOrZero.class)) {
            attrs.put("min", "0");
        }

        if (field.isAnnotationPresent(Negative.class)) {
            attrs.put("max", "-0.00001");
        }

        if (field.isAnnotationPresent(NegativeOrZero.class)) {
            attrs.put("max", "0");
        }

        if (field.isAnnotationPresent(Pattern.class)) {
            attrs.put("pattern", field.getAnnotation(Pattern.class).regexp());
        }

        if (field.isAnnotationPresent(Size.class)) {
            Size size = field.getAnnotation(Size.class);
            if (size.min() > 0) {
                attrs.put("minlength", String.valueOf(size.min()));
            }
            if (size.max() < Integer.MAX_VALUE) {
                attrs.put("maxlength", String.valueOf(size.max()));
            }
        }

        return attrs;
    }

    /**
     * Generates a JavaScript validation function for a model class.
     */
    public static String generateValidationScript(Class<?> modelClass, String inputPrefix, String inputSuffix) {
        StringBuilder sb = new StringBuilder();
        sb.append("function validateModelRules(suffix) {\n")
          .append("  const s = suffix || '';\n")
          .append("  let errors = [];\n");

        if (modelClass != null) {
            for (Field field : modelClass.getDeclaredFields()) {
                String name = field.getName();
                String inputId = inputPrefix + name + inputSuffix;

                // 1. Check @Rules
                if (field.isAnnotationPresent(Rules.class)) {
                    Rules rule = field.getAnnotation(Rules.class);
                    String apply = rule.apply();
                    String than = rule.than();
                    String message = rule.message();

                    sb.append("  {\n")
                      .append("    const el = document.getElementById('").append(inputId).append("');\n")
                      .append("    if (el) {\n")
                      .append("      const val = parseFloat(el.value) || 0;\n");

                    if (than.matches("-?\\d+(\\.\\d+)?")) {
                        sb.append("      const thanVal = ").append(than).append(";\n");
                    } else {
                        String thanInputId = inputPrefix + than + inputSuffix;
                        sb.append("      const thanEl = document.getElementById('").append(thanInputId).append("');\n")
                          .append("      const thanVal = thanEl ? (parseFloat(thanEl.value) || 0) : 0;\n");
                    }

                    if ("greater".equalsIgnoreCase(apply)) {
                        sb.append("      if (!(val > thanVal)) errors.push('").append(message).append("');\n");
                    } else if ("lessorequals".equalsIgnoreCase(apply)) {
                        sb.append("      if (!(val <= thanVal)) errors.push('").append(message).append("');\n");
                    } else if ("less".equalsIgnoreCase(apply)) {
                        sb.append("      if (!(val < thanVal)) errors.push('").append(message).append("');\n");
                    } else if ("greaterorequals".equalsIgnoreCase(apply)) {
                        sb.append("      if (!(val >= thanVal)) errors.push('").append(message).append("');\n");
                    } else if ("equals".equalsIgnoreCase(apply)) {
                        sb.append("      if (val !== thanVal) errors.push('").append(message).append("');\n");
                    } else if ("notequals".equalsIgnoreCase(apply)) {
                        sb.append("      if (val === thanVal) errors.push('").append(message).append("');\n");
                    }

                    sb.append("    }\n  }\n");
                }

                // 2. Check @NotNull / @NotBlank / @NotEmpty
                if (field.isAnnotationPresent(NotNull.class) ||
                    field.isAnnotationPresent(NotBlank.class) ||
                    field.isAnnotationPresent(NotEmpty.class)) {
                    sb.append("  {\n")
                      .append("    const el = document.getElementById('").append(inputId).append("');\n")
                      .append("    if (el && (!el.value || el.value.trim() === '')) {\n")
                      .append("      errors.push('El campo \"").append(name).append("\" es requerido');\n")
                      .append("    }\n  }\n");
                }

                // 3. Check @Min
                if (field.isAnnotationPresent(Min.class)) {
                    Min min = field.getAnnotation(Min.class);
                    sb.append("  {\n")
                      .append("    const el = document.getElementById('").append(inputId).append("');\n")
                      .append("    if (el && parseFloat(el.value) < ").append(min.value()).append(") {\n")
                      .append("      errors.push('El campo \"").append(name).append("\" debe ser mayor o igual a ").append(min.value()).append("');\n")
                      .append("    }\n  }\n");
                }

                // 4. Check @Max
                if (field.isAnnotationPresent(Max.class)) {
                    Max max = field.getAnnotation(Max.class);
                    sb.append("  {\n")
                      .append("    const el = document.getElementById('").append(inputId).append("');\n")
                      .append("    if (el && parseFloat(el.value) > ").append(max.value()).append(") {\n")
                      .append("      errors.push('El campo \"").append(name).append("\" debe ser menor o igual a ").append(max.value()).append("');\n")
                      .append("    }\n  }\n");
                }
            }
        }

        sb.append("  return errors;\n}\n");
        return sb.toString();
    }

    /**
     * Generates a JavaScript real-time calculation function for @Compute fields.
     */
    public static String generateComputeScript(Class<?> modelClass, String inputPrefix, String inputSuffix) {
        StringBuilder sb = new StringBuilder();
        if (modelClass == null) return "";

        for (Field field : modelClass.getDeclaredFields()) {
            if (field.isAnnotationPresent(Compute.class)) {
                Compute computeAnno = field.getAnnotation(Compute.class);
                String targetId = inputPrefix + field.getName() + inputSuffix;
                String[] sourceFields = computeAnno.fields();

                sb.append("function compute_").append(field.getName()).append("() {\n")
                  .append("  const target = document.getElementById('").append(targetId).append("');\n");

                if (sourceFields != null && sourceFields.length > 0) {
                    for (String source : sourceFields) {
                        String sourceId = inputPrefix + source + inputSuffix;
                        sb.append("  const el_").append(source).append(" = document.getElementById('").append(sourceId).append("');\n")
                          .append("  const val_").append(source).append(" = el_").append(source).append(" ? (parseFloat(el_").append(source).append(".value) || 0) : 0;\n");
                    }

                    sb.append("  let result = 0;\n");
                    OperationType op = computeAnno.operation();

                    if (op == OperationType.SUM) {
                        for (String source : sourceFields) sb.append("  result += val_").append(source).append(";\n");
                    } else if (op == OperationType.SUBTRACTION) {
                        sb.append("  result = val_").append(sourceFields[0]).append(";\n");
                        for (int i = 1; i < sourceFields.length; i++) sb.append("  result -= val_").append(sourceFields[i]).append(";\n");
                    } else if (op == OperationType.MULT) {
                        sb.append("  result = 1;\n");
                        for (String source : sourceFields) sb.append("  result *= val_").append(source).append(";\n");
                    } else if (op == OperationType.DIV) {
                        sb.append("  result = val_").append(sourceFields[0]).append(";\n");
                        for (int i = 1; i < sourceFields.length; i++) {
                            sb.append("  if (val_").append(sourceFields[i]).append(" !== 0) result /= val_").append(sourceFields[i]).append(";\n");
                        }
                    } else if (op == OperationType.AVERAGE) {
                        sb.append("  let sum = 0;\n");
                        for (String source : sourceFields) sb.append("  sum += val_").append(source).append(";\n");
                        sb.append("  result = sum / ").append(sourceFields.length).append(";\n");
                    } else if (op == OperationType.PERCENTAGE || op == OperationType.TAX || op == OperationType.DISCOUNT) {
                        if (sourceFields.length >= 2) {
                            sb.append("  result = (val_").append(sourceFields[0]).append(" * val_").append(sourceFields[1]).append(") / 100;\n");
                        }
                    } else if (op == OperationType.NET_VALUE) {
                        if (sourceFields.length >= 2) {
                            sb.append("  result = val_").append(sourceFields[0]).append(" - val_").append(sourceFields[1]).append(";\n");
                        }
                    } else if (op == OperationType.INTEREST) {
                        if (sourceFields.length >= 3) {
                            sb.append("  result = val_").append(sourceFields[0]).append(" * (val_").append(sourceFields[1]).append(" / 100) * val_").append(sourceFields[2]).append(";\n");
                        }
                    } else if (op == OperationType.MAX) {
                        sb.append("  result = Math.max(");
                        for (int i = 0; i < sourceFields.length; i++) {
                            if (i > 0) sb.append(", ");
                            sb.append("val_").append(sourceFields[i]);
                        }
                        sb.append(");\n");
                    } else if (op == OperationType.MIN) {
                        sb.append("  result = Math.min(");
                        for (int i = 0; i < sourceFields.length; i++) {
                            if (i > 0) sb.append(", ");
                            sb.append("val_").append(sourceFields[i]);
                        }
                        sb.append(");\n");
                    } else if (op == OperationType.ABS) {
                        sb.append("  result = Math.abs(val_").append(sourceFields[0]).append(");\n");
                    } else if (op == OperationType.ROUND) {
                        sb.append("  result = Math.round(val_").append(sourceFields[0]).append(");\n");
                    } else if (op == OperationType.CEIL) {
                        sb.append("  result = Math.ceil(val_").append(sourceFields[0]).append(");\n");
                    } else if (op == OperationType.FLOOR) {
                        sb.append("  result = Math.floor(val_").append(sourceFields[0]).append(");\n");
                    }

                    sb.append("  if (target) target.value = result.toFixed(2);\n")
                      .append("  if (target) target.dispatchEvent(new Event('input', { bubbles: true }));\n");
                }

                sb.append("}\n");

                // Add real-time event listeners for compute source fields
                if (sourceFields != null) {
                    for (String source : sourceFields) {
                        String sourceId = inputPrefix + source + inputSuffix;
                        sb.append("document.getElementById('").append(sourceId).append("')?.addEventListener('input', compute_").append(field.getName()).append(");\n");
                    }
                }
            }
        }

        return sb.toString();
    }

    /**
     * Generates a full JavaScript suite including toast utility, rules validation, real-time compute listeners,
     * field error highlighting, and form submission interception.
     */
    public static String generateFullWebRulesScript(Class<?> modelClass, String formId, String inputPrefix, String inputSuffix, String toastFunctionName) {
        StringBuilder script = new StringBuilder();
        String toastFn = (toastFunctionName != null && !toastFunctionName.isEmpty()) ? toastFunctionName : "showRulesToast";

        script.append("function ").append(toastFn).append("(msg, type) {\n")
              .append("  let toast = document.getElementById('j-rules-toast');\n")
              .append("  if(!toast) {\n")
              .append("    toast = document.createElement('div');\n")
              .append("    toast.id = 'j-rules-toast';\n")
              .append("    toast.style = 'position:fixed;top:20px;right:20px;z-index:99999;padding:12px 20px;border-radius:8px;color:white;font-weight:bold;transition:all 0.3s;display:none;';\n")
              .append("    document.body.appendChild(toast);\n")
              .append("  }\n")
              .append("  toast.innerText = msg;\n")
              .append("  toast.style.backgroundColor = type === 'error' ? 'rgba(239, 68, 68, 0.9)' : 'rgba(16, 185, 129, 0.9)';\n")
              .append("  toast.style.display = 'block';\n")
              .append("  toast.style.opacity = '1';\n")
              .append("  setTimeout(() => { toast.style.opacity = '0'; setTimeout(() => toast.style.display='none', 300); }, 3000);\n")
              .append("}\n\n");

        script.append(generateValidationScript(modelClass, inputPrefix, inputSuffix)).append("\n");
        script.append(generateComputeScript(modelClass, inputPrefix, inputSuffix)).append("\n");

        // Add real-time event listeners for rules validation highlighting
        if (modelClass != null) {
            for (Field field : modelClass.getDeclaredFields()) {
                if (field.isAnnotationPresent(Rules.class)) {
                    Rules rule = field.getAnnotation(Rules.class);
                    String inputId = inputPrefix + field.getName() + inputSuffix;
                    script.append("document.getElementById('").append(inputId).append("')?.addEventListener('input', () => {\n")
                          .append("  const errs = validateModelRules('").append(inputSuffix).append("');\n")
                          .append("  const input = document.getElementById('").append(inputId).append("');\n")
                          .append("  const myErrs = errs.filter(e => e.includes('").append(field.getName()).append("') || e.includes('").append(rule.message()).append("'));\n")
                          .append("  if(myErrs.length > 0) {\n")
                          .append("    input.style.borderColor = '#ef4444';\n")
                          .append("    input.style.boxShadow = '0 0 8px rgba(239, 68, 68, 0.4)';\n")
                          .append("    ").append(toastFn).append("(myErrs[0], 'error');\n")
                          .append("  } else {\n")
                          .append("    input.style.borderColor = '';\n")
                          .append("    input.style.boxShadow = '';\n")
                          .append("  }\n")
                          .append("});\n");
                }
            }
        }

        // Form submit handler
        if (formId != null && !formId.isEmpty()) {
            script.append("const form_").append(formId.replace("-", "_")).append(" = document.getElementById('").append(formId).append("');\n")
                  .append("if (form_").append(formId.replace("-", "_")).append(") {\n")
                  .append("  form_").append(formId.replace("-", "_")).append(".addEventListener('submit', function(e) {\n")
                  .append("    const errs = validateModelRules('").append(inputSuffix).append("');\n")
                  .append("    if (errs.length > 0) {\n")
                  .append("      e.preventDefault();\n")
                  .append("      ").append(toastFn).append("('Error: ' + errs[0], 'error');\n")
                  .append("      return false;\n")
                  .append("    }\n")
                  .append("  });\n")
                  .append("}\n");
        }

        return script.toString();
    }
}
