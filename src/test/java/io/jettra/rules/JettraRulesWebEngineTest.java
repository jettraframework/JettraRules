package io.jettra.rules;

import io.jettra.rules.annotations.Compute;
import io.jettra.rules.annotations.Rules;
import io.jettra.rules.core.JettraComputeEngine;
import io.jettra.rules.core.JettraRulesEngine;
import io.jettra.rules.core.JettraRulesWebEngine;
import io.jettra.rules.core.RuleResult;
import io.jettra.rules.enums.OperationType;
import io.jettra.rules.validations.Email;
import io.jettra.rules.validations.Min;
import io.jettra.rules.validations.NotNull;
import io.jettra.rules.validations.Size;
import io.jettra.test.annotation.NotRequiresRunningServer;
import io.jettra.test.annotation.Test;

import java.util.List;
import java.util.Map;

import static io.jettra.test.core.JettraAssert.*;

@NotRequiresRunningServer
public class JettraRulesWebEngineTest {

    public static class TestModel {
        @NotNull
        @Rules(apply = "greater", than = "0", message = "El saldo debe ser mayor a 0")
        private Double saldo = 100.0;

        @NotNull
        @Rules(apply = "lessorequals", than = "saldo", message = "El descuento no puede superar el saldo")
        private Double descuento = 20.0;

        @Compute(operation = OperationType.SUBTRACTION, fields = {"saldo", "descuento"})
        private Double saldoNeto;

        @NotNull
        @Email
        private String email = "test@example.com";

        @Min(0)
        private Integer edad = 25;

        public TestModel() {}
        public TestModel(Double saldo, Double descuento) {
            this.saldo = saldo;
            this.descuento = descuento;
        }
    }

    @Test
    public void testMethodLevelComputeAndRules() {
        TestModel model = new TestModel(500.0, 100.0);
        JettraComputeEngine.compute(model);
        assertEquals(400.0, model.saldoNeto);

        List<RuleResult> results = JettraRulesEngine.validate(model);
        assertTrue(results.stream().allMatch(RuleResult::isValid));

        // Invalid model
        TestModel invalidModel = new TestModel(50.0, 100.0);
        List<RuleResult> invalidResults = JettraRulesEngine.validate(invalidModel);
        assertFalse(invalidResults.stream().allMatch(RuleResult::isValid));
    }

    @Test
    public void testWebLevelScriptGeneration() {
        String script = JettraRulesWebEngine.generateFullWebRulesScript(TestModel.class, "testForm", "input_", "_test", "showToast");
        assertNotNull(script);
        assertTrue(script.contains("function validateModelRules"));
        assertTrue(script.contains("function compute_saldoNeto"));
        assertTrue(script.contains("El descuento no puede superar el saldo"));
        assertTrue(script.contains("correo electrónico válido"));
        assertTrue(script.contains("showToast"));
    }

    @Test
    public void testHtmlAttributesGeneration() {
        Map<String, String> attrs = JettraRulesWebEngine.getHtmlAttributes(TestModel.class, "saldo");
        assertEquals("true", attrs.get("required"));
    }
}

