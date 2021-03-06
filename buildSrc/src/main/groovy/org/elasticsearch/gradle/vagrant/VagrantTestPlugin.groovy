package org.elasticsearch.gradle.vagrant

import org.elasticsearch.gradle.FileContentsTask
import org.gradle.BuildAdapter
import org.gradle.BuildResult
import org.gradle.api.*
import org.gradle.api.artifacts.dsl.RepositoryHandler
import org.gradle.api.internal.artifacts.dependencies.DefaultProjectDependency
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec

class VagrantTestPlugin implements Plugin<Project> {

    /** All available boxes **/
    static List<String> BOXES = [
            'centos-6',
            'centos-7',
            'debian-8',
            'fedora-24',
            'oel-6',
            'oel-7',
            'opensuse-13',
            'sles-12',
            'ubuntu-1404',
            'ubuntu-1604'
    ]

    /** Boxes used when sampling the tests **/
    static List<String> SAMPLE = [
            'centos-7',
            'ubuntu-1404',
    ]

    /** All onboarded archives by default, available for Bats tests even if not used **/
    static List<String> DISTRIBUTION_ARCHIVES = ['tar', 'rpm', 'deb']

    /** Packages onboarded for upgrade tests **/
    static List<String> UPGRADE_FROM_ARCHIVES = ['rpm', 'deb']

    private static final BATS = 'bats'
    private static final String BATS_TEST_COMMAND ="cd \$BATS_ARCHIVES && sudo bats --tap \$BATS_TESTS/*.$BATS"

    @Override
    void apply(Project project) {

        // Creates the Vagrant extension for the project
        project.extensions.create('esvagrant', VagrantPropertiesExtension, listVagrantBoxes(project))

        // Add required repositories for Bats tests
        configureBatsRepositories(project)

        // Creates custom configurations for Bats testing files (and associated scripts and archives)
        createBatsConfiguration(project)

        // Creates all the main Vagrant tasks
        createVagrantTasks(project)

        if (project.extensions.esvagrant.boxes == null || project.extensions.esvagrant.boxes.size() == 0) {
            throw new InvalidUserDataException('Vagrant boxes cannot be null or empty for esvagrant')
        }

        for (String box : project.extensions.esvagrant.boxes) {
            if (BOXES.contains(box) == false) {
                throw new InvalidUserDataException("Vagrant box [${box}] not found, available virtual machines are ${BOXES}")
            }
        }

        // Creates all tasks related to the Vagrant boxes
        createVagrantBoxesTasks(project)
    }

    private List<String> listVagrantBoxes(Project project) {
        String vagrantBoxes = project.getProperties().get('vagrant.boxes', 'sample')
        if (vagrantBoxes == 'sample') {
            return SAMPLE
        } else if (vagrantBoxes == 'all') {
            return BOXES
        } else {
            return vagrantBoxes.split(',')
        }
    }

    private static Set<String> listVersions(Project project) {
        Node xml
        new URL('https://repo1.maven.org/maven2/org/elasticsearch/elasticsearch/maven-metadata.xml').openStream().withStream { s ->
            xml = new XmlParser().parse(s)
        }

        final String versionAsString = (String)project.version;
        final List<Integer> current = parse(versionAsString.substring(0, versionAsString.indexOf("-SNAPSHOT")))
        final Set<String> versions =
                new TreeSet<>(xml.versioning.versions.version.collect { it.text() }.findAll { it ==~ /[25]\.\d\.\d/ && compare(parse(it), current) < 0})
        if (versions.isEmpty() == false) {
            return versions
        }

        // If no version is found, we run the tests with the current version
        return Collections.singleton(project.version)
    }

    private static List<Integer> parse(final String value) {
        final List<Integer> version = new ArrayList<Integer>()
        final String[] components = value.split("\\.")
        for (final String component : components) {
            version.add(Integer.valueOf(component))
        }
        return version
    }

    private static int compare(final List<Integer> left, final List<Integer> right) {
        // lexicographically compare two lists, treating missing entries as zeros
        final int len = Math.max(left.size(), right.size())
        for (int i = 0; i < len; i++) {
            final int l = (i < left.size()) ? left.get(i) : 0
            final int r = (i < right.size()) ? right.get(i) : 0
            if (l < r) {
                return -1
            }
            if (r < l) {
                return 1
            }
        }
        return 0
    }

    private static File getVersionsFile(Project project) {
        File versions = new File(project.projectDir, 'versions');
        if (versions.exists() == false) {
            // Use the elasticsearch's versions file from project :qa:vagrant
            versions = project.project(":qa:vagrant").file('versions')
        }
        return versions
    }

