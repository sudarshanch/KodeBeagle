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

package com.kodebeagle.spark

import com.kodebeagle.configuration.KodeBeagleConfig
import com.kodebeagle.indexer.{TypeAggregator, TypesInFile}
import com.kodebeagle.logging.Logger
import com.kodebeagle.util.SparkIndexJobHelper._
import org.apache.spark.SparkConf
import org.json4s.NoTypeHints
import org.json4s.jackson.Serialization
import org.json4s.jackson.Serialization._

import scala.util.Try


object TypeAggregationJob extends Logger {
  implicit val formats = Serialization.formats(NoTypeHints)

  def main(args: Array[String]) {
    val conf = new SparkConf()
      .setMaster(KodeBeagleConfig.sparkMaster)
      .setAppName("TypeAggregation")
      .set("spark.serializer", "org.apache.spark.serializer.KryoSerializer")
    val sc = createSparkContext(conf)
    sc.hadoopConfiguration.set("dfs.replication", "1")

    val typesInfoLocation = Try {
      args(0).trim
    }.toOption

    sc.textFile(typesInfoLocation.getOrElse(KodeBeagleConfig.typesInfoLocation))
      .flatMap(f => {
        f.trim.isEmpty match {
          case true => None
          case false => Try {
            try {
              read[TypesInFile](f)
            } catch {
              case e: Throwable => log.error(s"Could not read line ${f}"); throw e
            }
          }.toOption
        }
      }).flatMap(f => {
      val dTypes = f.declaredTypes.mapValues((Set.empty[String], _, f.repoName, f.fileName)).toSeq
      val uTypes = f.usedTypes.mapValues(v => (v._1, v._2, f.repoName, f.fileName)).toSeq
      dTypes ++ uTypes
    }).aggregateByKey(new TypeAggregator())(
      (agg, value) => agg.merge(value._1, value._2, value._3, value._4),
      (agg1, agg2) => agg1.merge(agg2))
      .map(agg => toIndexTypeJson("java", "aggregation", agg._2.result(agg._1)))
      .saveAsTextFile(s"${KodeBeagleConfig.repoIndicesHdfsPath}Java/types_aggregate")
  }
}
