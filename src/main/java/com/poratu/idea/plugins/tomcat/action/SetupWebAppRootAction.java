package com.poratu.idea.plugins.tomcat.action;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.module.JavaModuleType;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.poratu.idea.plugins.tomcat.conf.TomcatRunConfiguration;
import com.poratu.idea.plugins.tomcat.conf.TomcatRunConfigurationType;
import com.poratu.idea.plugins.tomcat.conf.WebappConfig;
import com.poratu.idea.plugins.tomcat.utils.PluginUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class SetupWebAppRootAction extends AnAction implements ActionUpdateThreadAware {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getProject();
        if (project == null) return;

        VirtualFile selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (selectedFile == null) {
            Messages.showErrorDialog(project, "No file/directory selected", "Setup Profile PSL Root Error");
            return;
        }

        VirtualFile directory = selectedFile.isDirectory() ? selectedFile : selectedFile.getParent();
        if (directory == null) return;

        // Check if the directory is a module, if not, make it a module
        checkAndMakeModuleRoot(project, directory);
    }

    private void checkAndMakeModuleRoot(Project project, VirtualFile directory) {
        boolean isModuleRoot = false;
        ModuleManager moduleManager = ModuleManager.getInstance(project);
        Module[] modules = moduleManager.getModules();

        // Check if this directory is already a module root
        for (Module module : modules) {
            ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
            VirtualFile[] contentRoots = rootManager.getContentRoots();

            for (VirtualFile root : contentRoots) {
                if (directory.equals(root)) {
                    isModuleRoot = true;
                    break;
                }
            }

            if (isModuleRoot) break;
        }

        if (!isModuleRoot) {
            // Directory is not a module root, so create a new module with this directory as root
            try {
                ModifiableModuleModel modifiableModuleModel = moduleManager.getModifiableModel();

                // Create a new module with the directory name
                String moduleName = directory.getName();
                String modulePath = directory.getPath() + "/" + moduleName + ".iml";

                // Create a general module
                Module newModule = modifiableModuleModel.newModule(modulePath, JavaModuleType.getModuleType().getId());

                // Commit changes
                ApplicationManager.getApplication().runWriteAction(() -> {
                    modifiableModuleModel.commit();

                    // Configure module structure
                    configureModuleStructure(project, newModule, directory);
                });
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void configureModuleStructure(Project project, Module module, VirtualFile rootDirectory) {
        ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        ModifiableRootModel modifiableRootModel = rootManager.getModifiableModel();

        // Add content root
        ContentEntry contentEntry = modifiableRootModel.addContentEntry(rootDirectory);

        // Set module SDK to match project SDK
        Sdk projectSdk = ProjectRootManager.getInstance(project).getProjectSdk();
        if (projectSdk != null) {
            modifiableRootModel.setSdk(projectSdk);
        }

        // Find JavaSource directory and mark as Sources
        VirtualFile javaSourceDir = rootDirectory.findChild("JavaSource");
        if (javaSourceDir != null && javaSourceDir.isDirectory()) {
            contentEntry.addSourceFolder(javaSourceDir, false);
        }

        // Check for src directory
        javaSourceDir = rootDirectory.findChild("src");
        if (javaSourceDir != null && javaSourceDir.isDirectory()) {
            // Check for Maven/Gradle structure
            VirtualFile mainDir = javaSourceDir.findChild("main");
            if (mainDir != null && mainDir.isDirectory()) {
                // Add src/main/java as source folder
                VirtualFile javaDir = mainDir.findChild("java");
                if (javaDir != null && javaDir.isDirectory()) {
                    contentEntry.addSourceFolder(javaDir, false);
                }

                // Add src/main/resources as resource folder
                VirtualFile resourcesDir = mainDir.findChild("resources");
                if (resourcesDir != null && resourcesDir.isDirectory()) {
                    contentEntry.addSourceFolder(resourcesDir.getUrl(), JavaResourceRootType.RESOURCE);
                }
            } else {
                // If not Maven/Gradle structure, add src as source folder
                contentEntry.addSourceFolder(javaSourceDir, false);
            }
        }

        VirtualFile confDevDir = rootDirectory.findChild("confdev");
        if (confDevDir != null && confDevDir.isDirectory()) {
            //confdev/JavaSource
            javaSourceDir = confDevDir.findChild("JavaSource");
            if (javaSourceDir != null && javaSourceDir.isDirectory()) {
                contentEntry.addSourceFolder(javaSourceDir, false);
            }

            //confdev/WEB-INF
            VirtualFile resourceDir = confDevDir.findChild("Resource");
            if (resourceDir != null && resourceDir.isDirectory()) {
                // add confdev as resource
                contentEntry.addSourceFolder(resourceDir.getUrl(), JavaResourceRootType.RESOURCE);
            }
        }

        // Set compiler output path
        CompilerModuleExtension compilerExtension = modifiableRootModel.getModuleExtension(CompilerModuleExtension.class);
        VirtualFile outputDir = rootDirectory.findChild("bin");
        if (outputDir == null) {
            try {
                outputDir = rootDirectory.createChildDirectory(this, "bin");
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        if (outputDir != null) {
            compilerExtension.setCompilerOutputPath(outputDir);
            compilerExtension.setCompilerOutputPathForTests(outputDir);
        }

        // Add libraries from endorsed
        VirtualFile endorsedDir = rootDirectory.findChild("endorsed");
        if (endorsedDir != null && endorsedDir.isDirectory()) {
            // Create a library for all JARs in the lib directory
            Library library = modifiableRootModel.getModuleLibraryTable().createLibrary("EndorsedLibs");
            Library.ModifiableModel libraryModel = library.getModifiableModel();
            for (VirtualFile jarFile : endorsedDir.getChildren()) {
                if (jarFile.getExtension() != null && jarFile.getExtension().equalsIgnoreCase("jar")) {
                    VirtualFile jarRoot = com.intellij.openapi.vfs.JarFileSystem.getInstance().getJarRootForLocalFile(jarFile);
                    if (jarRoot != null) {
                        libraryModel.addRoot(jarRoot, OrderRootType.CLASSES);
                    }
                }
            }
            libraryModel.commit();

            // Set the scope to PROVIDED for the EndorsedLibs library
            for (OrderEntry orderEntry : modifiableRootModel.getOrderEntries()) {
                if (orderEntry instanceof LibraryOrderEntry &&
                        ((LibraryOrderEntry) orderEntry).getLibraryName() != null &&
                        ((LibraryOrderEntry) orderEntry).getLibraryName().equals("EndorsedLibs")) {
                    ((LibraryOrderEntry) orderEntry).setScope(DependencyScope.PROVIDED);
                    break;
                }
            }
        }

        // Add libraries from WebContent/WEB-INF/lib
        VirtualFile webContentDir = rootDirectory.findChild("WebContent");
        if (webContentDir != null && webContentDir.isDirectory()) {
            VirtualFile webInfDir = webContentDir.findChild("WEB-INF");
            if (webInfDir != null && webInfDir.isDirectory()) {
                VirtualFile libDir = webInfDir.findChild("lib");
                if (libDir != null && libDir.isDirectory()) {
                    // Create a single project library for all JARs
                    Library library = modifiableRootModel.getModuleLibraryTable().createLibrary("WebInfLibs");
                    Library.ModifiableModel libraryModel = library.getModifiableModel();
                    for (VirtualFile jarFile : libDir.getChildren()) {
                        if (jarFile.getExtension() != null && jarFile.getExtension().equalsIgnoreCase("jar")) {
                            VirtualFile jarRoot = com.intellij.openapi.vfs.JarFileSystem.getInstance().getJarRootForLocalFile(jarFile);
                            if (jarRoot != null) {
                                libraryModel.addRoot(jarRoot, OrderRootType.CLASSES);
                            }
                        }
                    }
                    libraryModel.commit();
                }
            }
        }

        // Commit all changes
        modifiableRootModel.commit();

        // Configure Smart Tomcat if available
        configureTomcatRunConfiguration(project, module, rootDirectory);
    }

    private void configureTomcatRunConfiguration(Project project, Module module, VirtualFile rootDirectory) {

        RunManager runManager = RunManager.getInstance(project);

        String name = "Tomcat: "+project.getName().toUpperCase(Locale.ROOT) + " - 8080";
        boolean newSettings = false;
        RunnerAndConfigurationSettings settings = runManager.findConfigurationByTypeAndName("com.poratu.idea.plugins.tomcat", name);

        // Create a new Smart Tomcat configuration
        if(settings == null) {
            settings = runManager.createConfiguration(name, TomcatRunConfigurationType.class);
            newSettings = true;
        }
        TomcatRunConfiguration configuration = (TomcatRunConfiguration) settings.getConfiguration();

        if(newSettings) {
            // Set Tomcat home path - this would need to be detected or provided
            //String tomcatHomePath = System.getProperty("user.home") + "/.SmartTomcat/"+project.getName();
            Path tomcatHomePath = Paths.get(System.getProperty("user.home"), ".SmartTomcat", project.getName());
            configuration.setCatalinaBase(tomcatHomePath.toFile().getAbsolutePath());

            // initial webapp
            configuration.setWebappConfigs(new ArrayList<>());

            // Set server port
            configuration.setPort(8080);
        }

        // Check if module is already added, remove it if exists
        String moduleName = module.getName();
        List<WebappConfig> existingConfigs = configuration.getWebappConfigs();
        existingConfigs.removeIf(config -> moduleName.equals(config.getModuleName()));

        WebappConfig webappConfig = new WebappConfig();
        webappConfig.setModuleName(moduleName);
        webappConfig.setContextPath("/"+PluginUtils.extractContextPath(module));
        List<VirtualFile> list = PluginUtils.findWebRoots(module);
        webappConfig.setDocBase(list.getFirst().getCanonicalPath());
        configuration.addWebappConfig(webappConfig);

        if(newSettings) {
            // Add the configuration to run manager
            runManager.addConfiguration(settings);
        }

        runManager.setSelectedConfiguration(settings);
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        // Set the availability for folder has .psl folder on its parents
        Project project =event.getProject();
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);

        if(project ==null || file == null) return;

        final Presentation presentation = event.getPresentation();
            presentation.setEnabled(true);
            presentation.setVisible(true);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
