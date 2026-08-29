package edu.unisabana.tyvs.dedup;

import net.jqwik.api.ForAll;
import net.jqwik.api.Property;
import net.jqwik.api.Provide;
import net.jqwik.api.Arbitraries;
import net.jqwik.api.Arbitrary;
import net.jqwik.api.constraints.IntRange;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MODULO 2 — PRUEBAS METAMORFICAS
 *
 * Es la tecnica central del testing de sistemas sin oraculo, y merece
 * entenderse bien porque resuelve un problema que parecia irresoluble.
 *
 * EL TRUCO: no hace falta saber cual es la respuesta correcta para saber que
 * DOS respuestas tienen que estar relacionadas.
 *
 * No se sabe cuanto deben parecerse "Jose Rodriguez" y "Jose Rodrigez". Pero
 * si se sabe, con total certeza, que ese numero tiene que ser el mismo que el
 * de "Jose Rodrigez" y "Jose Rodriguez": comparar es simetrico. Si no lo
 * fuera, el sistema estaria mal aunque no se sepa cual es la respuesta buena.
 *
 * Cada propiedad de abajo es una RELACION METAMORFICA: una transformacion de
 * la entrada y lo que le tiene que pasar a la salida.
 *
 *   entrada  --transformacion-->  entrada'
 *      |                             |
 *   salida   --relacion esperada--> salida'
 *
 * Esto se usa hoy tal cual con LLMs: reformular una pregunta sin cambiar su
 * sentido no deberia cambiar la respuesta; anadir "por favor" tampoco;
 * traducir y volver a traducir deberia converger. Cambia el sistema, no la
 * tecnica.
 *
 * Se usa jqwik (el mismo del taller de pruebas unitarias) porque una relacion
 * metamorfica ES una propiedad: tiene que valer para TODA entrada, y hace
 * falta generar cientos para creerselo.
 */
class PruebasMetamorficasTest {

    /**
     * RELACION 1 — SIMETRIA.
     *
     * similitud(a, b) == similitud(b, a).
     *
     * Es la relacion mas facil de enunciar y la que mas sistemas reales
     * incumplen, porque el orden de los argumentos se cuela por la puerta de
     * atras: un bucle que recorre solo la cadena mas corta, una normalizacion
     * que se aplica a un lado.
     */
    @Property(tries = 500)
    void laSimilitudEsSimetrica(@ForAll("nombres") String a, @ForAll("nombres") String b) {
        assertEquals(Similitud.similitud(a, b), Similitud.similitud(b, a), 1e-9,
                "similitud(\"" + a + "\", \"" + b + "\") != similitud al reves");
    }

    /**
     * RELACION 2 — REFLEXIVIDAD.
     *
     * Un nombre comparado consigo mismo da 1.0. Suena trivial y es una red de
     * seguridad barata: si esto falla, la normalizacion esta rompiendo la
     * cadena de alguna forma.
     */
    @Property(tries = 300)
    void todoNombreEsIdenticoASiMismo(@ForAll("nombres") String nombre) {
        assertEquals(1.0, Similitud.similitud(nombre, nombre), 1e-9);
    }

    /**
     * RELACION 3 — RANGO ACOTADO.
     *
     * El resultado siempre cae en [0, 1]. Sin esto, un umbral no significa
     * nada. Es el tipo de invariante que solo se rompe en casos extremos que
     * nadie escribe a mano, y que un generador encuentra enseguida.
     */
    @Property(tries = 500)
    void laSimilitudSiempreEstaEntreCeroYUno(
            @ForAll("nombres") String a, @ForAll("nombres") String b) {
        double s = Similitud.similitud(a, b);
        assertTrue(s >= 0.0 && s <= 1.0, "Similitud fuera de rango: " + s);
    }

    /**
     * RELACION 4 — INVARIANZA A LAS MAYUSCULAS.
     *
     * Nadie escribe su nombre igual dos veces en un formulario. Que "ANA
     * TORRES" y "ana torres" den resultados distintos seria un defecto que
     * afectaria a muchisima gente y que ninguna prueba de ejemplo suelta
     * detecta, porque los ejemplos se escriben siempre bien formateados.
     */
    @Property(tries = 300)
    void lasMayusculasNoCambianElResultado(
            @ForAll("nombres") String a, @ForAll("nombres") String b) {
        assertEquals(
                Similitud.similitud(a, b),
                Similitud.similitud(a.toUpperCase(), b.toLowerCase()),
                1e-9);
    }

