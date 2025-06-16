package org.dobi.api;

import org.dobi.entities.Machine;
import org.dobi.entities.Tag;

public interface IDriver {

    /**
     * Configure le driver avec les informations de la machine cible.
     * @param machine L'entité machine contenant les paramètres de connexion (IP, rack, slot...).
     */
    void configure(Machine machine);

    /**
     * Établit la connexion avec l'équipement.
     * @return true si la connexion est réussie, false sinon.
     */
    boolean connect();

    /**
     * Ferme la connexion avec l'équipement.
     */
    void disconnect();

    /**
     * Vérifie si le driver est actuellement connecté.
     * @return true si connecté, false sinon.
     */
    boolean isConnected();

    /**
     * Lit la valeur d'un tag (variable) depuis l'équipement.
     * @param tag Le tag à lire contenant toutes ses informations (adresse, type, etc).
     * @return Un objet représentant la valeur lue.
     */
    Object read(Tag tag);

    /**
     * Écrit une valeur sur un tag (variable) dans l'équipement.
     * @param tag Le tag à écrire.
     * @param value La valeur à écrire.
     */
    void write(Tag tag, Object value);
}
