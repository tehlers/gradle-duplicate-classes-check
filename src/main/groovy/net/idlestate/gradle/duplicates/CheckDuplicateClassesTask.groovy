/*
 * Copyright 2018 Thorsten Ehlers
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
import org.gradle.api.artifacts.ResolvedArtifact
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

    @Internal
    private List<Configuration> configurationsToCheck = [] as List<Configuration>

    private List<String> excludes = [] as List<String>

    private List<String> includes = [] as List<String>

    CheckDuplicateClassesTask() {
    }

    @TaskAction
    void checkForDuplicateClasses() {
        final StringBuilder result = new StringBuilder()

        def engine = new CheckDuplicateClassesEngine(excludes, includes)

        if (configurationsToCheck.isEmpty()) {
            configurationsToCheck.addAll(project.configurations)
        }

        if (_generateReport) {
            prepareReportDirectory()
        }

        Map<Configuration, Collection<List<String>>> configurationResult = configurationsToCheck.stream().filter { isConfigurationResolvable(it) }.
                collect(Collectors.toMap({ it }, {
                    logger.info("Checking configuration '${it.name}'")
                    checkConfiguration(it, engine)
                }))

        if (configurationResult.isEmpty() || configurationResult.values().findAll({!it.isEmpty()}).isEmpty()) {
            return
        }

        processResult(configurationResult)
    }

    private void processResult(StringBuilder result) {
        final StringBuilder message = new StringBuilder('There are conflicting files in the modules of the following configurations')

        if (!logger.isInfoEnabled()) {
            message.append(' (add --info for details)')
        }

        message.append(":${result.toString()}")

        if (_ignoreFailures) {
            logger.warn(message.toString())
        } else {
            throw new GradleException(message.toString())
        }
    }

    String checkConfiguration(final Configuration configuration, CheckDuplicateClassesEngine engine) {
        def artifactsStream = configuration.resolvedConfiguration.resolvedArtifacts.stream()
        Map<String, Set<String>> modulesByFile = artifactsStream.flatMap { artifact ->
            processArtifact(artifact, engine).stream().
                    map { new FileToVersion(it, artifact.moduleVersion.toString()) }
        }.collect(concurrentMapCollector())

        return searchForDuplicates(modulesByFile, configuration.name, logger.isInfoEnabled() ? { logger.info(it) } : null)
    }


    Collection<String> processArtifact(ResolvedArtifact artifact, CheckDuplicateClassesEngine engine) {
        if (artifact.moduleVersion != null) {
            logger.info("    '${artifact.file.path}' of '${artifact.moduleVersion}'")
        } else {
            logger.info("    '${artifact.file.path}'")
        }

        if (!artifact.file.exists()) {
            throw new GradleException("File `$artifact.file.path` does not exist!!!")
        }

        engine.processArtifact(artifact.file)
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
        println "setGenerateReport"
        this._generateReport = generateReport
    }

    CheckDuplicateClassesTask excludes(Iterable<String> excludes) {
        this.excludes.addAll(excludes)
        return this
    }

    CheckDuplicateClassesTask includes(String... includes) {
        this.includes.addAll(includes.collect())
        return this
    }

    CheckDuplicateClassesTask includes(Iterable<String> includes) {
        this.includes.addAll(includes)
        return this
    }

    CheckDuplicateClassesTask excludes(String... excludes) {
        this.excludes.addAll(excludes.collect())
        return this
    }

    CheckDuplicateClassesTask configurationsToCheck(Configuration... configurations) {
        this.configurationsToCheck.addAll(configurations.collect())
        return this
    }

    CheckDuplicateClassesTask configurationsToCheck(Iterable<Configuration> configurations) {
        this.configurationsToCheck.addAll(configurations.collect())
        return this
    }

    CheckDuplicateClassesTask configurationsToCheck(Configuration configuration) {
        this.configurationsToCheck.add(configuration)
        return this
    }


}
