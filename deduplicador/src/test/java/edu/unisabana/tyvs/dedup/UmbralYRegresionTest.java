package edu.unisabana.tyvs.dedup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MODULO 4 — EL COMPROMISO DEL UMBRAL Y LA REGRESION DEL MODELO
 *
 * Dos ideas, y las dos son incomodas.
 *
 * PRIMERA: no existe una configuracion buena, solo un compromiso elegido.
 * Se demuestra midiendolo, no argumentandolo: al recorrer el umbral, la
 * precision sube y el recall baja de forma monotona. Siempre. No hay un punto
 * magico donde las dos mejoren.
 *
 * SEGUNDA: en un sistema de IA, "no rompas nada" deja de ser binario. Un
 * cambio en la normalizacion no produce un error de compilacion ni una
 * excepcion: produce tres decimas menos de recall, que nadie nota hasta que
 * alguien se queja. Por eso la prueba de regresion no compara resultados sino
 * METRICAS, y no exige igualdad sino un minimo.
 *
 * Es exactamente el mismo papel que juega el mutationThreshold en el taller
 * de pruebas unitarias: no impide cambiar, impide degradar en silencio.
 */
@DisplayName("Modulo 4 - Umbral, compromiso y regresion")
class UmbralYRegresionTest {

    private final List<ConjuntoDeEvaluacion.Par> pares = ConjuntoDeEvaluacion.cargar();

    @Test
    @DisplayName("01 - La curva del compromiso: precision sube, recall baja")
    void laCurvaDelCompromiso() {
        double[] umbrales = { 0.80, 0.85, 0.88, 0.90, 0.92, 0.95, 0.97 };

        System.out.println();
        System.out.println("  umbral  precision  recall     F1");
        System.out.println("  ------------------------------------");

        double precisionAnterior = -1;
        double recallAnterior = 2;

        for (double u : umbrales) {
            Metricas m = Metricas.evaluar(new Deduplicador(u), pares);
            System.out.printf("   %.2f     %.3f     %.3f    %.3f%n",
                    u, m.precision(), m.recall(), m.f1());

            // La monotonia no es una casualidad de estos datos: es una
            // propiedad matematica del umbral. Subirlo solo puede quitar
            // predicciones positivas, nunca anadirlas.
            assertTrue(m.precision() >= precisionAnterior - 1e-9,
                    "La precision bajo al subir el umbral a " + u);
            assertTrue(m.recall() <= recallAnterior + 1e-9,
                    "El recall subio al subir el umbral a " + u);

            precisionAnterior = m.precision();
            recallAnterior = m.recall();
        }

        System.out.println();
        System.out.println("  No hay ninguna fila en la que las dos mejoren a la vez.");
        System.out.println("  Elegir la fila es una decision de politica publica.");
    }

    @Test
    @DisplayName("02 - Los extremos del umbral degeneran a sistemas inutiles")
    void losExtremosDegeneran() {
        // Umbral 0: todo el mundo es la misma persona.
        Metricas todoDuplicado = Metricas.evaluar(new Deduplicador(0.0), pares);
        assertEquals(1.0, todoDuplicado.recall(), 0.001,
                "Con umbral 0 se encuentran todos los duplicados...");
        assertEquals(0.5, todoDuplicado.precision(), 0.01,
                "...y la mitad de las personas marcadas no lo eran");

        // Umbral 1.0: solo los nombres identicos caracter a caracter.
        Metricas soloIdenticos = Metricas.evaluar(new Deduplicador(1.0), pares);
        System.out.println("  umbral 1.0 -> " + soloIdenticos);

        // Los dos extremos "funcionan" segun alguna metrica, y ninguno sirve.
        // Es la razon por la que una sola metrica nunca basta para aceptar un
        // sistema.
        assertTrue(soloIdenticos.recall() < todoDuplicado.recall());
    }

    @Test
    @DisplayName("03 - Regresion: la linea base medida no se degrada")
    void noHayRegresionRespectoALaLineaBase() {
        // LINEA BASE CONGELADA. Estos numeros se midieron el 2026-08-29 con
        // Jaro-Winkler y el umbral 0.95 sobre los 80 pares del conjunto.
        //
        // Se comparan con igualdad exacta a proposito, no con un minimo: aqui
        // se quiere detectar CUALQUIER cambio de comportamiento, incluida una
        // mejora. Una mejora inesperada tambien es una senal: significa que
        // algo cambio sin que se decidiera.
        Metricas actual = Metricas.evaluar(new Deduplicador(), pares);

        assertEquals(30, actual.verdaderosPositivos, "VP cambio: " + actual);
        assertEquals(2, actual.falsosPositivos, "FP cambio: " + actual);
        assertEquals(10, actual.falsosNegativos, "FN cambio: " + actual);
        assertEquals(38, actual.verdaderosNegativos, "VN cambio: " + actual);

        // Si esta prueba falla, NO la actualice sin mas. Primero conteste:
        //   - cambio el algoritmo, el umbral o el conjunto de evaluacion?
        //   - la nueva matriz es mejor o peor, y para quien?
        //   - la brecha entre grupos crecio o se redujo?
        // Actualizar el numero sin contestar eso convierte la prueba en un
        // sello de goma.
    }

    @Test
    @DisplayName("04 - Los falsos positivos actuales son casos genuinamente dificiles")
    void losFalsosPositivosSonCasosDificiles() {
        Deduplicador sistema = new Deduplicador();

        // Los dos unicos falsos positivos que quedan:
        //   "Sofia Vargas"  vs "Sonia Vargas"   -> 0.956
        //   "Andres Pinto"  vs "Andrea Pinto"   -> 0.967
        //
        // Merece la pena mirarlos de cerca, porque son el limite real del
        // problema y no un descuido: una letra de diferencia en el nombre de
        // pila y el apellido identico. Un humano tampoco lo resuelve viendo
        // solo el nombre; necesita la fecha de nacimiento o el documento.
        //
        // Conclusion practica: cuando la metrica se estanca, a veces la
        // respuesta no es ajustar el modelo sino DARLE MAS INFORMACION.
        assertTrue(sistema.confianza("Sofia Vargas", "Sonia Vargas") > 0.95);
        assertTrue(sistema.confianza("Andres Pinto", "Andrea Pinto") > 0.95);

        // Y aqui esta la prueba de que el problema no se arregla con el
        // umbral: subirlo lo suficiente para excluir estos dos casos deja al
        // sistema sin encontrar practicamente nada.
        Metricas muyEstricto = Metricas.evaluar(new Deduplicador(0.97), pares);
        assertTrue(muyEstricto.recall() <= 0.60,
                "Subir el umbral hasta eliminar estos falsos positivos hunde el recall: "
                        + muyEstricto);
    }
}
