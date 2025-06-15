import org.dobi.entities.Tag;
package org.dobi.api;

import org.dobi.entities.Machine;
import org.dobi.entities.Tag;

public interface IDriver {

    /**
     * Configure le driver avec les informations de la machine cible.
     *
     * @param machine L'entitÃ© machine contenant les paramÃ¨tres de connexion
     * (IP, rack, slot...).
     */
    void configure(Machine machine);

    /**
     * Ã‰tablit la connexion avec l'Ã©quipement.
     *
     * @return true si la connexion est rÃ©ussie, false sinon.
     */
    boolean connect();

    /**
     * Ferme la connexion avec l'Ã©quipement.
     */
    void disconnect();

    /**
     * VÃ©rifie si le driver est actuellement connectÃ©.
     *
     * @return true si connectÃ©, false sinon.
     */
    boolean isConnected();

    /**
     * Lit la valeur d'un tag (variable) depuis l'Ã©quipement. La structure de
     * l'objet retournÃ© sera dÃ©finie plus tard (ex: une classe TagValue).
     *
     * @param address L'adresse du tag Ã  lire (ex: "DB1.DBD10").
     * @return Un objet reprÃ©sentant la valeur lue.
     */
    Object read(org.dobi.entities.Tag tag);

    /**
     * Ã‰crit une valeur sur un tag (variable) dans l'Ã©quipement.
     *
     * @param address L'adresse du tag Ã  Ã©crire.
     * @param value La valeur Ã  Ã©crire.
     */
    void write(org.dobi.entities.Tag tag, Object value);
}

