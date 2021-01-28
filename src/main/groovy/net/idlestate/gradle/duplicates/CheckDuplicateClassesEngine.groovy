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

import java.nio.file.Path
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

    static Collection<Set<String>> searchForDuplicates(Map<String, Set<String>> jarsByClass, Consumer<String> detailedInfo) {
        Map<String, Set<String>> duplicateJarsByClass = jarsByClass.findAll { it.value.size() > 1 }
        jarsByClass.clear()

        if (!duplicateJarsByClass) {
            return Collections.EMPTY_LIST
        }

        if (detailedInfo != null) {
            detailedInfo.accept(buildMessageWithConflictingClasses(duplicateJarsByClass))
        }

        new HashSet<>(duplicateJarsByClass.values())
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

    static void writeReportFiles(Path path, Map<String, String> reportMap) {
        reportMap.keySet().forEach { name ->
            new FileWriter(path.resolve(name).toFile()).with { writer ->
                writer.write(reportMap.get(name))
                writer.flush()
                writer.close()
            }
        }

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
            String jars = entry.getValue().join(', ')
            List values = conflictingClasses.get(jars)
            values.add(entry.getKey())

            [jars, values]
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

    static Map<String, String> buildReport(Map<String, Set<String>> classesByArtifactMap, Collection<List<String>> jarsWithDuplicateClasses) {
        Map<String, String> result = [:]

        def indexTemplate = readTemplateResource("index.html")

        if (jarsWithDuplicateClasses.isEmpty()) {
            def indexHtml = indexTemplate.replace("_____files rows_____", "There are no JAR files with duplicate classes.")
            result.put("index.html", indexHtml)
            return result
        }

        StringBuilder indexHtml = new StringBuilder()
        Map<String, List<String>> duplicateMap = jarsWithDuplicateClasses.stream().filter {
            it.size() > 1
        }.flatMap {
            def tempFileName = "duplicate_classes" + it.hashCode() + ".html"
            indexHtml.append('<li>').append('<a href="').append(tempFileName).append('">').append(it.stream().collect(Collectors.joining(","))).append('</a></li><br/>\n')
            Collections.singletonMap(tempFileName, it).entrySet().stream()
        }.collect(Collectors.toMap({
            it.getKey()
        }, {
            it.getValue()
        }))

        duplicateMap.keySet().forEach {
            result.put(it, generateDuplicateClassesReport(new ArrayList<String>(duplicateMap.get(it)), classesByArtifactMap))
        }

        result.put("index.html", indexTemplate.replace("_____files rows_____", indexHtml.toString()))

        result
    }

    static String generateDuplicateClassesReport(List<String> jarFiles, Map<String, Set<String>> jarFileContent) {
        def allFiles = jarFiles.stream().flatMap {
            jarFileContent.get(it).stream()
        }.collect(Collectors.toSet())

        allFiles = allFiles.sort()
        jarFiles = jarFiles.sort()

        String htmlTemplate = readTemplateResource("detail.html")

        htmlTemplate = buildTableHeader(jarFiles, htmlTemplate)

        StringBuilder classesRows = new StringBuilder()
        allFiles.forEach { file ->
            classesRows.append("<tr>").append("<td>").append(file).append("</td>")
            jarFiles.forEach {
                def value = jarFileContent.get(it).contains(file) ? "*" : "-"
                classesRows.append("<td class=\"present\">").append(value).append("</td>")
            }
            classesRows.append("</tr>")
        }

        htmlTemplate.replace("____class file names____", classesRows.toString())
    }

    private static String readTemplateResource(def name) {
        new BufferedReader(new InputStreamReader(CheckDuplicateClassesEngine.class.getResourceAsStream(name))).with {
            it.readLines().stream().collect(Collectors.joining(System.lineSeparator()))
        }
    }

    private static String buildTableHeader(List<String> jarFiles, String htmlTemplate) {
        StringBuilder tableHead = new StringBuilder("<td>Path</td>")
        jarFiles.forEach {
            tableHead.append("<td>").append(it).append("</td>")
        }

        htmlTemplate.replace("____jar file names____", tableHead.toString())
    }

}
