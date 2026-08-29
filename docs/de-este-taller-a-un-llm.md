# Módulo 5 — De este taller a un LLM

> El deduplicador de este taller es determinista: la misma entrada da siempre
> la misma salida. Un LLM no. Este documento explica qué cambia y qué **no**
> cambia al pasar de uno al otro, y por qué el taller se hace con el
> determinista.

---

## Por qué el taller no usa un LLM de verdad

Por tres razones prácticas, en este orden:

1. **Reproducibilidad.** Usted y su compañero obtienen exactamente el mismo
   número. Con un LLM, dos ejecuciones del mismo caso pueden diferir, y eso
   convierte cualquier discusión sobre resultados en una discusión sobre
   ruido.
2. **Coste y acceso.** Nadie necesita clave de API, tarjeta ni GPU. El taller
   corre sin internet.
3. **Aislamiento del concepto.** El no determinismo es *un* problema más,
   encima de todos los demás. Aprender el problema del oráculo, las métricas
   desagregadas y las relaciones metamórficas es más fácil sin él.

**Todo lo que aprendió aplica igual.** Las cuatro técnicas del taller son las
mismas que se usan hoy para evaluar un LLM en producción.

---

## Lo que NO cambia

| Técnica del taller | Cómo se ve con un LLM |
|---|---|
| Conjunto de evaluación etiquetado | Se llama *golden dataset* o *eval set*. Mismo papel, mismo problema: si no es representativo, las métricas mienten. |
| Métricas agregadas, no aserciones por caso | Igual. Se fija un mínimo (`la tasa de acierto no baja de X`), no una igualdad. |
| Análisis desagregado por grupo | Igual, y **más** importante: los LLM rinden peor en idiomas con menos datos de entrenamiento, y eso perjudica a poblaciones enteras. |
| Relaciones metamórficas | Igual. Reformular una pregunta sin cambiar su sentido no debería cambiar la respuesta. |
| Umbral como decisión de política | Igual. El punto de corte de un filtro de contenido decide qué se bloquea de más y qué se escapa. |
| Detección de regresión | Igual, y más urgente: el proveedor puede actualizar el modelo bajo sus pies sin avisarle. |

---

## Lo que sí cambia: cuatro problemas nuevos

### 1. No determinismo

La misma entrada puede dar salidas distintas. Consecuencias:

- **Una ejecución no es una medición.** Hay que ejecutar cada caso *n* veces
  y reportar media y dispersión. Un caso que pasa una vez de tres no está
  "pasando".
- **`temperature = 0` reduce la variabilidad pero no la elimina**, y además
  cambia el comportamiento del modelo: puede empeorar tareas donde la
  diversidad ayuda. No es un interruptor de "modo determinista".
- **Un fallo intermitente es información, no ruido.** Si un caso falla el 20%
  de las veces, ese 20% es su tasa de fallo real en producción.

### 2. El oráculo también es un modelo (*LLM-as-judge*)

Cuando la salida es texto libre, no hay forma mecánica de decidir si es
correcta. La práctica actual es usar **otro LLM como juez**.

Funciona, y trae un problema circular evidente: **¿quién evalúa al juez?**

La respuesta es la misma de siempre: un conjunto etiquetado por humanos. Se
mide **la concordancia entre el juez y el humano** sobre una muestra, y solo
si esa concordancia es alta se confía en el juez para el resto. Es
exactamente el mismo razonamiento que
`SastDetectaLaVulnerabilidadTest` en el taller de seguridad: antes de confiar
en un verificador, compruebe que verifica.

Sesgos documentados de los jueces LLM, que hay que controlar:

- **Sesgo de posición**: prefieren la primera de dos respuestas. Se controla
  presentando los pares en los dos órdenes. *Es una relación metamórfica: la
  simetría del módulo 2, aplicada al juez.*
- **Sesgo de verbosidad**: puntúan mejor las respuestas largas.
- **Autopreferencia**: puntúan mejor el texto generado por su propia familia
  de modelos.

### 3. Superficie de ataque nueva

Un LLM acepta instrucciones en lenguaje natural, y por eso **no distingue de
forma fiable entre sus instrucciones y los datos del usuario**. De ahí la
*inyección de prompt*, que es al LLM lo que la inyección SQL al deduplicador:
un dato que se interpreta como instrucción.

La diferencia incómoda: la inyección SQL **se resuelve** parametrizando. La
inyección de prompt no tiene hoy una solución equivalente, solo mitigaciones
parciales. Lo que aprendió en el taller de seguridad sobre casos de abuso
aplica; lo que no aplica es la expectativa de cerrar el problema del todo.

### 4. El modelo cambia sin que usted lo toque

Un `gpt-x` de hoy puede no comportarse como el de hace tres meses. Su código
no cambió y su sistema sí.

Consecuencia práctica: **la evaluación deja de ser algo del pipeline de CI y
pasa a ser también monitorización en producción**. Se ejecuta el conjunto de
evaluación de forma periódica contra el modelo en vivo, y se alerta si la
métrica cae. Es la misma idea del *job* nocturno de SCA del taller de
seguridad: hay riesgos que aparecen sin que usted haga nada.

---

## Cómo llevaría este taller a un LLM

Si quiere hacerlo (no es obligatorio, y es un buen proyecto final):

1. Sustituya `Deduplicador.esMismaPersona` por una llamada a un LLM con un
   *prompt* que pida decidir si dos nombres son la misma persona.
2. **No toque el conjunto de evaluación ni la clase `Metricas`.** Siguen
   valiendo tal cual, y eso es justamente lo que demuestra el punto.
3. Ejecute cada par **5 veces** y reporte la media y la desviación. Compare la
   dispersión con la del sistema determinista, que es cero.
4. Vuelva a correr las relaciones metamórficas del módulo 2. La simetría es la
   más reveladora: pregunte `(A, B)` y `(B, A)` y mida con qué frecuencia el
   modelo se contradice a sí mismo.
5. Repita el análisis desagregado del módulo 3. ¿El LLM cierra la brecha del
   grupo `abreviado`, o la mueve a otro sitio?

La pregunta que cierra el ejercicio: **¿le compensa?** Un LLM cuesta dinero
por llamada, introduce latencia, no es reproducible y añade una superficie de
ataque. Jaro-Winkler es gratis, instantáneo y determinista. Si el LLM no gana
por un margen claro en las métricas **que a usted le importan**, la respuesta
correcta es no usarlo — y saber justificar eso también es parte del trabajo.
