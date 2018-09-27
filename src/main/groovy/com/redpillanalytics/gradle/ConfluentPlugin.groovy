package com.redpillanalytics.gradle

import com.redpillanalytics.gradle.containers.TaskGroupContainer
import com.redpillanalytics.gradle.tasks.CreateScriptsTask
import groovy.util.logging.Slf4j
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.UnknownConfigurationException
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.bundling.Zip

@Slf4j
class ConfluentPlugin implements Plugin<Project> {

   void apply(Project project) {

      // apply Gradle built-in plugins
      project.apply plugin: 'base'

      // apply the Gradle extension plugin and the context container
      applyExtension(project)

      project.afterEvaluate {

         // Go look for any -P properties that have "confluent." in them
         // If so... update the extension value
         project.ext.properties.each { key, value ->

            if (key =~ /confluent\./) {

               def (extension, property) = key.toString().split(/\./)

               //log.warn "Setting configuration property for extension: $extension, property: $property, value: $value"

               if (extension == 'confluent' && project.confluent.hasProperty(property)) {

                  log.warn "Setting configuration property for extension: $extension, property: $property, value: $value"

                  if (project.extensions.getByName(extension)."$property" instanceof Boolean) {

                     project.extensions.getByName(extension)."$property" = value.toBoolean()
                  } else if (project.extensions.getByName(extension)."$property" instanceof Integer) {

                     project.extensions.getByName(extension)."$property" = value.toInteger()
                  } else {

                     project.extensions.getByName(extension)."$property" = value
                  }
               }
            }
         }

         def dependencyMatching = { configuration, regexp ->

            try {
               return (project.configurations."$configuration".dependencies.find { it.name =~ regexp }) ?: false
            }
            catch (NullPointerException e) {
            }
         }

         def getDependency = { configuration, regexp ->

            return project.configurations."$configuration".find { File file -> file.absolutePath =~ regexp }
         }

         def isUsableConfiguration = { configuration, regexp ->

            try {

               if (getDependency(configuration, regexp)) {

                  return true
               } else {

                  return false
               }

            } catch (UnknownConfigurationException e) {

               return false
            }
         }

         // add task to show configurations
         project.task('showConfigurations') {

            group "help"

            doLast {
               project.configurations.each { config ->
                  log.info config.toString()
               }
            }
         }

         // get the taskGroup
         String taskGroup = project.extensions.confluent.taskGroup

         // get the location of the SQL source files
         File pipelineDir = project.file(project.extensions.confluent.getPipelinePath())
         log.debug "pipelineDir: ${pipelineDir.getCanonicalPath()}"

         File buildDir = project.file("${project.buildDir}/${project.extensions.confluent.pipelineBuildName}")
         log.debug "pipelineBuildDir: ${buildDir.canonicalPath}"

         File deployDir = project.file("${project.buildDir}/${project.extensions.confluent.pipelineDeployName}")
         log.debug "pipelineDeployDir: ${deployDir.canonicalPath}"

         String pipelineAppendix = project.extensions.confluent.pipelineAppendix
         log.debug "pipelineAppendix: ${pipelineAppendix}"

         // create deploy task
         project.task('deploy') {

            group taskGroup
            description "Calls all dependent deployment tasks."

         }

         // configure build groups
         project.confluent.taskGroups.all { tg ->

            if (tg.isBuildEnv()) {

               project.task(tg.getTaskName('createScripts'), type: CreateScriptsTask) {

                  group taskGroup
                  description('Build a single KSQL deployment script with all the individual pipeline processes ordered.'
                          + ' Primarily used for building a server start script.')

                  dirPath pipelineDir.canonicalPath

               }

               //project.build.dependsOn tg.getTaskName('deployScript')

               project.task(tg.getTaskName('pipelineZip'), type: Zip) {

                  group taskGroup
                  description('Build a distribution ZIP file with a single KSQL deployment script,'
                          + ' as well as all the individual pipeline SQL scripts that are included in it')
                  appendix = project.extensions.confluent.pipelineAppendix
                  includeEmptyDirs false

                  from buildDir

                  dependsOn tg.getTaskName('createScripts')

               }

               project.build.dependsOn tg.getTaskName('pipelineZip')

            }

            if (getDependency('archives', pipelineAppendix)) {

               project.task(tg.getTaskName('pipelineExtract'), type: Copy) {

                  group taskGroup
                  description = "Extract the deployment artifact into the deployment directory."

                  from project.zipTree(getDependency('archives', pipelineAppendix))
                  into { deployDir }

               }
            }

            project.deploy.dependsOn tg.getTaskName('pipelineExtract')

         }

         project.publishing.publications {

            pipeline(MavenPublication) {
               artifact project.pipelineZip {

                  artifactId project.archivesBaseName + '-' + pipelineAppendix

               }
            }
         }

      }

      // end of afterEvaluate
   }

   void applyExtension(Project project) {

      project.configure(project) {
         extensions.create('confluent', ConfluentPluginExtension)
      }

      project.confluent.extensions.taskGroups = project.container(TaskGroupContainer)

      project.extensions.confluent.taskGroups.add(new TaskGroupContainer(name: 'default'))

   }
}

