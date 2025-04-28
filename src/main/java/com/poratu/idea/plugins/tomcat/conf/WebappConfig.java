package com.poratu.idea.plugins.tomcat.conf;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

import java.io.Serializable;

public class WebappConfig implements Serializable {
    private String docBase;
    private String contextPath;
    private String moduleName; // Store module name instead of Module object for serialization

    public WebappConfig() {
    }

    public WebappConfig(String docBase, String contextPath, Module module) {
        this.docBase = docBase;
        this.contextPath = contextPath;
        this.moduleName = module != null ? module.getName() : null;
    }

    public String getDocBase() {
        return docBase;
    }

    public void setDocBase(String docBase) {
        this.docBase = docBase;
    }

    public String getContextPath() {
        return contextPath;
    }

    public void setContextPath(String contextPath) {
        this.contextPath = contextPath;
    }

    public String getModuleName() {
        return moduleName;
    }

    public void setModuleName(String moduleName) {
        this.moduleName = moduleName;
    }

    public Module resolveModule(Project project) {
        if (moduleName == null) return null;
        return com.intellij.openapi.module.ModuleManager.getInstance(project).findModuleByName(moduleName);
    }
}
