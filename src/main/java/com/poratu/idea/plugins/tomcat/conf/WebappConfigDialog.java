package com.poratu.idea.plugins.tomcat.conf;

import com.intellij.application.options.ModulesComboBox;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.ValidationInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.FormBuilder;
import com.poratu.idea.plugins.tomcat.utils.PluginUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ItemEvent;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class WebappConfigDialog extends DialogWrapper {
    private final Project project;
    private final WebappConfig webappConfig;
    private final boolean isNewConfig;
    private final List<WebappConfig> existingConfigs;

    private final ModulesComboBox modulesComboBox = new ModulesComboBox();
    private ComboBox<String> docBaseComboBox = new ComboBox<>();
    private final JTextField contextPathField = new JTextField();

    public WebappConfigDialog(@NotNull Project project, @NotNull WebappConfig webappConfig,
                              boolean isNewConfig, @NotNull List<WebappConfig> existingConfigs) {
        super(project, true);
        this.project = project;
        this.webappConfig = webappConfig;
        this.isNewConfig = isNewConfig;
        this.existingConfigs = existingConfigs;

        setTitle(isNewConfig ? "Add Web Application" : "Edit Web Application");
        init();
        initFields();
    }

    private void initFields() {
        // Initialize modules combo box with available modules
        populateModulesComboBox();

        // Add listener to module selection to update docBase dropdown
        modulesComboBox.addItemListener(e -> {
            if (e.getStateChange() == ItemEvent.SELECTED) {
                Module selectedModule = modulesComboBox.getSelectedModule();

                // Update deployment directory dropdown
                updateDocBaseComboBox();

                // Update context path suggestion for new configs
                if (isNewConfig && selectedModule != null) {
                    contextPathField.setText("/" + PluginUtils.extractContextPath(selectedModule));
                }

                // Enable/disable docBase components based on module selection
                docBaseComboBox.setEnabled(selectedModule != null);
            }
        });

        // Set initial values
        if (!isNewConfig) {
            Module module = webappConfig.resolveModule(project);
            if (module != null) {
                modulesComboBox.setSelectedModule(module);
                updateDocBaseComboBox();
                selectDocBase(webappConfig.getDocBase());
            }
            contextPathField.setText(webappConfig.getContextPath());
        } else if (modulesComboBox.getSelectedModule() != null) {
            // For new configs, suggest a context path based on the module name
            Module selectedModule = modulesComboBox.getSelectedModule();
            contextPathField.setText("/" + PluginUtils.extractContextPath(selectedModule));
            updateDocBaseComboBox();
        }

        // Initially disable docBase components if no module is selected
        docBaseComboBox.setEnabled(modulesComboBox.getSelectedModule() != null);
    }

    private void updateDocBaseComboBox() {
        Module selectedModule = modulesComboBox.getSelectedModule();
        docBaseComboBox.removeAllItems();
        // Increase the preferred width to display full paths
        docBaseComboBox.setPreferredSize(new Dimension(480, docBaseComboBox.getPreferredSize().height));

        if (selectedModule != null) {
            // Find web content roots for the selected module
           // List<String> webRoots = findWebContentRoots(selectedModule);
            List<VirtualFile> webRoots = PluginUtils.findWebRoots(selectedModule);

            // Add them to the combo box
            for (VirtualFile webRoot : webRoots) {
                docBaseComboBox.addItem(webRoot.getPath());
            }

            // Select the first item if available
            if (docBaseComboBox.getItemCount() > 0) {
                docBaseComboBox.setSelectedIndex(0);
            }
        }
    }

    private void selectDocBase(String docBase) {
        if (docBase == null) return;

        // Try to find and select the docBase in the combo box
        for (int i = 0; i < docBaseComboBox.getItemCount(); i++) {
            String path = docBaseComboBox.getItemAt(i);
            if (path.equals(docBase)) {
                docBaseComboBox.setSelectedIndex(i);
               // docBaseComboBox.setPrototypeDisplayValue(path);
                return;
            }
        }

        // If not found, add it as a new entry
        docBaseComboBox.addItem(docBase);
        docBaseComboBox.setSelectedItem(docBase);
       // docBaseComboBox.setPrototypeDisplayValue(docBase);
    }

    private void populateModulesComboBox() {
        // Get all modules
        List<Module> allModules = PluginUtils.getModules(project);

        // Filter out modules that are already used in other configs
        List<Module> availableModules = new ArrayList<>();
        Set<String> usedModuleNames = new HashSet<>();

        // Collect module names that are already in use
        for (WebappConfig config : existingConfigs) {
            // Skip the current config being edited
            if (!isNewConfig && webappConfig != null &&
                    config.getModuleName() != null &&
                    config.getModuleName().equals(webappConfig.getModuleName())) {
                continue;
            }

            if (config.getModuleName() != null) {
                usedModuleNames.add(config.getModuleName());
            }
        }

        // Add modules that are not in use
        for (Module module : allModules) {
            if (!usedModuleNames.contains(module.getName())) {
                availableModules.add(module);
            }
        }

        // Set the filtered modules to the combo box
        modulesComboBox.setModules(availableModules);

        // If editing, make sure the current module is selected
        if (!isNewConfig && webappConfig.getModuleName() != null) {
            Module module = webappConfig.resolveModule(project);
            if (module != null) {
                modulesComboBox.setSelectedModule(module);
            }
        }
    }

    @Nullable
    @Override
    protected JComponent createCenterPanel() {
        // Reordered fields: module, deployment directory, context path
        JPanel panel = FormBuilder.createFormBuilder()
                .addLabeledComponent("Module:", modulesComboBox)
                .addLabeledComponent("WebContent directory:", docBaseComboBox)
                .addLabeledComponent("Context path:", contextPathField)
                .getPanel();

        panel.setPreferredSize(new Dimension(500, 150));
        return panel;
    }

    @Nullable
    @Override
    protected ValidationInfo doValidate() {
        if (modulesComboBox.getSelectedModule() == null) {
            return new ValidationInfo("Module must be selected", modulesComboBox);
        }

        if (docBaseComboBox.getSelectedItem() == null) {
            return new ValidationInfo("WebContent directory must be selected", docBaseComboBox);
        }

        if (StringUtil.isEmpty(contextPathField.getText())) {
            return new ValidationInfo("Context path cannot be empty", contextPathField);
        }

        if (!contextPathField.getText().startsWith("/")) {
            return new ValidationInfo("Context path must start with '/'", contextPathField);
        }

        // Check for duplicate context paths
        String contextPath = contextPathField.getText();
        for (WebappConfig config : existingConfigs) {
            if (config != webappConfig && // Skip the current config
                    contextPath.equals(config.getContextPath())) {
                return new ValidationInfo("Context path is already in use", contextPathField);
            }
        }

        return null;
    }

    @Override
    protected void doOKAction() {
        // Update the webapp config
        Module selectedModule = modulesComboBox.getSelectedModule();
        webappConfig.setModuleName(selectedModule != null ? selectedModule.getName() : null);

        String selectedPath = (String) docBaseComboBox.getSelectedItem();
        if (selectedPath != null) {
            webappConfig.setDocBase(selectedPath);
        }

        webappConfig.setContextPath(contextPathField.getText());

        super.doOKAction();
    }

}
