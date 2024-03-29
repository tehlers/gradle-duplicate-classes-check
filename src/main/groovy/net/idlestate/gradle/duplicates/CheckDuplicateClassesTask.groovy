/*
 * Portions Copyright 2018 Thorsten Ehlers
 * Portions Copyright 2021 Doychin Bondzhev
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package net.idlestate.gradle.duplicates

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.artifacts.ResolvedArtifact
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask

import java.util.stream.Collectors

import static net.idlestate.gradle.duplicates.CheckDuplicateClassesEngine.*

/**
 * Checks whether the artifacts of the configurations of the project contain the same classes.
 */
class CheckDuplicateClassesTask extends DefaultTask implements VerificationTask {

    private boolean _ignoreFailures = false

    private boolean _generateReport = false;

    private File reportDirectory

    private Map<String, Set<String>> classesByArtifactMap

    CheckDuplicateClassesTask() {
    }

    private List<Configuration> configurationsToCheck = [] as List<Configuration>

    private List<String> excludes = [] as List<String>

    private List<ModuleIdentifier> excludeModules = [] as List<ModuleIdentifier>

    private List<String> includes = [] as List<String>

    @TaskAction
    void checkForDuplicateClasses() {
        def engine = new CheckDuplicateClassesEngine(excludes, excludeModules, includes)

        if (configurationsToCheck.isEmpty()) {
            configurationsToCheck.addAll(project.configurations)
        }

        if (_generateReport) {
            prepareReportsDirectory()
        }

        Map<Configuration, Collection<List<String>>> configurationResult = configurationsToCheck.stream().filter { isConfigurationResolvable(it) }.
                collect(Collectors.toMap({ it }, {
                    logger.info("Checking configuration '${it.name}'")
                    checkConfiguration(it, engine)
                }))

        if (configurationResult.isEmpty() || configurationResult.values().findAll({ !it.isEmpty() }).isEmpty()) {
            return
        }

        processResult(configurationResult)
    }

    private void prepareReportsDirectory() {
        if (reportDirectory != null) {
            return
        }

        if (!project.buildDir.exists()) {
            project.buildDir.mkdir()
        }

        def reportDir = project.buildDir.toPath().resolve("reports").toFile()
        if (!reportDir.exists()) {
            reportDir.mkdir()
        }

        this.reportDirectory = reportDir.toPath().resolve("duplicate classes").toFile()
        if (!this.reportDirectory.exists()) {
            this.reportDirectory.mkdir()
        }
    }

    private void processResult(Map<Configuration, Collection<List<String>>> result) {
        final StringBuilder message = new StringBuilder('There are conflicting files in the modules of the following configurations')

        if (!logger.isInfoEnabled()) {
            message.append(' (add --info for details)')
        }

        if (!_generateReport) {
            message.append(' (add generateReport = true to task parameters get detailed report)')
        }

        result.entrySet().forEach {
            message.append("\n\n${it.key.name}\n${buildMessageWithUniqueModules(it.value)}")
            buildReportFiles(it.value)
        }

        if (_ignoreFailures) {
            logger.warn(message.toString())
        } else {
            throw new GradleException(message.toString())
        }
    }

    def buildReportFiles(Collection<List<String>> jarFiles) {
        if (!_generateReport) {
            return
        }
        Map<String, String> reportMap = buildReport(classesByArtifactMap, jarFiles)
        writeReportFiles(this.reportDirectory.toPath(), reportMap)
    }

    Collection<List<String>> checkConfiguration(final Configuration configuration, CheckDuplicateClassesEngine engine) {
        def artifactsStream = configuration.resolvedConfiguration.resolvedArtifacts.stream()

        Map<String, List<FileToVersion>> duplicateClasses = artifactsStream.
                flatMap { processArtifact(it, engine).stream() }.
                collect(Collectors.groupingBy{it.file})

        duplicateClasses = duplicateClasses.entrySet().stream().
                filter { it.value.size() > 1 }.
                filter { a -> a.value.stream().anyMatch { b -> b.crc != a.value.get(0).crc }}.
                collect(Collectors.toMap({it.key},{it.value}))

        classesByArtifactMap = duplicateClasses.
                values().
                stream().
                flatMap { it.stream() }.
                collect(Collectors.groupingBy({ it.version }, Collectors.mapping({ it.file }, Collectors.toSet())))

        Map<String, Set<String>> jarsByClassMap = duplicateClasses.entrySet().stream().collect(
                Collectors.toMap({it.key}, {it.value.stream().map({it.version}).collect(Collectors.toSet())}))

        return searchForDuplicates(jarsByClassMap, logger.isInfoEnabled() ? { logger.info(it) } : null)
    }


    List<FileToVersion> processArtifact(ResolvedArtifact artifact, CheckDuplicateClassesEngine engine) {
        if (artifact.moduleVersion != null) {
            logger.info("    '${artifact.file.path}' of '${artifact.moduleVersion}'")
        } else {
            logger.info("    '${artifact.file.path}'")
        }

        engine.processArtifact(artifact)
    }

    /**
     * Gradle 3.4 introduced the configuration 'apiElements' that isn't resolvable. So
     * we have to check before accessing it.
     */
    static boolean isConfigurationResolvable(configuration) {
        if (!configuration.metaClass.respondsTo(configuration, 'isCanBeResolved')) {
            // If the recently introduced method 'isCanBeResolved' is unavailable, we
            // assume (for now) that the configuration can be resolved.
            return true
        }

        return configuration.isCanBeResolved()
    }

    @Override
    void setIgnoreFailures(final boolean ignoreFailures) {
        _ignoreFailures = ignoreFailures
    }

    @Override
    boolean getIgnoreFailures() {
        return _ignoreFailures
    }

    @Input
    boolean getGenerateReport() {
        return _generateReport
    }

    void setGenerateReport(boolean generateReport) {
        this._generateReport = generateReport
    }

    CheckDuplicateClassesTask excludes(Iterable<String> excludes) {
        this.excludes.addAll(excludes)
        this
    }

    CheckDuplicateClassesTask includes(String... includes) {
        this.includes.addAll(includes.collect())
        this
    }

    CheckDuplicateClassesTask includes(Iterable<String> includes) {
        this.includes.addAll(includes)
        this
    }

    CheckDuplicateClassesTask excludes(String... excludes) {
        this.excludes.addAll(excludes.collect())
        this
    }

    CheckDuplicateClassesTask excludeModule(String excludeModule) {
        String[] splitted = excludeModule.split(':')
        if (splitted.size() != 2) {
            throw new GradleException('Specify module identifier as "group:name" got "' + excludeModule + '"')
        }

        this.excludeModules.add(DefaultModuleIdentifier.newId(splitted[0], splitted[1]))
        this
    }

    CheckDuplicateClassesTask excludeModules(Iterable<ModuleIdentifier> excludeModules) {
        this.excludeModules.addAll(excludeModules)
        this
    }

    CheckDuplicateClassesTask configurationsToCheck(Configuration... configurations) {
        this.configurationsToCheck.addAll(configurations.collect())
        this
    }

    CheckDuplicateClassesTask configurationsToCheck(Iterable<Configuration> configurations) {
        this.configurationsToCheck.addAll(configurations.collect())
        this
    }

    CheckDuplicateClassesTask configurationsToCheck(Configuration configuration) {
        this.configurationsToCheck.add(configuration)
        this
    }

    CheckDuplicateClassesTask reportDirectory(File reportDirectory) {
        this.reportDirectory = reportDirectory
        this
    }
}
