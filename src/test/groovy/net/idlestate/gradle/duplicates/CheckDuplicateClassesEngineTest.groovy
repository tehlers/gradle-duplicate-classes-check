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

import org.gradle.internal.impldep.org.junit.Test

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

import static net.idlestate.gradle.duplicates.CheckDuplicateClassesEngine.buildMessageWithUniqueModules
import static net.idlestate.gradle.duplicates.CheckDuplicateClassesEngine.concurrentMapCollector

class CheckDuplicateClassesEngineTest extends GroovyTestCase {

    private static TEST_1_EXPECTED = "\n" +
            "\n" +
            "test\n" +
            "    axiom-dom.jar, axiom-impl.jar\n" +
            "    geronimo-jta_1.1_spec-1.1.1.jar, javax.transaction-api-1.3.jar, jboss-transaction-api_1.2_spec-1.0.1.Final.jar\n" +
            "    geronimo-jta_1.1_spec-1.1.1.jar, jboss-transaction-api_1.2_spec-1.0.1.Final.jar\n" +
            "    jakarta.activation-api-1.2.1.jar, javax.activation-api-1.2.0.jar\n" +
            "    javax.transaction-api-1.3.jar, jboss-transaction-api_1.2_spec-1.0.1.Final.jar"

    @Test
    void testSearchForDuplicates() {
        def engine = new CheckDuplicateClassesEngine([] as List, [] as List)

        Path libsPath = testJarsPath()

        Map<String, Set> modulesByFile =
                processArtifacts(libsPath, engine)

        def result = CheckDuplicateClassesEngine.searchForDuplicates(modulesByFile, null)

        try {
            def string = "\n\ntest\n${buildMessageWithUniqueModules(result)}"
            println(string)
            assertEquals(TEST_1_EXPECTED, string)
        } catch (Exception e) {
            throw e
        }
    }

    private Path testJarsPath() {
        URL resourceUrl = getClass().getClassLoader().getResource("libs")

        Paths.get(resourceUrl.toURI())
    }

    private static String TEST_2_RESULT = "\n" +
            "\n" +
            "test\n" +
            "    axiom-dom.jar, axiom-impl.jar\n" +
            "    geronimo-jta_1.1_spec-1.1.1.jar, javax.transaction-api-1.3.jar, jboss-transaction-api_1.2_spec-1.0.1.Final.jar\n" +
            "    geronimo-jta_1.1_spec-1.1.1.jar, jboss-transaction-api_1.2_spec-1.0.1.Final.jar\n" +
            "    javax.transaction-api-1.3.jar, jboss-transaction-api_1.2_spec-1.0.1.Final.jar"

    @Test
    void testExcludeJarFiles() {
        def engine = new CheckDuplicateClassesEngine(['^.*(jakarta).*(.jar)$'], [] as List)
        Path libsPath = testJarsPath()

        Map<String, Set> modulesByFile = processArtifacts(libsPath, engine)

        def result = CheckDuplicateClassesEngine.searchForDuplicates(modulesByFile, null)
        try {
            def string = "\n\ntest\n${buildMessageWithUniqueModules(result)}"
            println(string)
            assertEquals(TEST_2_RESULT, string)
        } catch (Exception e) {
            throw e
        }
    }

    private static Map<String, Set<String>> processArtifacts(Path libsPath, engine) {
        Collection<File> artifactsPath = Files.walk(libsPath).map {
            it.toFile()
        }.findAll {
            it.isFile()
        }.collect()

        Map<String, Set<String>> filesByArtifact = artifactsPath.stream().flatMap { artifact ->
            engine.processArtifact(artifact).stream().
                    map { new FileToVersion(it, artifact.name) }
        }.collect(concurrentMapCollector())

        filesByArtifact.entrySet().stream().flatMap { es ->
            es.value.stream().map {
                new FileToVersion(es.key, it)
            }
        }.collect(concurrentMapCollector())
    }
}
