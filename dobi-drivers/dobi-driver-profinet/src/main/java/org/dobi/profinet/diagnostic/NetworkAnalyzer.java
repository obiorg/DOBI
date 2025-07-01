package org.dobi.profinet.diagnostic;

import org.dobi.profinet.config.ProfinetConfig;
import org.dobi.logging.LogLevelManager;

import java.net.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Analyseur réseau pour Profinet
 * Gère l'analyse de l'interface réseau, les statistiques de trafic et les diagnostics
 */
public class NetworkAnalyzer {
    private static final String COMPONENT_NAME = "PROFINET-ANALYZER";
    
    private final ProfinetConfig config;
    
    // Statistiques de trafic
    private final AtomicLong bytesReceived = new AtomicLong(0);
    private final AtomicLong bytesSent = new AtomicLong(0);
    private final AtomicLong packetsReceived = new AtomicLong(0);
    private final AtomicLong packetsSent = new AtomicLong(0);
    private final AtomicLong errorsCount = new AtomicLong(0);
    
    // Interface réseau active
    private NetworkInterface activeInterface;
    private String activeInterfaceName;
    
    // Historique des performances
    private final List<NetworkSample> performanceHistory = new ArrayList<>();
    private final int MAX_HISTORY_SIZE = 100;
    
    // Temps de début pour calculs de performance
    private final long startTime = System.currentTimeMillis();
    
    public NetworkAnalyzer(ProfinetConfig config) {
        this.config = config;
        LogLevelManager.logDebug(COMPONENT_NAME, "NetworkAnalyzer initialisé");
    }
    
