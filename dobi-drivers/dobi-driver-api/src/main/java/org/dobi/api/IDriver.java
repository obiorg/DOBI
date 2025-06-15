package ${GroupId}.api;

import ${GroupId}.entities.Machine;

public interface IDriver {

    /**
     * Configure le driver avec les informations de la machine cible.
     * @param machine L'entit� machine contenant les param�tres de connexion (IP, rack, slot...).
     */
    void configure(Machine machine);

    /**
     * �tablit la connexion avec l'�quipement.
     * @return true si la connexion est r�ussie, false sinon.
     */
    boolean connect();

    /**
     * Ferme la connexion avec l'�quipement.
     */
    void disconnect();

    /**
     * V�rifie si le driver est actuellement connect�.
     * @return true si connect�, false sinon.
     */
    boolean isConnected();

    /**
     * Lit la valeur d'un tag (variable) depuis l'�quipement.
     * La structure de l'objet retourn� sera d�finie plus tard (ex: une classe TagValue).
     * @param address L'adresse du tag � lire (ex: "DB1.DBD10").
     * @return Un objet repr�sentant la valeur lue.
     */
    Object read(String address);

    /**
     * �crit une valeur sur un tag (variable) dans l'�quipement.
     * @param address L'adresse du tag � �crire.
     * @param value La valeur � �crire.
     */
    void write(String address, Object value);
