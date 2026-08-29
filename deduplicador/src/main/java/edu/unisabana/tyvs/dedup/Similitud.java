package edu.unisabana.tyvs.dedup;

import java.text.Normalizer;
import java.util.Locale;

/**
 * Mide que tan parecidos son dos nombres, en una escala de 0 a 1.
 *
 * Por que esto es un "sistema de IA" para efectos del taller:
 *
 * No es una red neuronal, y eso es a proposito. Es un sistema de decision
 * ESTADISTICO, y tiene exactamente las mismas propiedades incomodas que un
 * modelo de aprendizaje automatico o un LLM:
 *
 *   - No hay respuesta unica correcta. "Jose Rodriguez" y "Jose Rodrigez"
 *     son la misma persona? Depende. No existe la funcion que lo decide sin
 *     equivocarse nunca.
 *   - Se equivoca, y equivocarse forma parte de su funcionamiento normal.
 *     La pregunta util no es "acierta?" sino "con que frecuencia y en que
 *     direccion se equivoca?".
 *   - Su comportamiento depende de un umbral que alguien eligio, y mover ese
 *     umbral cambia QUIEN sale perjudicado.
 *   - Puede funcionar peor con unos grupos de personas que con otros.
 *
 * La ventaja didactica de que sea determinista es que usted puede ejecutar
 * el taller entero sin GPU, sin claves de API y sin internet, y obtener el
 * mismo numero que su companero. Las tecnicas son las mismas que se aplican
 * a un clasificador de imagenes o a un LLM; lo unico que cambia es el coste
 * de cada ejecucion.
 */
public final class Similitud {

    private Similitud() {
    }

    /**
     * Normaliza un nombre antes de compararlo.
     *
     * ESTE METODO ES UNA DECISION DE PRODUCTO DISFRAZADA DE DETALLE TECNICO,
     * y conviene verlo asi desde el principio.
     *
     * Al quitar las tildes, "Munoz" y "Munoz" pasan a ser identicos. Eso
     * ayuda a quien escribio su nombre sin tilde en un formulario, y a la vez
     * borra una distincion que en otros idiomas SI separa a dos personas
     * distintas. No hay una opcion correcta: hay una opcion elegida.
     *
     * El modulo 3 mide el efecto de esta linea sobre distintos grupos.
     */
    public static String normalizar(String nombre) {
        if (nombre == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(nombre, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");

        return sinTildes
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /**
     * Similitud de Jaro-Winkler entre dos nombres ya normalizados.
     *
     * Se elige Jaro-Winkler y no la distancia de Levenshtein porque premia
     * las coincidencias al PRINCIPIO de la cadena, y en nombres propios eso
     * es lo que importa: los errores de digitacion tienden a estar al final,
     * y los nombres que empiezan igual suelen ser la misma persona.
     *
     * Es una eleccion razonable. No es la unica, y el modulo 4 mide que pasa
     * cuando se cambia.
     */
    public static double similitud(String a, String b) {
        String x = normalizar(a);
        String y = normalizar(b);

        if (x.isEmpty() && y.isEmpty()) {
            return 1.0;
        }
        if (x.isEmpty() || y.isEmpty()) {
            return 0.0;
        }
        if (x.equals(y)) {
            return 1.0;
        }

        double jaro = jaro(x, y);

        // Winkler: bonifica hasta 4 caracteres iniciales comunes.
        int prefijo = 0;
        int maximo = Math.min(4, Math.min(x.length(), y.length()));
        while (prefijo < maximo && x.charAt(prefijo) == y.charAt(prefijo)) {
            prefijo++;
        }

        return jaro + prefijo * 0.1 * (1.0 - jaro);
    }

    private static double jaro(String x, String y) {
        int ventana = Math.max(x.length(), y.length()) / 2 - 1;
        if (ventana < 0) {
            ventana = 0;
        }

        boolean[] usadoEnX = new boolean[x.length()];
        boolean[] usadoEnY = new boolean[y.length()];

        int coincidencias = 0;
        for (int i = 0; i < x.length(); i++) {
            int desde = Math.max(0, i - ventana);
            int hasta = Math.min(y.length(), i + ventana + 1);

            for (int j = desde; j < hasta; j++) {
                if (usadoEnY[j] || x.charAt(i) != y.charAt(j)) {
                    continue;
                }
                usadoEnX[i] = true;
                usadoEnY[j] = true;
                coincidencias++;
                break;
            }
        }

        if (coincidencias == 0) {
            return 0.0;
        }

        // Transposiciones: caracteres que coinciden pero en distinto orden.
        int transposiciones = 0;
        int k = 0;
        for (int i = 0; i < x.length(); i++) {
            if (!usadoEnX[i]) {
                continue;
            }
            while (!usadoEnY[k]) {
                k++;
            }
            if (x.charAt(i) != y.charAt(k)) {
                transposiciones++;
            }
            k++;
        }

        double m = coincidencias;
        return (m / x.length() + m / y.length() + (m - transposiciones / 2.0) / m) / 3.0;
    }
}
