package edu.unisabana.tyvs.dedup;

import java.util.List;

/**
 * Matriz de confusion y metricas derivadas.
 *
 * ============================================================================
 * POR QUE "PORCENTAJE DE ACIERTO" ES UNA MALA METRICA
 * ============================================================================
 *
 * Es la primera que se le ocurre a todo el mundo y casi siempre miente.
 *
 * Imagine un padron de un millon de inscripciones con mil duplicados reales.
 * Un sistema que responda SIEMPRE "no es duplicado", sin mirar nada, acierta
 * el 99.9% de las veces. Su exactitud es magnifica y su utilidad es cero.
 *
 * Por eso hacen falta dos metricas separadas, y hay que entender que miden
 * dos preguntas DISTINTAS:
 *
 *   PRECISION — "de los que marque como duplicados, cuantos lo eran?"
 *               Castiga los falsos positivos.
 *               Baja precision  = se le niega el voto a gente real.
 *
 *   RECALL    — "de los duplicados que habia, cuantos encontre?"
 *               Castiga los falsos negativos.
 *               Bajo recall     = hay gente votando dos veces.
 *
 * Y estan en tension: subir el umbral mejora la precision y empeora el
 * recall. Siempre. No hay configuracion que mejore las dos a la vez, y por
 * eso elegir el umbral es una decision de politica, no de ingenieria.
 *
 * F1 es la media armonica de las dos. Es comoda para comparar dos versiones
 * del sistema de un vistazo, pero ESCONDE la decision: dos sistemas con el
 * mismo F1 pueden perjudicar a personas completamente distintas. Nunca
 * reporte solo F1.
 */
public final class Metricas {

    /** Marcados como duplicados, y lo eran. */
    public final int verdaderosPositivos;
    /** Marcados como duplicados, y NO lo eran. A esta gente se le niega el voto. */
    public final int falsosPositivos;
    /** No marcados, y si eran duplicados. Esta gente vota dos veces. */
    public final int falsosNegativos;
    /** No marcados, y no lo eran. */
    public final int verdaderosNegativos;

    private Metricas(int vp, int fp, int fn, int vn) {
        this.verdaderosPositivos = vp;
        this.falsosPositivos = fp;
        this.falsosNegativos = fn;
        this.verdaderosNegativos = vn;
    }

    public static Metricas evaluar(Deduplicador sistema, List<ConjuntoDeEvaluacion.Par> pares) {
        int vp = 0, fp = 0, fn = 0, vn = 0;

        for (ConjuntoDeEvaluacion.Par par : pares) {
            boolean prediccion = sistema.esMismaPersona(par.nombreA, par.nombreB);

            if (prediccion && par.mismaPersona) {
                vp++;
            } else if (prediccion) {
                fp++;
            } else if (par.mismaPersona) {
                fn++;
            } else {
                vn++;
            }
        }
        return new Metricas(vp, fp, fn, vn);
    }

    /** De los que marque, cuantos acerte. Devuelve 1.0 si no marque ninguno. */
    public double precision() {
        int marcados = verdaderosPositivos + falsosPositivos;
        return marcados == 0 ? 1.0 : (double) verdaderosPositivos / marcados;
    }

    /** De los que habia, cuantos encontre. Devuelve 1.0 si no habia ninguno. */
    public double recall() {
        int existentes = verdaderosPositivos + falsosNegativos;
        return existentes == 0 ? 1.0 : (double) verdaderosPositivos / existentes;
    }

    public double f1() {
        double p = precision();
        double r = recall();
        return (p + r) == 0 ? 0.0 : 2 * p * r / (p + r);
    }

    /** Se incluye para poder ensenar por que NO hay que usarla sola. */
    public double exactitud() {
        int total = total();
        return total == 0 ? 0.0 : (double) (verdaderosPositivos + verdaderosNegativos) / total;
    }

    public int total() {
        return verdaderosPositivos + falsosPositivos + falsosNegativos + verdaderosNegativos;
    }

    @Override
    public String toString() {
        return String.format(
                "n=%d  VP=%d FP=%d FN=%d VN=%d  |  precision=%.3f  recall=%.3f  F1=%.3f  exactitud=%.3f",
                total(), verdaderosPositivos, falsosPositivos, falsosNegativos, verdaderosNegativos,
                precision(), recall(), f1(), exactitud());
    }
}
