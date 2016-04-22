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

package com.kodebeagle.spark.job

import com.kodebeagle.configuration.KodeBeagleConfig
import com.kodebeagle.spark.producer.{JavaIndexProducer, ScalaIndexProducer}
import com.kodebeagle.spark.util.SparkIndexJobHelper._
import org.apache.spark.SparkConf

object ConsolidatedIndexJob {

  val TYPEREFS = "typereferences"

  def main(args: Array[String]): Unit = {
    val batch = args(0)
    val conf = new SparkConf()
      .setMaster(KodeBeagleConfig.sparkMaster)
      .setAppName("ConsolidatedIndexJob")
    val sc = createSparkContext(conf)
    val rdd = makeRDD(sc, batch)
    val repoIndex = sc.broadcast(createRepoIndex(rdd, batch))
    val (javaRDD, scalaRDD) = (rdd.filter { case (_, file) => file._1.endsWith(".java") },
      rdd.filter { case (_, file) => file._1.endsWith(".scala") })
    JavaIndexProducer.createIndices(javaRDD, batch, repoIndex.value)
    ScalaIndexProducer.createIndices(scalaRDD, batch, repoIndex.value)
  }
}
