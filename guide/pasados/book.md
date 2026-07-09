# JettraRules Guide

JettraRules is a powerful business rule engine for JettraStack that allows you to define complex validations using annotations.

## The @Rules Annotation

The `@Rules` annotation can be applied to fields, methods, or classes to enforce business constraints.

### Parameters

- **field**: Optional field name (used primarily when the annotation is at class level).
- **apply**: The validation operation to perform.
- **than**: The comparison target. It can be another field name or a literal value.
- **message**: Custom error message.

### Supported Operations

| Operation | Description |
|-----------|-------------|
| `equals` | Checks if the value is equal to the target. |
| `notequals` | Checks if the value is NOT equal to the target. |
| `greater` | Checks if the value is strictly greater than the target. |
| `greaterorequals` | Checks if the value is greater than or equal to the target. |
| `less` | Checks if the value is strictly less than the target. |
| `lessorequals` | Checks if the value is less than or equal to the target. |
| `contains` | Checks if the string representation contains the target. |
| `notcontains` | Checks if the string representation does NOT contain the target. |
| `startswith` | Checks if the value starts with the target. |
| `endswith` | Checks if the value ends with the target. |
| `regex` | Validates against a Regular Expression. |

---

## Usage Examples

### Comparison between fields

This is the most common use case, where you want to ensure a field's value depends on another field.

```java
public class FacturaModel {
    private Double saldo;

    @Rules(field="saldo", apply="lessorequals", than="saldo", message="El descuento no puede superar al saldo")
    private Double descuento;
}
```

In this case, `descuento` will be validated against the current value of `saldo`.

### Validation against literal values

```java
public class ProductoModel {
    @Rules(apply="greater", than="0", message="El precio debe ser positivo")
    private Double precio;
}
```

### Regular Expressions

```java
public class UsuarioModel {
    @Rules(apply="regex", than="^[A-Z]{3}-\\d{3}$", message="Formato de código inválido (AAA-999)")
    private String codigoInterno;
}
```

---

## Executing the Rules

To validate an object, use the `JettraRulesEngine`:

```java
FacturaModel factura = new FacturaModel();
factura.setSaldo(100.0);
factura.setDescuento(150.0);

List<RuleResult> results = JettraRulesEngine.validate(factura);

for (RuleResult result : results) {
    if (!result.isValid()) {
        System.out.println("Error en campo " + result.getField() + ": " + result.getMessage());
    }
}
```

## Localización de Mensajes

JettraRules permite el uso de etiquetas de archivos de propiedades (como `messages.properties`) para los mensajes de error.

### Uso en el Modelo

En lugar de escribir un mensaje literal, use la clave que se encuentra en su archivo de propiedades:

```java
public class ProductoModel {
    @Rules(apply="greater", than="0", message="msg.error.precio_positivo")
    private Double precio;
}
```

### Ejecución con Propiedades

Para que el motor resuelva las etiquetas, pase el objeto `Properties` al método `validate`:

```java
// Cargado previamente desde messages.properties
Properties myMessages = ...; 

List<RuleResult> results = JettraRulesEngine.validate(producto, myMessages);
```

Si la clave no se encuentra en el archivo de propiedades, el motor utilizará el valor del atributo `message` como un mensaje literal.

## La Anotación @Compute

La anotación `@Compute` permite realizar cálculos dinámicos entre campos y almacenar el resultado en un atributo del ViewModel. Soporta tanto operaciones simples como expresiones complejas encadenadas.

### Atributos

- **operation**: Operación matemática o de negocio (Enum `OperationType`).
- **fields**: Lista de campos que intervienen en la operación.
- **expression**: Cadena que define una lógica compleja (condicionales, encadenamiento).
- **editable**: Indica si el atributo será editable en una interfaz. Por defecto es `false`. Si está en `false`, indica que el valor se actualizará de manera automática basándose en los campos establecidos en la anotación.

### Operaciones Disponibles (OperationType)

| Operación | Descripción |
|-----------|-------------|
| `SUM` | Suma todos los campos listados. |
| `SUBTRACTION` | Resta al primer campo los valores de los siguientes. |
| `MULT` | Multiplica los valores de los campos. |
| `DIV` | Divide el primer valor entre los subsecuentes. |
| `PERCENTAGE` | Calcula el porcentaje (campo1 * campo2 / 100). |
| `MAX` / `MIN` | Obtiene el valor máximo o mínimo. |
| `TAX` / `INTEREST` | Operaciones financieras predefinidas. |