    /**
     * RELACION 5 — INVARIANZA AL ESPACIO SOBRANTE.
     *
     * Un espacio de mas al final es el error de captura mas comun que existe,
     * y es invisible en pantalla.
     */
    @Property(tries = 300)
    void elEspacioSobranteNoCambiaElResultado(
            @ForAll("nombres") String a,
            @ForAll("nombres") String b,
            @ForAll @IntRange(min = 1, max = 5) int espacios) {

        String conEspacios = " ".repeat(espacios) + a + " ".repeat(espacios);

        assertEquals(Similitud.similitud(a, b), Similitud.similitud(conEspacios, b), 1e-9,
                "El espacio sobrante cambio el resultado");
    }

    /**
     * RELACION 6 — INVARIANZA A LAS TILDES.
     *
     * ESTA RELACION ES UNA DECISION DE PRODUCTO, NO UNA VERDAD MATEMATICA.
     *
     * Se afirma que "Munoz" y "Munoz" (con y sin enie) son el mismo nombre.
     * Para un padron colombiano es lo correcto: la gente escribe su apellido
     * sin tilde constantemente, y el sistema debe reconocerla.
     *
     * En otro contexto seria un defecto. Hay idiomas donde el diacritico
     * distingue dos palabras, y hay sistemas donde perder esa distincion
     * fusiona a dos personas reales.
     *
     * Se deja escrita como prueba, y no como comentario, para que si alguien
     * cambia la normalizacion, la discusion vuelva a abrirse en vez de
     * ocurrir en silencio.
     */
    @Property(tries = 200)
    void lasTildesNoCambianElResultado(@ForAll("nombresConTilde") String nombre) {
        String sinTildes = quitarTildesAMano(nombre);

        assertEquals(1.0, Similitud.similitud(nombre, sinTildes), 1e-9,
                "\"" + nombre + "\" y \"" + sinTildes + "\" deberian ser identicos");
    }

    /**
     * RELACION 7 — MONOTONIA ANTE EL RUIDO.
     *
     * Si a un nombre se le anaden caracteres basura, no puede parecerse MAS
     * al original que el original a si mismo. Es una cota, no una igualdad:
     * las relaciones metamorficas no siempre son "igual a", tambien valen
     * "no mayor que".
     */
    @Property(tries = 300)
    void anadirRuidoNoAumentaLaSimilitud(
            @ForAll("nombres") String nombre,
            @ForAll("nombres") String ruido) {

        double original = Similitud.similitud(nombre, nombre);
        double conRuido = Similitud.similitud(nombre, nombre + " " + ruido);

        assertTrue(conRuido <= original + 1e-9,
                "Anadir ruido aumento la similitud: " + conRuido + " > " + original);
    }

    // ------------------------------------------------------------------
    // Generadores
    // ------------------------------------------------------------------

    @Provide
    Arbitrary<String> nombres() {
        Arbitrary<String> palabra = Arbitraries.strings()
                .withCharRange('a', 'z')
                .ofMinLength(2).ofMaxLength(10);

        return palabra.list().ofMinSize(1).ofMaxSize(3)
                .map(partes -> String.join(" ", partes));
    }

    @Provide
    Arbitrary<String> nombresConTilde() {
        return Arbitraries.of(
                "José Rodríguez", "María Núñez", "Iván Muñoz", "Ramón Peña",
                "César Ibáñez", "Adrián Vásquez", "Mónica Ángulo", "Raúl Ocaña",
                "Nicolás Dueñas", "Verónica Alemán", "Andrés Peláez", "Óscar Zúñiga");
    }

    private static String quitarTildesAMano(String s) {
        return s.replace("á", "a").replace("é", "e").replace("í", "i")
                .replace("ó", "o").replace("ú", "u").replace("ñ", "n")
                .replace("Á", "A").replace("É", "E").replace("Í", "I")
                .replace("Ó", "O").replace("Ú", "U").replace("Ñ", "N");
    }
}
