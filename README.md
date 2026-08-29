# Taller de Pruebas de Sistemas de IA

Todos los talleres anteriores dan por hecho algo que aquí desaparece: que
usted **sabe cuál es la respuesta correcta**.

```java
assertEquals(RegisterResult.VALID, registry.registerVoter(ana));
```

Esa línea funciona porque existe una respuesta y usted la conoce. A eso se le
llama tener un **oráculo**.

Ahora responda: *¿"José Rodríguez" y "Jose Rodrigez" son la misma persona?*

No hay una función que conteste eso sin equivocarse nunca. Si la hubiera, no
haría falta el sistema. Y sin oráculo, **no se puede escribir una aserción
sobre un caso suelto**. Hay que probar de otra manera.

---

## 🎯 Objetivos

- Entender el **problema del oráculo** y por qué obliga a cambiar de aserciones
  a métricas.
- Medir con **precisión y recall**, y saber por qué el porcentaje de acierto
  miente en casi todos los problemas interesantes.
- Escribir **pruebas metamórficas**: verificar sin saber la respuesta correcta.
- Hacer **análisis desagregado** y descubrir que una métrica global sana puede
  esconder un grupo completamente desatendido.
- Entender que **el umbral es una decisión de política**, no un parámetro
  técnico.
- Detectar **regresiones** en un sistema cuyo comportamiento no es binario.

---

## Sistema bajo prueba

Un **deduplicador de votantes**: decide si dos inscripciones son de la misma
persona comparando los nombres. Es el problema real de una registraduría —
alguien se inscribe dos veces escribiendo su nombre distinto.

> ### ¿Por qué esto es "un sistema de IA"?
>
> No es una red neuronal, y es a propósito. Es un sistema de decisión
> **estadístico**, y tiene exactamente las mismas propiedades incómodas que un
> modelo de aprendizaje automático o un LLM:
>
> - No hay respuesta única correcta.
> - Equivocarse forma parte de su funcionamiento normal.
> - Su comportamiento depende de un umbral que alguien eligió.
> - Puede funcionar peor con unos grupos de personas que con otros.
>
> La ventaja: **es determinista**. Usted y su compañero obtienen el mismo
> número, sin GPU, sin clave de API y sin internet. El
> [módulo 5](docs/de-este-taller-a-un-llm.md) explica qué cambia con un LLM de
> verdad, y qué no cambia (casi todo).

```text
.
├─ deduplicador/
│   ├─ src/main/java/.../dedup/
│   │   ├─ Similitud.java          # Jaro-Winkler + normalización
│   │   └─ Deduplicador.java       # el umbral y su justificación
│   └─ src/test/
│       ├─ java/.../dedup/         # módulos 1 a 4
│       └─ resources/
│           └─ pares-de-evaluacion.csv    # EL ORÁCULO
├─ docs/
│   └─ de-este-taller-a-un-llm.md  # módulo 5
└─ defectos_template.md
```

---

## Puesta en marcha

```bash
cd deduplicador
mvn clean test
```

**21 pruebas** en verde. Fíjese en la salida por consola: varias pruebas
imprimen tablas de métricas que son parte del material.

---

## Módulos del taller

### Módulo 1 — El problema del oráculo ([`MetricasDeEvaluacionTest`](deduplicador/src/test/java/edu/unisabana/tyvs/dedup/MetricasDeEvaluacionTest.java))

Sin oráculo, el procedimiento cambia:

1. Se etiqueta a mano un conjunto de casos ([`pares-de-evaluacion.csv`](deduplicador/src/test/resources/pares-de-evaluacion.csv), 80 pares).
2. Se ejecuta el sistema sobre todos.
3. Se afirma sobre la **métrica agregada**, no sobre cada caso.

La aserción pasa de *"esto vale X"* a *"la precisión no baja de X"*. Y con
ella cambia algo más importante: **un caso que falla deja de ser un bug
automáticamente**. Puede ser un caso difícil, una etiqueta mal puesta o una
degradación real. Distinguirlos es el trabajo.

> ⚠️ **El conjunto de evaluación es código.** Se versiona, se revisa en pull
> request y se discute, porque cambiarlo cambia la definición de "correcto".
> Y la calidad del sistema no puede superar la de sus etiquetas.