---

## Expresiones Complejas y Encadenamiento

Para casos donde una simple operación no es suficiente, se utiliza el atributo `expression`. Este permite usar la sintaxis `.APPLY()` y condicionales `IF`.

### RESULT.BEFORE
Dentro de una expresión encadenada, `RESULT.BEFORE` (o simplemente `BEFORE`) hace referencia al resultado obtenido por la operación inmediatamente anterior.

---

## Ejemplos de Uso Combinado (@Rules y @Compute)

### Ejemplo 1: Cálculo de Saldo Neto con Regla de Validación

```java
public class CuentaModel {
    private Double saldo;
    
    @Rules(apply="lessorequals", than="saldo", message="El descuento no puede ser mayor al saldo")
    private Double descuento;

    @Compute(operation=OperationType.SUBTRACTION, fields={"saldo", "descuento"}, editable=false)
    private Double saldoNeto;
}
```

### Ejemplo 2: Cálculo Condicional de Bonificación (Uso de expression)

En este ejemplo, si el ITBMS es menor a 0.05, se aplica una multiplicación, de lo contrario se realiza una suma.

```java
public class NominaModel {
    private Double saldo;
    private Double descuento;
    private Double itbms;

    @Compute(expression="SUBTRACTION(saldo, descuento).APPLY(IF(itbms, LESS, 0.05).THEN(MULT(BEFORE, itbms)).ELSE(SUM(saldo, itbms)))")
    private Double montoEspecial;
}
```

### Ejemplo 3: Impuestos y Totales

```java
public class VentaModel {
    private Double subtotal;
    private Double tasaImpuesto; // ej: 7.0

    @Compute(operation=OperationType.PERCENTAGE, fields={"subtotal", "tasaImpuesto"})
    private Double impuestoCalculado;

    @Compute(operation=OperationType.SUM, fields={"subtotal", "impuestoCalculado"})
    private Double totalFactura;
}
```

### Ejemplo 4: Interés Simple con Literales

En las expresiones, también puedes usar números literales directamente.

```java
public class PrestamoModel {
    private Double capital;
    private Double tasaMensual;
    private Double meses;

    @Compute(operation=OperationType.INTEREST, fields={"capital", "tasaMensual", "meses"})
    private Double interesTotal;
    
    @Compute(operation=OperationType.SUM, fields={"capital", "interesTotal"})
    private Double montoAPagar;
}
```

### Ejemplo 5: Cálculo de Nómina Complejo (Chaining)

```java
public class NominaDetalle {
    private Double salarioBase;
    private Double horasExtras;
    private Double precioHoraExtra;
    private Double retenciones;

    @Compute(operation=OperationType.MULT, fields={"horasExtras", "precioHoraExtra"})
    private Double montoExtras;

    // SalarioBase + MontoExtras - Retenciones
    @Compute(expression="SUM(salarioBase, montoExtras).APPLY(SUBTRACTION(BEFORE, retenciones))")
    private Double salarioNeto;
}
```

### Ejemplo 6: Uso de MAX y MIN para Stock de Seguridad

```java
public class InventarioModel {
    private Integer stockActual;
    private Integer stockMinimoRequerido;

    // Calcula cuánto falta para el mínimo, pero asegura que no sea negativo (mínimo 0)
    @Compute(expression="SUBTRACTION(stockMinimoRequerido, stockActual).APPLY(MAX(BEFORE, 0))")
    private Integer cantidadAComprar;
}
```

---

## Ejecución del Motor de Cómputo

A diferencia de las reglas que devuelven resultados de validación, el motor de cómputo modifica el estado del objeto:

```java
VentaModel venta = new VentaModel();
venta.setSubtotal(100.0);
venta.setTasaImpuesto(7.0);

// Ejecuta los cálculos
JettraComputeEngine.compute(venta);

System.out.println("Impuesto: " + venta.getImpuestoCalculado()); // 7.0
System.out.println("Total: " + venta.getTotalFactura()); // 107.0
```

## Integración en Ciclo de Vida

Normalmente, en un entorno JettraWUI, el flujo recomendado es:
1. Capturar datos del formulario.
2. Ejecutar `JettraComputeEngine.compute(model)`.
3. Ejecutar `JettraRulesEngine.validate(model)`.
4. Si las reglas pasan, persistir el modelo.
