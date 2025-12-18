package thawee.idea.plugins.tomcat.action;

import com.intellij.execution.RunManager;
import com.intellij.execution.RunnerAndConfigurationSettings;
import com.intellij.notification.Notification;
import com.intellij.notification.NotificationType;
import com.intellij.notification.Notifications;
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
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.poratu.idea.plugins.tomcat.conf.TomcatRunConfiguration;
import com.poratu.idea.plugins.tomcat.conf.TomcatRunConfigurationType;
import com.poratu.idea.plugins.tomcat.conf.WebappConfig;
import com.poratu.idea.plugins.tomcat.utils.PluginUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class LinkSmartTomcatModuleAction extends AnAction implements ActionUpdateThreadAware {

    private @NotNull String PLUGIN_ID = "com.poratu.idea.plugins.tomcat";

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        final Project project = e.getProject();
        if (project == null) return;

        VirtualFile selectedFile = e.getData(CommonDataKeys.VIRTUAL_FILE);

        selectedFile = getRootProjectDir(selectedFile);

        if (selectedFile == null) {
            Messages.showErrorDialog(project, "No web.xml selected", "Setup SmartTomcat Project Error");
            return;
        }

        // Check if the directory is a module, if not, make it a module
        checkAndMakeModuleRoot(project, selectedFile);
    }

    /**
     * Resolves the project root directory based on the selected web.xml file.
     * Supports both standard Maven/Gradle structure (src/main/webapp/WEB-INF/web.xml)
     * and legacy Eclipse structure (WebContent/WEB-INF/web.xml).
     *
     * @param selectedFile The selected file (expected to be web.xml)
     * @return The project root directory, or null if the structure is not recognized
     */
    private static @Nullable VirtualFile getRootProjectDir(VirtualFile selectedFile) {
        if (selectedFile != null && "web.xml".equals(selectedFile.getName())) {
            VirtualFile webInf = selectedFile.getParent(); // WEB-INF
            if (webInf != null) {
                VirtualFile webRoot = webInf.getParent(); // WebContent or webapp
                if (webRoot != null) {
                    // Handle Maven/Gradle structure: src/main/webapp
                    if ("webapp".equals(webRoot.getName())) {
                        VirtualFile mainDir = webRoot.getParent();
                        if (mainDir != null && "main".equals(mainDir.getName())) {
                            VirtualFile srcDir = mainDir.getParent();
                            if (srcDir != null && "src".equals(srcDir.getName())) {
                                return srcDir.getParent();
                            }
                        }
                    }
                    // Handle Eclipse/Legacy structure: WebContent
                    return webRoot.getParent();
                }
            }
        }
        return null;
    }
    /*
    private static @Nullable VirtualFile getRootProjectDir(VirtualFile selectedFile) {
        // support project_root/WebContent/WEB-INF/web.xml
        // support project_root/src/main/webapp/WEB-INF/web.xml
        if(selectedFile != null && "web.xml".equals(selectedFile.getName())) {
            VirtualFile parent = selectedFile.getParent(); // WEB-INF
            if(parent != null) {
                selectedFile = parent.getParent(); // WebContent
                if(selectedFile != null) {
                    selectedFile = selectedFile.getParent(); // project
                }
            }
        }else {
            selectedFile = null;
        }
        return selectedFile;
    } */

    private void checkAndMakeModuleRoot(Project project, VirtualFile directory) {
        ModuleManager moduleManager = ModuleManager.getInstance(project);
        Module[] modules = moduleManager.getModules();
        Module existingModule = null;
        String title = "SmartTomcat Project";

        // Check if this directory is already a module root
        for (Module module : modules) {
            ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
            VirtualFile[] contentRoots = rootManager.getContentRoots();

            for (VirtualFile root : contentRoots) {
                if (directory.equals(root)) {
                    existingModule = module;
                    break;
                }
            }

            if (existingModule != null) break;
        }

        if (existingModule != null) {
            // Directory is already a module root, just update libraries and add Tomcat library
            Module finalExistingModule = existingModule;
            ApplicationManager.getApplication().runWriteAction(() -> {
                // Configure Tomcat run configuration
                TomcatRunConfiguration runConfig = configureTomcatRunConfiguration(project, finalExistingModule, directory);

                // Update existing module structure
                updateModuleLibraries(project, finalExistingModule, directory, runConfig.getTomcatInfo().getName());

                String content ="Java module is updated with dependencies, and added to tomcat run configuration";
                Notification notification = new Notification(PLUGIN_ID, title, content, NotificationType.INFORMATION);
                Notifications.Bus.notify(notification);
            });
        } else {
            // Directory is not a module root, so create a new module with this directory as root
            try {
                ModifiableModuleModel modifiableModuleModel = moduleManager.getModifiableModel();

                // Create a new module with the directory name
                String moduleName = directory.getName();
                String modulePath = Paths.get(directory.getPath(), moduleName + ".iml").toString();

                // Create a general module
                Module newModule = modifiableModuleModel.newModule(modulePath, JavaModuleType.getModuleType().getId());

                // Commit changes
                ApplicationManager.getApplication().runWriteAction(() -> {
                    modifiableModuleModel.commit();

                    // Configure module structure
                    configureModuleStructure(project, newModule, directory);

                    String content ="New java module is created, and added to tomcat run configuration";
                    Notification notification = new Notification(PLUGIN_ID, title, content, NotificationType.INFORMATION);
                    Notifications.Bus.notify(notification);
                });
            } catch (Exception ex) {
                String content = "Problem occurred - " + ex.getMessage();
                Notification notification = new Notification(PLUGIN_ID, title, content, NotificationType.ERROR);
                Notifications.Bus.notify(notification);
            }
        }
    }

    private void updateModuleLibraries(Project project, Module module, VirtualFile rootDirectory, String tomcatName) {
        ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        ModifiableRootModel modifiableRootModel = rootManager.getModifiableModel();

        // Remove existing EndorsedLibs and WebInfLibs if they exist
        for (OrderEntry entry : modifiableRootModel.getOrderEntries()) {
            if (entry instanceof LibraryOrderEntry libraryEntry) {
                String libraryName = libraryEntry.getLibraryName();
                if (libraryName != null && (libraryName.equals("EndorsedLibs") || libraryName.equals("WebInfLibs") || libraryName.equals(tomcatName))) {
                    Library library = libraryEntry.getLibrary();
                    if (library != null) {
                        modifiableRootModel.getModuleLibraryTable().removeLibrary(library);
                    }
                }
            }
        }

        // Add libraries from WebContent/WEB-INF/lib or src/main/webapp/WEB-INF/lib
        VirtualFile webInfDir = null;

        // Check legacy structure
        VirtualFile webContentDir = rootDirectory.findChild("WebContent");
        if (webContentDir != null && webContentDir.isDirectory()) {
            webInfDir = webContentDir.findChild("WEB-INF");
        }

        // Check Maven/Gradle structure if not found in legacy
        if (webInfDir == null) {
            VirtualFile srcDir = rootDirectory.findChild("src");
            if (srcDir != null && srcDir.isDirectory()) {
                VirtualFile mainDir = srcDir.findChild("main");
                if (mainDir != null && mainDir.isDirectory()) {
                    VirtualFile webappDir = mainDir.findChild("webapp");
                    if (webappDir != null && webappDir.isDirectory()) {
                        webInfDir = webappDir.findChild("WEB-INF");
                    }
                }
            }
        }

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

        // Add Tomcat library from global libraries as provided
        LibraryTable.ModifiableModel globalLibraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable().getModifiableModel();
        Library tomcatLibrary = null;

        // Look for Smart Tomcat library in global libraries
        for (Library library : globalLibraryTable.getLibraries()) {
            if (library.getName() != null && library.getName().equals(tomcatName)) {
                tomcatLibrary = library;
                break;
            }
        }

        if (tomcatLibrary != null) {
            // Add the Tomcat library to the module with PROVIDED scope
            LibraryOrderEntry tomcatEntry = modifiableRootModel.addLibraryEntry(tomcatLibrary);
            tomcatEntry.setScope(DependencyScope.PROVIDED);
        }

        // Commit all changes
        modifiableRootModel.commit();
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
            VirtualFile resourceDir = confDevDir.findChild("WEB-INF");
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

        // Commit all changes
        modifiableRootModel.commit();

        // Configure Smart Tomcat if available
        TomcatRunConfiguration runConfig = configureTomcatRunConfiguration(project, module, rootDirectory);
        updateModuleLibraries(project, module, rootDirectory, runConfig.getTomcatInfo().getName());

    }

    private TomcatRunConfiguration configureTomcatRunConfiguration(Project project, Module module, VirtualFile rootDirectory) {

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

        return (TomcatRunConfiguration) settings.getConfiguration();
    }

    @Override
    public void update(@NotNull AnActionEvent event) {
        // Set the availability for folder has .psl folder on its parents
        Project project =event.getProject();
        VirtualFile file = event.getData(CommonDataKeys.VIRTUAL_FILE);

        VirtualFile selectedFile = getRootProjectDir(file);

        boolean show = (project != null && selectedFile != null);

        final Presentation presentation = event.getPresentation();
            presentation.setEnabled(show);
            presentation.setVisible(show);
    }

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }
}