    private static void configureBatsRepositories(Project project) {
        RepositoryHandler repos = project.repositories

        // Try maven central first, it'll have releases before 5.0.0
        repos.mavenCentral()

        /* Setup a repository that tries to download from
          https://artifacts.elastic.co/downloads/elasticsearch/[module]-[revision].[ext]
          which should work for 5.0.0+. This isn't a real ivy repository but gradle
          is fine with that */
        repos.ivy {
            artifactPattern "https://artifacts.elastic.co/downloads/elasticsearch/[module]-[revision].[ext]"
        }
    }

    private static void createBatsConfiguration(Project project) {
        project.configurations.create(BATS)

        Long seed
        String formattedSeed = null
        String[] upgradeFromVersions

        String maybeTestsSeed = System.getProperty("tests.seed", null);
        if (maybeTestsSeed != null) {
            List<String> seeds = maybeTestsSeed.tokenize(':')
            if (seeds.size() != 0) {
                String masterSeed = seeds.get(0)
                seed = new BigInteger(masterSeed, 16).longValue()
                formattedSeed = maybeTestsSeed
            }
        }
        if (formattedSeed == null) {
            seed = new Random().nextLong()
            formattedSeed = String.format("%016X", seed)
        }

        String maybeUpdradeFromVersions = System.getProperty("tests.packaging.upgrade.from.versions", null)
        if (maybeUpdradeFromVersions != null) {
            upgradeFromVersions = maybeUpdradeFromVersions.split(",")
        } else {
            upgradeFromVersions = getVersionsFile(project)
        }

        String upgradeFromVersion = upgradeFromVersions[new Random(seed).nextInt(upgradeFromVersions.length)]

        DISTRIBUTION_ARCHIVES.each {
            // Adds a dependency for the current version
            project.dependencies.add(BATS, project.dependencies.project(path: ":distribution:${it}", configuration: 'archives'))
        }

        UPGRADE_FROM_ARCHIVES.each {
            // The version of elasticsearch that we upgrade *from*
            project.dependencies.add(BATS, "org.elasticsearch.distribution.${it}:elasticsearch:${upgradeFromVersion}@${it}")
        }

        project.extensions.esvagrant.testSeed = seed
        project.extensions.esvagrant.formattedTestSeed = formattedSeed
        project.extensions.esvagrant.upgradeFromVersion = upgradeFromVersion
        project.extensions.esvagrant.upgradeFromVersions = upgradeFromVersions
    }

    private static void createCleanTask(Project project) {
        project.tasks.create('clean', Delete.class) {
            description 'Clean the project build directory'
            group 'Build'
            delete project.buildDir
        }
    }

    private static void createStopTask(Project project) {
        project.tasks.create('stop') {
            description 'Stop any tasks from tests that still may be running'
            group 'Verification'
        }
    }

    private static void createSmokeTestTask(Project project) {
        project.tasks.create('vagrantSmokeTest') {
            description 'Smoke test the specified vagrant boxes'
            group 'Verification'
        }
    }

