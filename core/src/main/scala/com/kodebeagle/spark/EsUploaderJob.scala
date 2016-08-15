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

import java.util.UUID

import com.kodebeagle.configuration.KodeBeagleConfig
import com.kodebeagle.logging.Logger
import com.kodebeagle.logging.LoggerUtils._
import com.kodebeagle.util.SparkIndexJobHelper._
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.fs.{FileSystem, Path}
import org.apache.hadoop.io.Text
import org.apache.hadoop.mapreduce.Job
import org.apache.hadoop.mapreduce.lib.input.FileInputFormat
import org.apache.spark.broadcast.Broadcast
import org.apache.spark.deploy.SparkHadoopUtil
import org.apache.spark.rdd.{RDD, SmartWholeTextFileRDD}
import org.apache.spark.serializer.KryoSerializer
import org.apache.spark.{SerializableWritable, SparkConf, SparkContext}

import scala.sys.process._

object EsUploaderJob extends Logger {


  def main(args: Array[String]) {

    val language = "Java"

    val conf = new SparkConf().setMaster(KodeBeagleConfig.sparkMaster).
      setAppName("EsUploaderJob").
      set("spark.serializer", classOf[KryoSerializer].getName).
      set("spark.kryoserializer.buffer.max", "128m").
      set("spark.executor.cores", "2")

    val sc = createSparkContext(conf)
    // val basePath = s"${KodeBeagleConfig.repoIndicesHdfsPath}/Java/"
    val basePath = s"${KodeBeagleConfig.repoIndicesHdfsPath}$language/"

    val javaMetaIndicesLoc = s"$basePath${KodeBeagleIndices.META}"
    val javaSourcesIndicesLoc = s"$basePath${KodeBeagleIndices.SOURCES}"
    val javaTokensIndicesLoc = s"$basePath${KodeBeagleIndices.TOKENS}"
    val javaTypesAggIndicesLoc = s"$basePath${KodeBeagleIndices.TYPE_AGG}"
    val javaRepoDetailsLoc = s"$basePath${KodeBeagleIndices.REPO_DETAILS}"
    val javaFileDetailsLoc = s"$basePath${KodeBeagleIndices.FILE_DETAILS}"
    val javaDocsLoc = s"$basePath${KodeBeagleIndices.DOCS}"

    val allIndicesPaths = Seq(javaMetaIndicesLoc, javaSourcesIndicesLoc, javaTokensIndicesLoc,
      javaTypesAggIndicesLoc, javaRepoDetailsLoc, javaFileDetailsLoc, javaDocsLoc).mkString(",")

    val confBroadcast = sc.broadcast(new SerializableWritable(sc.hadoopConfiguration))


    createRDD(sc, allIndicesPaths, 1024 * 1024 * 15L).foreachPartition(
      (filePathsItr: Iterator[String]) => uploadFilesToEs(confBroadcast, filePathsItr))

  }

  def createRDD(sc: SparkContext, path: String, maxSplitSize: Long):
  RDD[String] = {
    val job = new Job(sc.hadoopConfiguration)
    // Use setInputPaths so that wholeTextFiles aligns with hadoopFile/textFile in taking
    // comma separated files as input. (see SPARK-7155)
    FileInputFormat.setInputPaths(job, path)
    val updateConf = SparkHadoopUtil.get.getConfigurationFromJobContext(job)
    val rdd = new SmartWholeTextFileRDD(
      sc,
      classOf[LazyWholeTextFileInputFormat],
      classOf[Text],
      classOf[() => Text],
      updateConf,
      maxSplitSize).map(record => (record._1.toString)).setName(path)
    rdd
  }

  // scalastyle:off
  def uploadFilesToEs(confBroadcast: Broadcast[SerializableWritable[Configuration]],
                      filePathsItr: Iterator[String]): Unit = {
    val fs = FileSystem.get(confBroadcast.value.value)
    val r = scala.util.Random

    val nodes = KodeBeagleConfig.esNodes.split(",")

    val consolidatedFilePath = s"/tmp/kodebeagle-indices-${UUID.randomUUID()}"
    filePathsItr.foreach(file => {
      val pathElements = file.split("/")
      val relPath = s"${pathElements(pathElements.length-2)}/${pathElements(pathElements.length-1)}"
      val localFilePath = s"/tmp/kodebeagle/indices/$relPath"
      fs.copyToLocalFile(new Path(file), new Path(localFilePath))
      Seq("/bin/bash", "-c", s"cat $localFilePath >> $consolidatedFilePath").!!
      s"rm -f $localFilePath".!!
    })

    try {
      val node = nodes(r.nextInt(nodes.length)).trim
      val esUploadCmd =
        s"curl -s -XPOST '${node}/_bulk' --data-binary '@'$consolidatedFilePath >/dev/null"
      Seq("/bin/bash", "-c", esUploadCmd).!!
      s"rm -f $consolidatedFilePath".!!
    } catch {
      case e: Exception => {
        log.sparkInfo(s"ERROR: could not upload $consolidatedFilePath, skipping it.")
        throw e
      }
    }

  }
}

object KodeBeagleIndices {
  val META = "meta"
  val SOURCES = "sources"
  val TOKENS = "tokens"
  val TYPE_AGG = "types_aggregate"
  val FILE_DETAILS = "filedetails"
  val REPO_DETAILS = "repodetails"
  val DELETEINDICES = "deleteindices"
  val DOCS = "comments"
}
