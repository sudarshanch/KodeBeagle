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

package org.apache.spark.rdd

import com.kodebeagle.logging.LoggerUtils._
import com.kodebeagle.spark.{Configurable, LazyWholeTextFileInputFormat}
import org.apache.hadoop.conf.Configuration
import org.apache.hadoop.io.{Text, Writable}
import org.apache.hadoop.mapreduce.InputSplit
import org.apache.spark._


class SmartWholeTextFileRDD(
                             sc: SparkContext,
                             inputFormatClass: Class[_ <: LazyWholeTextFileInputFormat],
                             keyClass: Class[Text],
                             valueClass: Class[() => Text],
                             conf: Configuration,
                             maxPartitionSize: Long)
  extends NewHadoopRDD[Text, () => Text](sc, inputFormatClass, keyClass, valueClass, conf) {

  override def getPartitions: Array[Partition] = {
    val inputFormat = inputFormatClass.newInstance
    val conf = getConf
    inputFormat match {
      case configurable: Configurable =>
        configurable.setConf(conf)
      case _ =>
    }
    val jobContext = newJobContext(conf, jobId)
    inputFormat.setMaxSplitSize(maxPartitionSize)
    val rawSplits = inputFormat.getSplits(jobContext).toArray
    val result = new Array[Partition](rawSplits.size)
    for (i <- 0 until rawSplits.size) {
      result(i) = new NewHadoopPartition(id, i, rawSplits(i).asInstanceOf[InputSplit with Writable])
    }
    result
  }

  override def compute(theSplit: Partition, context: TaskContext):
  InterruptibleIterator[(Text, () => Text)] = {
    val partition = theSplit.asInstanceOf[NewHadoopPartition]
    log.sparkInfo(s"Input split " +
      s"[Length: ${partition.serializableHadoopSplit.value.getLength}]" +
      s"[Locations: ${partition.serializableHadoopSplit.value.getLocations}]"
      + partition.serializableHadoopSplit)
    super.compute(theSplit, context)
  }
}
