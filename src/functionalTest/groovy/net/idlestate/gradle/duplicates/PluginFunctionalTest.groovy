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

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.UnexpectedBuildFailure
import org.junit.Assert
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

import static org.gradle.internal.impldep.junit.framework.Assert.assertFalse
import static org.gradle.internal.impldep.junit.framework.Assert.assertTrue
import static org.gradle.internal.impldep.junit.framework.Assert.fail

class PluginFunctionalTest {

    @Rule
    public TemporaryFolder testProjectDir = new TemporaryFolder()
    File buildFile

    def expectedLines = [
            "com.fasterxml.woodstox:woodstox-core:5.1.0, org.codehaus.woodstox:woodstox-core-asl:4.2.0",
            "org.apache.cassandra:cassandra-all:1.2.11, org.apache.cassandra:cassandra-thrift:1.2.11",
            "com.sun.xml.bind:jaxb-core:2.3.0, org.glassfish.jaxb:jaxb-runtime:2.3.2",
            "com.sun.xml.bind:jaxb-impl:2.3.0, org.glassfish.jaxb:jaxb-runtime:2.3.2",
            "jakarta.xml.bind:jakarta.xml.bind-api:2.3.2, javax.xml.bind:jaxb-api:2.3.0",
            "com.sun.istack:istack-commons-runtime:3.0.8, com.sun.xml.bind:jaxb-core:2.3.0",
            "com.sun.xml.bind:jaxb-core:2.3.0, org.glassfish.jaxb:txw2:2.3.2",
            "com.sun.istack:istack-commons-runtime:3.0.8, com.sun.xml.bind:jaxb-xjc:2.3.1",
            "org.apache.logging.log4j:log4j-slf4j-impl:2.11.2, org.slf4j:slf4j-log4j12:1.7.21",
            "jakarta.jws:jakarta.jws-api:1.1.1, org.apache.geronimo.specs:geronimo-ws-metadata_2.0_spec:1.1.2",
            "jakarta.persistence:jakarta.persistence-api:2.2.3, org.hibernate.javax.persistence:hibernate-jpa-2.1-api:1.0.0.Final",
            "org.w3c.css:sac:1.3, xml-apis:xml-apis-ext:1.3.04",
            "javax.transaction:javax.transaction-api:1.3, org.apache.geronimo.specs:geronimo-jta_1.1_spec:1.1.1, org.jboss.spec.javax.transaction:jboss-transaction-api_1.2_spec:1.0.1.Final",
            "javax.transaction:javax.transaction-api:1.3, org.jboss.spec.javax.transaction:jboss-transaction-api_1.2_spec:1.0.1.Final",
            "org.apache.geronimo.specs:geronimo-jta_1.1_spec:1.1.1, org.jboss.spec.javax.transaction:jboss-transaction-api_1.2_spec:1.0.1.Final",
            "commons-logging:commons-logging:1.2, org.slf4j:jcl-over-slf4j:1.7.26, org.springframework:spring-jcl:5.2.8.RELEASE",
            "commons-logging:commons-logging:1.2, org.slf4j:jcl-over-slf4j:1.7.26",
            "commons-logging:commons-logging:1.2, org.springframework:spring-jcl:5.2.8.RELEASE",
            "javax.servlet:servlet-api:2.5, org.mortbay.jetty:servlet-api:2.5-20081211",]

    @Before
    void setup() {
        buildFile = testProjectDir.newFile('build.gradle')
        buildFile << """
            plugins {
                id 'net.idlestate.gradle-duplicate-classes-check'
            }
            
            apply plugin: 'java'
        """
    }

