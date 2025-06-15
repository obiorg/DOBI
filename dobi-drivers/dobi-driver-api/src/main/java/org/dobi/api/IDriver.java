import org.dobi.entities.Tag;
package org.dobi.api;

import org.dobi.entities.Machine;
import org.dobi.entities.Tag;

public interface IDriver {

    /**
     * Configure le driver avec les informations de la machine cible.
     *
     * @param machine L'entitÃƒÂ© machine contenant les paramÃƒÂ¨tres de connexion
     * (IP, rack, slot...).
     */
    void configure(Machine machine);

    /**
     * Ãƒâ€°tablit la connexion avec l'ÃƒÂ©quipement.
     *
     * @return true si la connexion est rÃƒÂ©ussie, false sinon.
     */
    boolean connect();

    /**
     * Ferme la connexion avec l'ÃƒÂ©quipement.
     */
    void disconnect();

    /**
     * VÃƒÂ©rifie si le driver est actuellement connectÃƒÂ©.
     *
     * @return true si connectÃƒÂ©, false sinon.
     */
    boolean isConnected();

    /**
     * Lit la valeur d'un tag (variable) depuis l'ÃƒÂ©quipement. La structure de
     * l'objet retournÃƒÂ© sera dÃƒÂ©finie plus tard (ex: une classe TagValue).
     *
     * @param address L'adresse du tag ÃƒÂ  lire (ex: "DB1.DBD10").
     * @return Un objet reprÃƒÂ©sentant la valeur lue.
     */
    Object read(org.dobi.entities.Tag tag);

    /**
     * Ãƒâ€°crit une valeur sur un tag (variable) dans l'ÃƒÂ©quipement.
     *
     * @param address L'adresse du tag ÃƒÂ  ÃƒÂ©crire.
     * @param value La valeur ÃƒÂ  ÃƒÂ©crire.
     */
    void write(org.dobi.entities.Tag tag, Object value);
}


