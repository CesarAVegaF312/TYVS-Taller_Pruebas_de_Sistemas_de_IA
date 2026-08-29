# Registro de Defectos (plantilla)

En un sistema de IA, "defecto" no significa lo mismo que en el resto de los
talleres, y la plantilla lo refleja.

Un caso que falla NO es automaticamente un defecto. Puede ser:

  - un caso genuinamente dificil (el sistema no puede acertar siempre),
  - una ETIQUETA mal puesta en el conjunto de evaluacion,
  - una degradacion real del sistema,
  - o un problema de EQUIDAD, que es el mas grave y el mas facil de no ver.

Clasificar cual de los cuatro es constituye el trabajo. Escribir "falla con
este par" no dice nada por si solo.

---

## Formato

### Defecto XX — <titulo breve>

- **Tipo**: Caso dificil | Etiqueta incorrecta | Degradacion | Equidad
- **Como se detecto**: metrica global | analisis desagregado | prueba
  metamorfica | inspeccion del conjunto
- **Evidencia cuantitativa**: la metrica concreta, antes y despues si aplica.
  "Recall 0.000 en el grupo abreviado (10 de 10 duplicados no detectados)"
  dice algo; "a veces falla" no.
- **A quien afecta**: que personas quedan perjudicadas y de que forma. Si no
  puede contestar esto, probablemente no es un defecto de equidad.
- **Causa**: por que ocurre, a nivel de algoritmo o de datos.
- **Decision**: Corregir | Aceptar y documentar | Reetiquetar el conjunto
- **Coste de la correccion**: que empeora a cambio. En un sistema de este tipo
  casi todo arreglo mueve el error de sitio en vez de eliminarlo, y no decirlo
  es peor que no arreglarlo.
- **Estado**: Abierto | En progreso | Resuelto | Aceptado

---

## Tabla de seguimiento

| ID | Titulo | Tipo | Evidencia | A quien afecta | Decision | Estado |
|----|--------|------|-----------|----------------|----------|--------|
| IA-01 | ... | ... | ... | ... | ... | Abierto |

---

## Un defecto de equidad bien redactado

> ❌ "El sistema no funciona bien con algunos nombres."
>
> ✅ "El sistema tiene recall 0.000 sobre nombres con la inicial abreviada:
> no detecta ninguno de los 10 duplicados reales del grupo. Afecta a quien se
> inscribe como 'Ana M Torres' y despues como 'Ana Maria Torres': nunca sera
> detectada como duplicada. El F1 global de 0.833 no lo insinua. Causa:
> Jaro-Winkler penaliza la diferencia de longitud y las puntuaciones caen
> entre 0.808 y 0.925, bajo el umbral de 0.95."

La diferencia es que el segundo se puede priorizar, corregir y verificar.
