package org.dobi.profinet.protocol;

import org.dobi.logging.LogLevelManager;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Arrays;

public class FrameBuilder {

    private static final String COMPONENT_NAME = "PROFINET-FRAME";

    // Constantes Profinet/Ethernet
    private static final int ETHERNET_HEADER_SIZE = 14;
    private static final int PROFINET_RT_HEADER_SIZE = 4;
    private static final int PROFINET_CYCLIC_HEADER_SIZE = 4;

    // EtherType Profinet
    private static final short ETHERTYPE_PROFINET_RT = (short) 0x8892;
    private static final short ETHERTYPE_PROFINET_DCP = (short) 0x8893;

    // Frame IDs Profinet
    private static final short FRAME_ID_DCP_IDENTIFY = (short) 0xFEFE;
    private static final short FRAME_ID_DCP_HELLO = (short) 0xFEFC;
    private static final short FRAME_ID_RT_CYCLIC_DATA = (short) 0x8000;
    private static final short FRAME_ID_RT_ACYCLIC_DATA = (short) 0xFC01;

    /**
     * Construit une trame DCP Identify All
     */
    public static byte[] buildDCPIdentifyFrame(byte[] sourceMac, byte[] destinationMac) {
        LogLevelManager.logTrace(COMPONENT_NAME, "Construction trame DCP Identify");

        try {
            ByteBuffer frame = ByteBuffer.allocate(100); // Taille suffisante pour DCP
            frame.order(ByteOrder.BIG_ENDIAN); // Ethernet utilise Big Endian

            // === En-tête Ethernet ===
            frame.put(destinationMac); // 6 bytes - MAC destination
            frame.put(sourceMac);      // 6 bytes - MAC source
            frame.putShort(ETHERTYPE_PROFINET_DCP); // 2 bytes - EtherType

            // === En-tête DCP ===
            frame.putShort(FRAME_ID_DCP_IDENTIFY); // 2 bytes - Frame ID
            frame.put((byte) 0x05); // 1 byte - Service ID (Identify)
            frame.put((byte) 0x01); // 1 byte - Service Type (Request)

            // === Transaction ID ===
            frame.putInt(generateTransactionId()); // 4 bytes - Transaction ID

            // === Response Delay ===
            frame.putShort((short) 0x0000); // 2 bytes - Pas de délai

            // === Data Length ===
            frame.putShort((short) 0x0004); // 2 bytes - 4 bytes de données

            // === DCP Block: AllSelector ===
            frame.put((byte) 0xFF); // 1 byte - Option: AllSelector
            frame.put((byte) 0xFF); // 1 byte - SubOption: AllSelector
            frame.putShort((short) 0x0000); // 2 bytes - Block Length (0 pour AllSelector)

            // Retour du tableau de la taille exacte
            byte[] result = new byte[frame.position()];
            frame.rewind();
            frame.get(result);

            LogLevelManager.logTrace(COMPONENT_NAME, "Trame DCP Identify construite: " + result.length + " bytes");
            return result;

        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur construction trame DCP: " + e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Construit une trame RT de données cycliques
     */
    public static byte[] buildRTCyclicFrame(byte[] sourceMac, byte[] destinationMac,
            short frameId, byte[] cyclicData) {
        LogLevelManager.logTrace(COMPONENT_NAME, "Construction trame RT cyclique (FrameID: 0x"
                + Integer.toHexString(frameId & 0xFFFF) + ")");

        try {
            int frameSize = ETHERNET_HEADER_SIZE + PROFINET_RT_HEADER_SIZE + cyclicData.length + 4; // +4 pour CRC
            ByteBuffer frame = ByteBuffer.allocate(frameSize);
            frame.order(ByteOrder.BIG_ENDIAN);

            // === En-tête Ethernet ===
            frame.put(destinationMac); // 6 bytes - MAC destination
            frame.put(sourceMac);      // 6 bytes - MAC source
            frame.putShort(ETHERTYPE_PROFINET_RT); // 2 bytes - EtherType RT

            // === En-tête Profinet RT ===
            frame.putShort(frameId);   // 2 bytes - Frame ID
            frame.putShort((short) cyclicData.length); // 2 bytes - Data Length

            // === Données cycliques ===
            frame.put(cyclicData);

            // === Cycle Counter (optionnel) ===
            // frame.putShort(cycleCounter); // Pour IRT seulement
            // === Data Status ===
            frame.put((byte) 0x35); // Data Status: Valid, No Problem, Run, Station OK
            frame.put((byte) 0x00); // Transfer Status: Reserved

            // === CRC (Frame Check Sequence) ===
            int crc = calculateCRC32(frame.array(), 0, frame.position());
            frame.putInt(crc);

            byte[] result = new byte[frame.position()];
            frame.rewind();
            frame.get(result);

            LogLevelManager.logTrace(COMPONENT_NAME, "Trame RT cyclique construite: " + result.length
                    + " bytes, données: " + cyclicData.length + " bytes");
            return result;

        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur construction trame RT: " + e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Construit une trame RT pour données acycliques (Read/Write services)
     */
    public static byte[] buildRTAcyclicFrame(byte[] sourceMac, byte[] destinationMac,
            byte serviceType, int apiId, int slotNumber,
            int subslotNumber, byte[] serviceData) {
        LogLevelManager.logTrace(COMPONENT_NAME, "Construction trame RT acyclique (Service: 0x"
                + Integer.toHexString(serviceType & 0xFF) + ")");

        try {
            int serviceHeaderSize = 12; // En-tête service acyclique
            int frameSize = ETHERNET_HEADER_SIZE + PROFINET_RT_HEADER_SIZE
                    + serviceHeaderSize + serviceData.length + 4;

            ByteBuffer frame = ByteBuffer.allocate(frameSize);
            frame.order(ByteOrder.BIG_ENDIAN);

            // === En-tête Ethernet ===
            frame.put(destinationMac);
            frame.put(sourceMac);
            frame.putShort(ETHERTYPE_PROFINET_RT);

            // === En-tête Profinet RT ===
            frame.putShort(FRAME_ID_RT_ACYCLIC_DATA);
            frame.putShort((short) (serviceHeaderSize + serviceData.length));

            // === En-tête Service Acyclique ===
            frame.put(serviceType);    // 1 byte - Type de service (Read=0x01, Write=0x02)
            frame.put((byte) 0x00);    // 1 byte - Service modifier
            frame.putShort((short) 0x0001); // 2 bytes - Sequence number

            frame.putInt(apiId);       // 4 bytes - API ID
            frame.putShort((short) slotNumber);    // 2 bytes - Slot number
            frame.putShort((short) subslotNumber); // 2 bytes - Subslot number

            // === Données du service ===
            frame.put(serviceData);

            // === Data Status ===
            frame.put((byte) 0x35);
            frame.put((byte) 0x00);

            // === CRC ===
            int crc = calculateCRC32(frame.array(), 0, frame.position());
            frame.putInt(crc);

            byte[] result = new byte[frame.position()];
            frame.rewind();
            frame.get(result);

            LogLevelManager.logTrace(COMPONENT_NAME, "Trame RT acyclique construite: " + result.length + " bytes");
            return result;

        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur construction trame acyclique: " + e.getMessage());
            return new byte[0];
        }
    }

    /**
     * Parse une trame DCP reçue
     */
    public static DCPResponse parseDCPFrame(byte[] frameData) {
        LogLevelManager.logTrace(COMPONENT_NAME, "Parsing trame DCP: " + frameData.length + " bytes");

        try {
            if (frameData.length < ETHERNET_HEADER_SIZE + 8) { // Taille minimale
                LogLevelManager.logError(COMPONENT_NAME, "Trame DCP trop courte");
                return null;
            }

            ByteBuffer frame = ByteBuffer.wrap(frameData);
            frame.order(ByteOrder.BIG_ENDIAN);

            // === Vérification En-tête Ethernet ===
            frame.position(12); // Skip MACs
            short etherType = frame.getShort();

            if (etherType != ETHERTYPE_PROFINET_DCP) {
                LogLevelManager.logDebug(COMPONENT_NAME, "EtherType non-DCP: 0x" + Integer.toHexString(etherType & 0xFFFF));
                return null;
            }

            // === En-tête DCP ===
            short frameId = frame.getShort();
            byte serviceId = frame.get();
            byte serviceType = frame.get();
            int transactionId = frame.getInt();
            short responseDelay = frame.getShort();
            short dataLength = frame.getShort();

            LogLevelManager.logTrace(COMPONENT_NAME, "DCP Frame ID: 0x" + Integer.toHexString(frameId & 0xFFFF)
                    + ", Service: 0x" + Integer.toHexString(serviceId & 0xFF)
                    + ", Type: 0x" + Integer.toHexString(serviceType & 0xFF));

            // === Parsing des blocs DCP ===
            DCPResponse response = new DCPResponse();
            response.frameId = frameId;
            response.serviceId = serviceId;
            response.serviceType = serviceType;
            response.transactionId = transactionId;

            // Parsing des blocs de données
            int remainingData = dataLength;
            while (remainingData > 4 && frame.hasRemaining()) {
                byte option = frame.get();
                byte suboption = frame.get();
                short blockLength = frame.getShort();

                remainingData -= 4;

                if (blockLength > remainingData || blockLength < 0) {
                    break;
                }

                byte[] blockData = new byte[blockLength];
                if (blockLength > 0) {
                    frame.get(blockData);
                    remainingData -= blockLength;
                }

                // Parsing des blocs selon leur type
                parseDCPBlock(response, option, suboption, blockData);

                // Alignement sur 2 bytes si nécessaire
                if (blockLength % 2 != 0 && frame.hasRemaining()) {
                    frame.get(); // Padding byte
                    remainingData--;
                }
            }

            LogLevelManager.logTrace(COMPONENT_NAME, "Trame DCP parsée: " + response.deviceName);
            return response;

        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur parsing trame DCP: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse une trame RT cyclique reçue
     */
    public static RTCyclicData parseRTCyclicFrame(byte[] frameData) {
        LogLevelManager.logTrace(COMPONENT_NAME, "Parsing trame RT cyclique: " + frameData.length + " bytes");

        try {
            if (frameData.length < ETHERNET_HEADER_SIZE + PROFINET_RT_HEADER_SIZE) {
                LogLevelManager.logError(COMPONENT_NAME, "Trame RT trop courte");
                return null;
            }

            ByteBuffer frame = ByteBuffer.wrap(frameData);
            frame.order(ByteOrder.BIG_ENDIAN);

            // === Vérification En-tête Ethernet ===
            frame.position(12);
            short etherType = frame.getShort();

            if (etherType != ETHERTYPE_PROFINET_RT) {
                return null;
            }

            // === En-tête Profinet RT ===
            short frameId = frame.getShort();
            short dataLength = frame.getShort();

            // Vérification que c'est bien des données cycliques
            if ((frameId & 0x8000) == 0) {
                LogLevelManager.logDebug(COMPONENT_NAME, "Frame ID non-cyclique: 0x" + Integer.toHexString(frameId & 0xFFFF));
                return null;
            }

            // === Extraction des données cycliques ===
            if (dataLength > frameData.length - frame.position() - 4) { // -4 pour CRC
                LogLevelManager.logError(COMPONENT_NAME, "Longueur de données invalide");
                return null;
            }

            byte[] cyclicData = new byte[dataLength];
            frame.get(cyclicData);

            // === Status bytes (optionnels) ===
            byte dataStatus = 0;
            byte transferStatus = 0;

            if (frame.remaining() >= 6) { // Au moins 2 bytes status + 4 bytes CRC
                dataStatus = frame.get();
                transferStatus = frame.get();
            }

            RTCyclicData result = new RTCyclicData();
            result.frameId = frameId;
            result.cyclicData = cyclicData;
            result.dataStatus = dataStatus;
            result.transferStatus = transferStatus;

            LogLevelManager.logTrace(COMPONENT_NAME, "Trame RT cyclique parsée: FrameID=0x"
                    + Integer.toHexString(frameId & 0xFFFF) + ", données=" + cyclicData.length + " bytes");

            return result;

        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur parsing trame RT: " + e.getMessage());
            return null;
        }
    }

    /**
     * Parse un bloc DCP spécifique
     */
    private static void parseDCPBlock(DCPResponse response, byte option, byte suboption, byte[] blockData) {
        try {
            switch (option & 0xFF) {
                case 0x01: // IP Option
                    if ((suboption & 0xFF) == 0x02) { // IP Parameter
                        if (blockData.length >= 6) {
                            ByteBuffer buffer = ByteBuffer.wrap(blockData);
                            buffer.order(ByteOrder.BIG_ENDIAN);

                            response.ipAddress = String.format("%d.%d.%d.%d",
                                    buffer.get() & 0xFF, buffer.get() & 0xFF,
                                    buffer.get() & 0xFF, buffer.get() & 0xFF);

                            response.subnetMask = String.format("%d.%d.%d.%d",
                                    buffer.get() & 0xFF, buffer.get() & 0xFF,
                                    buffer.get() & 0xFF, buffer.get() & 0xFF);

                            if (buffer.hasRemaining()) {
                                response.gateway = String.format("%d.%d.%d.%d",
                                        buffer.get() & 0xFF, buffer.get() & 0xFF,
                                        buffer.get() & 0xFF, buffer.get() & 0xFF);
                            }
                        }
                    }
                    break;

                case 0x02: // Device Option
                    if ((suboption & 0xFF) == 0x01) { // Device Vendor
                        response.vendorName = new String(blockData, "UTF-8").trim();
                    } else if ((suboption & 0xFF) == 0x02) { // Device Name
                        response.deviceName = new String(blockData, "UTF-8").trim();
                    } else if ((suboption & 0xFF) == 0x03) { // Device ID
                        if (blockData.length >= 4) {
                            ByteBuffer buffer = ByteBuffer.wrap(blockData);
                            buffer.order(ByteOrder.BIG_ENDIAN);
                            response.vendorId = buffer.getShort() & 0xFFFF;
                            response.deviceId = buffer.getShort() & 0xFFFF;
                        }
                    }
                    break;

                case 0x03: // DHCP Option
                    // Traitement DHCP si nécessaire
                    break;

                case 0x05: // Control Option
                    if ((suboption & 0xFF) == 0x01) { // Start Transaction
                        // Traitement transaction si nécessaire
                    }
                    break;

                case 0x06: // Device Initiative Option
                    if ((suboption & 0xFF) == 0x01) { // Device Initiative
                        response.deviceInitiative = blockData.length > 0 ? blockData[0] : 0;
                    }
                    break;

                default:
                    LogLevelManager.logTrace(COMPONENT_NAME, "Bloc DCP non traité: Option=0x"
                            + Integer.toHexString(option & 0xFF) + ", SubOption=0x"
                            + Integer.toHexString(suboption & 0xFF));
                    break;
            }

        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur parsing bloc DCP: " + e.getMessage());
        }
    }

    /**
     * Calcul CRC32 simplifié pour les trames Ethernet
     */
    private static int calculateCRC32(byte[] data, int offset, int length) {
        // Implémentation simplifiée du CRC32
        // En production, utiliser une bibliothèque CRC optimisée

        int crc = 0xFFFFFFFF;
        int polynomial = 0xEDB88320;

        for (int i = offset; i < offset + length; i++) {
            crc ^= data[i] & 0xFF;

            for (int j = 0; j < 8; j++) {
                if ((crc & 1) != 0) {
                    crc = (crc >>> 1) ^ polynomial;
                } else {
                    crc = crc >>> 1;
                }
            }
        }

        return ~crc;
    }

    /**
     * Génère un Transaction ID unique pour DCP
     */
    private static int generateTransactionId() {
        return (int) (System.currentTimeMillis() & 0xFFFFFFFF);
    }

    /**
     * Convertit une adresse MAC string en bytes
     */
    public static byte[] parseMacAddress(String macAddress) {
        if (macAddress == null || macAddress.length() != 17) {
            return new byte[6];
        }

        try {
            String[] parts = macAddress.split(":");
            if (parts.length != 6) {
                return new byte[6];
            }

            byte[] mac = new byte[6];
            for (int i = 0; i < 6; i++) {
                mac[i] = (byte) Integer.parseInt(parts[i], 16);
            }

            return mac;

        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur parsing adresse MAC: " + e.getMessage());
            return new byte[6];
        }
    }

    /**
     * Convertit des bytes MAC en string
     */
    public static String formatMacAddress(byte[] mac) {
        if (mac == null || mac.length != 6) {
            return "00:00:00:00:00:00";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < mac.length; i++) {
            if (i > 0) {
                sb.append(":");
            }
            sb.append(String.format("%02X", mac[i] & 0xFF));
        }

        return sb.toString();
    }

    // === CLASSES DE DONNÉES ===
    /**
     * Classe pour les réponses DCP
     */
    public static class DCPResponse {

        public short frameId;
        public byte serviceId;
        public byte serviceType;
        public int transactionId;

        public String deviceName = "";
        public String vendorName = "";
        public String ipAddress = "";
        public String subnetMask = "";
        public String gateway = "";
        public int vendorId = 0;
        public int deviceId = 0;
        public byte deviceInitiative = 0;

        @Override
        public String toString() {
            return "DCPResponse{"
                    + "deviceName='" + deviceName + '\''
                    + ", vendorName='" + vendorName + '\''
                    + ", ipAddress='" + ipAddress + '\''
                    + ", vendorId=" + vendorId
                    + ", deviceId=" + deviceId
                    + '}';
        }
    }

    /**
     * Classe pour les données cycliques RT
     */
    public static class RTCyclicData {

        public short frameId;
        public byte[] cyclicData;
        public byte dataStatus;
        public byte transferStatus;

        public boolean isDataValid() {
            return (dataStatus & 0x04) != 0; // Bit 2: Data Valid
        }

        public boolean isStationOK() {
            return (dataStatus & 0x01) != 0; // Bit 0: Station OK
        }

        public boolean isRunning() {
            return (dataStatus & 0x02) != 0; // Bit 1: Provider Run
        }

        @Override
        public String toString() {
            return "RTCyclicData{"
                    + "frameId=0x" + Integer.toHexString(frameId & 0xFFFF)
                    + ", dataLength=" + (cyclicData != null ? cyclicData.length : 0)
                    + ", dataValid=" + isDataValid()
                    + ", stationOK=" + isStationOK()
                    + '}';
        }
    }

    /**
     * Classe pour les services acycliques
     */
    public static class AcyclicService {

        public static final byte SERVICE_READ = 0x01;
        public static final byte SERVICE_WRITE = 0x02;
        public static final byte SERVICE_CONNECT = 0x03;
        public static final byte SERVICE_RELEASE = 0x04;

        public byte serviceType;
        public int apiId;
        public int slotNumber;
        public int subslotNumber;
        public byte[] serviceData;

        public AcyclicService(byte serviceType, int apiId, int slotNumber, int subslotNumber, byte[] serviceData) {
            this.serviceType = serviceType;
            this.apiId = apiId;
            this.slotNumber = slotNumber;
            this.subslotNumber = subslotNumber;
            this.serviceData = serviceData != null ? serviceData : new byte[0];
        }

        @Override
        public String toString() {
            return "AcyclicService{"
                    + "serviceType=0x" + Integer.toHexString(serviceType & 0xFF)
                    + ", apiId=" + apiId
                    + ", slot=" + slotNumber
                    + ", subslot=" + subslotNumber
                    + ", dataLength=" + serviceData.length
                    + '}';
        }
    }
}