    void appendDependencies() {
        buildFile << """
         repositories {
            mavenLocal()
            mavenCentral()
         }

        dependencies {
          compile(
            "com.fasterxml.woodstox:woodstox-core:5.1.0",
            "org.codehaus.woodstox:woodstox-core-asl:4.2.0",
            "org.apache.cassandra:cassandra-all:1.2.11",
            "org.apache.cassandra:cassandra-thrift:1.2.11",
            "jakarta.xml.bind:jakarta.xml.bind-api:2.3.2",
            "javax.xml.bind:jaxb-api:2.3.0",
            "com.sun.xml.bind:jaxb-core:2.3.0",
            "org.glassfish.jaxb:jaxb-runtime:2.3.2",
            "com.sun.istack:istack-commons-runtime:3.0.8",
            "com.sun.xml.bind:jaxb-core:2.3.0",
            "org.glassfish.jaxb:txw2:2.3.2",
            "com.sun.xml.bind:jaxb-impl:2.3.0",
            "org.apache.logging.log4j:log4j-slf4j-impl:2.11.2",
            "org.slf4j:slf4j-log4j12:1.7.21",
            "com.sun.xml.bind:jaxb-xjc:2.3.1",
            "jakarta.jws:jakarta.jws-api:1.1.1",
            "org.apache.geronimo.specs:geronimo-ws-metadata_2.0_spec:1.1.2",
            "jakarta.persistence:jakarta.persistence-api:2.2.3",
            "org.hibernate.javax.persistence:hibernate-jpa-2.1-api:1.0.0.Final",
            "org.w3c.css:sac:1.3",
            "xml-apis:xml-apis-ext:1.3.04",
            "javax.transaction:javax.transaction-api:1.3",
            "org.apache.geronimo.specs:geronimo-jta_1.1_spec:1.1.1",
            "org.jboss.spec.javax.transaction:jboss-transaction-api_1.2_spec:1.0.1.Final",
            "commons-logging:commons-logging:1.2",
            "org.slf4j:jcl-over-slf4j:1.7.26",
            "org.springframework:spring-jcl:5.2.8.RELEASE"
          )
        }
        """
    }

    @Test
    void runWithIgnoreFailuresTrue() {
        appendDependencies()
        buildFile << """
        checkForDuplicateClasses {
            excludes('^.*(axiom-).*(.jar)\$', '^.*(RSSecrets).*(.jar)\$')

            configurationsToCheck(configurations.compile)

            ignoreFailures = true
        }
        """

        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('checkForDuplicateClasses')
                .withPluginClasspath()
                .build()

        checkForExpectedLines(result.output)

        assertFalse("Report directory should not be present", testProjectDir.root.toPath().resolve("report").toFile().exists())
    }

    @Test
    void runWithIgnoreFailuresFalse() {
        appendDependencies()
        buildFile << """
        checkForDuplicateClasses {
            excludes('^.*(axiom-).*(.jar)\$', '^.*(RSSecrets).*(.jar)\$')

            configurationsToCheck(configurations.compile)

            ignoreFailures = false
        }
        """

        try {
            GradleRunner.create()
                    .withProjectDir(testProjectDir.root)
                    .withArguments('checkForDuplicateClasses')
                    .withPluginClasspath()
                    .build()
            fail()
        } catch (UnexpectedBuildFailure ge) {
            checkForExpectedLines(ge.message)
        }
    }

    private void checkForExpectedLines(def message) {
        for (def expected : expectedLines) {
            Assert.assertTrue(message.contains(expected))
        }
    }

    @Test
    void runWithReport() {
        appendDependencies()
        buildFile << """
        checkForDuplicateClasses {
            excludes('^.*(axiom-).*(.jar)\$', '^.*(RSSecrets).*(.jar)\$')

            configurationsToCheck(configurations.compile)

            ignoreFailures = true
            generateReport = true
        }
        """

        def result = GradleRunner.create()
                .withProjectDir(testProjectDir.root)
                .withArguments('checkForDuplicateClasses')
                .withPluginClasspath()
                .build()

        checkForExpectedLines(result.output)

        println result.output

        assertTrue("Report directory is not present", testProjectDir.root.toPath().resolve("build").resolve("report").toFile().exists())
    }
}
