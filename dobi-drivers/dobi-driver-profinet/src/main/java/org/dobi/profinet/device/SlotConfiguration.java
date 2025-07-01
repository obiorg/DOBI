package org.dobi.profinet.device;

import java.util.ArrayList;
import java.util.List;

public class SlotConfiguration {
    private int slotNumber;
    private String moduleType;
    private String moduleName;
    private int inputSize;
    private int outputSize;
    private List<SubmoduleInfo> submodules = new ArrayList<>();
    
    public SlotConfiguration() {}
    
    public SlotConfiguration(int slotNumber, String moduleType) {
        this.slotNumber = slotNumber;
        this.moduleType = moduleType;
    }
    
    // Getters et Setters
    public int getSlotNumber() { return slotNumber; }
    public void setSlotNumber(int slotNumber) { this.slotNumber = slotNumber; }
    
    public String getModuleType() { return moduleType; }
    public void setModuleType(String moduleType) { this.moduleType = moduleType; }
    
    public String getModuleName() { return moduleName; }
    public void setModuleName(String moduleName) { this.moduleName = moduleName; }
    
    public int getInputSize() { return inputSize; }
    public void setInputSize(int inputSize) { this.inputSize = inputSize; }
    
    public int getOutputSize() { return outputSize; }
    public void setOutputSize(int outputSize) { this.outputSize = outputSize; }
    
    public List<SubmoduleInfo> getSubmodules() { return submodules; }
    public void setSubmodules(List<SubmoduleInfo> submodules) { this.submodules = submodules; }
    
    public void addSubmodule(SubmoduleInfo submodule) {
        submodules.add(submodule);
    }
    
    @Override
    public String toString() {
        return "SlotConfiguration{" +
                "slotNumber=" + slotNumber +
                ", moduleType='" + moduleType + '\'' +
                ", inputSize=" + inputSize +
                ", outputSize=" + outputSize +
                '}';
    }
    
    public static class SubmoduleInfo {
        private int submoduleIndex;
        private String submoduleName;
        private String submoduleType;
        private int inputOffset;
        private int outputOffset;
        private int inputLength;
        private int outputLength;
        
        public SubmoduleInfo() {}
        
        public SubmoduleInfo(int submoduleIndex, String submoduleName) {
            this.submoduleIndex = submoduleIndex;
            this.submoduleName = submoduleName;
        }
        
        // Getters et Setters
        public int getSubmoduleIndex() { return submoduleIndex; }
        public void setSubmoduleIndex(int submoduleIndex) { this.submoduleIndex = submoduleIndex; }
        
        public String getSubmoduleName() { return submoduleName; }
        public void setSubmoduleName(String submoduleName) { this.submoduleName = submoduleName; }
        
        public String getSubmoduleType() { return submoduleType; }
        public void setSubmoduleType(String submoduleType) { this.submoduleType = submoduleType; }
        
        public int getInputOffset() { return inputOffset; }
        public void setInputOffset(int inputOffset) { this.inputOffset = inputOffset; }
        
        public int getOutputOffset() { return outputOffset; }
        public void setOutputOffset(int outputOffset) { this.outputOffset = outputOffset; }
        
        public int getInputLength() { return inputLength; }
        public void setInputLength(int inputLength) { this.inputLength = inputLength; }
        
        public int getOutputLength() { return outputLength; }
        public void setOutputLength(int outputLength) { this.outputLength = outputLength; }
    }
}