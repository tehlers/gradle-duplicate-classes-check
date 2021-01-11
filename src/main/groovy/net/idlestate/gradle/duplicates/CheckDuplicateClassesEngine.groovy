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


import java.util.function.Consumer
import java.util.regex.Pattern
import java.util.stream.Collector
import java.util.stream.Collectors
import java.util.zip.ZipFile

class CheckDuplicateClassesEngine {
    private static final List<String> defaultExclude =
            Arrays.asList('^(META-INF/).*',
                    '^(OSGI-INF/).*',
                    '^(module-info.class)$',
                    '^.*(.html)$',
                    '^.*(.htm)$',
                    '^.*(.txt)$',
                    '^.*(.doc)$',
                    '^(README).*')

    private Pattern excludePattern

    private Pattern includePattern

    CheckDuplicateClassesEngine(List<String> excludes, List<String> includes) {
        excludes.addAll(defaultExclude)

        excludePattern = ~excludes.join('|')
        includePattern = includes.isEmpty() ? ~'.*' : ~includes.join('|')
    }

    static boolean isValidEntry(final entry, final excludePattern, final includePattern) {
        if (entry.isDirectory()) {
            return false
        }

        if (excludePattern.matcher(entry.name).find()) {
            return false
        }

        return includePattern.matcher(entry.name).find()
    }

    static Collection<Set<String>> searchForDuplicates(Map<String, Set<String>> modulesByFile, Consumer<String> detailedInfo) {
        Map<String, Set<String>> duplicateFiles = modulesByFile.findAll { it.value.size() > 1 }
        modulesByFile.clear()

        if (!duplicateFiles) {
            return Collections.EMPTY_LIST
        }

        if (detailedInfo != null) {
            detailedInfo.accept(buildMessageWithConflictingClasses(duplicateFiles))
        }

        duplicateFiles.values()
    }

    @SuppressWarnings('GroovyAssignabilityCheck')
    static Collector<FileToVersion, ?, Map<String, Set<String>>> concurrentMapCollector() {
        Collectors.toMap({ FileToVersion it -> it.version },
                { FileToVersion it -> Collections.singleton(it.file) },
                { Set<String> a, Set<String> b ->
                    a.addAll(b)
                    return a
                }, { Collections.synchronizedMap([:].withDefault { key -> [] as Set<String> }) })
    }

    Collection<String> processArtifact(File artifactFile) {
        if (!isValidEntry(artifactFile, excludePattern, includePattern)) {
            return Collections.emptySet()
        }

        new ZipFile(artifactFile).entries().findAll { isValidEntry(it, excludePattern, includePattern) }.
                collect { it.name }
    }

    static String buildMessageWithConflictingClasses(final Map<String, Set> duplicateFiles) {
        Map<String, List<String>> conflictingClasses = [:].withDefault { key -> [] }

        duplicateFiles.collectEntries(conflictingClasses) { entry ->
            String key = entry.getValue().join(', ')
            List values = conflictingClasses.get(key)
            values.add(entry.getKey())

            [key, values]
        }

        final StringBuilder message = new StringBuilder()
        conflictingClasses.each { entry ->
            message.append("\n    Found duplicate classes in ${entry.key}:\n        ${entry.value.join('\n        ')}")
        }

        return message.toString()
    }

    static String buildMessageWithUniqueModules(final Collection<Set<String>> conflictingModules) {
        List moduleMessages = []

        conflictingModules.each { modules ->
            String message = "    ${joinModules(modules)}"
            if (!moduleMessages.contains(message)) {
                moduleMessages.add(message)
            }
        }

        return moduleMessages.join('\n')
    }

    static String joinModules(final Set<String> modules) {
        return modules.sort({ first, second ->
            (first <=> second)
        }).join(', ')
    }

}
