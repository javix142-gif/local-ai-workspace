# Estado operativo — AWS H0 → H1 → H2

Fecha: 2026-10-20. Rama `feat/0.5.0-skills-agents`; código publicado `dffd919a26d1d1c5a708787c77388c00510f7014`.

## H0 — CI aún pendiente de corregir

- El run `39443809946` ejecutó la instrumentación conectada en API 35 x86_64: XML con 16 casos, 10 ejecutados sin fallos y 6 omitidos porque requieren modelos reales. La migración comprobada no falló.
- El collector marcó FAIL únicamente porque Android Gradle Plugin retiró los paquetes al concluir la tarea y la comprobación posterior no reconoció la instalación durante la prueba. Se preservan `applicationApkInstalled=false` y el resultado del run original; no se reclasifica retroactivamente.
- Hay una corrección local del collector con prueba HOST 4/4. Aún requiere publicación y un nuevo run. H0 no se declara cerrado.
- El workflow QA oficial quedó fail-closed sin APK. Personal QA no pudo firmar porque faltan secretos persistentes de firma; no se exponen ni se reemplazan con otra clave.

## H1 — implementación en curso

- Contrato vigente: [`H1_PRUEBAS_ACEPTACION.md`](H1_PRUEBAS_ACEPTACION.md), versión 1.1; la versión 1.0 original se conserva en `archive/`.
- La revisión autorizada de H1-AT-04 fija el límite del bloque en 15 %: tres fijadas cortas deben caber; un pin que excedería el cupo se rechaza con explicación y sin cambiar el estado.
- Hay cambios locales no publicados para preferencias/contexto y continuidad multimodal. El selector de Skills ahora descuenta el costo de preferencias. La clase focal H1 pasó 5/5 en el árbol de trabajo actual (Gradle 8.10.2, JDK 17, offline, 1m36s); se está ampliando la cobertura de los demás criterios.
- No se declara ningún criterio H1 aceptado aún. H2 no ha comenzado.

## Secuencia y límites

1. Publicar el arreglo del collector y verificar un nuevo run H0; si queda un bloqueo externo demostrado, registrar `CI_PENDING` y continuar H1 sin decir H0 aprobado.
2. Completar los criterios HOST de H1; mantener separados HOST, CI y dispositivo.
3. H2 sigue después de H1, según el contrato de redirección.

`PHYSICAL_DEVICE=NOT_RUN`; Motorola y métricas reales de inferencia pendientes. `M01-06=PENDING_REQUIRES_DEVICE`. El catálogo histórico continúa intacto: 97 IDs y 120 dependencias. `EXECUTION_STATE.json` ya no es el estado operativo; su última copia antes de la sustitución por este archivo se archivó sin cambios en `archive/EXECUTION_STATE_pre-STATUS_20261020.json`.

Referencias preservadas: [`OBJETIVO_AWS.md`](OBJETIVO_AWS.md), [`PROMPT_CODEX_REDIRECCION.md`](PROMPT_CODEX_REDIRECCION.md), [`guía visual`](../design/GUIA_VISUAL_AWS.md), [`contrato de continuidad`](CORRECCION_CONTINUIDAD_H0_H1_H2.md) y [`backlog activo`](BACKLOG_ACTIVO.md).