    /**
     * Initialise l'interface réseau spécifiée
     */
    public boolean initializeInterface(String interfaceName) {
        try {
            LogLevelManager.logInfo(COMPONENT_NAME, "Initialisation interface réseau: " + interfaceName);
            
            if ("auto".equals(interfaceName)) {
                // Sélection automatique de l'interface
                NetworkInterface selectedInterface = selectBestInterface();
                if (selectedInterface != null) {
                    this.activeInterface = selectedInterface;
                    this.activeInterfaceName = selectedInterface.getName();
                    LogLevelManager.logInfo(COMPONENT_NAME, "Interface sélectionnée automatiquement: " + 
                                         selectedInterface.getName());
                    return true;
                } else {
                    LogLevelManager.logError(COMPONENT_NAME, "Aucune interface réseau appropriée trouvée");
                    return false;
                }
            } else {
                // Interface spécifiée
                NetworkInterface networkInterface = NetworkInterface.getByName(interfaceName);
                if (networkInterface != null && networkInterface.isUp()) {
                    this.activeInterface = networkInterface;
                    this.activeInterfaceName = interfaceName;
                    LogLevelManager.logInfo(COMPONENT_NAME, "Interface configurée: " + interfaceName);
                    
                    // Validation de l'interface pour Profinet
                    if (validateInterfaceForProfilet(networkInterface)) {
                        return true;
                    } else {
                        LogLevelManager.logError(COMPONENT_NAME, "Interface non compatible avec Profinet: " + interfaceName);
                        return false;
                    }
                } else {
                    LogLevelManager.logError(COMPONENT_NAME, "Interface non disponible ou inactive: " + interfaceName);
                    return false;
                }
            }
            
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur initialisation interface: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Test de connectivité ping vers une adresse IP
     */
    public boolean pingDevice(String ipAddress) {
        return pingDevice(ipAddress, 3000);
    }
    
    /**
     * Test de connectivité ping avec timeout personnalisé
     */
    public boolean pingDevice(String ipAddress, int timeoutMs) {
        try {
            LogLevelManager.logTrace(COMPONENT_NAME, "Test ping vers " + ipAddress + " (timeout: " + timeoutMs + "ms)");
            
            InetAddress address = InetAddress.getByName(ipAddress);
            long startTime = System.nanoTime();
            
            boolean reachable = address.isReachable(timeoutMs);
            
            long duration = (System.nanoTime() - startTime) / 1_000_000; // Conversion en ms
            
            if (reachable) {
                LogLevelManager.logTrace(COMPONENT_NAME, "Ping " + ipAddress + " réussi (" + duration + "ms)");
                
                // Mise à jour des statistiques
                updateNetworkStats(0, 32, 0, 1); // Ping = ~32 bytes envoyés
            } else {
                LogLevelManager.logTrace(COMPONENT_NAME, "Ping " + ipAddress + " échoué (" + duration + "ms)");
                errorsCount.incrementAndGet();
            }
            
            return reachable;
            
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur ping " + ipAddress + ": " + e.getMessage());
            errorsCount.incrementAndGet();
            return false;
        }
    }
    
    /**
     * Analyse complète de l'interface réseau active
     */
    public Map<String, Object> analyzeInterface() {
        Map<String, Object> analysis = new HashMap<>();
        
        try {
            if (activeInterface == null) {
                analysis.put("error", "Aucune interface active");
                return analysis;
            }
            
            LogLevelManager.logDebug(COMPONENT_NAME, "Analyse de l'interface: " + activeInterface.getName());
            
            // Informations de base
            analysis.put("interface_name", activeInterface.getName());
            analysis.put("display_name", activeInterface.getDisplayName());
            analysis.put("mtu", activeInterface.getMTU());
            analysis.put("is_up", activeInterface.isUp());
            analysis.put("is_loopback", activeInterface.isLoopback());
            analysis.put("supports_multicast", activeInterface.supportsMulticast());
            analysis.put("is_virtual", activeInterface.isVirtual());
            analysis.put("is_point_to_point", activeInterface.isPointToPoint());
            
            // Adresses IP
            List<String> ipAddresses = new ArrayList<>();
            for (InterfaceAddress interfaceAddr : activeInterface.getInterfaceAddresses()) {
                InetAddress inetAddr = interfaceAddr.getAddress();
                ipAddresses.add(inetAddr.getHostAddress() + "/" + interfaceAddr.getNetworkPrefixLength());
            }
            analysis.put("ip_addresses", ipAddresses);
            
            // Adresse MAC
            byte[] mac = activeInterface.getHardwareAddress();
            if (mac != null) {
                StringBuilder macStr = new StringBuilder();
                for (int i = 0; i < mac.length; i++) {
                    if (i > 0) macStr.append(":");
                    macStr.append(String.format("%02X", mac[i]));
                }
                analysis.put("mac_address", macStr.toString());
            } else {
                analysis.put("mac_address", "Non disponible");
            }
            
            // Interfaces parentes/enfants
            NetworkInterface parent = activeInterface.getParent();
            if (parent != null) {
                analysis.put("parent_interface", parent.getName());
            }
            
            List<String> subInterfaces = new ArrayList<>();
            for (NetworkInterface subIf : Collections.list(activeInterface.getSubInterfaces())) {
                subInterfaces.add(subIf.getName());
            }
            if (!subInterfaces.isEmpty()) {
                analysis.put("sub_interfaces", subInterfaces);
            }
            
            // Statistiques de performance
            Map<String, Object> perfStats = getPerformanceStatistics();
            analysis.put("performance", perfStats);
            
            // Validation Profinet
            analysis.put("profinet_compatible", validateInterfaceForProfilet(activeInterface));
            analysis.put("profinet_recommendations", getProfinetRecommendations());
            
        } catch (Exception e) {
            analysis.put("error", e.getMessage());
            LogLevelManager.logError(COMPONENT_NAME, "Erreur analyse interface: " + e.getMessage());
        }
        
        return analysis;
    }
    
    /**
     * Obtient les statistiques de trafic réseau
     */
    public Map<String, Long> getTrafficStatistics() {
        Map<String, Long> stats = new HashMap<>();
        
        long bytesRx = bytesReceived.get();
        long bytesTx = bytesSent.get();
        long packetsRx = packetsReceived.get();
        long packetsTx = packetsSent.get();
        long errors = errorsCount.get();
        
        stats.put("bytes_received", bytesRx);
        stats.put("bytes_sent", bytesTx);
        stats.put("packets_received", packetsRx);
        stats.put("packets_sent", packetsTx);
        stats.put("errors", errors);
        
        // Calculs dérivés
        long totalBytes = bytesRx + bytesTx;
        long totalPackets = packetsRx + packetsTx;
        
        stats.put("total_bytes", totalBytes);
        stats.put("total_packets", totalPackets);
        
        if (totalPackets > 0) {
            stats.put("average_packet_size", totalBytes / totalPackets);
        } else {
            stats.put("average_packet_size", 0L);
        }
        
        // Taux d'erreur
        if (totalPackets > 0) {
            stats.put("error_rate_permille", (errors * 1000) / totalPackets);
        } else {
            stats.put("error_rate_permille", 0L);
        }
        
        // Durée de fonctionnement
        long uptimeSeconds = (System.currentTimeMillis() - startTime) / 1000;
        stats.put("uptime_seconds", uptimeSeconds);
        
        // Débit moyen
        if (uptimeSeconds > 0) {
            stats.put("avg_bytes_per_second", totalBytes / uptimeSeconds);
            stats.put("avg_packets_per_second", totalPackets / uptimeSeconds);
        }
        
        return stats;
    }
    
    /**
     * Calcule la charge réseau actuelle
     */
    public double calculateNetworkLoad() {
        try {
            Map<String, Long> stats = getTrafficStatistics();
            long uptimeSeconds = stats.get("uptime_seconds");
            
            if (uptimeSeconds <= 0) {
                return 0.0;
            }
            
            long totalBytes = stats.get("total_bytes");
            long bytesPerSecond = totalBytes / uptimeSeconds;
            
            // Estimation de la bande passante selon l'interface
            long estimatedBandwidthBps = estimateInterfaceBandwidth();
            
            if (estimatedBandwidthBps <= 0) {
                return 0.0;
            }
            
            // Calcul du pourcentage d'utilisation
            double loadPercentage = (double) (bytesPerSecond * 8) / estimatedBandwidthBps * 100.0;
            
            // Limitation à 100%
            return Math.min(100.0, Math.max(0.0, loadPercentage));
            
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur calcul charge réseau: " + e.getMessage());
            return 0.0;
        }
    }
    
    /**
     * Met à jour les statistiques de trafic
     */
    public void updateNetworkStats(long bytesRx, long bytesTx, long packetsRx, long packetsTx) {
        bytesReceived.addAndGet(bytesRx);
        bytesSent.addAndGet(bytesTx);
        packetsReceived.addAndGet(packetsRx);
        packetsSent.addAndGet(packetsTx);
        
        // Enregistrement d'un échantillon de performance
        recordPerformanceSample();
        
        LogLevelManager.logTrace(COMPONENT_NAME, String.format(
            "Stats mises à jour: +%d/%d bytes, +%d/%d packets", 
            bytesRx, bytesTx, packetsRx, packetsTx));
    }
    
    /**
     * Enregistre un échantillon de performance pour l'historique
     */
    private void recordPerformanceSample() {
        synchronized (performanceHistory) {
            NetworkSample sample = new NetworkSample(
                System.currentTimeMillis(),
                bytesReceived.get(),
                bytesSent.get(),
                packetsReceived.get(),
                packetsSent.get(),
                errorsCount.get()
            );
            
            performanceHistory.add(sample);
            
            // Limitation de la taille de l'historique
            if (performanceHistory.size() > MAX_HISTORY_SIZE) {
                performanceHistory.remove(0);
            }
        }
    }
    
    /**
     * Obtient les statistiques de performance avec historique
     */
    public Map<String, Object> getPerformanceStatistics() {
        Map<String, Object> perfStats = new HashMap<>();
        
        // Statistiques actuelles
        Map<String, Long> currentStats = getTrafficStatistics();
        perfStats.put("current", currentStats);
        
        // Charge réseau
        perfStats.put("network_load_percent", calculateNetworkLoad());
        
        // Bande passante estimée
        perfStats.put("estimated_bandwidth_bps", estimateInterfaceBandwidth());
        
        // Historique des performances (derniers échantillons)
        synchronized (performanceHistory) {
            if (!performanceHistory.isEmpty()) {
                NetworkSample latest = performanceHistory.get(performanceHistory.size() - 1);
                NetworkSample oldest = performanceHistory.get(0);
                
                long timeDiff = latest.timestamp - oldest.timestamp;
                if (timeDiff > 0) {
                    long bytesDiff = (latest.bytesReceived + latest.bytesSent) - 
                                   (oldest.bytesReceived + oldest.bytesSent);
                    long packetsDiff = (latest.packetsReceived + latest.packetsSent) - 
                                     (oldest.packetsReceived + oldest.packetsSent);
                    
                    double avgBytesPerSec = (double) bytesDiff / (timeDiff / 1000.0);
                    double avgPacketsPerSec = (double) packetsDiff / (timeDiff / 1000.0);
                    
                    perfStats.put("avg_bytes_per_sec_recent", avgBytesPerSec);
                    perfStats.put("avg_packets_per_sec_recent", avgPacketsPerSec);
                    perfStats.put("history_samples", performanceHistory.size());
                    perfStats.put("history_duration_ms", timeDiff);
                }
            }
        }
        
        return perfStats;
    }
    
    /**
     * Sélectionne la meilleure interface réseau disponible
     */
    private NetworkInterface selectBestInterface() throws Exception {
        LogLevelManager.logDebug(COMPONENT_NAME, "Sélection automatique de l'interface réseau");
        
        Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
        NetworkInterface bestInterface = null;
        int bestScore = -1;
        
        while (interfaces.hasMoreElements()) {
            NetworkInterface networkInterface = interfaces.nextElement();
            
            // Critères d'exclusion
            if (!networkInterface.isUp() || 
                networkInterface.isLoopback() || 
                networkInterface.isVirtual() ||
                networkInterface.getHardwareAddress() == null) {
                continue;
            }
            
            // Calcul du score de l'interface
            int score = calculateInterfaceScore(networkInterface);
            
            LogLevelManager.logTrace(COMPONENT_NAME, String.format(
                "Interface %s: score=%d, MTU=%d, multicast=%b", 
                networkInterface.getName(), score, networkInterface.getMTU(), 
                networkInterface.supportsMulticast()));
            
            if (score > bestScore) {
                bestScore = score;
                bestInterface = networkInterface;
            }
        }
        
        if (bestInterface != null) {
            LogLevelManager.logInfo(COMPONENT_NAME, String.format(
                "Meilleure interface sélectionnée: %s (score: %d)", 
                bestInterface.getName(), bestScore));
        }
        
        return bestInterface;
    }
    
    /**
     * Calcule un score pour une interface réseau (plus élevé = meilleur)
     */
    private int calculateInterfaceScore(NetworkInterface networkInterface) {
        int score = 0;
        
        try {
            String name = networkInterface.getName().toLowerCase();
            
            // Bonus pour les interfaces Ethernet
            if (name.startsWith("eth") || name.startsWith("en")) {
                score += 100;
            } else if (name.startsWith("wlan") || name.startsWith("wi")) {
                score += 50; // WiFi moins prioritaire
            }
            
            // Bonus pour support multicast (requis pour Profinet)
            if (networkInterface.supportsMulticast()) {
                score += 50;
            }
            
            // Bonus pour MTU élevé
            int mtu = networkInterface.getMTU();
            if (mtu >= 1500) {
                score += 30;
            } else if (mtu >= 1000) {
                score += 15;
            }
            
            // Bonus pour présence d'adresse IP
            if (!networkInterface.getInterfaceAddresses().isEmpty()) {
                score += 20;
                
                // Bonus supplémentaire pour adresse IPv4 non-loopback
                for (InterfaceAddress addr : networkInterface.getInterfaceAddresses()) {
                    InetAddress inetAddr = addr.getAddress();
                    if (inetAddr instanceof Inet4Address && !inetAddr.isLoopbackAddress()) {
                        score += 20;
                        break;
                    }
                }
            }
            
            // Pénalité pour interfaces point-to-point
            if (networkInterface.isPointToPoint()) {
                score -= 20;
            }
            
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur calcul score interface: " + e.getMessage());
            score = 0;
        }
        
        return score;
    }
    
    /**
     * Valide qu'une interface est compatible avec Profinet
     */
    private boolean validateInterfaceForProfilet(NetworkInterface networkInterface) {
        try {
            // Vérifications obligatoires pour Profinet
            if (!networkInterface.supportsMulticast()) {
                LogLevelManager.logError(COMPONENT_NAME, "Interface ne supporte pas le multicast (requis pour Profinet)");
                return false;
            }
            
            if (networkInterface.getMTU() < 1500) {
                LogLevelManager.logError(COMPONENT_NAME, "MTU insuffisant pour Profinet (minimum 1500, actuel: " + 
                                     networkInterface.getMTU() + ")");
                return false;
            }
            
            if (networkInterface.getHardwareAddress() == null) {
                LogLevelManager.logError(COMPONENT_NAME, "Adresse MAC non disponible");
                return false;
            }
            
            // Vérification présence d'une adresse IP
            boolean hasValidIP = false;
            for (InterfaceAddress addr : networkInterface.getInterfaceAddresses()) {
                InetAddress inetAddr = addr.getAddress();
                if (inetAddr instanceof Inet4Address && !inetAddr.isLoopbackAddress()) {
                    hasValidIP = true;
                    break;
                }
            }
            
            if (!hasValidIP) {
                LogLevelManager.logError(COMPONENT_NAME, "Aucune adresse IPv4 valide trouvée");
                return false;
            }
            
            LogLevelManager.logDebug(COMPONENT_NAME, "Interface validée pour Profinet: " + networkInterface.getName());
            return true;
            
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur validation interface: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Estime la bande passante de l'interface active
     */
    private long estimateInterfaceBandwidth() {
        if (activeInterface == null) {
            return 0;
        }
        
        try {
            String name = activeInterface.getName().toLowerCase();
            
            // Estimation basée sur le nom de l'interface
            if (name.contains("eth") || name.contains("en")) {
                // Ethernet - par défaut 100 Mbps, mais peut être 1 Gbps ou plus
                return 100_000_000L; // 100 Mbps en bits/sec
            } else if (name.contains("wlan") || name.contains("wi")) {
                // WiFi - estimation conservatrice
                return 54_000_000L; // 54 Mbps
            } else if (name.contains("lo")) {
                // Loopback - très élevé
                return 1_000_000_000L; // 1 Gbps
            } else {
                // Défaut conservateur
                return 10_000_000L; // 10 Mbps
            }
            
        } catch (Exception e) {
            LogLevelManager.logError(COMPONENT_NAME, "Erreur estimation bande passante: " + e.getMessage());
            return 100_000_000L; // Valeur par défaut
        }
    }
    
    /**
     * Fournit des recommandations pour l'optimisation Profinet
     */
    private List<String> getProfinetRecommendations() {
        List<String> recommendations = new ArrayList<>();
        
        try {
            if (activeInterface == null) {
                recommendations.add("Aucune interface active - initialiser d'abord une interface");
                return recommendations;
            }
            
            // Vérification MTU
            int mtu = activeInterface.getMTU();
            if (mtu < 1500) {
                recommendations.add("Augmenter le MTU à 1500 minimum (actuel: " + mtu + ")");
            } else if (mtu > 1500) {
                recommendations.add("MTU optimal: " + mtu + " (compatible Jumbo Frames)");
            }
            
            // Vérification multicast
            if (!activeInterface.supportsMulticast()) {
                recommendations.add("CRITIQUE: Interface ne supporte pas le multicast - requis pour Profinet");
            }
            
            // Recommandations sur la charge réseau
            double load = calculateNetworkLoad();
            if (load > 80) {
                recommendations.add("Charge réseau élevée (" + String.format("%.1f%%", load) + 
                                 ") - considérer un réseau dédié Profinet");
            } else if (load > 50) {
                recommendations.add("Charge réseau modérée (" + String.format("%.1f%%", load) + 
                                 ") - surveiller les performances");
            }
            
            // Recommandations générales
            recommendations.add("Utiliser un switch managé pour diagnostics avancés");
            recommendations.add("Configurer des VLANs séparés pour le trafic Profinet");
            recommendations.add("Activer la priorité QoS pour les trames temps réel");
            
            if (activeInterface.isVirtual()) {
                recommendations.add("ATTENTION: Interface virtuelle détectée - performances réduites possibles");
            }
            
        } catch (Exception e) {
            recommendations.add("Erreur génération recommandations: " + e.getMessage());
        }
        
        return recommendations;
    }
    
    /**
     * Réinitialise toutes les statistiques
     */
    public void resetStatistics() {
        bytesReceived.set(0);
        bytesSent.set(0);
        packetsReceived.set(0);
        packetsSent.set(0);
        errorsCount.set(0);
        
        synchronized (performanceHistory) {
            performanceHistory.clear();
        }
        
        LogLevelManager.logInfo(COMPONENT_NAME, "Statistiques réseau réinitialisées");
    }
    
    /**
     * Obtient un résumé de l'état du réseau
     */
    public String getNetworkSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("=== Résumé Réseau Profinet ===\n");
        
        if (activeInterface != null) {
            summary.append("Interface active: ").append(activeInterface.getName()).append("\n");
            
            try {
                summary.append("État: ").append(activeInterface.isUp() ? "UP" : "DOWN").append("\n");
                summary.append("MTU: ").append(activeInterface.getMTU()).append("\n");
                summary.append("Multicast: ").append(activeInterface.supportsMulticast() ? "Supporté" : "Non supporté").append("\n");
                
                byte[] mac = activeInterface.getHardwareAddress();
                if (mac != null) {
                    StringBuilder macStr = new StringBuilder();
                    for (int i = 0; i < mac.length; i++) {
                        if (i > 0) macStr.append(":");
                        macStr.append(String.format("%02X", mac[i]));
                    }
                    summary.append("MAC: ").append(macStr.toString()).append("\n");
                }
                
            } catch (Exception e) {
                summary.append("Erreur lecture interface: ").append(e.getMessage()).append("\n");
            }
        } else {
            summary.append("Aucune interface active\n");
        }
        
        // Statistiques
        Map<String, Long> stats = getTrafficStatistics();
        summary.append("\nStatistiques:\n");
        summary.append("  Bytes reçus: ").append(formatBytes(stats.get("bytes_received"))).append("\n");
        summary.append("  Bytes envoyés: ").append(formatBytes(stats.get("bytes_sent"))).append("\n");
        summary.append("  Packets reçus: ").append(stats.get("packets_received")).append("\n");
        summary.append("  Packets envoyés: ").append(stats.get("packets_sent")).append("\n");
        summary.append("  Erreurs: ").append(stats.get("errors")).append("\n");
        summary.append("  Charge réseau: ").append(String.format("%.1f%%", calculateNetworkLoad())).append("\n");
        
        return summary.toString();
    }
    
    /**
     * Formate les bytes en unités lisibles
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.1f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }
    
    // === CLASSE INTERNE POUR ÉCHANTILLONS DE PERFORMANCE ===
    
    /**
     * Échantillon de performance réseau à un moment donné
     */
    private static class NetworkSample {
        final long timestamp;
        final long bytesReceived;
        final long bytesSent;
        final long packetsReceived;
        final long packetsSent;
        final long errors;
        
        NetworkSample(long timestamp, long bytesReceived, long bytesSent, 
                     long packetsReceived, long packetsSent, long errors) {
            this.timestamp = timestamp;
            this.bytesReceived = bytesReceived;
            this.bytesSent = bytesSent;
            this.packetsReceived = packetsReceived;
            this.packetsSent = packetsSent;
            this.errors = errors;
        }
    }
}