**Por qué el porcentaje de acierto miente.** En un padrón de un millón de
inscripciones con mil duplicados, un sistema que responda **siempre** "no es
duplicado", sin mirar nada, acierta el **99.9%**. Su exactitud es magnífica y
su recall es cero. La prueba 04 lo calcula.

Casi todos los problemas interesantes están desequilibrados —el fraude, la
enfermedad rara, el duplicado— porque lo que se busca es, por definición, lo
que escasea.

### Módulo 2 — Pruebas metamórficas ([`PruebasMetamorficasTest`](deduplicador/src/test/java/edu/unisabana/tyvs/dedup/PruebasMetamorficasTest.java))

La técnica central del testing sin oráculo, y resuelve un problema que parecía
irresoluble.

**El truco: no hace falta saber cuál es la respuesta correcta para saber que
dos respuestas tienen que estar relacionadas.**

No se sabe cuánto deben parecerse `"José Rodríguez"` y `"Jose Rodrigez"`. Pero
sí se sabe, con total certeza, que ese número tiene que ser **el mismo** que
el de `"Jose Rodrigez"` y `"José Rodríguez"`. Comparar es simétrico.

```text
entrada  ──transformación──▶  entrada'
   │                             │
salida   ──relación esperada──▶ salida'
```

Las siete relaciones verificadas, con jqwik (cientos de casos generados cada
una):

| # | Relación | Enunciado |
|---|---|---|
| 1 | Simetría | `similitud(a,b) == similitud(b,a)` |
| 2 | Reflexividad | `similitud(a,a) == 1.0` |
| 3 | Rango acotado | el resultado siempre en `[0,1]` |
| 4 | Invarianza a mayúsculas | `"ANA"` y `"ana"` dan lo mismo |
| 5 | Invarianza al espacio sobrante | `" ana "` == `"ana"` |
| 6 | Invarianza a las tildes | `"Muñoz"` == `"Munoz"` |
| 7 | Monotonía ante el ruido | añadir basura no puede aumentar la similitud |

> La relación 6 **es una decisión de producto disfrazada de detalle técnico**.
> Para un padrón colombiano es correcta: la gente escribe su apellido sin
> tilde constantemente. En otro contexto sería un defecto, porque hay idiomas
> donde el diacrítico distingue dos palabras. Está escrita como prueba, y no
> como comentario, para que si alguien cambia la normalización la discusión
> vuelva a abrirse en vez de ocurrir en silencio.

Esta técnica se usa hoy tal cual con LLMs: reformular una pregunta sin cambiar
su sentido no debería cambiar la respuesta.

### Módulo 3 — Equidad ([`EquidadEntreGruposTest`](deduplicador/src/test/java/edu/unisabana/tyvs/dedup/EquidadEntreGruposTest.java))

**Este es el módulo que justifica el taller.**

Métricas globales del sistema, medidas:

```text
precisión 0.938   recall 0.750   F1 0.833
```

Se ve razonable. Ahora las mismas métricas, desagregadas por grupo:

| Grupo | Precisión | Recall | F1 |
|---|---|---|---|
| compuesto | 1.000 | 1.000 | **1.000** |
| tilde | 1.000 | 1.000 | **1.000** |
| simple | 0.833 | 1.000 | 0.909 |
| **abreviado** | 1.000 | **0.000** | **0.000** |

**El sistema no encuentra ni uno solo de los diez duplicados del grupo
`abreviado`.** No es "peor con ese grupo": es **ciego** a ese grupo. Quien se
inscribe como `Ana M Torres` y luego como `Ana María Torres` no será detectada
nunca. Y el F1 global de 0.833 no lo insinúa siquiera.

La causa no tiene nada de misteriosa: abreviar `Antonio` a `A` borra seis
caracteres y Jaro-Winkler penaliza la diferencia de longitud. Las puntuaciones
caen entre 0.808 y 0.925, todas bajo el umbral de 0.95.

**No es un bug del algoritmo: hace exactamente lo que se le pidió. Es que a
nadie se le ocurrió comprobar si lo que se le pidió funcionaba igual para
todos.**

