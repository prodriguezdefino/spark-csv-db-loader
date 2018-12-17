package org.tests

import java.util.Properties
import scala.util.{Success, Failure}
import org.apache.log4j.{LogManager, Logger}
import org.apache.spark.SparkConf
import org.apache.spark.sql.execution.datasources.jdbc.JDBCOptions
import org.apache.spark.sql._
import com.typesafe.config._

import scala.util.Try

object FromCSVtoDB {

  val log: Logger = LogManager.getRootLogger

  /**
    * Defines an read DataFrame based on the configured location and source file expected type.
    * Based on the repartition configuration entries will force to the expected number.
    *
    * @return DataFrame pointing to the configured location.
    */
  def readDF(spark: SparkSession, config: Config): Try[DataFrame] = {
    Try {
      var extractDFReader = spark
        .read
        .format(config.getString("source.format"))
        .option("mode", "DROPMALFORMED")
        .option("header", "true")

      Some(config.getBoolean("source.compression"))
        .filter(v => v)
        .map(_ => extractDFReader.option("compression", config.getString("source.compressionFormat")))

      val extractDF = extractDFReader.load(config.getString("source.location"))

      val minPartitions = config.getInt("source.numPartitions")
      val readPartitions = extractDF.rdd.getNumPartitions
      log.info(s"Read Dataframe Partition Count: $readPartitions")

      if (config.hasPath("source.sourceDFRepartition") && config.getBoolean("source.sourceDFRepartition")) {
        if (readPartitions > minPartitions) {
          log.info(s"Coalescing the number of the partitions from $readPartitions to $minPartitions")
          extractDF.coalesce(minPartitions)
        } else if (readPartitions < minPartitions) {
          log.warn(s"Repartitioning from $readPartitions to $minPartitions")
          extractDF.repartition(minPartitions)
        }
      }
      extractDF
    }
  }

  /**
    * Function to write data into a JDBC datasource
    *
    * @return Try computation for producing a Unit result.
    */
  def writeFn(sourceDF: DataFrame, config: Config): Try[Unit] = {

    val destConnectionProps = new Properties()
    destConnectionProps.put("user", config.getString("target.user"))
    destConnectionProps.put("password", config.getString("target.password"))
    destConnectionProps.put("driver", config.getString("target.driver"))

    Try(sourceDF.write
      .option(JDBCOptions.JDBC_DRIVER_CLASS, config.getString("target.driver"))
      .option(JDBCOptions.JDBC_TABLE_NAME, config.getString("target.table"))
      .option(JDBCOptions.JDBC_BATCH_INSERT_SIZE, config.getInt("target.batchInsertSize"))
      .mode(SaveMode.Append)
      .jdbc(config.getString("target.jdbcUrl"), config.getString("target.table"), destConnectionProps))
  }

  def main(args: Array[String]): Unit = {

    //Initialize Spark Session
    val sparkConf = new SparkConf()

    val spark = SparkSession
      .builder()
      .config(sparkConf)
      .getOrCreate()

    // Load Config JSON File from the location provides as the first argument
    ConfigFactory.invalidateCaches()

    val configFile = args(0)

    val config = ConfigFactory
      .parseString(spark
        .read
        .json(configFile)
        .toJSON
        .collect()(0))

    config.resolve()

    // Load JDBC Drivers
    Class.forName(config.getString("target.driver")).newInstance

    readDF(spark, config) match {
      case Success(df) => writeFn(df, config) recover {
        case t => log.error("Error while trying to write loaded data.", t)
      }
      case Failure(t) => log.error("Error while trying to read data from source.", t)
    }

    spark.stop()
  }
}
