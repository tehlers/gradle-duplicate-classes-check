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
import org.gradle.api.logging.LogLevel
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask

/**
 * Checks whether the artifacts of the configurations of the project contain the same classes.
 */
class CheckDuplicateClassesTask extends DefaultTask implements VerificationTask {

    private boolean _ignoreFailures = false

    @Internal
    private List<String> excludes = [] as List

    @Internal
    private List<String> includes = [] as List

    CheckDuplicateClassesTask() {
    }

    @TaskAction
    void checkForDuplicateClasses() {
        final StringBuilder result = new StringBuilder()

        def engine = new CheckDuplicateClassesEngine(excludes, includes)

        project.configurations.each { configuration ->
            if (isConfigurationResolvable(configuration)) {
                logger.info("Checking configuration '${configuration.name}'")
                result.append(checkConfiguration(configuration, engine))
            }
        }

        if (result) {
            final StringBuilder message = new StringBuilder('There are conflicting files in the modules of the following configurations')

            if (project.gradle.startParameter.logLevel.compareTo(LogLevel.INFO) > 0) {
                message.append(' (add --info for details)')
            }

            message.append(":${result.toString()}")

            if (_ignoreFailures) {
                logger.warn(message.toString())
            } else {
                throw new GradleException(message.toString())
            }
        }
    }

    String checkConfiguration(final Configuration configuration, def engine) {
        Map<String, Set> modulesByFile = [:].withDefault { key -> [] as Set }

        configuration.resolvedConfiguration.resolvedArtifacts.each { artifact ->
            logger.info("    '${artifact.file.path}' of '${artifact.moduleVersion}'")

            engine.processArtifact(artifact.file, artifact.moduleVersion.toString(), modulesByFile)
        }

        return engine.searchForDuplicates(modulesByFile, configuration.name, { logger.info(it) })
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

    CheckDuplicateClassesTask excludes(Iterable<String> excludes) {
        this.excludes.addAll(excludes)
        return this;
    }

    CheckDuplicateClassesTask includes(String... includes) {
        this.includes.addAll(includes.collect())
        return this;
    }

    CheckDuplicateClassesTask includes(Iterable<String> includes) {
        this.includes.addAll(includes)
        return this;
    }

    CheckDuplicateClassesTask excludes(String... excludes) {
        this.excludes.addAll(excludes.collect())
        return this;
    }
}
