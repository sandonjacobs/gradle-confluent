package com.redpillanalytics.gradle.containers

import groovy.util.logging.Slf4j

@Slf4j
class TaskGroupContainer {

   // Build Group defaults
   private static final String DEFAULT_GROUP = 'default'

   /**
    * The name of the container entity.
    */
   String name

   String groupName = 'confluent'

   Boolean deployOnly = false

   // capture the debug status
   Boolean isDebugEnabled = log.isDebugEnabled()

   def isBuildEnv() {

      return !isDeployOnly()
   }

   def isDeployOnly() {

      return deployOnly
   }

   def getDomainName() {

      return ((getClass() =~ /\w+$/)[0] - "Container")
   }

   def logTaskName(String task) {

      log.debug "${getDomainName()}: $name, TaskName: $task"

   }

   def isDefaultTask(String buildName) {

      return (buildName == DEFAULT_GROUP) ? true : false

   }

   def getTaskName(String baseTaskName) {

      // return either the baseTaskName or prepend with a name
      String taskName = isDefaultTask(getName()) ? baseTaskName : getName() + baseTaskName.capitalize()

      logTaskName(taskName)

      return taskName


   }

}
