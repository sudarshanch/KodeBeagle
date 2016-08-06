/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.kodebeagle.logging

import org.apache.spark.TaskContext
import org.slf4j.LoggerFactory


trait Logger {
  val log = LoggerFactory.getLogger(this.getClass.getName)
}

object LoggerUtils {
  implicit  class SparkLogger(log: org.slf4j.Logger) extends {

    private def appendContextInfo(msg: String) = {
      val maybeContext = Option(TaskContext.get())
      maybeContext match {
        case Some(c) => s"Task[${c.taskAttemptId()}.${c.attemptNumber()}] ${msg}"
        case None => msg
      }
    }

    def sparkInfo(msg: String): Unit = log.info(appendContextInfo(msg))
  }
}
