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

import org.apache.hadoop.fs.Path
import org.apache.hadoop.io.Text
import org.apache.hadoop.mapreduce.lib.input.CombineFileInputFormat
import org.apache.hadoop.mapreduce.{InputSplit, JobContext, RecordReader, TaskAttemptContext}

class LazyWholeTextFileInputFormat
  extends CombineFileInputFormat[Text, () => Text] with Configurable {

  override protected def isSplitable(context: JobContext, file: Path): Boolean = false

  override def createRecordReader(
                                   split: InputSplit,
                                   context: TaskAttemptContext): RecordReader[Text, () => Text] = {
    val reader =
      new LazyCombineFileRecordReader(split, context,
        classOf[LazyWholeTextFileRecordReader])
    reader.setConf(getConf)
    reader
  }

  override def setMaxSplitSize(maxSplitSize: Long) {
    super.setMaxSplitSize(maxSplitSize)
  }

}

