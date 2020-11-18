package controllers

import javax.inject.{Inject, Singleton}
import org.apache.spark.{SparkConf, SparkContext}
import org.apache.spark.ml.feature.VectorAssembler
import org.apache.spark.ml.regression.GBTRegressor
import org.apache.spark.ml.{Pipeline, PipelineModel}
import org.apache.spark.sql.SparkSession
import org.apache.spark.sql.functions.{abs, col, expr}
import play.api.inject.ApplicationLifecycle
import scala.util.{Failure, Success, Try}


/**
  * @project CSYE7200_FinalProject
  * @author
  */
@Singleton
class datasetProcess @Inject() (lifeCycle: ApplicationLifecycle){

  lazy val spark: SparkSession = SparkSession.builder()
          .appName("Classifier")
          .config("executor-memory", 8)
          .config("executor-cores", 4)
          .master("local")
          .getOrCreate()

  val model: PipelineModel = {
    // val t = spark.read.option("header","true").option("inferschema","true").csv("train.csv")

    // val train1 = t.withColumn("diff_long",expr("dropoff_longitude - pickup_longitude")).
    //         withColumn("diff_lat",expr("dropoff_latitude - pickup_latitude"))
    // val train2=train1.withColumn("diff_long",abs(col("diff_long"))).
    //         withColumn("diff_lat",abs(col("diff_lat")))
    // val train3 = train2.na.drop()
    // val train4 = train3.filter(col("diff_long") < 5).filter(col("diff_lat") < 5).filter(col("fare_amount") > 0).toDF()
    // val train5 = train4.drop(col("pickup_datetime")).drop(col("key")).toDF()
    // val sampledData = train5.sample(true, 0.000001)

    // val inputCol = sampledData.columns.filter(!_.equals("fare_amount"))
    // val inputVec = new VectorAssembler().setInputCols(inputCol).setOutputCol("features")

    import org.apache.spark.ml.regression.{GBTRegressionModel, GBTRegressor}

    val dataset = spark.read.option("header","true").option("inferSchema","true").csv("train.csv")

    val data = dataset.where("pickup_longitude!=0 and pickup_latitude!=0 and dropoff_longitude!=0 and dropoff_latitude!=0")
    val assembler = new VectorAssembler().setInputCols(Array("pickup_longitude", "pickup_latitude","dropoff_longitude","dropoff_latitude", "passenger_count")).setOutputCol("features")
    val output = assembler.transform(data).select("features", "fare_amount")

    val gbt = new GBTRegressor().setLabelCol("fare_amount").setFeaturesCol("features")
    val pipeline = new Pipeline().setStages(Array(gbt))

    val sample = output.sample(true, 0.000001)

    val Array(train, test) = sample.randomSplit(Array(0.8, 0.2))

    pipeline.fit(train)

//     val gbt = new GBTRegressor().setLabelCol("fare_amount").setFeaturesCol("features")
//     val pipeline = new Pipeline().setStages(Array(inputVec, gbt))
//     val Array(train, test) = sampledData.randomSplit(Array(0.8, 0.2))
//     pipeline.fit(train)
// //    PipelineModel.load("datas/trained_model")
  }
//
//  lazy val model: PipelineModel = {
//    PipelineModel.read.session(spark).load("/Users/jimzhou/Documents/GitHub/CSYE7200_FinalProject/csye7200_finalproject/public/datas/trained_model")
//  }

//  val inputCol :Array[String] = Array("pickup_longitude", "pickup_latitude", "dropoff_longitude", "dropoff_latitude", "passenger_count")
//  val inputVec: VectorAssembler = new VectorAssembler().setInputCols(inputCol).setOutputCol("features")

//  def loadModel(): PipelineModel = {
////    val gbt = new GBTRegressor().setLabelCol("fare_amount").setFeaturesCol("features")
////    val pipeline = new Pipeline().setStages(Array(inputVec, gbt))
////    val cv = new CrossValidator().setEstimator(pipeline)
//    PipelineModel.load("/Users/jimzhou/Documents/GitHub/CSYE7200_FinalProject/csye7200_finalproject/app/mymodel/trained_model")
////    PipelineModel.load("/Users/jimzhou/Documents/GitHub/CSYE7200_FinalProject/csye7200_finalproject/app/mymodel/trained_model")
//  }
  def saveModel(): Unit = {
    model.write.overwrite().save("/Users/jimzhou/Documents/GitHub/CSYE7200_FinalProject/csye7200_finalproject/app/mymodel/trained_model")
  }

