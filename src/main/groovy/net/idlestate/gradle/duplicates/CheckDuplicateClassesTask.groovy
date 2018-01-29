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
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.VerificationTask

import java.util.zip.ZipFile

/**
 * Checks whether the artifacts of the configurations of the project contain the same classes.
 */
class CheckDuplicateClassesTask extends DefaultTask implements VerificationTask {
    private boolean _ignoreFailures = false

    @TaskAction
    void checkForDuplicateClasses() {
        final StringBuilder result = new StringBuilder()

        project.configurations.each { configuration ->
            if ( isConfigurationResolvable( configuration ) ) {
                logger.info( "Checking configuration '${configuration.name}'" )
                result.append( checkConfiguration( configuration ) )
            }
        }

        if ( result ) {
            String message = "There are conflicting files in the modules of the following configurations:${result.toString()}"

            if ( _ignoreFailures ) {
                logger.warn( message )
            } else {
                throw new GradleException( message )
            }
        }
    }

    String checkConfiguration( final Configuration configuration ) {
        Map<String, List> modulesByFile = [:].withDefault { key -> [] as Set }

        configuration.resolvedConfiguration.resolvedArtifacts.each { artifact ->
            logger.info( "    '${artifact.file.name}' of '${artifact.moduleVersion}'" )

            new ZipFile( artifact.file ).entries().each { entry ->
                if ( !entry.isDirectory() && !entry.name.startsWith( 'META-INF/' ) ) {
                    // logger.debug( "        ${entry.name}" )
                    final Set modules = modulesByFile.get( entry.name )
                    modules.add( artifact.moduleVersion.toString() )
                    modulesByFile.put( entry.name, modules )
                }
            }
        }

        Map duplicateFiles = modulesByFile.findAll { it.value.size() > 1 }
        if ( duplicateFiles ) {
            // logger.info( duplicateFiles.toString() )
            return "\n\n${configuration.name}\n${buildMessageWithUniqueModules( duplicateFiles.values() )}"
        }

        return ''
    }

    static String buildMessageWithUniqueModules(final Collection conflictingModules ) {
        List moduleMessages = []

        conflictingModules.each { modules ->
            String message = "    ${joinModules( modules )}"
            if ( !moduleMessages.contains( message ) ) {
                moduleMessages.add( message )
            }
        }

        return moduleMessages.join( '\n' )
    }

    static String joinModules( final Set modules ) {
        return modules.sort( { first, second ->
            ( first <=> second )
        } ).join( ', ' )
    }

    /**
     * Gradle 3.4 introduced the configuration 'apiElements' that isn't resolvable. So
     * we have to check before accessing it.
     */
    static boolean isConfigurationResolvable( configuration ) {
        if ( !configuration.metaClass.respondsTo( configuration, 'isCanBeResolved' ) ) {
            // If the recently introduced method 'isCanBeResolved' is unavailable, we
            // assume (for now) that the configuration can be resolved.
            return true
        }

        return configuration.isCanBeResolved()
    }

    @Override
    void setIgnoreFailures( final boolean ignoreFailures ) {
        _ignoreFailures = ignoreFailures
    }

    @Override
    boolean getIgnoreFailures() {
        return _ignoreFailures
    }
}