    private static void createPrepareVagrantTestEnvTask(Project project) {
        File batsDir = new File("${project.buildDir}/${BATS}")

        Task createBatsDirsTask = project.tasks.create('createBatsDirs')
        createBatsDirsTask.outputs.dir batsDir
        createBatsDirsTask.dependsOn project.tasks.vagrantVerifyVersions
        createBatsDirsTask.doLast {
            batsDir.mkdirs()
        }

        Copy copyBatsArchives = project.tasks.create('copyBatsArchives', Copy) {
            dependsOn createBatsDirsTask
            into "${batsDir}/archives"
            from project.configurations[BATS]
        }

        Copy copyBatsTests = project.tasks.create('copyBatsTests', Copy) {
            dependsOn createBatsDirsTask
            into "${batsDir}/tests"
            from {
                "${project.extensions.esvagrant.batsDir}/tests"
            }
        }

        Copy copyBatsUtils = project.tasks.create('copyBatsUtils', Copy) {
            dependsOn createBatsDirsTask
            into "${batsDir}/utils"
            from {
                "${project.extensions.esvagrant.batsDir}/utils"
            }
        }

        // Now we iterate over dependencies of the bats configuration. When a project dependency is found,
        // we bring back its own archives, test files or test utils.
        project.afterEvaluate {
            project.configurations.bats.dependencies.findAll {it.configuration == BATS }.each { d ->
                if (d instanceof DefaultProjectDependency) {
                    DefaultProjectDependency externalBatsDependency = (DefaultProjectDependency) d
                    Project externalBatsProject = externalBatsDependency.dependencyProject
                    String externalBatsDir = externalBatsProject.extensions.esvagrant.batsDir

                    if (project.extensions.esvagrant.inheritTests) {
                        copyBatsTests.from(externalBatsProject.files("${externalBatsDir}/tests"))
                    }
                    if (project.extensions.esvagrant.inheritTestArchives) {
                        copyBatsArchives.from(externalBatsDependency.projectConfiguration.files)
                    }
                    if (project.extensions.esvagrant.inheritTestUtils) {
                        copyBatsUtils.from(externalBatsProject.files("${externalBatsDir}/utils"))
                    }
                }
            }
        }

        Task createVersionFile = project.tasks.create('createVersionFile', FileContentsTask) {
            dependsOn createBatsDirsTask
            file "${batsDir}/archives/version"
            contents project.version
        }

        Task createUpgradeFromFile = project.tasks.create('createUpgradeFromFile', FileContentsTask) {
            dependsOn createBatsDirsTask
            file "${batsDir}/archives/upgrade_from_version"
            contents project.extensions.esvagrant.upgradeFromVersion
        }

        Task vagrantSetUpTask = project.tasks.create('vagrantSetUp')
        vagrantSetUpTask.dependsOn 'vagrantCheckVersion'
        vagrantSetUpTask.dependsOn copyBatsTests, copyBatsUtils, copyBatsArchives, createVersionFile, createUpgradeFromFile
        vagrantSetUpTask.doFirst {
            project.gradle.addBuildListener new BuildAdapter() {
                @Override
                void buildFinished(BuildResult result) {
                    if (result.failure) {
                        println "Reproduce with: gradle packagingTest "
                        +"-Pvagrant.boxes=${project.extensions.esvagrant.boxes} "
                        + "-Dtests.seed=${project.extensions.esvagrant.formattedSeed} "
                        + "-Dtests.packaging.upgrade.from.versions=${project.extensions.esvagrant.upgradeFromVersions.join(",")}"
                    }
                }
            }
        }
    }

    private static void createUpdateVersionsTask(Project project) {
        project.tasks.create('vagrantUpdateVersions') {
            description 'Update file containing options for the\n    "starting" version in the "upgrade from" packaging tests.'
            group 'Verification'
            doLast {
                File versions = getVersionsFile(project)
                versions.text = listVersions(project).join('\n') + '\n'
            }
        }
    }

    private static void createVerifyVersionsTask(Project project) {
        project.tasks.create('vagrantVerifyVersions') {
            description 'Update file containing options for the\n    "starting" version in the "upgrade from" packaging tests.'
            group 'Verification'
            doLast {
                String maybeUpdateFromVersions = System.getProperty("tests.packaging.upgrade.from.versions", null)
                if (maybeUpdateFromVersions == null) {
                    Set<String> versions = listVersions(project)
                    Set<String> actualVersions = new TreeSet<>(project.extensions.esvagrant.upgradeFromVersions)
                    if (!versions.equals(actualVersions)) {
                        throw new GradleException("out-of-date versions " + actualVersions +
                                ", expected " + versions + "; run gradle vagrantUpdateVersions")
                    }
                }
            }
        }
    }

    private static void createCheckVagrantVersionTask(Project project) {
        project.tasks.create('vagrantCheckVersion', Exec) {
            description 'Check the Vagrant version'
            group 'Verification'
            commandLine 'vagrant', '--version'
            standardOutput = new ByteArrayOutputStream()
            doLast {
                String version = standardOutput.toString().trim()
                if ((version ==~ /Vagrant 1\.(8\.[6-9]|9\.[0-9])+/) == false) {
                    throw new InvalidUserDataException("Illegal version of vagrant [${version}]. Need [Vagrant 1.8.6+]")
                }
            }
        }
    }

    private static void createCheckVirtualBoxVersionTask(Project project) {
        project.tasks.create('virtualboxCheckVersion', Exec) {
            description 'Check the Virtualbox version'
            group 'Verification'
            commandLine 'vboxmanage', '--version'
            standardOutput = new ByteArrayOutputStream()
            doLast {
                String version = standardOutput.toString().trim()
                try {
                    String[] versions = version.split('\\.')
                    int major = Integer.parseInt(versions[0])
                    int minor = Integer.parseInt(versions[1])
                    if ((major < 5) || (major == 5 && minor < 1)) {
                        throw new InvalidUserDataException("Illegal version of virtualbox [${version}]. Need [5.1+]")
                    }
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
                    throw new InvalidUserDataException("Unable to parse version of virtualbox [${version}]. Required [5.1+]", e)
                }
            }
        }
    }

