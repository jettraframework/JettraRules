# JettraRules - Comprehensive Guide & Architecture Manual

## 1. Overview & Architecture
`JettraRules` is an embedded, declarative business rule engine and reactive event trigger system for Java 25 applications. It enables dynamic validation, conditional workflow branching, and policy enforcement across Jettra data streams and database transactions.

---

## 2. Key Features
- **Declarative Rule Definitions**: Rule evaluation using pure Java expressions and lightweight AST evaluations.
- **Pre/Post Storage Interceptors**: Execute business logic automatically on database insert, update, or delete.
- **High-Throughput Evaluation**: Zero-reflection rule caching optimized for concurrent virtual threads.
- **Audit Trails**: Complete execution traceability for financial, tax, and compliance domain workflows.

---

## 3. Installation
```xml
<dependency>
  <groupId>com.github.jettraframework</groupId>
    <artifactId>JettraRules</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

---

## 4. Usage & Code Examples

### 4.1 Defining and Executing Rules
```java
import io.jettra.rules.RuleEngine;
import io.jettra.rules.Rule;
import io.jettra.rules.RuleResult;

public record Order(String id, double totalAmount, boolean isVip) {}

public class RulesDemo {
    public static void main(String[] args) {
        RuleEngine engine = new RuleEngine();

        // Register a discount rule
        engine.registerRule(new Rule("VIP_DISCOUNT")
            .when(fact -> fact instanceof Order o && o.isVip() && o.totalAmount() > 1000.0)
            .then(fact -> {
                System.out.println("Applied 15% VIP discount to order!");
                return true;
            }));

        Order order = new Order("ORD-901", 1250.0, true);
        RuleResult result = engine.evaluate(order);
        System.out.println("Rules matched: " + result.matchedCount());
    }
}
```
