# JettraRules

## Descripción General
`JettraRules` es un motor de validación basado en anotaciones (`@Rules`) que aplica reglas de negocio tanto en tiempo real (en el lado del cliente y la vista UI) como en el servidor durante la inserción de datos.

## Detalles Específicos
- **Arquitectura general**: API de validación extensible, que provee interceptores de la UI y utilidades para procesar las anotaciones en los modelos de datos.
- **Dependencias clave**: Altamente integrado con las vistas de `JettraWUI` (especialmente `CrudView` y componentes reactivos).
- **Roles dentro del sistema**: Asegurar la integridad de los datos previniendo errores de entrada, fallos de tipo y vulnerando reglas de negocio antes de que la información llegue a la base de datos o capas inferiores.

## Características Detalladas
- **Soporte `@Rules`**: Anotaciones que definen campos obligatorios, formatos de fecha, restricciones numéricas y longitud de cadenas.
- **Validación del Lado del Cliente**: Generación de reglas que operan en vivo dentro de la tabla o el formulario, dando retroalimentación inmediata.
- **Mensajes Personalizables**: Notificaciones que integran las descripciones de los errores con las modales del visualizador.

## Guía de Entrenamiento (AI / Nuevas Características)
- Al definir nuevas reglas de validación (por ejemplo, validación de un formato de documento de identidad específico), añade la regla en este módulo.
- Asegúrate de implementar no solo la lógica de chequeo del lado de Java, sino la adaptación para emitir los constraints en el frontend en conjunto con `JettraWUI`.
- Mantener compatibilidad con los mensajes del visualizador local de reporte y mensajes de advertencia generales.
