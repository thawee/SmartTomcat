package com.poratu.idea.plugins.tomcat.runner;

import com.intellij.debugger.impl.GenericDebuggerRunner;
import com.intellij.execution.ExecutionException;
import com.intellij.execution.configurations.RemoteConnection;
import com.intellij.execution.configurations.RunProfile;
import com.intellij.execution.configurations.RunProfileState;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.execution.ui.RunContentDescriptor;
import com.poratu.idea.plugins.tomcat.conf.TomcatRunConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Author : zengkid
 * Date   : 2017-02-17
 * Time   : 11:00 AM
 */
public class TomcatDebugger extends GenericDebuggerRunner {
    private static final String RUNNER_ID = "SmartTomcatDebugger";

    @Override
    @NotNull
    public String getRunnerId() {
        return RUNNER_ID;
    }

    @Override
    public boolean canRun(@NotNull String executorId, @NotNull RunProfile profile) {
        return (DefaultDebugExecutor.EXECUTOR_ID.equals(executorId) && profile instanceof TomcatRunConfiguration);
    }

    /*
    @Nullable
    @Override
    protected RunContentDescriptor createContentDescriptor(@NotNull RunProfileState state, @NotNull ExecutionEnvironment environment) throws ExecutionException {
        // Create a remote connection to the debug port (8081 as defined in JAVA_DEBUG_OPTIONS)
        RemoteConnection connection = new RemoteConnection(true, "localhost", "5005", false);
        return attachVirtualMachine(state, environment, connection, true);
    } */

}
