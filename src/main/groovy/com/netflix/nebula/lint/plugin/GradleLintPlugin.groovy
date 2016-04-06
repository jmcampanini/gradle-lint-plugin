/*
 * Copyright 2015-2016 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.netflix.nebula.lint.plugin

import org.gradle.api.Plugin
import org.gradle.api.Project

class GradleLintPlugin implements Plugin<Project> {
    private final exemptTasks = ['help', 'tasks', 'dependencies', 'dependencyInsight',
        'components', 'model', 'projects', 'properties', 'fixGradleLint']

    @Override
    void apply(Project project) {
        LintRuleRegistry.classLoader = getClass().classLoader
        def lintExt = project.extensions.create('gradleLint', GradleLintExtension)

        if (project.rootProject == project) {
            project.tasks.create('fixGradleLint', GradleLintCorrectionTask)
            project.tasks.create('fixLintGradle', GradleLintCorrectionTask)
            project.tasks.create('lintGradle', GradleLintTask)
        } else {
            project.rootProject.apply plugin: GradleLintPlugin
            project.tasks.create('lintGradle').finalizedBy project.rootProject.tasks.getByName('lintGradle')
            project.tasks.create('fixGradleLint').finalizedBy project.rootProject.tasks.getByName('fixGradleLint')
            project.tasks.create('fixLintGradle').finalizedBy project.rootProject.tasks.getByName('fixGradleLint')
        }

        configureReportTask(project, lintExt)

        // ensure that lint runs
        project.tasks.whenTaskAdded { task ->
            def rootLint = project.rootProject.tasks.getByName('lintGradle')
            if (task != rootLint && !exemptTasks.contains(task.name)) {
                // when running a lint-eligible task on a subproject, we want to lint the whole project
                task.finalizedBy rootLint

                // because technically you can override path in a Gradle task implementation and cause path to be null!
                if(task.getPath() != null) {
                    try {
                        rootLint.shouldRunAfter task
                    } catch(Throwable t) {
                        // just quietly DON'T add rootLint to run after this task, it will probably still run because
                        // it will be hung on some other task as a shouldRunAfter
                    }
                }
            }
        }
    }

    private void configureReportTask(Project project, GradleLintExtension extension) {
        def task = project.tasks.create('generateGradleLintReport', GradleLintReportTask)
        task.reports.all { report ->
            report.conventionMapping.with {
                enabled = { report.name == extension.reportFormat }
                destination = {
                    def fileSuffix = report.name == 'text' ? 'txt' : report.name
                    new File(project.buildDir, "reports/gradleLint/${project.name}.$fileSuffix")
                }
            }
        }
    }
}
