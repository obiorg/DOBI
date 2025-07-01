package org.dobi.profinet.config;

import org.dobi.profinet.device.ProfinetDevice;
import org.dobi.profinet.device.SlotConfiguration;
import org.dobi.logging.LogLevelManager;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import jakarta.xml.bind.annotation.*;
import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

public class GsdmlParser {
    private static final String COMPONENT_NAME = "PROFINET-GSDML";
    
    /**
     * Parse un fichier GSDML et retourne la configuration de l'équipement
     */
    public static ProfinetDevice parseGsdmlFile(String gsdmlPath) {
        try {
            LogLevelManager.logInfo(COMPONENT_NAME, "Parsing fichier GSDML: " + gsdmlPath);
            
            JAXBContext context = JAXBContext.newInstance(GsdmlRoot.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            
            GsdmlRoot gsdmlRoot;
            if (gsdmlPath.startsWith("classpath:")) {
                // Chargement depuis classpath
                String resourcePath = gsdmlPath.substring("classpath:".length());
                InputStream inputStream = GsdmlParser.class.getClassLoader().getResourceAsStream(resourcePath);
                if (inputStream == null) {
                    throw new RuntimeException("Fichier GSDML non trouvé: " + resourcePath);
                }
                gsdmlRoot = (GsdmlRoot) unmarshaller.unmarshal(inputStream);
            } else {
                // Chargement depuis fichier
                File gsdmlFile = new File(gsdmlPath);
                if (!gsdmlFile.exists()) {
                    throw new RuntimeException("Fichier GSDML non trouvé: " + gsdmlPath);
                }
                gsdmlRoot = (GsdmlRoot) unmarshaller.unmarshal(gsdmlFile);
            }
            
            ProfinetDevice device = convertGsdmlToDevice(gsdmlRoot);
            
            LogLevelManager.logInfo(COMPONENT_NAME, "GSDML parsé avec succès: " + device.getDeviceName());
            return device;
            
        } catch (JAXBException e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur parsing GSDML: " + e.getMessage());
            throw new RuntimeException("Erreur parsing GSDML", e);
        }
    }
    
    /**
     * Parse un fichier GSDML depuis un InputStream
     */
    public static ProfinetDevice parseGsdmlStream(InputStream inputStream) {
        try {
            JAXBContext context = JAXBContext.newInstance(GsdmlRoot.class);
            Unmarshaller unmarshaller = context.createUnmarshaller();
            
            GsdmlRoot gsdmlRoot = (GsdmlRoot) unmarshaller.unmarshal(inputStream);
            return convertGsdmlToDevice(gsdmlRoot);
            
        } catch (JAXBException e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur parsing GSDML stream: " + e.getMessage());
            throw new RuntimeException("Erreur parsing GSDML", e);
        }
    }
    
    /**
     * Valide un fichier GSDML
     */
    public static boolean validateGsdml(String gsdmlPath) {
        try {
            parseGsdmlFile(gsdmlPath);
            return true;
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "GSDML invalide: " + e.getMessage());
            return false;
        }
    }
    
    private static ProfinetDevice convertGsdmlToDevice(GsdmlRoot gsdmlRoot) {
        ProfinetDevice device = new ProfinetDevice();
        
        if (gsdmlRoot.device != null) {
            // Informations de base
            device.setDeviceName(gsdmlRoot.device.deviceName);
            device.setDeviceType(gsdmlRoot.device.deviceType);
            device.setVendorName(gsdmlRoot.device.vendorName);
            device.setVersion(gsdmlRoot.device.version);
            device.setDeviceId(gsdmlRoot.device.deviceId);
            device.setVendorId(gsdmlRoot.device.vendorId);
            
            // Configuration des slots
            if (gsdmlRoot.device.slots != null) {
                for (GsdmlSlot gsdmlSlot : gsdmlRoot.device.slots) {
                    SlotConfiguration slot = new SlotConfiguration();
                    slot.setSlotNumber(gsdmlSlot.slotNumber);
                    slot.setModuleType(gsdmlSlot.moduleType);
                    slot.setModuleName(gsdmlSlot.moduleName);
                    slot.setInputSize(gsdmlSlot.inputSize);
                    slot.setOutputSize(gsdmlSlot.outputSize);
                    
                    // Sous-modules
                    if (gsdmlSlot.submodules != null) {
                        for (GsdmlSubmodule gsdmlSub : gsdmlSlot.submodules) {
                            SlotConfiguration.SubmoduleInfo submodule = new SlotConfiguration.SubmoduleInfo();
                            submodule.setSubmoduleIndex(gsdmlSub.index);
                            submodule.setSubmoduleName(gsdmlSub.name);
                            submodule.setSubmoduleType(gsdmlSub.type);
                            submodule.setInputOffset(gsdmlSub.inputOffset);
                            submodule.setOutputOffset(gsdmlSub.outputOffset);
                            submodule.setInputLength(gsdmlSub.inputLength);
                            submodule.setOutputLength(gsdmlSub.outputLength);
                            
                            slot.addSubmodule(submodule);
                        }
                    }
                    
                    device.addSlot(slot.getSlotNumber(), slot);
                }
            }
        }
        
        return device;
    }
    
    // === CLASSES JAXB POUR PARSING XML ===
    
    @XmlRootElement(name = "ISO15745Profile")
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class GsdmlRoot {
        @XmlElement(name = "Device")
        public GsdmlDevice device;
    }
    
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class GsdmlDevice {
        @XmlAttribute(name = "DeviceName")
        public String deviceName;
        
        @XmlAttribute(name = "DeviceType")
        public String deviceType;
        
        @XmlAttribute(name = "VendorName")
        public String vendorName;
        
        @XmlAttribute(name = "Version")
        public String version;
        
        @XmlAttribute(name = "DeviceID")
        public int deviceId;
        
        @XmlAttribute(name = "VendorID")
        public int vendorId;
        
        @XmlElementWrapper(name = "SlotList")
        @XmlElement(name = "Slot")
        public List<GsdmlSlot> slots;
    }
    
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class GsdmlSlot {
        @XmlAttribute(name = "SlotNumber")
        public int slotNumber;
        
        @XmlAttribute(name = "ModuleType")
        public String moduleType;
        
        @XmlAttribute(name = "ModuleName")
        public String moduleName;
        
        @XmlAttribute(name = "InputSize")
        public int inputSize;
        
        @XmlAttribute(name = "OutputSize")
        public int outputSize;
        
        @XmlElementWrapper(name = "SubmoduleList")
        @XmlElement(name = "Submodule")
        public List<GsdmlSubmodule> submodules;
    }
    
    @XmlAccessorType(XmlAccessType.FIELD)
    public static class GsdmlSubmodule {
        @XmlAttribute(name = "Index")
        public int index;
        
        @XmlAttribute(name = "Name")
        public String name;
        
        @XmlAttribute(name = "Type")
        public String type;
        
        @XmlAttribute(name = "InputOffset")
        public int inputOffset;
        
        @XmlAttribute(name = "OutputOffset")
        public int outputOffset;
        
        @XmlAttribute(name = "InputLength")
        public int inputLength;
        
        @XmlAttribute(name = "OutputLength")
        public int outputLength;
    }
}