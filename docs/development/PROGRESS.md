# Continuidad de progreso

2026-10-20: H0 host estabilizado en 49f5913937fe7c049bf0ad71d2d75f7e5321b557: 918/918 JVM PASS, lint/AndroidTest compile/ARM64 Debug build PASS.
APK local verificado, pero no entregado: la firma no coincide con la clave previa y esa clave privada no está disponible.
La recomendación M02-01 de ese checkpoint quedó supersedida por el contrato directo H0→H1→H2; no es la próxima tarea vigente.
Android instrumentación y Motorola permanecen sin validar; el Room v1 histórico sigue bloqueado.

2026-10-20: Continuidad redirigida a H0→H1→H2; guardados sin edición los cuatro documentos de referencia y añadidos STATUS/BACKLOG/HANDOFF vigentes. H0 sigue abierto por el fallo de migración instrumentada de `39431545966`; ningún estado de tarea histórica cambia. Próximo paso: corregir ese defecto y validar el workflow; después H1 conforme a H1-AT-01…24. Motorola y rendimiento físico siguen pendientes.

2026-10-20: Se mantiene la secuencia autorizada H0→H1→H2; no se retoma M02-01. El run conectado `39443809946` generó XML coherente (16 casos: 10 ejecutados, 6 omitidos por falta de modelos, 0 fallos/errores) pero el workflow quedó FAIL por la comprobación de paquete posterior a la tarea. Una corrección local del collector pasó 4/4 pruebas Python y aún no tiene nuevo run. H1 sigue en curso: el test de presupuesto combinado detectó una regresión real de admisión de Skills que excede el presupuesto cuando ya hay preferencias; corregir antes de revalidar. H1-AT-04 vigente es v1.1 por autorización directa: tope 15 %, tres pins de ~60 caracteres en el caso que cabe y rechazo explicado antes de persistir si fijar excede el tope. No se declara H0 ni H1 cerrado; H2 no iniciado, Motorola `NOT_RUN`.

2026-10-20: La corrección H1-AT-04 ya está aplicada en el árbol actual. `H1PreferenceContextTest` pasó 5/5 (Gradle 8.10.2 offline/JDK 17; 96 s; XML 5 tests, 0 skips/failures/errors). Se verificaron tres pins cortos dentro del 15 %, rechazo transaccional del pin que excedería el cupo, texto de explicación y reparto de presupuesto entre preferencias y Skills. Código aún no publicado; continuar con cobertura de ámbito/documento/multimodal. La v1.0 adjunta se preservó íntegra y la v1.1 activa contiene solo el cambio aprobado de AT-04.
