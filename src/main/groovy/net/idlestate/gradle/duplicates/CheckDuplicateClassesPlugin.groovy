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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.language.base.plugins.LifecycleBasePlugin

/**
 * A plugin the provides a task that checks whether there are modules that provide the same classes.
 */
class CheckDuplicateClassesPlugin implements Plugin<Project> {

    @Override
    void apply(final Project project) {
        final Task checkForDuplicateClasses = project.task(
                'checkForDuplicateClasses',
                type: CheckDuplicateClassesTask,
                group: LifecycleBasePlugin.VERIFICATION_GROUP,
                description: 'Checks whether there are modules that provide the same classes.'
        )

        project.getTasksByName( LifecycleBasePlugin.CHECK_TASK_NAME, false )*.dependsOn checkForDuplicateClasses
    }
}
