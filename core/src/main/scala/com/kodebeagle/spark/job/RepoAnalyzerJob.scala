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
import com.kodebeagle.model.GithubRepo
import com.kodebeagle.spark.producer.{ScalaIndexProducer, JavaIndexProducer}
import com.kodebeagle.spark.util.SparkIndexJobHelper._
import org.apache.spark.{SerializableWritable, SparkConf}


/**
  * Created by sachint on 5/7/16.
  */
object RepoAnalyzerJob {

  def main(args: Array[String]): Unit = {
    val conf = new SparkConf()
      .setMaster(KodeBeagleConfig.sparkMaster)
      .setAppName("RepoAnalyzerJob")
    val sc = createSparkContext(conf)
    val rdd = sc.parallelize(Array("niesteszeck/idDHT11", "mrjoes/flask-babel","rmcastil/dotfiles"))

    // TODO: SerializableWritable is marked as developer API. (But then we are also developers ;) )
    val confBroadcast = sc.broadcast(new SerializableWritable(sc.hadoopConfiguration))
    val count = rdd.map(f => new GithubRepo(confBroadcast.value.value, f)).count()
    print("Count is >>>>>\n\n" + count + "\n\n")
  }
}
