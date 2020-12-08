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

        URL resourceUrl = getClass().getClassLoader().getResource("libs")

        Path libsPath = Paths.get(resourceUrl.toURI())

        Map<String, Set> modulesByFile = [:].withDefault { key -> [] as Set }
        Files.walk(libsPath).map {
            it.toFile()
        }.filter {
            it.isFile()
        }.each {
            engine.processArtifact(it, it.name, modulesByFile)
        }

        def result = CheckDuplicateClassesEngine.searchForDuplicates(modulesByFile, "test", {  })

        try {
            println(result)
            assertEquals(-1949525080, result.hashCode())
        } catch (Exception e) {
            throw e;
        }
    }

    @Test
    void testExcludeJarFiles() {
        def engine = new CheckDuplicateClassesEngine(['^.*(jakarta).*(.jar)$'], [] as List)

        URL resourceUrl = getClass().getClassLoader().getResource("libs")

        Path libsPath = Paths.get(resourceUrl.toURI())

        Map<String, Set> modulesByFile = [:].withDefault { key -> [] as Set }
        Files.walk(libsPath).map {
            it.toFile()
        }.filter {
            it.isFile()
        }.each {
            engine.processArtifact(it, it.name, modulesByFile)
        }

        def result = CheckDuplicateClassesEngine.searchForDuplicates(modulesByFile, "test", { })
        try {
            println(result)
            assertEquals(1023966719, result.hashCode())
        } catch (Exception e) {
            throw e;
        }
    }
}