> 🔍 **La prueba 04 enseña el error contrario, y hay que verlo una vez.** Con
> umbral 0.90, el grupo `tilde` salía con la peor precisión de los cuatro
> (0.588) y era tentador concluir *"el sistema discrimina a las personas con
> tildes"*. Era **falso**: los pares negativos de ese grupo se habían escrito
> compartiendo el nombre de pila y las primeras letras del apellido
> (Rodríguez/Rodrigo, Muñoz/Mendoza), y por eso eran más difíciles. La
> diferencia venía **del conjunto de evaluación, no del sistema**.
>
> Antes de escribir "el sistema discrimina", descarte que la diferencia venga
> de cómo construyó el conjunto. La primera vez casi siempre viene de ahí.

**Sobre los grupos.** Se agrupa por la *forma* del nombre, no por atributos
protegidos. Aun así es relevante: la forma del nombre correlaciona con el
origen. Un sistema que falla con apellidos compuestos falla más con personas
de ciertas comunidades, **aunque nadie haya escrito nunca la palabra "origen"
en el código**. Así es como un sistema discrimina sin que nadie lo programara
para discriminar.

### Módulo 4 — Umbral y regresión ([`UmbralYRegresionTest`](deduplicador/src/test/java/edu/unisabana/tyvs/dedup/UmbralYRegresionTest.java))

**No existe una configuración buena, solo un compromiso elegido.** Medido:

| umbral | precisión | recall | F1 |
|---|---|---|---|
| 0.80 | 0.513 | 1.000 | 0.678 |
| 0.85 | 0.543 | 0.950 | 0.691 |
| 0.88 | 0.644 | 0.950 | 0.768 |
| 0.90 | 0.729 | 0.875 | 0.795 |
| 0.92 | 0.775 | 0.775 | 0.775 |
| **0.95** | **0.938** | **0.750** | 0.833 |

**No hay ninguna fila en la que las dos mejoren a la vez**, y no es casualidad
de estos datos: es una propiedad matemática del umbral.

Y lo que está en juego no es un número:

- **Umbral alto** → se escapan duplicados → alguien vota dos veces.
- **Umbral bajo** → se marcan personas distintas → **a alguien le niegan el
  voto por llamarse parecido a otro**.

Se eligió 0.95 con un criterio escrito: *negarle el voto a una persona real es
peor que dejar pasar un duplicado, que después se revisa a mano*. Ese es el
orden correcto — **primero la política, después el número**. Elegir el umbral
que maximiza F1 y justificarlo después es hacerlo al revés, y F1 pondera los
dos errores por igual justo cuando la decisión consiste en que no valen igual.

**Regresión.** En un sistema de IA, "no rompas nada" deja de ser binario. Un
cambio en la normalización no produce una excepción: produce tres décimas
menos de recall, que nadie nota hasta que alguien se queja. Por eso la línea
base está congelada como matriz de confusión exacta (`VP=30 FP=2 FN=10
VN=38`), con instrucciones de qué preguntarse **antes** de actualizarla.

### Módulo 5 — De este taller a un LLM ([`docs/de-este-taller-a-un-llm.md`](docs/de-este-taller-a-un-llm.md))

Qué cambia y qué no al pasar a un sistema no determinista: *golden datasets*,
*LLM-as-judge* y su circularidad, inyección de *prompt*, y el hecho incómodo
de que el proveedor puede cambiar el modelo bajo sus pies sin avisarle.

---

## PARA ENTREGAR CON ESTE TALLER

### 1) Repositorio

- `mvn clean test` en verde tras un clon limpio.

### 2) Ampliar el conjunto de evaluación (módulo 1)

- **20 pares nuevos** etiquetados por usted, en un **grupo nuevo** que no
  exista. Sugerencias: nombres invertidos (apellido primero), nombres de una
  sola palabra, nombres muy largos, apodos.
- Métricas del sistema sobre su grupo, comparadas con las de los cuatro
  existentes.
- Explique **por qué eligió ese grupo**: ¿qué población representa?

### 3) Una relación metamórfica nueva (módulo 2)

- Enunciada y automatizada con jqwik.
- Debe ser una relación **real**, no una tautología. Si su propiedad pasa
  también con un sistema que devuelve siempre 0.5, no está probando nada.

### 4) Cerrar la brecha de equidad (módulo 3)

**Es el entregable principal.** El sistema tiene recall 0.000 con los nombres
abreviados. Arréglelo.

