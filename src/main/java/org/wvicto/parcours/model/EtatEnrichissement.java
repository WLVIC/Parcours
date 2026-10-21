package org.wvicto.parcours.model;

/**
 * Où en est un trajet par rapport à l'enrichissement des altitudes. L'état n'est jamais
 * stocké : il se déduit du trajet (voir {@link Trajet#getEtatEnrichissement()}).
 */
public enum EtatEnrichissement {

    /** Copie enrichie : le fichier porte la marque « enrichi : Parcours ». */
    ENRICHI,

    /** Fichier GPX d'origine, dont les altitudes n'ont pas encore été enrichies. */
    A_ENRICHIR,

    /** Trajet qui ne vient pas d'un fichier (lissé, sous-trajet...) : rien à enrichir. */
    SANS_FICHIER
}