  def getTestResult(myRecord: MyRecord): MyRecord = {
    import spark.implicits._
    val data = Seq(
      (0, myRecord.plng, myRecord.plat, myRecord.dlng, myRecord.dlat, myRecord.pc)
    ).toDF("fare_amount", "pickup_longitude", "pickup_latitude", "dropoff_longitude", "dropoff_latitude", "passenger_count")
    // val train1 = data.withColumn("diff_long",expr("dropoff_longitude - pickup_longitude")).
    //         withColumn("diff_lat",expr("dropoff_latitude - pickup_latitude"))
    // val train2=train1.withColumn("diff_long",abs(col("diff_long"))).
    //         withColumn("diff_lat",abs(col("diff_lat")))

    val assembler = new VectorAssembler().setInputCols(Array("pickup_longitude", "pickup_latitude","dropoff_longitude","dropoff_latitude", "passenger_count")).setOutputCol("features")
    val sampleData = assembler.transform(data).select("features", "fare_amount")
    
    // val sampleData = train2.sample(true, 1)
    val result = model.transform(sampleData)
    val fare: Option[Double] = Try(result.select(col("prediction")).head().getDouble(0)) match {
      case Success(a) => Option(a)
      case Failure(e) => Option(0.1.toDouble)
    }

    MyRecord(fare, myRecord.plng, myRecord.plat, myRecord.dlng, myRecord.dlat, myRecord.pc)
  }

  /** **************************************************************************************************************************************************
    * **************************************************************************************************************************************************
    * **************************************************************************************************************************************************
    * **************************************************************************************************************************************************
    * **************************************************************************************************************************************************
    * **************************************************************************************************************************************************
    * **************************************************************************************************************************************************
    * **************************************************************************************************************************************************
    * */


  /**
    * this method is used to first process the dataset into the format we want to use when we do training*/
  def trainandprocess(path: String): Unit = {
    val t = spark.read.option("header","true").option("inferschema","true").csv(path)

    val train1 = t.withColumn("diff_long",expr("dropoff_longitude - pickup_longitude")).
            withColumn("diff_lat",expr("dropoff_latitude - pickup_latitude"))
    val train2=train1.withColumn("diff_long",abs(col("diff_long"))).
            withColumn("diff_lat",abs(col("diff_lat")))
    val train3 = train2.na.drop()
    val train4 = train3.filter(col("diff_long") < 5).filter(col("diff_lat") < 5).filter(col("fare_amount") > 0).toDF()
    val train5 = train4.drop(col("pickup_datetime")).drop(col("key")).drop(col("diff_lat")).drop(col("diff_long")).toDF()
    train5.coalesce(1).write.option("header","true").option("format","csv").save("mydatas/train.csv")
    //    train5.coalesce(1).write.option("format","csv").option("mode","append").option("sep","\t").save("datas/train.csv")
    val sampledData = train5.sample(true, 1)

    val inputCol = sampledData.columns.filter(!_.equals("fare_amount"))
    val inputVec = new VectorAssembler().setInputCols(inputCol).setOutputCol("features")
    val gbt = new GBTRegressor().setLabelCol("fare_amount").setFeaturesCol("features")
    val pipeline = new Pipeline().setStages(Array(inputVec, gbt))
    val model = pipeline.fit(sampledData)
    saveModel()
  }

  def retrainDataSet() = {
    val t = spark.read.option("header","true").option("inferschema","true").csv("datas/train.csv")
    val train1 = t.withColumn("diff_long",expr("dropoff_longitude - pickup_longitude")).
            withColumn("diff_lat",expr("dropoff_latitude - pickup_latitude"))
    val train2=train1.withColumn("diff_long",abs(col("diff_long"))).
            withColumn("diff_lat",abs(col("diff_lat")))
    val train3 = train2.na.drop()
    val train4 = train3.filter(col("diff_long") < 5).filter(col("diff_lat") < 5).filter(col("fare_amount") > 0).toDF()
    val sampledData = train4.sample(true, 1)

    val inputCol :Array[String] = Array("pickup_longitude", "pickup_latitude", "dropoff_longitude", "dropoff_latitude", "passenger_count")
    val inputVec: VectorAssembler = new VectorAssembler().setInputCols(inputCol).setOutputCol("features")

    val gbt = new GBTRegressor().setLabelCol("fare_amount").setFeaturesCol("features")
    val pipeline = new Pipeline().setStages(Array(inputVec, gbt))
    val model = pipeline.fit(sampledData)
    saveModel()
  }
}