- Implemente la mejora (una pista: la normalización podría tratar una inicial
  suelta como compatible con cualquier nombre que empiece por esa letra).
- Mida **antes y después**, global y por grupo.
- Conteste explícitamente: **¿qué se rompió a cambio?** Si su arreglo mejora
  `abreviado` y empeora `simple`, dígalo. Casi siempre pasa algo así, y
  ocultarlo es peor que no arreglarlo.
- Actualice las pruebas 02, 03 y 05 de `EquidadEntreGruposTest`, que hoy
  documentan el defecto y fallarán cuando usted lo corrija.

### 5) Justificar un umbral (módulo 4)

- Elija un umbral **distinto** de 0.95 y defiéndalo, empezando por la política
  y no por la métrica.
- Diga **a quién perjudica** su elección. Toda elección perjudica a alguien.

### 6) Reflexión final (en el Wiki)

Una de estas dos:

- El sistema tiene F1 global de 0.833 y recall 0.000 con un grupo. Si usted
  fuera responsable del padrón, ¿lo desplegaría? Justifique.
- ¿Qué información adicional (no el algoritmo) reduciría los falsos positivos
  de `Sofía Vargas` / `Sonia Vargas`? ¿Qué coste tendría pedirla?

---

## Rúbrica

| **Criterios de evaluación** | **Indicadores** | **Excelente (5 pts)** | **Bueno (4 pts)** | **Necesita mejorar (3.5 pts)** | **Deficiente (2.5 pts)** | **No cumple (0 pts)** |
|---|---|---|---|---|---|---|
| **Estructura y ejecución** | El proyecto corre con `mvn test`. | Todo verde tras un clon limpio. | Corre con ajustes menores. | Requiere pasos no documentados. | Falla en varias pruebas. | No ejecuta. |
| **Conjunto de evaluación** | Calidad y representatividad del grupo nuevo. | 20 pares equilibrados, grupo bien justificado, métricas comparadas. | 20 pares correctos, justificación breve. | Menos de 20 o desequilibrado. | Pares triviales o mal etiquetados. | No amplía el conjunto. |
| **Prueba metamórfica** | Relación real y automatizada. | Relación no trivial, con jqwik, que fallaría con un sistema degenerado. | Relación correcta pero poco exigente. | Relación tautológica. | No automatizada. | No entrega. |
| **Equidad: diagnóstico** **(vale por 2)** | Medición desagregada. | Mide antes y después, global y por grupo, e identifica el efecto colateral. | Mide antes y después sin analizar efectos colaterales. | Solo mide global. | Repite los números del README sin medir. | No mide. |
| **Equidad: corrección** **(vale por 2)** | La brecha se cierra. | Recall del grupo abreviado sustancialmente mayor, con pruebas actualizadas y sin degradar los otros grupos. | Mejora parcial, algún grupo degradado y reconocido. | Intento sin mejora medible. | Cambia el umbral y lo llama arreglo. | No intenta. |
| **Justificación del umbral** | Política antes que métrica. | Criterio explícito, dice a quién perjudica, coherente con los datos. | Justificación razonable. | Justifica solo con F1. | Elige sin justificar. | No aborda. |
| **Documentación y reflexión** | Wiki con análisis. | Reflexión que distingue rendimiento de daño. | Clara pero sin profundidad. | Incompleta. | Mínima. | Sin documentación. |

> **Cómo suma**: 9 criterios × 5 pts = **45 puntos**. Son 7 filas, pero
> *Equidad: diagnóstico* y *Equidad: corrección* valen por dos cada una — son
> el núcleo del taller.

| Rango de puntaje | Desempeño |
| ---------------- | --------- |
| 41 – 45 | Excelente dominio técnico y metodológico. |
| 32 – 40 | Buen trabajo con documentación o análisis parcial. |
| 27 – 31 | Cumple con lo básico pero sin profundidad. |
| < 27 | No cumple con los criterios mínimos del taller. |

---

## Créditos y uso académico

Material del curso **Testing y Validación de Software**, Maestría en
Ingeniería de Software, **Universidad de La Sabana**.

Todos los nombres de los conjuntos de datos son **ficticios**. No proceden de
ningún padrón real ni de ninguna base de datos de personas.
