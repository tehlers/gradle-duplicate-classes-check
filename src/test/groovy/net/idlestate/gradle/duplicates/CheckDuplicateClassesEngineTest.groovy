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

class CheckDuplicateClassesEngineTest extends GroovyTestCase {

    @Test
    void testSearchForDuplicates() {
        def engine = new CheckDuplicateClassesEngine([] as List, [] as List)

        Path libsPath = testJarsPath()

        Map<String, Set> modulesByFile =
                processArtifacts(libsPath, engine)

        def result = CheckDuplicateClassesEngine.searchForDuplicates(modulesByFile, "test", null)

        try {
            println(result)
            assertEquals(-1949525080, result.hashCode())
        } catch (Exception e) {
            throw e
        }
    }

    private Path testJarsPath() {
        URL resourceUrl = getClass().getClassLoader().getResource("libs")

        Paths.get(resourceUrl.toURI())
    }

    @Test
    void testExcludeJarFiles() {
        def engine = new CheckDuplicateClassesEngine(['^.*(jakarta).*(.jar)$'], [] as List)
        Path libsPath = testJarsPath()

        Map<String, Set> modulesByFile = processArtifacts(libsPath, engine)

        def result = CheckDuplicateClassesEngine.searchForDuplicates(modulesByFile, "test", null)
        try {
            println(result)
            assertEquals(1023966719, result.hashCode())
        } catch (Exception e) {
            throw e
        }
    }

    private static Map<String, Set> processArtifacts(Path libsPath, engine) {
        Collection<File> artifactsPath = Files.walk(libsPath).map {
            it.toFile()
        }.findAll {
            it.isFile()
        }.collect()

        artifactsPath.stream().flatMap { jarFile ->
            engine.processArtifact(jarFile).stream().
                    map {new FileToVersion(it, jarFile.name) }
        }.collect(CheckDuplicateClassesEngine.concurrentMapCollector())
    }
}