    private static void createPackagingTestTask(Project project) {
        project.tasks.create('packagingTest') {
            group 'Verification'
            description "Tests yum/apt packages using vagrant and bats.\n" +
                    "    Specify the vagrant boxes to test using the gradle property 'vagrant.boxes'.\n" +
                    "    'sample' can be used to test a single yum and apt box. 'all' can be used to\n" +
                    "    test all available boxes. The available boxes are: \n" +
                    "    ${BOXES}"
            dependsOn 'vagrantCheckVersion'
        }
    }

    private static void createVagrantTasks(Project project) {
        createCleanTask(project)
        createStopTask(project)
        createSmokeTestTask(project)
        createUpdateVersionsTask(project)
        createVerifyVersionsTask(project)
        createCheckVagrantVersionTask(project)
        createCheckVirtualBoxVersionTask(project)
        createPrepareVagrantTestEnvTask(project)
        createPackagingTestTask(project)
    }

    private static void createVagrantBoxesTasks(Project project) {
        assert project.extensions.esvagrant.boxes != null

        assert project.tasks.stop != null
        Task stop = project.tasks.stop

        assert project.tasks.vagrantSmokeTest != null
        Task vagrantSmokeTest = project.tasks.vagrantSmokeTest

        assert project.tasks.vagrantCheckVersion != null
        Task vagrantCheckVersion = project.tasks.vagrantCheckVersion

        assert project.tasks.virtualboxCheckVersion != null
        Task virtualboxCheckVersion = project.tasks.virtualboxCheckVersion

        assert project.tasks.vagrantSetUp != null
        Task vagrantSetUp = project.tasks.vagrantSetUp

        assert project.tasks.packagingTest != null
        Task packagingTest = project.tasks.packagingTest

        /*
         * We always use the main project.rootDir as Vagrant's current working directory (VAGRANT_CWD)
         * so that boxes are not duplicated for every Gradle project that use this VagrantTestPlugin.
         */
        def vagrantEnvVars = [
                'VAGRANT_CWD'           : "${project.rootDir.absolutePath}",
                'VAGRANT_VAGRANTFILE'   : 'Vagrantfile',
                'VAGRANT_PROJECT_DIR'   : "${project.projectDir.absolutePath}"
        ]

        // Each box gets it own set of tasks
        for (String box : BOXES) {
            String boxTask = box.capitalize().replace('-', '')

            // always add a halt task for all boxes, so clean makes sure they are all shutdown
            Task halt = project.tasks.create("vagrant${boxTask}#halt", VagrantCommandTask) {
                boxName box
                environmentVars vagrantEnvVars
                args 'halt', box
            }
            stop.dependsOn(halt)

            Task update = project.tasks.create("vagrant${boxTask}#update", VagrantCommandTask) {
                boxName box
                environmentVars vagrantEnvVars
                args 'box', 'update', box
                dependsOn vagrantCheckVersion, virtualboxCheckVersion, vagrantSetUp
            }

            Task up = project.tasks.create("vagrant${boxTask}#up", VagrantCommandTask) {
                boxName box
                environmentVars vagrantEnvVars
                /* Its important that we try to reprovision the box even if it already
                  exists. That way updates to the vagrant configuration take automatically.
                  That isn't to say that the updates will always be compatible. Its ok to
                  just destroy the boxes if they get busted but that is a manual step
                  because its slow-ish. */
                /* We lock the provider to virtualbox because the Vagrantfile specifies
                  lots of boxes that only work properly in virtualbox. Virtualbox is
                  vagrant's default but its possible to change that default and folks do.
                  But the boxes that we use are unlikely to work properly with other
                  virtualization providers. Thus the lock. */
                args 'up', box, '--provision', '--provider', 'virtualbox'
                /* It'd be possible to check if the box is already up here and output
                  SKIPPED but that would require running vagrant status which is slow! */
                dependsOn update
            }

            if (project.extensions.esvagrant.boxes.contains(box) == false) {
                // we d'ont need tests tasks if this box was not specified
                continue;
            }

            Task smoke = project.tasks.create("vagrant${boxTask}#smoketest", Exec) {
                environment vagrantEnvVars
                dependsOn up
                finalizedBy halt
                commandLine 'vagrant', 'ssh', box, '--command',
                        "set -o pipefail && echo 'Hello from ${project.path}' | sed -ue 's/^/    ${box}: /'"
            }
            vagrantSmokeTest.dependsOn(smoke)

            Task packaging = project.tasks.create("vagrant${boxTask}#packagingtest", BatsOverVagrantTask) {
                boxName box
                environmentVars vagrantEnvVars
                dependsOn up
                finalizedBy halt
                command BATS_TEST_COMMAND
            }
            packagingTest.dependsOn(packaging)
        }
    }
}
