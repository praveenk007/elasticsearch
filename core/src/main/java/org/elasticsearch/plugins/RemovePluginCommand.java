/*
 * Licensed to Elasticsearch under one or more contributor
 * license agreements. See the NOTICE file distributed with
 * this work for additional information regarding copyright
 * ownership. Elasticsearch licenses this file to you under
 * the Apache License, Version 2.0 (the "License"); you may
 * not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.elasticsearch.plugins;

import joptsimple.OptionSet;
import joptsimple.OptionSpec;
import org.apache.lucene.util.IOUtils;
import org.elasticsearch.cli.EnvironmentAwareCommand;
import org.elasticsearch.cli.ExitCodes;
import org.elasticsearch.cli.Terminal;
import org.elasticsearch.cli.UserException;
import org.elasticsearch.common.Strings;
import org.elasticsearch.env.Environment;

import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.elasticsearch.cli.Terminal.Verbosity.VERBOSE;

/**
 * A command for the plugin cli to remove a plugin from elasticsearch.
 */
class RemovePluginCommand extends EnvironmentAwareCommand {

    private final OptionSpec<String> arguments;

    RemovePluginCommand() {
        super("Removes a plugin from elasticsearch");
        this.arguments = parser.nonOptions("plugin name");
    }

    @Override
    protected void execute(Terminal terminal, OptionSet options, Environment env) throws Exception {
        String arg = arguments.value(options);
        execute(terminal, arg, env);
    }

    // pkg private for testing
    void execute(Terminal terminal, String pluginName, Environment env) throws Exception {
        if (pluginName == null) {
            throw new UserException(ExitCodes.USAGE, "plugin name is required");
        }

        terminal.println("-> Removing " + Strings.coalesceToEmpty(pluginName) + "...");

        final Path pluginDir = env.pluginsFile().resolve(pluginName);
        if (Files.exists(pluginDir) == false) {
            throw new UserException(
                    ExitCodes.CONFIG,
                    "plugin " + pluginName + " not found; run 'elasticsearch-plugin list' to get list of installed plugins");
        }

        final List<Path> pluginPaths = new ArrayList<>();

        final Path pluginBinDir = env.binFile().resolve(pluginName);
        if (Files.exists(pluginBinDir)) {
            if (Files.isDirectory(pluginBinDir) == false) {
                throw new UserException(ExitCodes.IO_ERROR, "Bin dir for " + pluginName + " is not a directory");
            }
            pluginPaths.add(pluginBinDir);
            terminal.println(VERBOSE, "Removing: " + pluginBinDir);
        }

        terminal.println(VERBOSE, "removing [" + pluginDir + "]");
         /*
         * We are going to create a marker file in the plugin directory that indicates that this plugin is a state of removal. If the
         * removal fails, the existence of this marker file indicates that the plugin is in a garbage state. We check for existence of this
         * marker file during startup so that we do not startup with plugins in such a garbage state.
         */
        final Path removing = pluginDir.resolve(".removing-" + pluginName);
        /*
         * Add the contents of the plugin directory before creating the marker file and adding it to the list of paths to be deleted so
         * that the marker file is the last file to be deleted.
         */
        try (Stream<Path> paths = Files.list(pluginDir)) {
            pluginPaths.addAll(paths.collect(Collectors.toList()));
        }
        try {
            Files.createFile(removing);
        } catch (final FileAlreadyExistsException e) {
            /*
             * We need to suppress the marker file already existing as we could be in this state if a previous removal attempt failed and
             * the user is attempting to remove the plugin again.
             */
            terminal.println(VERBOSE, "marker file [" + removing + "] already exists");
        }
        // now add the marker file
        pluginPaths.add(removing);
        // finally, add the plugin directory
        pluginPaths.add(pluginDir);
        IOUtils.rm(pluginPaths.toArray(new Path[pluginPaths.size()]));

        // we preserve the config files in case the user is upgrading the plugin, but we print
        // a message so the user knows in case they want to remove manually
        final Path pluginConfigDir = env.configFile().resolve(pluginName);
        if (Files.exists(pluginConfigDir)) {
            terminal.println(
                    "-> Preserving plugin config files [" + pluginConfigDir + "] in case of upgrade, delete manually if not needed");
        }
    }

}
