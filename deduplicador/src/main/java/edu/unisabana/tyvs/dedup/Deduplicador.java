package edu.unisabana.tyvs.dedup;

/**
 * Decide si dos inscripciones corresponden a la MISMA persona.
 *
 * Es el problema real de una registraduria: la misma persona se inscribe dos
 * veces escribiendo su nombre distinto ("Jose Rodriguez", "Jose A. Rodriguez",
 * "Jose Rodrigez"), y hay que detectarlo sin fusionar por error a dos personas
 * que de verdad son distintas.
 *
 * ============================================================================
 * EL UMBRAL ES UNA DECISION DE NEGOCIO, NO UN PARAMETRO TECNICO
 * ============================================================================
 *
 * Todo el sistema se reduce a una comparacion contra un numero. Y ese numero
 * decide a quien perjudica el sistema cuando se equivoca:
 *
 *   Umbral ALTO (p. ej. 0.95)
 *     Solo marca duplicados cuando esta muy seguro.
 *     -> Se le escapan duplicados reales (falsos negativos).
 *     -> Consecuencia: alguien vota dos veces.
 *
 *   Umbral BAJO (p. ej. 0.80)
 *     Marca duplicados a la minima sospecha.
 *     -> Marca como duplicadas a personas distintas (falsos positivos).
 *     -> Consecuencia: a alguien le niegan el derecho al voto por llamarse
 *        parecido a otro.
 *
 * No existe un umbral que elimine los dos errores. Solo se elige CUAL de los
 * dos se prefiere, y esa eleccion no la puede tomar quien programa: es una
 * decision de politica publica.
 *
 * CRITERIO ADOPTADO EN ESTE TALLER: en un padron electoral, negarle el voto a
 * una persona real se considera peor que dejar pasar un duplicado, que
 * despues se puede revisar a mano. Es decir, se prefiere PRECISION alta
 * aunque cueste RECALL.
 *
 * Medido sobre el conjunto de evaluacion (80 pares etiquetados):
 *
 *   umbral   precision   recall    F1
 *   0.80       0.513     1.000    0.678
 *   0.85       0.543     0.950    0.691
 *   0.88       0.644     0.950    0.768
 *   0.90       0.729     0.875    0.795
 *   0.92       0.775     0.775    0.775
 *   0.95       0.938     0.750    0.833
 *
 * Se elige 0.95 porque es el que mejor cumple el criterio escrito arriba. Ese
 * es el orden correcto: primero la politica, despues el numero. Elegir el
 * umbral que maximiza F1 y justificarlo despues es hacerlo al reves, y F1
 * pondera los dos errores por igual precisamente cuando la decision consiste
 * en que NO valen igual.
 *
 * ADVERTENCIA REGISTRADA (ver EquidadEntreGruposTest):
 * con 0.95 el sistema tiene recall 0.000 sobre los nombres con la inicial
 * abreviada. No es "peor con ese grupo": es CIEGO a ese grupo. Ninguna de las
 * metricas globales de la tabla lo insinua siquiera.
 *
 * Que todo esto este escrito importa: dentro de un ano nadie recordara si el
 * 0.95 fue una decision razonada o el primer numero que funciono.
 */
public class Deduplicador {

    /** Ver el javadoc de la clase: no es un numero cualquiera. */
    public static final double UMBRAL_POR_DEFECTO = 0.95;

    private final double umbral;

    public Deduplicador() {
        this(UMBRAL_POR_DEFECTO);
    }

    public Deduplicador(double umbral) {
        if (umbral < 0.0 || umbral > 1.0) {
            throw new IllegalArgumentException("El umbral debe estar entre 0 y 1: " + umbral);
        }
        this.umbral = umbral;
    }

    /** @return true si el sistema cree que son la misma persona. */
    public boolean esMismaPersona(String nombreA, String nombreB) {
        return Similitud.similitud(nombreA, nombreB) >= umbral;
    }

    /**
     * La confianza con la que lo cree, no solo el si/no.
     *
     * Exponer la puntuacion y no solo la decision es una buena practica en
     * cualquier sistema de este tipo: permite ordenar los casos dudosos para
     * revision humana, y permite medir el sistema sin volver a ejecutarlo con
     * otro umbral.
     */
    public double confianza(String nombreA, String nombreB) {
        return Similitud.similitud(nombreA, nombreB);
    }

    public double getUmbral() {
        return umbral;
    }
}
