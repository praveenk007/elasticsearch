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
package org.elasticsearch.gradle.test

import com.carrotsearch.gradle.junit4.RandomizedTestingTask
import org.elasticsearch.gradle.BuildPlugin
import org.gradle.api.DefaultTask
import org.gradle.api.Task
import org.gradle.api.internal.tasks.options.Option
import org.gradle.api.plugins.JavaBasePlugin
import org.gradle.api.tasks.Input
import org.gradle.util.ConfigureUtil

/**
 * A wrapper task around setting up a cluster and running rest tests.
 */
public class RestIntegTestTask extends DefaultTask {

    protected ClusterConfiguration clusterConfig

    protected RandomizedTestingTask runner

    protected Task clusterInit

    /** Info about nodes in the integ test cluster. Note this is *not* available until runtime. */
    List<NodeInfo> nodes

    /** Flag indicating whether the rest tests in the rest spec should be run. */
    @Input
    boolean includePackaged = false

    public RestIntegTestTask() {
        description = 'Runs rest tests against an elasticsearch cluster.'
        group = JavaBasePlugin.VERIFICATION_GROUP
        runner = project.tasks.create("${name}Runner", RandomizedTestingTask.class)
        super.dependsOn(runner)
        clusterInit = project.tasks.create(name: "${name}Cluster#init", dependsOn: project.testClasses)
        runner.dependsOn(clusterInit)
        runner.classpath = project.sourceSets.test.runtimeClasspath
        runner.testClassesDir = project.sourceSets.test.output.classesDir
        clusterConfig = project.extensions.create("${name}Cluster", ClusterConfiguration.class, project)

        // start with the common test configuration
        runner.configure(BuildPlugin.commonTestConfig(project))
        // override/add more for rest tests
        runner.parallelism = '1'
        runner.include('**/*IT.class')
        runner.systemProperty('tests.rest.load_packaged', 'false')
        // we pass all nodes to the rest cluster to allow the clients to round-robin between them
        // this is more realistic than just talking to a single node
        runner.systemProperty('tests.rest.cluster', "${-> nodes.collect{it.httpUri()}.join(",")}")
        runner.systemProperty('tests.config.dir', "${-> nodes[0].confDir}")
        // TODO: our "client" qa tests currently use the rest-test plugin. instead they should have their own plugin
        // that sets up the test cluster and passes this transport uri instead of http uri. Until then, we pass
        // both as separate sysprops
        runner.systemProperty('tests.cluster', "${-> nodes[0].transportUri()}")

        // copy the rest spec/tests into the test resources
        RestSpecHack.configureDependencies(project)
        project.afterEvaluate {
            runner.dependsOn(RestSpecHack.configureTask(project, includePackaged))
        }
        // this must run after all projects have been configured, so we know any project
        // references can be accessed as a fully configured
        project.gradle.projectsEvaluated {
            if (enabled == false) {
                runner.enabled = false
                clusterInit.enabled = false
                return // no need to add cluster formation tasks if the task won't run!
            }
            nodes = ClusterFormationTasks.setup(project, "${name}Cluster", runner, clusterConfig)
            super.dependsOn(runner.finalizedBy)
        }
    }

    @Option(
            option = "debug-jvm",
            description = "Enable debugging configuration, to allow attaching a debugger to elasticsearch."
    )
    public void setDebug(boolean enabled) {
        clusterConfig.debug = enabled;
    }

    public List<NodeInfo> getNodes() {
        return nodes
    }

    @Override
    public Task dependsOn(Object... dependencies) {
        runner.dependsOn(dependencies)
        for (Object dependency : dependencies) {
            if (dependency instanceof Fixture) {
                runner.finalizedBy(((Fixture)dependency).stopTask)
            }
        }
        return this
    }

    @Override
    public void setDependsOn(Iterable<?> dependencies) {
        runner.setDependsOn(dependencies)
        for (Object dependency : dependencies) {
            if (dependency instanceof Fixture) {
                runner.finalizedBy(((Fixture)dependency).stopTask)
            }
        }
    }

    @Override
    public Task mustRunAfter(Object... tasks) {
        clusterInit.mustRunAfter(tasks)
    }
}
