package edu.unisabana.tyvs.dedup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MODULO 1 — EL PROBLEMA DEL ORACULO
 *
 * En los talleres anteriores, una prueba se escribe asi:
 *
 *     assertEquals(RegisterResult.VALID, registry.registerVoter(ana));
 *
 * Funciona porque existe una respuesta correcta y usted la conoce. A eso se
 * le llama tener un ORACULO.
 *
 * Aqui no lo hay. "Jose Rodriguez" y "Jose Rodrigez" son la misma persona?
 * No existe la funcion que responda eso sin equivocarse nunca; si existiera,
 * no haria falta el sistema. Y como no hay respuesta unica, tampoco hay una
 * asercion que se pueda escribir sobre un caso suelto.
 *
 * Lo que se hace en su lugar:
 *
 *   1. Se etiqueta a mano un conjunto de casos (el CSV de recursos).
 *   2. Se ejecuta el sistema sobre todos.
 *   3. Se afirma sobre la METRICA AGREGADA, no sobre cada caso.
 *
 * La asercion deja de ser "esto vale X" y pasa a ser "la precision no baja de
 * X". El cambio de mentalidad es el objetivo del modulo: un caso que falla
 * deja de ser un bug automaticamente. Puede ser un caso dificil, una etiqueta
 * mal puesta, o una degradacion real. Distinguirlos es el trabajo.
 *
 * Los umbrales de abajo NO son objetivos aspiracionales: son el rendimiento
 * MEDIDO hoy, menos un margen. Sirven como deteccion de regresion, igual que
 * el mutationThreshold del taller de pruebas unitarias.
 */
@DisplayName("Modulo 1 - Metricas sobre el conjunto de evaluacion")
class MetricasDeEvaluacionTest {

    /**
     * Margen de tolerancia. Sin el, cualquier cambio inocuo pone el build en
     * rojo y el equipo aprende a subir el numero en vez de investigar. Con un
     * margen demasiado ancho, el sistema se degrada sin que nadie se entere.
     * 0.05 es una eleccion, y como toda eleccion, discutible.
     */
    private static final double MARGEN = 0.05;

    private final List<ConjuntoDeEvaluacion.Par> pares = ConjuntoDeEvaluacion.cargar();
    private final Deduplicador sistema = new Deduplicador();

    @Test
    @DisplayName("01 - El conjunto de evaluacion esta completo y equilibrado")
    void elConjuntoEstaEquilibrado() {
        // Primero se valida el oraculo, no el sistema. Un conjunto de
        // evaluacion desequilibrado produce metricas que parecen buenas y no
        // significan nada: con 95% de negativos, decir siempre "no" da 95%.
        assertEquals(80, pares.size(), "Cambio el tamano del conjunto de evaluacion");

        long positivos = pares.stream().filter(p -> p.mismaPersona).count();
        long negativos = pares.size() - positivos;

        assertEquals(40, positivos, "Deberia haber 40 pares de la misma persona");
        assertEquals(40, negativos, "Deberia haber 40 pares de personas distintas");

        // Cuatro grupos, veinte pares cada uno. Necesario para que el modulo 3
        // pueda comparar entre subpoblaciones sin que el tamano lo distorsione.
        assertEquals(List.of("abreviado", "compuesto", "simple", "tilde"),
                ConjuntoDeEvaluacion.grupos());
        for (String grupo : ConjuntoDeEvaluacion.grupos()) {
            assertEquals(20, ConjuntoDeEvaluacion.deGrupo(grupo).size(),
                    "El grupo " + grupo + " cambio de tamano");
        }
    }

    @Test
    @DisplayName("02 - La precision no cae por debajo de lo medido")
    void laPrecisionNoCae() {
        Metricas m = Metricas.evaluar(sistema, pares);
        System.out.println("GLOBAL: " + m);

        // Precision medida con umbral 0.95: 0.938.
        // Es la metrica que protege a las personas reales: una precision baja
        // significa gente a la que se le niega el voto por parecerse a otra.
        assertTrue(m.precision() >= 0.938 - MARGEN,
                "La precision cayo a " + m.precision() + ". Detalle: " + m);
    }

    @Test
    @DisplayName("03 - El recall no cae por debajo de lo medido")
    void elRecallNoCae() {
        Metricas m = Metricas.evaluar(sistema, pares);

        // Recall medido: 0.750. Mas bajo que la precision, y es intencional:
        // ver el javadoc de Deduplicador. Se acepta que se escapen duplicados
        // antes que rechazar a una persona real.
        assertTrue(m.recall() >= 0.750 - MARGEN,
                "El recall cayo a " + m.recall() + ". Detalle: " + m);
    }

    @Test
    @DisplayName("04 - Por que la exactitud es una metrica enganosa")
    void laExactitudEsEnganosa() {
        // Un sistema que responde SIEMPRE "no son la misma persona", sin mirar
        // nada. Umbral > 1.0 es imposible de alcanzar, asi que nunca marca.
        Deduplicador inutil = new Deduplicador(1.0) {
            @Override
            public boolean esMismaPersona(String a, String b) {
                return false;
            }
        };

        Metricas m = Metricas.evaluar(inutil, pares);

        // Sobre ESTE conjunto, equilibrado al 50/50, la exactitud del sistema
        // inutil es 0.5 y se le ve el truco enseguida.
        assertEquals(0.5, m.exactitud(), 0.001);

        // Pero un padron real no esta equilibrado. Si hay 1000 duplicados en
        // un millon de inscripciones, el MISMO sistema inutil acierta el
        // 99.9%. Se simula el calculo:
        int inscripciones = 1_000_000;
        int duplicadosReales = 1_000;
        double exactitudEnProduccion =
                (double) (inscripciones - duplicadosReales) / inscripciones;

        assertTrue(exactitudEnProduccion >= 0.999,
                "Un sistema que no hace nada tendria " + exactitudEnProduccion + " de exactitud");

        // Y su recall seria 0: no encuentra ni un solo duplicado.
        assertEquals(0.0, m.recall(), 0.001,
                "El sistema inutil no encuentra ningun duplicado, y eso es lo que hay que reportar");

        // MORALEJA: nunca reporte exactitud sobre un problema desequilibrado.
        // Y casi todos los problemas interesantes lo estan: el fraude, la
        // enfermedad rara, el duplicado. Lo que se busca es, por definicion,
        // lo que escasea.
    }

    @Test
    @DisplayName("05 - Se reporta precision y recall por separado, nunca solo F1")
    void seReportanLasDosPorSeparado() {
        // Dos sistemas con el mismo F1 pueden perjudicar a personas
        // completamente distintas. F1 sirve para comparar de un vistazo y
        // esconde exactamente la decision que importa.
        Metricas conservador = Metricas.evaluar(new Deduplicador(0.97), pares);
        Metricas permisivo = Metricas.evaluar(new Deduplicador(0.88), pares);

        System.out.printf("conservador (0.97): %s%n", conservador);
        System.out.printf("permisivo   (0.88): %s%n", permisivo);

        // El conservador protege a las personas reales; el permisivo encuentra
        // mas duplicados. Cual es "mejor" no lo decide esta prueba.
        assertTrue(conservador.precision() > permisivo.precision(),
                "Subir el umbral deberia mejorar la precision");
        assertTrue(permisivo.recall() > conservador.recall(),
                "Bajar el umbral deberia mejorar el recall");
    }
}
